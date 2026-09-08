package pixl.rec.core.stream

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
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.SSLSocketFactory

/**
 * RTMP / RTMPS Streaming Connection Engine.
 * Manages TCP/TLS socket connections, handshake, command negotiation,
 * and high-throughput non-blocking asynchronous streaming with backpressure protection.
 */
class RtmpConnection(
    private val scope: CoroutineScope
) {
    sealed interface State {
        object Idle : State
        object Connecting : State
        object Handshaking : State
        object Connected : State
        object Publishing : State
        object Streaming : State
        object Disconnected : State
        data class Error(val message: String, val cause: Throwable? = null) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var socket: Socket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private val chunkStream = RtmpChunkStream(RtmpChunkStream.DEFAULT_CHUNK_SIZE)
    private var assignedStreamId: Int = 1

    // Bounded channel to buffer media packets with backpressure drop-oldest protection
    private var mediaChannel: Channel<RtmpPacket>? = null
    private var streamingJob: Job? = null
    private var readerJob: Job? = null

    // Telemetry & Metrics
    val totalBytesSent = AtomicLong(0L)
    val totalFramesSent = AtomicLong(0L)
    val totalFramesDropped = AtomicLong(0L)

    private val isRunning = AtomicBoolean(false)

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

        try {
            _state.value = State.Connecting

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
            val bufIn = BufferedInputStream(rawIn, 64 * 1024)
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

            // Start dedicated asynchronous streaming writer loop
            val channel = Channel<RtmpPacket>(
                capacity = 64,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )
            mediaChannel = channel

            streamingJob = scope.launch(Dispatchers.IO) {
                runStreamingLoop(channel, bufOut)
            }

            // Start background reader loop for ping/ack and onStatus events
            readerJob = scope.launch(Dispatchers.IO) {
                runReaderLoop(bufIn)
            }

            _state.value = State.Streaming
            onReady?.invoke()

        } catch (e: Throwable) {
            _state.value = State.Error(e.message ?: "RTMP Connection Failed", e)
            closeInternal()
        }
    }

    /**
     * Enqueues an audio, video, or metadata packet for transmission to the RTMP server.
     * Guaranteed non-blocking on the caller's thread (e.g. MediaCodec output thread).
     */
    fun enqueuePacket(packet: RtmpPacket): Boolean {
        val channel = mediaChannel ?: return false
        val assignedPacket = if (packet.streamId == 0 && packet.messageType in setOf(
                RtmpPacket.TYPE_VIDEO, RtmpPacket.TYPE_AUDIO, RtmpPacket.TYPE_DATA_AMF0
            )
        ) {
            packet.copy(streamId = assignedStreamId)
        } else {
            packet
        }

        val sent = channel.trySend(assignedPacket).isSuccess
        if (sent) {
            totalFramesSent.incrementAndGet()
        } else {
            totalFramesDropped.incrementAndGet()
        }
        return sent
    }

    /**
     * Dedicated coroutine write loop consuming queued media packets.
     */
    private suspend fun runStreamingLoop(
        channel: Channel<RtmpPacket>,
        outputStream: OutputStream
    ) {
        try {
            for (packet in channel) {
                chunkStream.writePacket(packet, outputStream)
                outputStream.flush()
                totalBytesSent.addAndGet(packet.payload.size.toLong())
            }
        } catch (e: Throwable) {
            if (isRunning.get()) {
                _state.value = State.Error("Stream write loop interrupted: ${e.message}", e)
                closeInternal()
            }
        }
    }

    /**
     * Background reader loop to handle incoming server pings, acks, and status messages.
     */
    private suspend fun runReaderLoop(inputStream: InputStream) {
        while (isRunning.get()) {
            try {
                val packet = chunkStream.readPacket(inputStream)
                when (packet.messageType) {
                    RtmpPacket.TYPE_USER_CONTROL -> {
                        // Responds to Ping Request if needed
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
                // Heartbeat/idle timeout on incoming socket; normal when server has no incoming packets to send
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
            mediaChannel?.close()
            mediaChannel = null
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

        if (_state.value !is State.Error) {
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
