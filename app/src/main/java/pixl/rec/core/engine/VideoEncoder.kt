package pixl.rec.core.engine

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Surface
import pixl.rec.core.model.RecordingConfig
import pixl.rec.core.model.VideoCodec
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

/**
 * Zero-copy hardware video encoder.
 * Provides an input [Surface] directly fed by [android.hardware.display.VirtualDisplay]
 * via GPU GraphicBuffers with 0% CPU pixel manipulation overhead.
 */
class VideoEncoder(
    private val config: RecordingConfig,
    private val listener: OutputListener
) {
    interface OutputListener {
        fun onVideoFormatChanged(format: MediaFormat)
        fun onVideoSampleData(buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo)
        fun onVideoError(e: Throwable)
        fun onVideoFpsMeasured(fps: Float)
    }

    private val tag = "VideoEncoder"
    private var mediaCodec: MediaCodec? = null
    var inputSurface: Surface? = null
        private set
    var configuredWidth: Int = config.width
        private set
    var configuredHeight: Int = config.height
        private set
    var configuredFramerate: Int = config.framerate
        private set
    var configuredCodecName: String = ""
        private set

    private var drainJob: Job? = null
    private val isRunning = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)
    private val needKeyFrameOnResume = AtomicBoolean(false)
    private var pauseStartTimeNs: Long = 0L
    private var totalPauseOffsetNs: Long = 0L
    private var sessionBaseTimeNs: Long = 0L
    private var basePtsOffsetUs: Long = -1L
    private var firstFramePtsUs: Long = -1L
    private var lastPtsUs: Long = 0L

    // FPS measurement
    private var frameCount = 0
    private var lastFpsSampleTimeNs = 0L

    // Deterministic EOS await signal
    private var eosDeferred = CompletableDeferred<Unit>()

    /**
     * Initializes and configures the hardware encoder with zero-copy surface input.
     * Uses multi-tier fallback:
     *   Attempt 1: Optimal Profile/Level & BT.709 studio colorimetry metadata
     *   Attempt 2: Relaxed Profile/Level without optional colorimetry keys
     *   Attempt 3: Driver native auto-selection without explicit Profile/Level
     */
    fun prepare() {
        val preferredMime = config.videoCodec.mimeType
        val candidateMimes = listOf(
            preferredMime,
            if (preferredMime == MediaFormat.MIMETYPE_VIDEO_HEVC) MediaFormat.MIMETYPE_VIDEO_AVC else MediaFormat.MIMETYPE_VIDEO_HEVC
        )

        var lastException: Exception? = null

        for (mime in candidateMimes) {
            val hwCodecInfo = CodecProbe.findHardwareEncoder(mime)
            val swCodecInfo = CodecProbe.findEncoder(mime)
            val candidateCodecs = listOfNotNull(hwCodecInfo, swCodecInfo).distinctBy { it.name }

            for (codecInfo in candidateCodecs) {
                val (safeW, safeH) = CodecProbe.validateAndClampDimensions(
                    codecInfo,
                    mime,
                    config.width,
                    config.height
                )

                val profileLevel = CodecProbe.selectProfileAndLevel(codecInfo, mime, safeW, safeH, config.framerate)

                // Attempt 1: Full configuration (with Profile/Level and BT.709 colorimetry)
                val (success1, fps1, exc1) = tryConfigureCodec(codecInfo, mime, safeW, safeH, profileLevel, includeOptionalKeys = true)
                if (success1) {
                    configuredWidth = safeW
                    configuredHeight = safeH
                    configuredFramerate = fps1
                    configuredCodecName = codecInfo.name
                    return
                }
                if (exc1 != null) lastException = exc1

                // Attempt 2: Relaxed configuration (with Profile/Level, without extra colorimetry keys)
                val (success2, fps2, exc2) = tryConfigureCodec(codecInfo, mime, safeW, safeH, profileLevel, includeOptionalKeys = false)
                if (success2) {
                    configuredWidth = safeW
                    configuredHeight = safeH
                    configuredFramerate = fps2
                    configuredCodecName = codecInfo.name
                    return
                }
                if (exc2 != null) lastException = exc2

                // Attempt 3: Driver native auto Profile/Level selection
                val (success3, fps3, exc3) = tryConfigureCodec(codecInfo, mime, safeW, safeH, profileLevel = null, includeOptionalKeys = false)
                if (success3) {
                    configuredWidth = safeW
                    configuredHeight = safeH
                    configuredFramerate = fps3
                    configuredCodecName = codecInfo.name
                    return
                }
                if (exc3 != null) lastException = exc3
            }
        }

        throw lastException ?: IllegalStateException("Unable to configure video encoder for ${config.width}x${config.height} (${config.videoCodec.displayName})")
    }

    private data class ConfigResult(val success: Boolean, val effectiveFps: Int, val exception: Exception?)

    private fun tryConfigureCodec(
        codecInfo: MediaCodecInfo,
        mime: String,
        width: Int,
        height: Int,
        profileLevel: Pair<Int, Int>?,
        includeOptionalKeys: Boolean
    ): ConfigResult {
        var codec: MediaCodec? = null
        return try {
            // Validate and clamp framerate against encoder capabilities for the specified canvas dimensions
            val caps = try { codecInfo.getCapabilitiesForType(mime) } catch (_: Exception) { null }
            val vCaps = caps?.videoCapabilities
            val maxSupportedFps = try {
                val r1 = runCatching { vCaps?.getSupportedFrameRatesFor(width, height)?.upper?.toInt() }.getOrNull()
                val r2 = runCatching { vCaps?.getSupportedFrameRatesFor(height, width)?.upper?.toInt() }.getOrNull()
                val minDim = min(width, height)
                val maxDim = max(width, height)
                val r3 = runCatching { vCaps?.getSupportedFrameRatesFor(maxDim, minDim)?.upper?.toInt() }.getOrNull()
                listOfNotNull(r1, r2, r3).maxOrNull() ?: config.framerate
            } catch (_: Exception) {
                config.framerate
            }
            val effectiveFps = if (config.allowExperimentalFps) {
                config.framerate
            } else {
                min(config.framerate, maxSupportedFps).coerceAtLeast(30)
            }
            if (effectiveFps < config.framerate && !config.allowExperimentalFps) {
                Log.w(tag, "Encoder ${codecInfo.name} caps framerate for ${width}x${height} from ${config.framerate} to $effectiveFps fps")
            }

            codec = MediaCodec.createByCodecName(codecInfo.name)
            val format = MediaFormat.createVideoFormat(mime, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, config.videoBitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, effectiveFps)
                setFloat(MediaFormat.KEY_I_FRAME_INTERVAL, config.iFrameIntervalSeconds)
                setInteger(MediaFormat.KEY_BITRATE_MODE, config.bitrateMode.androidMode)

                // Real-time scheduling priority for Android kernel scheduler
                setInteger(MediaFormat.KEY_PRIORITY, 0)

                // Intra-refresh for ultra-smooth 3D gaming frametimes
                if (config.enableIntraRefresh && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    setInteger(MediaFormat.KEY_INTRA_REFRESH_PERIOD, config.framerate)
                }

                // Explicit Profile & Level (if available and not falling back to auto)
                if (profileLevel != null) {
                    setInteger(MediaFormat.KEY_PROFILE, profileLevel.first)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        setInteger(MediaFormat.KEY_LEVEL, profileLevel.second)
                    }
                }

                if (includeOptionalKeys) {
                    // Explicit Studio BT.709 sRGB Colorimetry & Dynamic Range Metadata
                    setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709)
                    setInteger(MediaFormat.KEY_COLOR_RANGE, config.colorRange.androidRange)
                    setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
                }
            }

            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = codec.createInputSurface()
            mediaCodec = codec
            Log.i(tag, "VideoEncoder prepared: ${codec.name} ($mime), ${width}x${height} @ ${effectiveFps}fps (requested ${config.framerate}fps), Profile: ${profileLevel?.first}, Level: ${profileLevel?.second}, optionalKeys: $includeOptionalKeys")
            ConfigResult(success = true, effectiveFps = effectiveFps, exception = null)
        } catch (e: Exception) {
            Log.w(tag, "Failed to configure codec ${codecInfo.name} ($mime) for ${width}x${height} (profile=${profileLevel?.first}, optionalKeys=$includeOptionalKeys): ${e.message}")
            try {
                codec?.release()
            } catch (_: Exception) {}
            ConfigResult(success = false, effectiveFps = config.framerate, exception = e)
        }
    }

    /**
     * Starts the encoder hardware pipeline and begins draining output buffers.
     */
    fun start(scope: CoroutineScope, baseTimeNs: Long = System.nanoTime()) {
        val codec = mediaCodec ?: throw IllegalStateException("VideoEncoder not prepared")
        codec.start()
        isRunning.set(true)
        isPaused.set(false)
        needKeyFrameOnResume.set(false)
        sessionBaseTimeNs = baseTimeNs
        basePtsOffsetUs = -1L
        firstFramePtsUs = -1L
        lastPtsUs = 0L
        totalPauseOffsetNs = 0L
        frameCount = 0
        lastFpsSampleTimeNs = System.nanoTime()
        eosDeferred = CompletableDeferred()

        drainJob = scope.launch(Dispatchers.IO) {
            drainOutputBuffers()
        }
    }

    /**
     * Pauses presentation timestamp progression and suspends surface encoding.
     */
    fun pause() {
        if (isPaused.compareAndSet(false, true)) {
            pauseStartTimeNs = System.nanoTime()
            try {
                val params = Bundle().apply {
                    putInt(MediaCodec.PARAMETER_KEY_SUSPEND, 1)
                }
                mediaCodec?.setParameters(params)
            } catch (e: Exception) {
                Log.w(tag, "Failed to suspend MediaCodec surface input", e)
            }
            Log.i(tag, "VideoEncoder paused")
        }
    }

    /**
     * Resumes presentation timestamp progression and requests an immediate sync keyframe.
     */
    fun resume() {
        if (isPaused.compareAndSet(true, false)) {
            val pausedDuration = System.nanoTime() - pauseStartTimeNs
            totalPauseOffsetNs += pausedDuration
            needKeyFrameOnResume.set(true)
            try {
                val params = Bundle().apply {
                    putInt(MediaCodec.PARAMETER_KEY_SUSPEND, 0)
                    putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
                }
                mediaCodec?.setParameters(params)
            } catch (e: Exception) {
                Log.w(tag, "Failed to resume/request sync frame on MediaCodec surface input", e)
            }
            Log.i(tag, "VideoEncoder resumed, total offset: ${totalPauseOffsetNs / 1_000_000}ms")
        }
    }

    /**
     * Signals End-of-Stream to the input surface and stops the draining loop.
     */
    fun stop() {
        isRunning.set(false)
        try {
            mediaCodec?.signalEndOfInputStream()
        } catch (e: Exception) {
            Log.w(tag, "Failed to signalEndOfInputStream", e)
            if (!eosDeferred.isCompleted) {
                eosDeferred.complete(Unit)
            }
        }
        drainJob?.cancel()
    }

    /**
     * Awaits completion of EOS output buffer processing.
     */
    suspend fun awaitEos(timeoutMs: Long = 2000L): Boolean {
        return withTimeoutOrNull(timeoutMs) {
            eosDeferred.await()
            true
        } ?: false
    }

    /**
     * Safely releases encoder and input surface resources.
     */
    fun release() {
        stop()
        if (!eosDeferred.isCompleted) {
            eosDeferred.complete(Unit)
        }
        try {
            mediaCodec?.stop()
        } catch (e: Exception) {
            Log.w(tag, "Error stopping MediaCodec", e)
        }
        try {
            mediaCodec?.release()
        } catch (e: Exception) {
            Log.w(tag, "Error releasing MediaCodec", e)
        }
        try {
            inputSurface?.release()
        } catch (e: Exception) {
            Log.w(tag, "Error releasing Input Surface", e)
        }
        mediaCodec = null
        inputSurface = null
        Log.i(tag, "VideoEncoder released")
    }

    private fun drainOutputBuffers() {
        val codec = mediaCodec ?: return
        val bufferInfo = MediaCodec.BufferInfo()
        val timeoutUs = 10_000L // 10ms

        while (isRunning.get()) {
            try {
                val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)

                when (outputBufferIndex) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = codec.outputFormat
                        Log.i(tag, "Video output format changed: $newFormat")
                        listener.onVideoFormatChanged(newFormat)
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        // No buffer available yet, continue loop
                    }
                    else -> {
                        if (outputBufferIndex >= 0) {
                            val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                            if (outputBuffer != null) {
                                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                    bufferInfo.size = 0
                                }

                                if (bufferInfo.size > 0) {
                                    if (!isPaused.get()) {
                                        val isKeyFrame = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
                                        if (needKeyFrameOnResume.get()) {
                                            if (!isKeyFrame) {
                                                // Drop non-key frames until the requested sync keyframe arrives to prevent macroblock glitching
                                                codec.releaseOutputBuffer(outputBufferIndex, false)
                                                continue
                                            }
                                            needKeyFrameOnResume.set(false)
                                            Log.i(tag, "Received clean keyframe following resume")
                                        }

                                        // Calibrate video PTS relative to the unified session base time
                                        if (basePtsOffsetUs < 0) {
                                            val nowNs = System.nanoTime()
                                            val elapsedSinceStartUs = ((nowNs - sessionBaseTimeNs) / 1000L).coerceAtLeast(0L)
                                            basePtsOffsetUs = bufferInfo.presentationTimeUs - elapsedSinceStartUs
                                            firstFramePtsUs = bufferInfo.presentationTimeUs
                                            Log.i(tag, "First video frame captured at raw PTS: ${firstFramePtsUs}us (calibrated with session offset: ${basePtsOffsetUs}us)")
                                        }

                                        val relativePtsUs = (bufferInfo.presentationTimeUs - basePtsOffsetUs).coerceAtLeast(0L)
                                        val adjustedPtsUs = (relativePtsUs - (totalPauseOffsetNs / 1_000L)).coerceAtLeast(lastPtsUs)
                                        bufferInfo.presentationTimeUs = adjustedPtsUs
                                        lastPtsUs = adjustedPtsUs

                                        outputBuffer.position(bufferInfo.offset)
                                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                                        listener.onVideoSampleData(outputBuffer, bufferInfo)

                                        // Telemetry FPS Calculation
                                        frameCount++
                                        val nowNs = System.nanoTime()
                                        val elapsedSec = (nowNs - lastFpsSampleTimeNs) / 1_000_000_000.0f
                                        if (elapsedSec >= 1.0f) {
                                            val currentFps = frameCount / elapsedSec
                                            listener.onVideoFpsMeasured(currentFps)
                                            frameCount = 0
                                            lastFpsSampleTimeNs = nowNs
                                        }
                                    }
                                }
                            }
                            codec.releaseOutputBuffer(outputBufferIndex, false)

                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                Log.i(tag, "Video encoder reached EOS")
                                if (!eosDeferred.isCompleted) {
                                    eosDeferred.complete(Unit)
                                }
                                break
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (isRunning.get()) {
                    Log.e(tag, "Error in VideoEncoder drain loop", e)
                    listener.onVideoError(e)
                }
                if (!eosDeferred.isCompleted) {
                    eosDeferred.complete(Unit)
                }
                break
            }
        }
    }
}
