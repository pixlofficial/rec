package pixl.rec.core.engine

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import pixl.rec.core.model.AbrTelemetrySignal
import pixl.rec.core.model.DestinationTelemetry
import pixl.rec.core.model.RateAdjustmentReason
import pixl.rec.core.model.SessionStreamTelemetry
import pixl.rec.core.model.StreamConfig
import pixl.rec.core.stream.FlvPacketizer
import pixl.rec.core.stream.RtmpConnection
import pixl.rec.core.stream.RtmpPacket
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Interface representing a destination sink for encoded video and audio frames.
 */
interface StreamOutputTarget {
    fun start()
    fun onVideoFormat(format: MediaFormat)
    fun onVideoSample(buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo)
    fun onAudioFormat(format: MediaFormat)
    fun onAudioSample(buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo)
    fun release()
    fun attachVideoEncoder(videoEncoder: VideoEncoder) {}
    fun getTelemetry(): SessionStreamTelemetry = SessionStreamTelemetry()
}

/**
 * RTMP Live Streaming Output Sink.
 * Translates MediaCodec buffers into FLV tags and delivers them to the RTMP network engine
 * with automatic reconnection handling and Adaptive Bitrate (ABR) rate control.
 */
class RtmpStreamOutputTarget(
    val streamConfig: StreamConfig,
    val scope: CoroutineScope,
    var videoEncoder: VideoEncoder? = null,
    private val onStateChanged: ((RtmpConnection.State) -> Unit)? = null,
    private val onUplinkHealthChanged: ((UplinkHealth) -> Unit)? = null
) : StreamOutputTarget {

    override fun attachVideoEncoder(videoEncoder: VideoEncoder) {
        this.videoEncoder = videoEncoder
    }

    private val tag = "RtmpStreamTarget"
    val connection = RtmpConnection(scope)

    // Pipeline telemetry tracking (Phase 0)
    val totalBytesEncoded = AtomicLong(0L)
    val totalBytesPacketized = AtomicLong(0L)
    val totalVideoFrames = AtomicLong(0L)
    val totalPacketizationTimeNs = AtomicLong(0L)
    val lastVideoPtsUs = AtomicLong(0L)
    val lastAudioPtsUs = AtomicLong(0L)

    private val abrController: AdaptiveBitrateController? = if (streamConfig.enableAbr) {
        AdaptiveBitrateController(
            scope = scope,
            initialBitrateBps = streamConfig.videoBitrate,
            minBitrateBps = streamConfig.minBitrate,
            maxBitrateBps = streamConfig.maxBitrate,
            onBitrateAdjusted = { newBitrate ->
                videoEncoder?.adjustBitrate(newBitrate)
            },
            onRequestSyncFrame = {
                videoEncoder?.requestSyncFrame()
            }
        )
    } else {
        null
    }

    private var isHevc = false
    private var cachedVideoFormat: MediaFormat? = null
    private var cachedAudioFormat: MediaFormat? = null
    private val isRunning = AtomicBoolean(false)
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 3
    val waitingForSyncFrame = AtomicBoolean(false)
    private val isReconnecting = AtomicBoolean(false)
    private var activeReconnectJob: Job? = null

    val connectionState: StateFlow<RtmpConnection.State> = connection.state
    val uplinkHealth: StateFlow<UplinkHealth>? = abrController?.uplinkHealth
    val lastAdjustmentReason: StateFlow<RateAdjustmentReason>? = abrController?.lastAdjustmentReason

    init {
        connection.onRequestSyncFrame = {
            videoEncoder?.requestSyncFrame()
        }
        connection.onInterFrameEvicted = {
            waitingForSyncFrame.set(true)
        }
    }

    override fun start() {
        if (isRunning.getAndSet(true)) return

        scope.launch(Dispatchers.IO) {
            connectWithRetry()
        }

        // Monitor connection state
        scope.launch {
            connection.state.collect { state ->
                onStateChanged?.invoke(state)
                if (state is RtmpConnection.State.Error && isRunning.get()) {
                    Log.w(tag, "Connection error: ${state.message}. Attempting auto-reconnect...")
                    reconnectWithBackoff()
                }
            }
        }

        // Monitor uplink health
        abrController?.let { abr ->
            scope.launch {
                abr.uplinkHealth.collect { health ->
                    onUplinkHealthChanged?.invoke(health)
                }
            }
        }
    }

    private suspend fun connectWithRetry() {
        try {
            connection.connectAndPublish(
                endpointUrl = streamConfig.activeEndpointUrl,
                streamKey = streamConfig.streamKey,
                onReady = {
                    Log.i(tag, "RTMP connection ready for media ingestion")
                    reconnectAttempts = 0
                    isReconnecting.set(false)

                    // Gate video frames until fresh keyframe arrives to avoid decoding corrupt inter-frames
                    waitingForSyncFrame.set(true)

                    // Send cached sequence headers if formats arrived before connection
                    cachedVideoFormat?.let { sendVideoSequenceHeader(it) }
                    cachedAudioFormat?.let { sendAudioSequenceHeader(it) }

                    // Request immediate sync keyframe from hardware video encoder
                    videoEncoder?.requestSyncFrame()

                    // Start measured ABR controller
                    val lastDrops = AtomicLong(connection.totalFramesDropped.get())
                    abrController?.startWithSignal {
                        val currentTotalDrops = connection.totalFramesDropped.get()
                        val prev = lastDrops.getAndSet(currentTotalDrops)
                        val deltaDrops = (currentTotalDrops - prev).coerceAtLeast(0L)
                        val p95 = connection.writeLatencyMetrics.getPercentiles().second
                        AbrTelemetrySignal(
                            newDrops = deltaDrops,
                            queueAgeMs = connection.lastObservedQueueAgeMs.get(),
                            writeLatencyP95Ms = p95,
                            bytesInQueue = connection.bytesInQueue.get(),
                            isAnyHealthyDestinationReconnecting = connection.state.value is RtmpConnection.State.Reconnecting
                        )
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(tag, "Failed to connect to RTMP server", e)
            isReconnecting.set(false)
            reconnectWithBackoff()
        }
    }

    private fun reconnectWithBackoff() {
        if (!isRunning.get()) return
        if (isReconnecting.getAndSet(true)) {
            // Reconnect attempt already in progress
            return
        }

        if (reconnectAttempts >= maxReconnectAttempts) {
            Log.e(tag, "Max auto-reconnect attempts ($maxReconnectAttempts) exhausted. Transitioning destination to UNHEALTHY.")
            isReconnecting.set(false)
            connection.setUnhealthy("Max auto-reconnect attempts ($maxReconnectAttempts) exhausted.")
            return
        }

        reconnectAttempts++
        val baseBackoffMs = (1000L * (1 shl (reconnectAttempts - 1))).coerceAtMost(8000L)
        val jitterMs = kotlin.random.Random.nextLong(0, 500)
        val backoffMs = baseBackoffMs + jitterMs
        Log.i(tag, "Scheduling auto-reconnect attempt $reconnectAttempts/$maxReconnectAttempts in ${backoffMs}ms...")

        activeReconnectJob?.cancel()
        activeReconnectJob = scope.launch(Dispatchers.IO) {
            try {
                connection.setReconnecting(reconnectAttempts, maxReconnectAttempts)
                delay(backoffMs)
                if (isRunning.get()) {
                    isReconnecting.set(false)
                    connectWithRetry()
                } else {
                    isReconnecting.set(false)
                }
            } catch (e: CancellationException) {
                isReconnecting.set(false)
                throw e
            }
        }
    }

    override fun onVideoFormat(format: MediaFormat) {
        cachedVideoFormat = format
        val mime = format.getString(MediaFormat.KEY_MIME) ?: MediaFormat.MIMETYPE_VIDEO_AVC
        isHevc = mime == MediaFormat.MIMETYPE_VIDEO_HEVC

        if (connection.state.value == RtmpConnection.State.Streaming) {
            sendVideoSequenceHeader(format)
        }
    }

    private fun sendVideoSequenceHeader(format: MediaFormat) {
        try {
            if (isHevc) {
                // HEVC: Extract VPS, SPS, PPS from csd-0
                val csd0 = format.getByteBuffer("csd-0")
                if (csd0 != null) {
                    val bytes = ByteArray(csd0.remaining())
                    csd0.get(bytes)
                    csd0.rewind()

                    val nals = FlvPacketizer.extractAnnexBNalUnits(bytes)
                    val vps = nals.find { (it[0].toInt() shr 1 and 0x3F) == 32 } ?: ByteArray(0)
                    val sps = nals.find { (it[0].toInt() shr 1 and 0x3F) == 33 } ?: ByteArray(0)
                    val pps = nals.find { (it[0].toInt() shr 1 and 0x3F) == 34 } ?: ByteArray(0)

                    val seqPacket = FlvPacketizer.createHevcSequenceStartPacket(vps, sps, pps)
                    connection.enqueuePacket(seqPacket)
                    Log.i(tag, "Dispatched Enhanced RTMP HEVC Sequence Start packet")
                }
            } else {
                // AVC: Extract SPS from csd-0 and PPS from csd-1
                val csd0 = format.getByteBuffer("csd-0")
                val csd1 = format.getByteBuffer("csd-1")

                if (csd0 != null && csd1 != null) {
                    val sps = ByteArray(csd0.remaining()).apply { csd0.get(this); csd0.rewind() }
                    val pps = ByteArray(csd1.remaining()).apply { csd1.get(this); csd1.rewind() }

                    // Strip start codes if present in CSD buffers
                    val cleanSps = FlvPacketizer.extractAnnexBNalUnits(sps).firstOrNull() ?: sps
                    val cleanPps = FlvPacketizer.extractAnnexBNalUnits(pps).firstOrNull() ?: pps

                    val seqPacket = FlvPacketizer.createAvcSequenceHeaderPacket(cleanSps, cleanPps)
                    connection.enqueuePacket(seqPacket)
                    Log.i(tag, "Dispatched AVC Sequence Header packet")
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to send video sequence header", e)
        }
    }

    override fun onAudioFormat(format: MediaFormat) {
        cachedAudioFormat = format
        if (connection.state.value == RtmpConnection.State.Streaming) {
            sendAudioSequenceHeader(format)
        }
    }

    private fun sendAudioSequenceHeader(format: MediaFormat) {
        try {
            val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 48000

            val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 2

            val headerPacket = FlvPacketizer.createAacSequenceHeaderPacket(
                sampleRate = sampleRate,
                channels = channelCount
            )
            connection.enqueuePacket(headerPacket)
            Log.i(tag, "Dispatched AAC AudioSpecificConfig sequence header ($sampleRate Hz, $channelCount ch)")
        } catch (e: Exception) {
            Log.e(tag, "Failed to send audio sequence header", e)
        }
    }

    override fun onVideoSample(buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        if (bufferInfo.size <= 0) return
        if (connection.state.value is RtmpConnection.State.Unhealthy) return

        totalBytesEncoded.addAndGet(bufferInfo.size.toLong())
        lastVideoPtsUs.set(bufferInfo.presentationTimeUs)

        val isKeyframe = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0

        // If waiting for a post-reconnect sync frame, drop stale delta frames until keyframe arrives
        if (waitingForSyncFrame.get()) {
            if (isKeyframe) {
                waitingForSyncFrame.set(false)
                Log.i(tag, "Fresh sync keyframe received after reconnect; resuming video transmission")
            } else {
                return
            }
        }

        val timestampMs = (bufferInfo.presentationTimeUs / 1000L).coerceAtLeast(0L)

        val packStartNs = System.nanoTime()

        val bytes = ByteArray(bufferInfo.size)
        val oldPos = buffer.position()
        buffer.position(bufferInfo.offset)
        buffer.get(bytes)
        buffer.position(oldPos)

        val nals = FlvPacketizer.extractAnnexBNalUnits(bytes)
        if (nals.isEmpty()) return

        val packet = if (isHevc) {
            FlvPacketizer.createHevcCodedFramePacket(
                nalUnits = nals,
                isKeyframe = isKeyframe,
                timestampMs = timestampMs
            )
        } else {
            FlvPacketizer.createAvcFramePacket(
                nalUnits = nals,
                isKeyframe = isKeyframe,
                timestampMs = timestampMs
            )
        }

        val packDurationNs = System.nanoTime() - packStartNs
        totalPacketizationTimeNs.addAndGet(packDurationNs)
        totalBytesPacketized.addAndGet(packet.payload.size.toLong())
        totalVideoFrames.incrementAndGet()

        connection.enqueuePacket(packet)
    }

    override fun onAudioSample(buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        if (bufferInfo.size <= 0) return
        if (connection.state.value is RtmpConnection.State.Unhealthy) return

        totalBytesEncoded.addAndGet(bufferInfo.size.toLong())
        lastAudioPtsUs.set(bufferInfo.presentationTimeUs)

        val timestampMs = (bufferInfo.presentationTimeUs / 1000L).coerceAtLeast(0L)
        val bytes = ByteArray(bufferInfo.size)
        val oldPos = buffer.position()
        buffer.position(bufferInfo.offset)
        buffer.get(bytes)
        buffer.position(oldPos)

        val packet = FlvPacketizer.createAacFramePacket(
            data = bytes,
            timestampMs = timestampMs
        )
        totalBytesPacketized.addAndGet(packet.payload.size.toLong())
        connection.enqueuePacket(packet)
    }

    /**
     * Enqueues a pre-packetized shared video frame (Phase 3 single-pass fanout).
     * Applies destination-specific sync frame gating and telemetry tracking.
     */
    fun enqueueSharedVideoPacket(
        packet: RtmpPacket,
        isKeyframe: Boolean,
        ptsUs: Long,
        packDurationNs: Long,
        encodedBytes: Long = 0L
    ) {
        if (connection.state.value is RtmpConnection.State.Unhealthy) return

        if (encodedBytes > 0) {
            totalBytesEncoded.addAndGet(encodedBytes)
        }
        lastVideoPtsUs.set(ptsUs)

        // If this destination is waiting for a sync keyframe, drop delta frames
        if (waitingForSyncFrame.get()) {
            if (isKeyframe) {
                waitingForSyncFrame.set(false)
                Log.i(tag, "Fresh sync keyframe received; resuming video transmission")
            } else {
                return
            }
        }

        totalPacketizationTimeNs.addAndGet(packDurationNs)
        totalBytesPacketized.addAndGet(packet.payload.size.toLong())
        totalVideoFrames.incrementAndGet()

        connection.enqueuePacket(packet)
    }

    /**
     * Purges un-transmitted in-flight video frames from the underlying RTMP queue.
     */
    fun purgeVideoQueue(): Int = connection.purgeVideoQueue()

    /**
     * Enqueues a pre-packetized shared audio frame (Phase 3 single-pass fanout).
     */
    fun enqueueSharedAudioPacket(
        packet: RtmpPacket,
        ptsUs: Long,
        encodedBytes: Long = 0L
    ) {
        if (connection.state.value is RtmpConnection.State.Unhealthy) return

        if (encodedBytes > 0) {
            totalBytesEncoded.addAndGet(encodedBytes)
        }
        lastAudioPtsUs.set(ptsUs)

        totalBytesPacketized.addAndGet(packet.payload.size.toLong())
        connection.enqueuePacket(packet)
    }

    override fun getTelemetry(): SessionStreamTelemetry {
        val frames = totalVideoFrames.get().coerceAtLeast(1L)
        val avgPackUs = (totalPacketizationTimeNs.get() / frames) / 1000L
        val vPts = lastVideoPtsUs.get()
        val aPts = lastAudioPtsUs.get()
        val driftMs = if (vPts > 0 && aPts > 0) Math.abs(vPts - aPts) / 1000L else 0L

        val destTelemetry = connection.getTelemetry(destinationId = streamConfig.platform.name).copy(
            platformName = streamConfig.platform.displayName
        )

        return SessionStreamTelemetry(
            totalBytesEncoded = totalBytesEncoded.get(),
            totalBytesPacketized = totalBytesPacketized.get(),
            encoderOutputFps = videoEncoder?.configuredFramerate?.toFloat() ?: 60f,
            avgPacketizationTimeUs = avgPackUs,
            audioVideoPtsDriftMs = driftMs,
            destinations = mapOf(streamConfig.platform.name to destTelemetry),
            currentBitrateBps = abrController?.currentBitrateBps?.value ?: streamConfig.videoBitrate,
            lastAdjustmentReason = abrController?.lastAdjustmentReason?.value ?: RateAdjustmentReason.NONE
        )
    }

    override fun release() {
        isRunning.set(false)
        isReconnecting.set(false)
        activeReconnectJob?.cancel()
        activeReconnectJob = null
        abrController?.stop()
        connection.disconnect()
    }
}
