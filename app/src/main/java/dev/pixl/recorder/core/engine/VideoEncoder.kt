package dev.pixl.recorder.core.engine

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import android.view.Surface
import dev.pixl.recorder.core.model.RecordingConfig
import dev.pixl.recorder.core.model.VideoCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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

    private var drainJob: Job? = null
    private val isRunning = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)
    private var pauseStartTimeNs: Long = 0L
    private var totalPauseOffsetNs: Long = 0L
    private var firstFramePtsUs: Long = -1L
    private var lastPtsUs: Long = 0L

    // FPS measurement
    private var frameCount = 0
    private var lastFpsSampleTimeNs = 0L

    /**
     * Initializes and configures the hardware encoder with zero-copy surface input.
     */
    fun prepare() {
        val mime = config.videoCodec.mimeType
        val hardwareCodecInfo = CodecProbe.findHardwareEncoder(mime)

        val codec = if (hardwareCodecInfo != null) {
            try {
                MediaCodec.createByCodecName(hardwareCodecInfo.name)
            } catch (e: Exception) {
                Log.w(tag, "Failed to create hardware codec by name ${hardwareCodecInfo.name}, falling back to type $mime", e)
                MediaCodec.createEncoderByType(mime)
            }
        } else {
            MediaCodec.createEncoderByType(mime)
        }

        val format = MediaFormat.createVideoFormat(mime, config.width, config.height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, config.videoBitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, config.framerate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, config.iFrameIntervalSeconds)
            setInteger(MediaFormat.KEY_BITRATE_MODE, config.bitrateMode.androidMode)
        }

        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = codec.createInputSurface()
            mediaCodec = codec
            Log.i(tag, "VideoEncoder prepared: ${codec.name}, ${config.width}x${config.height} @ ${config.framerate}fps, ${config.videoBitrate / 1_000_000}Mbps")
        } catch (e: Exception) {
            codec.release()
            throw e
        }
    }

    /**
     * Starts the encoder hardware pipeline and begins draining output buffers.
     */
    fun start(scope: CoroutineScope) {
        val codec = mediaCodec ?: throw IllegalStateException("VideoEncoder not prepared")
        codec.start()
        isRunning.set(true)
        isPaused.set(false)
        firstFramePtsUs = -1L
        lastPtsUs = 0L
        totalPauseOffsetNs = 0L
        frameCount = 0
        lastFpsSampleTimeNs = System.nanoTime()

        drainJob = scope.launch(Dispatchers.IO) {
            drainOutputBuffers()
        }
    }

    /**
     * Pauses presentation timestamp progression.
     */
    fun pause() {
        if (isPaused.compareAndSet(false, true)) {
            pauseStartTimeNs = System.nanoTime()
            Log.i(tag, "VideoEncoder paused")
        }
    }

    /**
     * Resumes presentation timestamp progression.
     */
    fun resume() {
        if (isPaused.compareAndSet(true, false)) {
            val pausedDuration = System.nanoTime() - pauseStartTimeNs
            totalPauseOffsetNs += pausedDuration
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
        }
        drainJob?.cancel()
    }

    /**
     * Safely releases encoder and input surface resources.
     */
    fun release() {
        stop()
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
                                        // Normalize video PTS so the first frame starts at 0 microseconds
                                        if (firstFramePtsUs < 0) {
                                            firstFramePtsUs = bufferInfo.presentationTimeUs
                                            Log.i(tag, "First video frame captured at raw PTS: ${firstFramePtsUs}us (uptime: ${firstFramePtsUs / 1_000_000}s), normalized to 0us")
                                        }

                                        val relativePtsUs = bufferInfo.presentationTimeUs - firstFramePtsUs
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
                break
            }
        }
    }
}
