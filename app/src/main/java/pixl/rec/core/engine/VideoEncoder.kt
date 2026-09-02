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
     * Negotiates 2K/4K profiles (AVC Level 5.1/5.2, HEVC Main Level 5.1) with multi-stage resilient fallback.
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

                // Attempt 1: Full configuration (with Profile/Level 5.1+, operating rate, colorimetry)
                if (tryConfigureCodec(codecInfo, mime, safeW, safeH, profileLevel, includeOptionalKeys = true)) {
                    configuredWidth = safeW
                    configuredHeight = safeH
                    configuredCodecName = codecInfo.name
                    return
                }

                // Attempt 2: Relaxed configuration (without operating rate / strict colorimetry keys that some vendor drivers reject on 2K)
                if (tryConfigureCodec(codecInfo, mime, safeW, safeH, profileLevel, includeOptionalKeys = false)) {
                    configuredWidth = safeW
                    configuredHeight = safeH
                    configuredCodecName = codecInfo.name
                    return
                }
            }
        }

        throw lastException ?: IllegalStateException("Unable to configure video encoder for ${config.width}x${config.height} (${config.videoCodec.displayName})")
    }

    private fun tryConfigureCodec(
        codecInfo: MediaCodecInfo,
        mime: String,
        width: Int,
        height: Int,
        profileLevel: Pair<Int, Int>?,
        includeOptionalKeys: Boolean
    ): Boolean {
        var codec: MediaCodec? = null
        return try {
            codec = MediaCodec.createByCodecName(codecInfo.name)
            val format = MediaFormat.createVideoFormat(mime, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, config.videoBitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, config.framerate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, config.iFrameIntervalSeconds)
                setInteger(MediaFormat.KEY_BITRATE_MODE, config.bitrateMode.androidMode)

                // Explicit Profile & Level for 2K / 4K / High Bitrate
                if (profileLevel != null) {
                    setInteger(MediaFormat.KEY_PROFILE, profileLevel.first)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        setInteger(MediaFormat.KEY_LEVEL, profileLevel.second)
                    }
                }

                if (includeOptionalKeys) {
                    // Operating rate hint for high framerate
                    setInteger(MediaFormat.KEY_OPERATING_RATE, config.framerate)
                    // Real-time priority for kernel scheduler
                    setInteger(MediaFormat.KEY_PRIORITY, 0)

                    // Explicit Studio BT.709 sRGB Colorimetry & Full Dynamic Range Metadata
                    setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709)
                    setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_FULL)
                    setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
                }
            }

            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = codec.createInputSurface()
            mediaCodec = codec
            Log.i(tag, "VideoEncoder prepared: ${codec.name} ($mime), ${width}x${height} @ ${config.framerate}fps, Profile: ${profileLevel?.first}, Level: ${profileLevel?.second}, optionalKeys: $includeOptionalKeys")
            true
        } catch (e: Exception) {
            Log.w(tag, "Failed to configure codec ${codecInfo.name} ($mime) for ${width}x${height} (optionalKeys=$includeOptionalKeys): ${e.message}")
            try {
                codec?.release()
            } catch (_: Exception) {}
            false
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
