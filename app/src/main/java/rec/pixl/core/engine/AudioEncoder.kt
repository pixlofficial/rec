package rec.pixl.core.engine

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import rec.pixl.core.model.RecordingConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Encodes raw 16-bit linear PCM audio buffers into AAC (MIMETYPE_AUDIO_AAC) bitstreams
 * ready for multiplexing into MP4 containers.
 */
class AudioEncoder(
    private val config: RecordingConfig,
    private val listener: OutputListener
) {
    interface OutputListener {
        fun onAudioFormatChanged(format: MediaFormat)
        fun onAudioSampleData(buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo)
        fun onAudioError(e: Throwable)
    }

    private val tag = "AudioEncoder"
    private var mediaCodec: MediaCodec? = null

    private var drainJob: Job? = null
    private val isRunning = AtomicBoolean(false)
    private var lastPtsUs = 0L

    fun prepare() {
        val mime = MediaFormat.MIMETYPE_AUDIO_AAC
        val format = MediaFormat.createAudioFormat(mime, config.audioSampleRate, config.audioChannelCount).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, config.audioBitrate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            setInteger(MediaFormat.KEY_PRIORITY, 0)
        }

        val codec = MediaCodec.createEncoderByType(mime)
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            mediaCodec = codec
            Log.i(tag, "AudioEncoder prepared: AAC LC ${config.audioSampleRate}Hz, ${config.audioBitrate / 1000}kbps")
        } catch (e: Exception) {
            codec.release()
            throw e
        }
    }

    fun start(scope: CoroutineScope) {
        val codec = mediaCodec ?: throw IllegalStateException("AudioEncoder not prepared")
        codec.start()
        isRunning.set(true)
        lastPtsUs = 0L

        drainJob = scope.launch(Dispatchers.IO) {
            drainOutputBuffers()
        }
    }

    /**
     * Enqueues a chunk of PCM audio data with nanosecond presentation timestamp.
     */
    fun enqueuePcmData(pcmBytes: ByteArray, length: Int, ptsUs: Long) {
        val codec = mediaCodec ?: return
        if (!isRunning.get()) return

        try {
            val inputBufferIndex = codec.dequeueInputBuffer(5_000L) // 5ms timeout
            if (inputBufferIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                if (inputBuffer != null) {
                    inputBuffer.clear()
                    inputBuffer.put(pcmBytes, 0, length)
                    codec.queueInputBuffer(
                        inputBufferIndex,
                        0,
                        length,
                        ptsUs,
                        0
                    )
                }
            } else {
                Log.w(tag, "Audio input buffer timeout, dropping frame")
            }
        } catch (e: Exception) {
            if (isRunning.get()) {
                Log.e(tag, "Error queueing PCM audio buffer", e)
                listener.onAudioError(e)
            }
        }
    }

    fun stop() {
        if (isRunning.compareAndSet(true, false)) {
            try {
                // Signal EOS on input
                val codec = mediaCodec
                if (codec != null) {
                    val inputIndex = codec.dequeueInputBuffer(10_000L)
                    if (inputIndex >= 0) {
                        codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "Failed to send audio EOS", e)
            }
        }
        drainJob?.cancel()
    }

    fun release() {
        stop()
        try {
            mediaCodec?.stop()
        } catch (e: Exception) {
            Log.w(tag, "Error stopping Audio MediaCodec", e)
        }
        try {
            mediaCodec?.release()
        } catch (e: Exception) {
            Log.w(tag, "Error releasing Audio MediaCodec", e)
        }
        mediaCodec = null
        Log.i(tag, "AudioEncoder released")
    }

    private fun drainOutputBuffers() {
        val codec = mediaCodec ?: return
        val bufferInfo = MediaCodec.BufferInfo()
        val timeoutUs = 10_000L

        while (isRunning.get()) {
            try {
                val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)

                when (outputBufferIndex) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val newFormat = codec.outputFormat
                        Log.i(tag, "Audio output format changed: $newFormat")
                        listener.onAudioFormatChanged(newFormat)
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        // Wait for more audio samples
                    }
                    else -> {
                        if (outputBufferIndex >= 0) {
                            val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                            if (outputBuffer != null) {
                                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                    bufferInfo.size = 0
                                }

                                if (bufferInfo.size > 0) {
                                    val adjustedPts = bufferInfo.presentationTimeUs.coerceAtLeast(lastPtsUs)
                                    bufferInfo.presentationTimeUs = adjustedPts
                                    lastPtsUs = adjustedPts

                                    outputBuffer.position(bufferInfo.offset)
                                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                                    listener.onAudioSampleData(outputBuffer, bufferInfo)
                                }
                            }
                            codec.releaseOutputBuffer(outputBufferIndex, false)

                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                Log.i(tag, "Audio encoder reached EOS")
                                break
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (isRunning.get()) {
                    Log.e(tag, "Error in AudioEncoder drain loop", e)
                    listener.onAudioError(e)
                }
                break
            }
        }
    }
}
