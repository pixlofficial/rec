package pixl.rec.core.audio

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import pixl.rec.core.model.AudioSource
import pixl.rec.core.model.RecordingConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages dual-stream audio capture:
 * 1. Internal game audio via [AudioPlaybackCaptureConfiguration] (API 29+)
 * 2. Microphone audio via [AudioRecord]
 * Mixes both streams in real-time with nanosecond PTS synchronization.
 */
class AudioCaptureManager(
    private val context: Context,
    private val config: RecordingConfig,
    private val mediaProjection: MediaProjection?,
    private val listener: AudioDataListener
) {
    interface AudioDataListener {
        fun onPcmAudioData(pcmBytes: ByteArray, length: Int, ptsUs: Long)
        fun onAudioLevels(gameDb: Float, micDb: Float)
        fun onAudioError(e: Throwable)
    }

    private val tag = "AudioCaptureManager"
    private var internalAudioRecord: AudioRecord? = null
    private var micAudioRecord: AudioRecord? = null

    private var captureJob: Job? = null
    private val isRunning = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)

    private var pauseStartTimeNs = 0L
    private var totalPauseOffsetNs = 0L
    private var startTimestampNs = 0L
    private var lastEmittedPtsUs = 0L

    // Throttled VU calculation state (10Hz UI matching)
    private var lastDbCalcTimeNs = 0L
    private val DB_CALC_INTERVAL_NS = 100_000_000L // 100ms

    private val sampleRate = config.audioSampleRate // 48000
    private val channelConfig = AudioFormat.CHANNEL_IN_STEREO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bytesPerSample = 2 * 2 // 16-bit (2 bytes) * 2 channels = 4 bytes per stereo frame

    private var bufferSizeInBytes: Int = 0

    /**
     * Prepares AudioRecord instances based on [RecordingConfig.audioSource].
     */
    @SuppressLint("MissingPermission")
    fun prepare() {
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        bufferSizeInBytes = (minBufferSize * 2).coerceAtLeast(4096)

        val audioFormatConfig = AudioFormat.Builder()
            .setEncoding(audioFormat)
            .setSampleRate(sampleRate)
            .setChannelMask(channelConfig)
            .build()

        // 1. Prepare Internal Audio Capture (API 29+)
        if (config.audioSource.hasInternal && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mediaProjection != null) {
            try {
                val playbackConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build()

                internalAudioRecord = AudioRecord.Builder()
                    .setAudioFormat(audioFormatConfig)
                    .setBufferSizeInBytes(bufferSizeInBytes)
                    .setAudioPlaybackCaptureConfig(playbackConfig)
                    .build()

                if (internalAudioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.w(tag, "Internal AudioRecord failed to initialize")
                    internalAudioRecord?.release()
                    internalAudioRecord = null
                } else {
                    Log.i(tag, "Internal AudioRecord initialized successfully")
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to configure AudioPlaybackCapture", e)
            }
        }

        // 2. Prepare Microphone Audio Record
        if (config.audioSource.hasMic) {
            val hasRecordPermission = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (hasRecordPermission) {
                try {
                    micAudioRecord = AudioRecord(
                        MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        sampleRate,
                        channelConfig,
                        audioFormat,
                        bufferSizeInBytes
                    )

                    if (micAudioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                        Log.w(tag, "Mic AudioRecord failed to initialize with VOICE_RECOGNITION, trying MIC")
                        micAudioRecord?.release()
                        micAudioRecord = AudioRecord(
                            MediaRecorder.AudioSource.MIC,
                            sampleRate,
                            channelConfig,
                            audioFormat,
                            bufferSizeInBytes
                        )
                    }

                    if (micAudioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                        Log.w(tag, "Mic AudioRecord failed to initialize")
                        micAudioRecord?.release()
                        micAudioRecord = null
                    } else {
                        Log.i(tag, "Mic AudioRecord initialized successfully")
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Failed to initialize mic AudioRecord", e)
                }
            } else {
                Log.w(tag, "RECORD_AUDIO permission not granted, skipping mic capture")
            }
        }
    }

    /**
     * Starts audio recording and mixing loops with unified session base time.
     */
    fun start(scope: CoroutineScope, sessionBaseTimeNs: Long = System.nanoTime()) {
        if (!config.audioSource.hasAudio) {
            Log.i(tag, "Audio is muted in config, skipping capture start")
            return
        }

        try {
            internalAudioRecord?.startRecording()
        } catch (e: Exception) {
            Log.e(tag, "Error starting internal AudioRecord", e)
        }

        try {
            micAudioRecord?.startRecording()
        } catch (e: Exception) {
            Log.e(tag, "Error starting mic AudioRecord", e)
        }

        isRunning.set(true)
        isPaused.set(false)
        startTimestampNs = sessionBaseTimeNs
        lastEmittedPtsUs = 0L
        totalPauseOffsetNs = 0L
        lastDbCalcTimeNs = 0L

        captureJob = scope.launch(Dispatchers.IO) {
            runCaptureLoop()
        }
    }

    fun pause() {
        if (isPaused.compareAndSet(false, true)) {
            pauseStartTimeNs = System.nanoTime()
            Log.i(tag, "AudioCaptureManager paused")
        }
    }

    fun resume() {
        if (isPaused.compareAndSet(true, false)) {
            val pausedDuration = System.nanoTime() - pauseStartTimeNs
            totalPauseOffsetNs += pausedDuration
            Log.i(tag, "AudioCaptureManager resumed")
        }
    }

    fun stop() {
        isRunning.set(false)
        captureJob?.cancel()

        try {
            internalAudioRecord?.stop()
        } catch (e: Exception) {
            Log.w(tag, "Error stopping internal AudioRecord", e)
        }

        try {
            micAudioRecord?.stop()
        } catch (e: Exception) {
            Log.w(tag, "Error stopping mic AudioRecord", e)
        }
    }

    fun release() {
        stop()
        try {
            internalAudioRecord?.release()
        } catch (e: Exception) {
            Log.w(tag, "Error releasing internal AudioRecord", e)
        }
        try {
            micAudioRecord?.release()
        } catch (e: Exception) {
            Log.w(tag, "Error releasing mic AudioRecord", e)
        }
        internalAudioRecord = null
        micAudioRecord = null
        Log.i(tag, "AudioCaptureManager released")
    }

    private suspend fun runCaptureLoop() {
        val chunkSize = bufferSizeInBytes
        val gameBuffer = ByteArray(chunkSize)
        val micBuffer = ByteArray(chunkSize)
        val mixedBuffer = ByteArray(chunkSize)

        while (isRunning.get()) {
            var gameBytesRead = 0
            var micBytesRead = 0

            // 1. Read Internal Game Audio
            val intRecord = internalAudioRecord
            if (intRecord != null && intRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                gameBytesRead = intRecord.read(gameBuffer, 0, chunkSize)
                if (gameBytesRead < 0) {
                    gameBytesRead = 0
                }
            }

            // 2. Read Mic Audio
            val micRecord = micAudioRecord
            if (micRecord != null && micRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                micBytesRead = micRecord.read(micBuffer, 0, chunkSize)
                if (micBytesRead < 0) {
                    micBytesRead = 0
                }
            }

            if (isPaused.get()) {
                // Non-blocking coroutine delay during pause
                delay(10)
                continue
            }

            // If neither source provided data, back off slightly with non-blocking delay
            if (gameBytesRead == 0 && micBytesRead == 0) {
                delay(10)
                continue
            }

            // Throttled VU decibel level calculation (10Hz matching UI telemetry ticker)
            val nowNs = System.nanoTime()
            if (nowNs - lastDbCalcTimeNs >= DB_CALC_INTERVAL_NS) {
                lastDbCalcTimeNs = nowNs
                val gameDb = if (gameBytesRead > 0) PcmAudioMixer.calculateDbLevel(gameBuffer, gameBytesRead) else -60f
                val micDb = if (micBytesRead > 0) PcmAudioMixer.calculateDbLevel(micBuffer, micBytesRead) else -60f
                listener.onAudioLevels(gameDb, micDb)
            }

            // Mix audio buffers
            val outputBytes: Int
            if (gameBytesRead > 0 && micBytesRead > 0) {
                outputBytes = PcmAudioMixer.mixStereo16Bit(
                    gameBuffer, gameBytesRead,
                    micBuffer, micBytesRead,
                    config.internalAudioGain, config.micGain,
                    mixedBuffer
                )
            } else if (gameBytesRead > 0) {
                outputBytes = PcmAudioMixer.applyGain16Bit(
                    gameBuffer, gameBytesRead,
                    config.internalAudioGain,
                    mixedBuffer
                )
            } else {
                outputBytes = PcmAudioMixer.applyGain16Bit(
                    micBuffer, micBytesRead,
                    config.micGain,
                    mixedBuffer
                )
            }

            if (outputBytes > 0) {
                // Compute presentation timestamp synchronized against the monotonic session baseline
                val elapsedUs = ((nowNs - startTimestampNs - totalPauseOffsetNs) / 1000L).coerceAtLeast(0L)
                val adjustedPtsUs = elapsedUs.coerceAtLeast(lastEmittedPtsUs)
                lastEmittedPtsUs = adjustedPtsUs

                listener.onPcmAudioData(mixedBuffer, outputBytes, adjustedPtsUs)
            }
        }
    }
}
