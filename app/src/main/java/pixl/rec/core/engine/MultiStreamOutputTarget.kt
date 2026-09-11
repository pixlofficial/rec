package pixl.rec.core.engine

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pixl.rec.core.model.AbrTelemetrySignal
import pixl.rec.core.model.DestinationTelemetry
import pixl.rec.core.model.RateAdjustmentReason
import pixl.rec.core.model.SessionStreamTelemetry
import pixl.rec.core.model.StreamConfig
import pixl.rec.core.model.StreamDestination
import pixl.rec.core.model.StreamPlatform
import pixl.rec.core.stream.FlvPacketizer
import pixl.rec.core.stream.RtmpConnection
import pixl.rec.core.stream.RtmpPacket
import pixl.rec.core.stream.SharedRtmpPacket
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Composite RTMP Live Streaming Sink for multi-destination broadcasting (Multistreaming).
 *
 * Fans out single zero-copy hardware encoded video and audio buffers to multiple
 * independent RTMP connections (e.g. YouTube + Twitch + Kick + Custom RTMP) with:
 * 1. Single-pass packetization: NAL parsing and FLV serialization executed once per frame.
 * 2. Shared reference-counted packet fanout: zero redundant heap copies across child destinations.
 * 3. Independent socket connection failure isolation (a Twitch disconnect does not kill YouTube).
 * 4. Centralized Adaptive Bitrate (ABR) rate control regulating the shared [VideoEncoder]
 *    against aggregate network uplink backpressure.
 */
