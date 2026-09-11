package pixl.rec.core.stream

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import pixl.rec.core.model.DestinationTelemetry
import pixl.rec.core.model.DropReason
import pixl.rec.core.model.PacketMediaType
import pixl.rec.core.engine.UplinkHealth
import pixl.rec.core.model.WriteLatencyMetrics
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLSocketFactory

/**
 * Counting stream wrapper tracking cumulative bytes received from the socket
 * for RTMP Window Acknowledgement calculations.
 */
class CountingInputStream(private val inner: InputStream) : InputStream() {
    var bytesRead: Long = 0L
        private set

    override fun read(): Int {
        val b = inner.read()
        if (b != -1) bytesRead++
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val count = inner.read(b, off, len)
        if (count > 0) bytesRead += count
        return count
    }

    override fun available(): Int = inner.available()
    override fun close() = inner.close()
}

/**
 * High-performance, zero-copy RTMP/RTMPS client connection.
 * Implements direct TCP/TLS streaming pipeline, C0/C1/S0/S1/S2 handshake,
 * AMF0 control signaling, and bounded non-blocking packet queueing.
 */
class RtmpConnection(
    val scope: CoroutineScope
) {
    private val tag = "RtmpConnection"

    sealed class State {
        object Idle : State()
        object Connecting : State()
        object Handshaking : State()
        object Publishing : State()
        object Streaming : State()
        data class Reconnecting(val attempt: Int, val maxAttempts: Int) : State()
        object Disconnected : State()
        data class Error(val message: String, val cause: Throwable? = null) : State()
        data class Unhealthy(val reason: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _uplinkHealth = MutableStateFlow(UplinkHealth.CLEAN)
    val uplinkHealth: StateFlow<UplinkHealth> = _uplinkHealth.asStateFlow()

    fun setReconnecting(attempt: Int, maxAttempts: Int) {
        _state.value = State.Reconnecting(attempt, maxAttempts)
    }

    fun setUnhealthy(reason: String) {
        _state.value = State.Unhealthy(reason)
        closeInternal()
    }

    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private val chunkStream = RtmpChunkStream(RtmpChunkStream.DEFAULT_CHUNK_SIZE)
    private var assignedStreamId: Int = 1

    // Priority channel for control messages (pings, acks, amf0 commands)
    private var controlChannel: Channel<RtmpPacket>? = null
    // Dedicated channel for audio packets guaranteeing audio continuity
    private var audioChannel: Channel<RtmpPacket>? = null
    // Dedicated channel for video packets with backpressure drop-oldest protection
    private var videoChannel: Channel<RtmpPacket>? = null
    private var streamingJob: Job? = null
    private var readerJob: Job? = null

    var onRequestSyncFrame: (() -> Unit)? = null
    var onInterFrameEvicted: (() -> Unit)? = null

    private var windowAckSize: Int = 2_500_000
    private var lastAckedBytes: Long = 0L

    var targetPlatformName: String = ""
    var targetEndpointUrl: String = ""

    // Telemetry & Metrics (Phase 0)
    val totalBytesSent = AtomicLong(0L)
    val totalFramesSent = AtomicLong(0L)
    val totalFramesDropped = AtomicLong(0L)

    val currentQueuePackets = AtomicInteger(0)
    val bytesInQueue = AtomicLong(0L)
    val lastObservedQueueAgeMs = AtomicLong(0L)
    val timeAboveLatencyBudgetMs = AtomicLong(0L)
    val writeLatencyMetrics = WriteLatencyMetrics(128)

    val droppedAudio = AtomicLong(0L)
    val droppedVideoKey = AtomicLong(0L)
    val droppedVideoInter = AtomicLong(0L)
    val droppedHeaders = AtomicLong(0L)

    val reconnectCount = AtomicInteger(0)
    val lastReconnectDurationMs = AtomicLong(0L)
    val timeToFirstKeyframeMs = AtomicLong(0L)

    val LATENCY_BUDGET_MS = 1000L

    private val isRunning = AtomicBoolean(false)

    fun recordDrop(packet: RtmpPacket, reason: DropReason) {
        totalFramesDropped.incrementAndGet()
        when (packet.mediaType) {
            PacketMediaType.AUDIO -> droppedAudio.incrementAndGet()
            PacketMediaType.VIDEO_KEYFRAME -> droppedVideoKey.incrementAndGet()
            PacketMediaType.VIDEO_INTER -> droppedVideoInter.incrementAndGet()
            PacketMediaType.VIDEO_SEQUENCE_HEADER,
            PacketMediaType.AUDIO_SEQUENCE_HEADER -> droppedHeaders.incrementAndGet()
            PacketMediaType.METADATA_COMMAND -> {}
        }
    }

    /**
     * Connects to the given RTMP/RTMPS server and begins live publishing.
     *
     * @param endpointUrl Base RTMP endpoint (e.g. "rtmp://a.rtmp.youtube.com/live2" or "rtmps://...")
     * @param streamKey Secret stream key provided by the broadcasting service
     * @param onReady Callback invoked when connection is fully established and ready for video/audio frames
     */
    suspend fun connectAndPublish(
        endpointUrl: String,
        streamKey: String,
        onReady: (() -> Unit)? = null
    ) = withContext(Dispatchers.IO) {
        if (isRunning.getAndSet(true)) {
            return@withContext
        }

        if (_state.value is State.Unhealthy) {
            isRunning.set(false)
            return@withContext
        }

        try {
            _state.value = State.Connecting
            targetEndpointUrl = endpointUrl

            val uri = URI(endpointUrl.trim())
            val scheme = uri.scheme?.lowercase() ?: "rtmp"
            val isRtmps = scheme == "rtmps"
            val host = uri.host ?: throw IllegalArgumentException("Missing host in RTMP endpoint: $endpointUrl")
            val port = if (uri.port != -1) uri.port else if (isRtmps) 443 else 1935
            val app = uri.path?.trimStart('/') ?: "live"
            val tcUrl = "$scheme://$host:$port/$app"

            // 1. Establish Socket Connection
            val newSocket = if (isRtmps) {
                SSLSocketFactory.getDefault().createSocket(host, port)
            } else {
                Socket().apply {
                    connect(InetSocketAddress(host, port), 10_000)
                }
            }

            newSocket.tcpNoDelay = true
            newSocket.sendBufferSize = 256 * 1024
            newSocket.soTimeout = 15_000
            socket = newSocket

            val rawIn = newSocket.getInputStream()
            val rawOut = newSocket.getOutputStream()
            val countingIn = CountingInputStream(rawIn)
            val bufIn = BufferedInputStream(countingIn, 64 * 1024)
            val bufOut = BufferedOutputStream(rawOut, 64 * 1024)
            inputStream = bufIn
            outputStream = bufOut

            // 2. Perform C0/C1 and S0/S1/S2 Handshake
            _state.value = State.Handshaking
            RtmpHandshake.performHandshake(bufIn, bufOut)

            // 3. Negotiate Target Chunk Size (4096 bytes)
            val setChunkPacket = chunkStream.createSetChunkSizePacket(RtmpChunkStream.TARGET_CHUNK_SIZE)
            chunkStream.writePacket(setChunkPacket, bufOut)
            bufOut.flush()
            chunkStream.outChunkSize = RtmpChunkStream.TARGET_CHUNK_SIZE

            // 4. Send Connect Command
            _state.value = State.Publishing
            val connectPayload = Amf0.encodeConnect(
                transactionId = 1.0,
                app = app,
                tcUrl = tcUrl
            )
            val connectPacket = RtmpPacket(
                messageType = RtmpPacket.TYPE_COMMAND_AMF0,
                timestamp = 0,
                streamId = 0,
                payload = connectPayload,
                csid = RtmpPacket.CSID_COMMAND
            )
            chunkStream.writePacket(connectPacket, bufOut)
            bufOut.flush()

            // 5. Read Server Connect Response (_result for transaction 1)
            var connected = false
            while (!connected) {
                val packet = chunkStream.readPacket(bufIn)
                if (packet.messageType == RtmpPacket.TYPE_COMMAND_AMF0) {
                    val resp = Amf0.parseCommand(packet.payload)
                    if (resp != null) {
                        if (resp.commandName == "_result" && resp.transactionId == 1.0) {
                            connected = true
                        } else if (resp.commandName == "_error") {
                            throw IllegalStateException("Server rejected connect command: ${resp.information}")
                        }
                    }
                }
            }

            // 6. Send releaseStream & FCPublish (FMS / NGINX / YouTube standard)
            val releasePayload = Amf0.encodeReleaseStream(transactionId = 2.0, streamKey = streamKey)
            chunkStream.writePacket(
                RtmpPacket(RtmpPacket.TYPE_COMMAND_AMF0, 0, 0, releasePayload),
                bufOut
            )

            val fcPublishPayload = Amf0.encodeFCPublish(transactionId = 3.0, streamKey = streamKey)
            chunkStream.writePacket(
                RtmpPacket(RtmpPacket.TYPE_COMMAND_AMF0, 0, 0, fcPublishPayload),
                bufOut
            )

            // 7. Send createStream Command
            val createStreamPayload = Amf0.encodeCreateStream(transactionId = 4.0)
            chunkStream.writePacket(
                RtmpPacket(RtmpPacket.TYPE_COMMAND_AMF0, 0, 0, createStreamPayload),
                bufOut
            )
            bufOut.flush()

            // 8. Wait for createStream result to receive streamId
            var streamCreated = false
            while (!streamCreated) {
                val packet = chunkStream.readPacket(bufIn)
                if (packet.messageType == RtmpPacket.TYPE_COMMAND_AMF0) {
                    val resp = Amf0.parseCommand(packet.payload)
                    if (resp != null) {
                        if (resp.commandName == "_result" && resp.transactionId == 4.0) {
                            val id = resp.streamId?.toInt() ?: 1
                            assignedStreamId = id
                            streamCreated = true
                        } else if (resp.commandName == "_error") {
                            throw IllegalStateException("Server failed createStream: ${resp.information}")
                        }
                    }
                }
            }

            // 9. Send publish Command
            val publishPayload = Amf0.encodePublish(
                transactionId = 5.0,
                streamKey = streamKey,
                publishType = "live"
            )
            chunkStream.writePacket(
                RtmpPacket(RtmpPacket.TYPE_COMMAND_AMF0, 0, assignedStreamId, publishPayload),
                bufOut
            )
            bufOut.flush()

            // Once publishing is negotiated, disable socket read timeout so idle server incoming socket never disconnects
            newSocket.soTimeout = 0

            // Start dedicated asynchronous streaming writer loop with prioritized control & audio channels
            val ctrlChannel = Channel<RtmpPacket>(capacity = 32)
            val aChannel = createAudioChannel(capacity = 64)
            val vChannel = createVideoChannel(capacity = 64)
            controlChannel = ctrlChannel
            audioChannel = aChannel
            videoChannel = vChannel

            streamingJob = scope.launch(Dispatchers.IO) {
                runStreamingLoop(ctrlChannel, aChannel, vChannel, bufOut)
            }

            // Start background reader loop for ping/ack and onStatus events
            readerJob = scope.launch(Dispatchers.IO) {
                runReaderLoop(bufIn, countingIn)
            }

            _state.value = State.Streaming
            onReady?.invoke()

        } catch (e: Throwable) {
            _state.value = State.Error(e.message ?: "RTMP Connection Failed", e)
            closeInternal()
        }
    }

    internal fun createAudioChannel(capacity: Int = 64): Channel<RtmpPacket> {
        return Channel(
            capacity = capacity,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
            onUndeliveredElement = { displacedPacket ->
                currentQueuePackets.decrementAndGet()
                bytesInQueue.addAndGet(-displacedPacket.payload.size.toLong())
                recordDrop(displacedPacket, DropReason.QUEUE_OVERFLOW)
            }
        )
    }

    internal fun createVideoChannel(capacity: Int = 64): Channel<RtmpPacket> {
        return Channel(
            capacity = capacity,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
            onUndeliveredElement = { displacedPacket ->
                currentQueuePackets.decrementAndGet()
                bytesInQueue.addAndGet(-displacedPacket.payload.size.toLong())
                recordDrop(displacedPacket, DropReason.QUEUE_OVERFLOW)
                if (displacedPacket.mediaType == PacketMediaType.VIDEO_INTER) {
                    onInterFrameEvicted?.invoke()
                    onRequestSyncFrame?.invoke()
                }
            }
        )
    }

    internal fun initChannelForTesting(
        capacity: Int = 64,
        audioCapacity: Int = capacity,
        videoCapacity: Int = capacity
    ) {
        controlChannel = Channel(capacity = 32)
        audioChannel = createAudioChannel(audioCapacity)
        videoChannel = createVideoChannel(videoCapacity)
    }

    internal fun getControlChannel(): Channel<RtmpPacket>? = controlChannel
    internal fun getAudioChannel(): Channel<RtmpPacket>? = audioChannel
    internal fun getVideoChannel(): Channel<RtmpPacket>? = videoChannel

    /**
     * Enqueues a high-priority protocol control packet (e.g. PingResponse, Acknowledgement).
     */
    fun sendControlPacket(packet: RtmpPacket): Boolean {
        val channel = controlChannel ?: return false
        val res = channel.trySend(packet)
        return res.isSuccess
    }

    /**
     * Enqueues an audio, video, or metadata packet for transmission to the RTMP server.
     * Guaranteed non-blocking on the caller's thread (e.g. MediaCodec output thread).
     * Routes audio to dedicated audioChannel and video to videoChannel.
     */
    fun enqueuePacket(packet: RtmpPacket): Boolean {
        val assignedPacket = if (packet.streamId == 0 && packet.messageType in setOf(
                RtmpPacket.TYPE_VIDEO, RtmpPacket.TYPE_AUDIO, RtmpPacket.TYPE_DATA_AMF0
            )
        ) {
            packet.copy(streamId = assignedStreamId)
        } else {
            packet
        }

        val channel = if (assignedPacket.mediaType == PacketMediaType.AUDIO ||
            assignedPacket.mediaType == PacketMediaType.AUDIO_SEQUENCE_HEADER ||
            assignedPacket.messageType == RtmpPacket.TYPE_AUDIO
        ) {
            audioChannel
        } else {
            videoChannel
        } ?: run {
            recordDrop(assignedPacket, DropReason.CONNECTION_CLOSED)
            return false
        }

        currentQueuePackets.incrementAndGet()
        bytesInQueue.addAndGet(assignedPacket.payload.size.toLong())

        val result = channel.trySend(assignedPacket)
        if (result.isSuccess) {
            totalFramesSent.incrementAndGet()
            return true
        } else {
            currentQueuePackets.decrementAndGet()
            bytesInQueue.addAndGet(-assignedPacket.payload.size.toLong())
            recordDrop(assignedPacket, DropReason.QUEUE_OVERFLOW)
            if (assignedPacket.mediaType == PacketMediaType.VIDEO_INTER) {
                onInterFrameEvicted?.invoke()
                onRequestSyncFrame?.invoke()
            }
            return false
        }
    }

    /**
     * Immediately purges all pending video frames from [videoChannel].
     * Invoked upon Privacy Shield engagement to guarantee no stale captured frames leak to the network.
     */
    fun purgeVideoQueue(): Int {
        val channel = videoChannel ?: return 0
        var purgedCount = 0
        var purgedBytes = 0L
        while (true) {
            val res = channel.tryReceive()
            if (res.isSuccess) {
                val p = res.getOrThrow()
                purgedCount++
                purgedBytes += p.payload.size
                currentQueuePackets.decrementAndGet()
                recordDrop(p, DropReason.QUEUE_OVERFLOW)
            } else {
                break
            }
        }
        bytesInQueue.addAndGet(-purgedBytes)
        if (purgedCount > 0) {
            Log.i(tag, "Purged $purgedCount in-flight video packets ($purgedBytes bytes) from video queue")
        }
        return purgedCount
    }

    /**
     * Dedicated coroutine write loop consuming queued media packets with strict control & audio priority.
     * Selects: controlChannel > audioChannel > videoChannel.
     * Stale video inter-frames exceeding LATENCY_BUDGET_MS are evicted to clear backlog.
     */
    private suspend fun runStreamingLoop(
        ctrlChannel: Channel<RtmpPacket>,
        aChannel: Channel<RtmpPacket>,
        vChannel: Channel<RtmpPacket>,
        outputStream: OutputStream
    ) {
        try {
            while (isRunning.get()) {
                // 1. Flush any pending high-priority control packets first
                var ctrl = ctrlChannel.tryReceive().getOrNull()
                while (ctrl != null) {
                    writePacketDirect(ctrl, outputStream)
                    ctrl = ctrlChannel.tryReceive().getOrNull()
                }

                // 2. Flush any pending audio packets to guarantee continuous audio playback
                var audio = aChannel.tryReceive().getOrNull()
                while (audio != null) {
                    currentQueuePackets.decrementAndGet()
                    bytesInQueue.addAndGet(-audio.payload.size.toLong())
                    writePacketDirect(audio, outputStream)
                    audio = aChannel.tryReceive().getOrNull()
                }

                // 3. Select across control, audio, and video
                val packet: RtmpPacket? = select {
                    ctrlChannel.onReceiveCatching { it.getOrNull() }
                    aChannel.onReceiveCatching { it.getOrNull() }
                    vChannel.onReceiveCatching { it.getOrNull() }
                }

                if (packet == null) {
                    break
                }

                if (packet.csid == RtmpPacket.CSID_CONTROL || packet.messageType in setOf(
                        RtmpPacket.TYPE_USER_CONTROL, RtmpPacket.TYPE_ACK,
                        RtmpPacket.TYPE_WINDOW_ACK_SIZE, RtmpPacket.TYPE_SET_CHUNK_SIZE
                    )
                ) {
                    writePacketDirect(packet, outputStream)
                } else {
                    currentQueuePackets.decrementAndGet()
                    bytesInQueue.addAndGet(-packet.payload.size.toLong())

                    val ageMs = ((System.nanoTime() - packet.enqueueTimeNs) / 1_000_000L).coerceAtLeast(0L)
                    lastObservedQueueAgeMs.set(ageMs)
                    if (ageMs > LATENCY_BUDGET_MS) {
                        timeAboveLatencyBudgetMs.addAndGet(ageMs - LATENCY_BUDGET_MS)
                        if (packet.mediaType == PacketMediaType.VIDEO_INTER) {
                            // Discard stale delta frame to clear latency backlog
                            recordDrop(packet, DropReason.LATENCY_BUDGET_EXCEEDED)
                            onInterFrameEvicted?.invoke()
                            onRequestSyncFrame?.invoke()
                            continue
                        }
                    }

                    writePacketDirect(packet, outputStream)
                }
            }
        } catch (e: Throwable) {
            if (isRunning.get()) {
                _state.value = State.Error("Stream write loop interrupted: ${e.message}", e)
                closeInternal()
            }
        }
    }

    private fun writePacketDirect(packet: RtmpPacket, outputStream: OutputStream) {
        val writeStartNs = System.nanoTime()
        chunkStream.writePacket(packet, outputStream)
        outputStream.flush()
        val writeDurationMs = ((System.nanoTime() - writeStartNs) / 1_000_000L).coerceAtLeast(0L)
        writeLatencyMetrics.record(writeDurationMs)
        totalBytesSent.addAndGet(packet.payload.size.toLong())
    }

    /**
     * Generates a point-in-time telemetry snapshot for this destination connection.
     */
    fun getTelemetry(destinationId: String = ""): DestinationTelemetry {
        val (p50, p95, p99) = writeLatencyMetrics.getPercentiles()
        return DestinationTelemetry(
            destinationId = destinationId,
            platformName = targetPlatformName,
            endpointUrl = targetEndpointUrl,
            isConnected = _state.value is State.Streaming,
            bytesTransmitted = totalBytesSent.get(),
            bytesInQueue = bytesInQueue.get().coerceAtLeast(0L),
            queueDepthPackets = currentQueuePackets.get().coerceAtLeast(0),
            oldestPacketAgeMs = lastObservedQueueAgeMs.get().coerceAtLeast(0L),
            timeAboveLatencyBudgetMs = timeAboveLatencyBudgetMs.get().coerceAtLeast(0L),
            writeLatencyP50Ms = p50,
            writeLatencyP95Ms = p95,
            writeLatencyP99Ms = p99,
            droppedAudio = droppedAudio.get(),
            droppedVideoKey = droppedVideoKey.get(),
            droppedVideoInter = droppedVideoInter.get(),
            droppedHeaders = droppedHeaders.get(),
            reconnectCount = reconnectCount.get(),
            lastReconnectDurationMs = lastReconnectDurationMs.get(),
            timeToFirstKeyframeMs = timeToFirstKeyframeMs.get()
        )
    }

    /**
     * Background reader loop to handle incoming server pings, acks, chunk size adjustments, and status messages.
     */
    private suspend fun runReaderLoop(inputStream: InputStream, countingIn: CountingInputStream) {
        while (isRunning.get()) {
            try {
                val packet = chunkStream.readPacket(inputStream)

                // Check if cumulative bytes read exceeds windowAckSize to dispatch Acknowledgement (0x03)
                if (windowAckSize > 0) {
                    val currentBytes = countingIn.bytesRead
                    if (currentBytes - lastAckedBytes >= windowAckSize) {
                        lastAckedBytes = currentBytes
                        sendControlPacket(RtmpPacket.createAcknowledgement(currentBytes))
                    }
                }

                when (packet.messageType) {
                    RtmpPacket.TYPE_SET_CHUNK_SIZE -> {
                        if (packet.payload.size >= 4) {
                            val newSize = ByteBuffer.wrap(packet.payload, 0, 4).int
                            if (newSize in 128..65536) {
                                chunkStream.inChunkSize = newSize
                            }
                        }
                    }
                    RtmpPacket.TYPE_WINDOW_ACK_SIZE -> {
                        if (packet.payload.size >= 4) {
                            val size = ByteBuffer.wrap(packet.payload, 0, 4).int
                            if (size > 0) {
                                windowAckSize = size
                            }
                        }
                    }
                    RtmpPacket.TYPE_SET_PEER_BANDWIDTH -> {
                        if (packet.payload.size >= 5) {
                            val bandwidth = ByteBuffer.wrap(packet.payload, 0, 4).int
                            val limitType = packet.payload[4].toInt() and 0xFF
                            if (bandwidth > 0) {
                                if (limitType == 0) {
                                    windowAckSize = bandwidth
                                } else if (limitType == 1) {
                                    windowAckSize = minOf(windowAckSize, bandwidth)
                                } else if (limitType == 2) {
                                    windowAckSize = bandwidth
                                }
                            }
                        }
                    }
                    RtmpPacket.TYPE_USER_CONTROL -> {
                        if (packet.payload.size >= 6) {
                            val eventType = ((packet.payload[0].toInt() and 0xFF) shl 8) or (packet.payload[1].toInt() and 0xFF)
                            if (eventType == 0x0006) { // PingRequest -> PingResponse
                                val timestamp = ByteBuffer.wrap(packet.payload, 2, 4).int
                                sendControlPacket(RtmpPacket.createPingResponse(timestamp))
                            }
                        }
                    }
                    RtmpPacket.TYPE_COMMAND_AMF0 -> {
                        val resp = Amf0.parseCommand(packet.payload)
                        if (resp?.commandName == "onStatus") {
                            val code = resp.statusCode
                            if (code == "NetStream.Publish.BadName" || code == "NetStream.Publish.Denied") {
                                _state.value = State.Error("Broadcast rejected by server: $code")
                                closeInternal()
                                break
                            }
                        }
                    }
                }
            } catch (_: java.net.SocketTimeoutException) {
                continue
            } catch (e: Throwable) {
                if (isRunning.get() && e !is EOFException) {
                    _state.value = State.Error("Stream read loop error: ${e.message}", e)
                    closeInternal()
                }
                break
            }
        }
    }

    /**
     * Gracefully tears down the connection.
     */
    fun disconnect() {
        if (!isRunning.getAndSet(false)) return

        scope.launch(Dispatchers.IO) {
            closeInternal()
        }
    }

    private fun closeInternal() {
        isRunning.set(false)
        try {
            controlChannel?.close()
            controlChannel = null
            audioChannel?.close()
            audioChannel = null
            videoChannel?.close()
            videoChannel = null
        } catch (_: Throwable) {}

        try {
            streamingJob?.cancel()
            streamingJob = null
            readerJob?.cancel()
            readerJob = null
        } catch (_: Throwable) {}

        try {
            socket?.close()
        } catch (_: Throwable) {}

        socket = null
        inputStream = null
        outputStream = null

        if (_state.value !is State.Error && _state.value !is State.Unhealthy) {
            _state.value = State.Disconnected
        }
    }

    companion object {
        /**
         * Performs a lightweight 1-second pre-flight probe:
         * TCP/TLS socket connection, C0/C1/S0/S1/S2 handshake, chunk negotiation, and AMF0 connect.
         * Closes the socket immediately upon receiving server response.
         */
        suspend fun testProbe(
            endpointUrl: String,
            timeoutMs: Int = 6_000
        ): Result<Unit> = withContext(Dispatchers.IO) {
            var probeSocket: Socket? = null
            try {
                val cleanUrl = endpointUrl.trim()
                if (cleanUrl.isBlank()) {
                    return@withContext Result.failure(IllegalArgumentException("Endpoint URL cannot be blank"))
                }

                val uri = URI(cleanUrl)
                val scheme = uri.scheme?.lowercase() ?: "rtmp"
                val isRtmps = scheme == "rtmps"
                val host = uri.host ?: return@withContext Result.failure(IllegalArgumentException("Missing host in endpoint: $cleanUrl"))
                val port = if (uri.port != -1) uri.port else if (isRtmps) 443 else 1935
                val app = uri.path?.trimStart('/') ?: "live"
                val tcUrl = "$scheme://$host:$port/$app"

                val s = if (isRtmps) {
                    SSLSocketFactory.getDefault().createSocket(host, port)
                } else {
                    Socket().apply {
                        connect(InetSocketAddress(host, port), timeoutMs)
                    }
                }
                probeSocket = s
                s.soTimeout = timeoutMs
                s.tcpNoDelay = true

                val rawIn = s.getInputStream()
                val rawOut = s.getOutputStream()
                val bufIn = BufferedInputStream(rawIn, 16 * 1024)
                val bufOut = BufferedOutputStream(rawOut, 16 * 1024)

                // 1. Handshake
                RtmpHandshake.performHandshake(bufIn, bufOut)

                // 2. Set chunk size
                val probeChunkStream = RtmpChunkStream(RtmpChunkStream.DEFAULT_CHUNK_SIZE)
                val setChunkPacket = probeChunkStream.createSetChunkSizePacket(RtmpChunkStream.TARGET_CHUNK_SIZE)
                probeChunkStream.writePacket(setChunkPacket, bufOut)
                bufOut.flush()
                probeChunkStream.outChunkSize = RtmpChunkStream.TARGET_CHUNK_SIZE

                // 3. Connect Command
                val connectPayload = Amf0.encodeConnect(1.0, app, tcUrl)
                probeChunkStream.writePacket(
                    RtmpPacket(RtmpPacket.TYPE_COMMAND_AMF0, 0, 0, connectPayload, RtmpPacket.CSID_COMMAND),
                    bufOut
                )
                bufOut.flush()

                // 4. Await _result
                var connected = false
                while (!connected) {
                    val packet = probeChunkStream.readPacket(bufIn)
                    if (packet.messageType == RtmpPacket.TYPE_COMMAND_AMF0) {
                        val resp = Amf0.parseCommand(packet.payload)
                        if (resp != null) {
                            if (resp.commandName == "_result" && resp.transactionId == 1.0) {
                                connected = true
                            } else if (resp.commandName == "_error") {
                                return@withContext Result.failure(
                                    IllegalStateException("Server rejected probe: ${resp.information}")
                                )
                            }
                        }
                    }
                }
                Result.success(Unit)
            } catch (e: Throwable) {
                Result.failure(e)
            } finally {
                runCatching { probeSocket?.close() }
            }
        }
    }
}