class MultiStreamOutputTarget(
    val destinations: List<StreamDestination>,
    val streamConfig: StreamConfig,
    val scope: CoroutineScope,
    var videoEncoder: VideoEncoder? = null,
    private val onUplinkHealthChanged: ((UplinkHealth) -> Unit)? = null
) : StreamOutputTarget {

    private val tag = "MultiStreamTarget"
    private val isRunning = AtomicBoolean(false)
    private var isHevc = false
    private var cachedVideoFormat: MediaFormat? = null
    private var cachedAudioFormat: MediaFormat? = null

    val childTargets: List<RtmpStreamOutputTarget> = destinations
        .filter { it.isConfigured }
        .map { dest ->
            val childConfig = streamConfig.copy(
                platform = dest.platform,
                customEndpointUrl = dest.customEndpointUrl,
                streamKey = dest.streamKey,
                enableAbr = false // Centralized ABR managed at MultiStreamOutputTarget level
            )
            RtmpStreamOutputTarget(
                streamConfig = childConfig,
                scope = scope,
                videoEncoder = videoEncoder
            )
        }

    private val abrController: AdaptiveBitrateController? = if (streamConfig.enableAbr && childTargets.isNotEmpty()) {
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

    private val _compositeUplinkHealth = MutableStateFlow(UplinkHealth.CLEAN)
    val compositeUplinkHealth: StateFlow<UplinkHealth> = _compositeUplinkHealth.asStateFlow()
    val lastAdjustmentReason: StateFlow<RateAdjustmentReason>? = abrController?.lastAdjustmentReason
    val currentBitrateBps: StateFlow<Int>? = abrController?.currentBitrateBps

    val isPrivacyShieldActive = AtomicBoolean(false)
    val awaitingRecoveryKeyframe = AtomicBoolean(false)

    /**
     * Purges all pending in-flight video frames across all active destinations.
     */
    fun purgeInFlightVideoQueues(): Int {
        var total = 0
        for (target in childTargets) {
            total += target.purgeVideoQueue()
        }
        Log.i(tag, "Purged $total in-flight video packets across ${childTargets.size} destinations for Privacy Shield")
        return total
    }

    /**
     * Toggles Privacy Shield behavior on [MultiStreamOutputTarget].
     */
    fun setPrivacyShield(active: Boolean, slatePacket: RtmpPacket? = null) {
        if (active) {
            isPrivacyShieldActive.set(true)
            awaitingRecoveryKeyframe.set(false)
            purgeInFlightVideoQueues()
            if (slatePacket != null) {
                injectSlatePacket(slatePacket)
            }
            Log.i(tag, "MultiStreamOutputTarget: Privacy Shield ACTIVE. Live video blocked, slate injected.")
        } else {
            isPrivacyShieldActive.set(false)
            awaitingRecoveryKeyframe.set(true)
            Log.i(tag, "MultiStreamOutputTarget: Privacy Shield INACTIVE. Awaiting recovery keyframe.")
        }
    }

    /**
     * Injects a synthetic black/privacy slate keyframe into all active destination queues.
     */
    fun injectSlatePacket(slatePacket: RtmpPacket) {
        val activeTargets = childTargets.filter {
            it.connectionState.value !is RtmpConnection.State.Unhealthy
        }
        if (activeTargets.isEmpty()) return

        val sharedPacket = SharedRtmpPacket(
            packet = slatePacket,
            initialReferences = activeTargets.size
        )

        for (target in activeTargets) {
            try {
                target.enqueueSharedVideoPacket(
                    packet = sharedPacket.packet,
                    isKeyframe = true,
                    ptsUs = slatePacket.timestamp * 1000L,
                    packDurationNs = 0L,
                    encodedBytes = slatePacket.payload.size.toLong()
                )
            } catch (e: Exception) {
                Log.e(tag, "Error injecting slate packet to ${target.streamConfig.platform}", e)
                sharedPacket.release()
            }
        }
    }

    override fun attachVideoEncoder(videoEncoder: VideoEncoder) {
        this.videoEncoder = videoEncoder
        for (target in childTargets) {
            target.attachVideoEncoder(videoEncoder)
        }
    }

    override fun start() {
        if (isRunning.getAndSet(true)) return

        Log.i(tag, "Starting MultiStreamOutputTarget with ${childTargets.size} active destinations: " +
            childTargets.map { it.streamConfig.platform.displayName })

        for (target in childTargets) {
            target.start()
        }

        // Start Centralized ABR evaluating aggregate multi-signal telemetry with destination health isolation
        val lastHealthyDrops = AtomicLong(0L)
        abrController?.let { abr ->
            abr.startWithSignal {
                val healthyTargets = childTargets.filter {
                    it.connection.state.value !is RtmpConnection.State.Unhealthy
                }
                if (healthyTargets.isEmpty()) {
                    return@startWithSignal AbrTelemetrySignal()
                }

                var currentHealthyDrops = 0L
                var maxQueueAge = 0L
                var maxWriteLatencyP95 = 0L
                var totalBytesInQueue = 0L
                var anyReconnecting = false

                for (target in healthyTargets) {
                    currentHealthyDrops += target.connection.totalFramesDropped.get()
                    val qAge = target.connection.lastObservedQueueAgeMs.get()
                    if (qAge > maxQueueAge) maxQueueAge = qAge
                    val p95 = target.connection.writeLatencyMetrics.getPercentiles().second
                    if (p95 > maxWriteLatencyP95) maxWriteLatencyP95 = p95
                    totalBytesInQueue += target.connection.bytesInQueue.get()
                    if (target.connection.state.value is RtmpConnection.State.Reconnecting) {
                        anyReconnecting = true
                    }
                }

                val prevDrops = lastHealthyDrops.getAndSet(currentHealthyDrops)
                val newDrops = (currentHealthyDrops - prevDrops).coerceAtLeast(0L)

                AbrTelemetrySignal(
                    newDrops = newDrops,
                    queueAgeMs = maxQueueAge,
                    writeLatencyP95Ms = maxWriteLatencyP95,
                    bytesInQueue = totalBytesInQueue,
                    isAnyHealthyDestinationReconnecting = anyReconnecting
                )
            }

            scope.launch {
                abr.uplinkHealth.collect { health ->
                    _compositeUplinkHealth.value = health
                    onUplinkHealthChanged?.invoke(health)
                }
            }
        }
    }

    override fun onVideoFormat(format: MediaFormat) {
        cachedVideoFormat = format
        val mime = format.getString(MediaFormat.KEY_MIME) ?: MediaFormat.MIMETYPE_VIDEO_AVC
        isHevc = mime == MediaFormat.MIMETYPE_VIDEO_HEVC

        for (target in childTargets) {
            target.onVideoFormat(format)
        }
    }

    override fun onAudioFormat(format: MediaFormat) {
        cachedAudioFormat = format
        for (target in childTargets) {
            target.onAudioFormat(format)
        }
    }

    override fun onVideoSample(buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        if (!isRunning.get() || childTargets.isEmpty() || bufferInfo.size <= 0) return

        // 1. If Privacy Shield is active, block ALL screen frames immediately to prevent leakage
        if (isPrivacyShieldActive.get()) {
            return
        }

        val isKeyframe = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0

        // 2. If resuming from Privacy Shield, gate on fresh hardware IDR keyframe
        if (awaitingRecoveryKeyframe.get()) {
            if (isKeyframe) {
                awaitingRecoveryKeyframe.set(false)
                Log.i(tag, "Recovery sync keyframe received from hardware encoder; resuming live screen transmission")
            } else {
                // Drop inter-frames until fresh keyframe arrives
                return
            }
        }

        // 3. Filter active healthy destinations
        val activeTargets = childTargets.filter {
            it.connectionState.value !is RtmpConnection.State.Unhealthy
        }
        if (activeTargets.isEmpty()) return

        // 4. Perform single-pass packetization: parse Annex B NALs and build FLV packet exactly once
        val timestampMs = (bufferInfo.presentationTimeUs / 1000L).coerceAtLeast(0L)

        val packStartNs = System.nanoTime()

        val bytes = ByteArray(bufferInfo.size)
        val oldPos = buffer.position()
        buffer.position(bufferInfo.offset)
        buffer.get(bytes)
        buffer.position(oldPos)

        val nals = FlvPacketizer.extractAnnexBNalUnits(bytes)
        if (nals.isEmpty()) return

        val rawPacket = if (isHevc) {
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

        // 3. Encapsulate in SharedRtmpPacket with reference count = activeTargets.size
        val sharedPacket = SharedRtmpPacket(
            packet = rawPacket,
            initialReferences = activeTargets.size
        )

        // 4. Fan out shared packet reference to all active targets
        for (target in activeTargets) {
            try {
                target.enqueueSharedVideoPacket(
                    packet = sharedPacket.packet,
                    isKeyframe = isKeyframe,
                    ptsUs = bufferInfo.presentationTimeUs,
                    packDurationNs = packDurationNs,
                    encodedBytes = bufferInfo.size.toLong()
                )
            } catch (e: Exception) {
                Log.e(tag, "Error routing shared video sample to ${target.streamConfig.platform}", e)
                sharedPacket.release()
            }
        }
    }

    override fun onAudioSample(buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        if (!isRunning.get() || childTargets.isEmpty() || bufferInfo.size <= 0) return

        val activeTargets = childTargets.filter {
            it.connectionState.value !is RtmpConnection.State.Unhealthy
        }
        if (activeTargets.isEmpty()) return

        val timestampMs = (bufferInfo.presentationTimeUs / 1000L).coerceAtLeast(0L)
        val bytes = ByteArray(bufferInfo.size)
        val oldPos = buffer.position()
        buffer.position(bufferInfo.offset)
        buffer.get(bytes)
        buffer.position(oldPos)

        val rawPacket = FlvPacketizer.createAacFramePacket(
            data = bytes,
            timestampMs = timestampMs
        )

        val sharedPacket = SharedRtmpPacket(
            packet = rawPacket,
            initialReferences = activeTargets.size
        )

        for (target in activeTargets) {
            try {
                target.enqueueSharedAudioPacket(
                    packet = sharedPacket.packet,
                    ptsUs = bufferInfo.presentationTimeUs,
                    encodedBytes = bufferInfo.size.toLong()
                )
            } catch (e: Exception) {
                Log.e(tag, "Error routing shared audio sample to ${target.streamConfig.platform}", e)
                sharedPacket.release()
            }
        }
    }

    override fun release() {
        if (!isRunning.getAndSet(false)) return

        Log.i(tag, "Releasing MultiStreamOutputTarget...")
        abrController?.stop()
        for (target in childTargets) {
            try {
                target.release()
            } catch (e: Exception) {
                Log.w(tag, "Error releasing child target for ${target.streamConfig.platform}", e)
            }
        }
    }

    /**
     * Diagnostic helper returning active connection states per platform.
     */
    fun getConnectionStates(): Map<StreamPlatform, RtmpConnection.State> {
        return childTargets.associate { it.streamConfig.platform to it.connectionState.value }
    }

    override fun getTelemetry(): SessionStreamTelemetry {
        val configured = destinations.filter { it.isConfigured }
        val destMap = mutableMapOf<String, DestinationTelemetry>()
        configured.forEachIndexed { index, dest ->
            val target = childTargets.getOrNull(index) ?: return@forEachIndexed
            val telem = target.connection.getTelemetry(destinationId = dest.id).copy(
                platformName = if (dest.platform.isCustomEndpoint) "Custom (${dest.activeEndpointUrl})" else dest.platform.displayName
            )
            destMap[dest.id] = telem
        }

        val firstChild = childTargets.firstOrNull()
        val frames = firstChild?.totalVideoFrames?.get()?.coerceAtLeast(1L) ?: 1L
        val avgPackUs = firstChild?.let { (it.totalPacketizationTimeNs.get() / frames) / 1000L } ?: 0L
        val vPts = firstChild?.lastVideoPtsUs?.get() ?: 0L
        val aPts = firstChild?.lastAudioPtsUs?.get() ?: 0L
        val driftMs = if (vPts > 0 && aPts > 0) Math.abs(vPts - aPts) / 1000L else 0L

        return SessionStreamTelemetry(
            totalBytesEncoded = firstChild?.totalBytesEncoded?.get() ?: 0L,
            totalBytesPacketized = childTargets.sumOf { it.totalBytesPacketized.get() },
            encoderOutputFps = videoEncoder?.configuredFramerate?.toFloat() ?: 60f,
            avgPacketizationTimeUs = avgPackUs,
            audioVideoPtsDriftMs = driftMs,
            destinations = destMap,
            currentBitrateBps = abrController?.currentBitrateBps?.value ?: streamConfig.videoBitrate,
            lastAdjustmentReason = abrController?.lastAdjustmentReason?.value ?: RateAdjustmentReason.NONE
        )
    }
}
