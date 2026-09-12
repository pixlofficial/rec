package pixl.rec.core.audio

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
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
import java.util.Arrays
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Manages dual-stream audio capture:
 * 1. Internal game audio via [AudioPlaybackCaptureConfiguration] (API 29+)
 * 2. Microphone audio via [AudioRecord]
 * Mixes both streams in real-time with sample-accurate monotonic PTS synchronization
 * and seamless silence synthesis for immediate MediaMuxer startup.
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
    private var internalReaderJob: Job? = null
    private var micReaderJob: Job? = null

    private val isRunning = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)

    // Throttled VU calculation state (10Hz UI matching)
    private var lastDbCalcTimeNs = 0L
    private val DB_CALC_INTERVAL_NS = 100_000_000L // 100ms

    private val sampleRate = config.audioSampleRate // 48000
    private val channelConfig = AudioFormat.CHANNEL_IN_STEREO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bytesPerSample = 2 * 2 // 16-bit (2 bytes) * 2 channels = 4 bytes per stereo frame

    // Standard AAC-LC frame: 1024 samples per channel = 4096 bytes per frame
    private val CHUNK_FRAME_COUNT = 1024
    private val CHUNK_BYTES = CHUNK_FRAME_COUNT * bytesPerSample // 4096 bytes
    private val CHUNK_INTERVAL_NS = (CHUNK_FRAME_COUNT.toLong() * 1_000_000_000L) / sampleRate.toLong() // 21,333,333 ns

    val synchronizer = AudioClockSynchronizer(
        sampleRate = sampleRate,
        channelCount = 2,
        audioSyncOffsetUs = config.audioSyncOffsetMs * 1000L
    )
    val totalTrimmedChunks = AtomicLong(0L)

    private val isPrivacyShieldActive = AtomicBoolean(false)
    private var privacyMicPolicy = pixl.rec.core.model.PrivacyMicPolicy.MUTE_MIC

    fun setPrivacyShield(active: Boolean, policy: pixl.rec.core.model.PrivacyMicPolicy = pixl.rec.core.model.PrivacyMicPolicy.MUTE_MIC) {
        privacyMicPolicy = policy
        isPrivacyShieldActive.set(active)
        Log.i(tag, "Privacy shield audio state: active=$active, policy=${policy.displayName}")
    }

    private val internalQueue = ConcurrentLinkedQueue<ByteArray>()
    private val micQueue = ConcurrentLinkedQueue<ByteArray>()
    private val bufferPool = ConcurrentLinkedQueue<ByteArray>()

    private fun getBuffer(): ByteArray {
        return bufferPool.poll() ?: ByteArray(CHUNK_BYTES)
    }

    private fun recycleBuffer(buf: ByteArray) {
        if (bufferPool.size < 32) {
            bufferPool.offer(buf)
        }
    }

    private var bufferSizeInBytes: Int = 0

    /**
     * Prepares AudioRecord instances based on [RecordingConfig.audioSource].
     */
    @SuppressLint("MissingPermission")
    fun prepare() {
        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        bufferSizeInBytes = (minBufferSize * 2).coerceAtLeast(CHUNK_BYTES * 2)

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
        lastDbCalcTimeNs = 0L
        synchronizer.reset(sessionBaseTimeNs, config.audioSyncOffsetMs * 1000L)

        internalQueue.clear()
        micQueue.clear()

        // 1. Dedicated asynchronous reader for Internal Game Audio
        if (config.audioSource.hasInternal && internalAudioRecord != null) {
            internalReaderJob = scope.launch(Dispatchers.IO) {
                val tempBuf = ByteArray(CHUNK_BYTES)
                while (isRunning.get()) {
                    val intRecord = internalAudioRecord ?: break
                    if (isPaused.get()) {
                        delay(10)
                        continue
                    }
                    try {
                        val read = intRecord.read(tempBuf, 0, CHUNK_BYTES)
                        if (read > 0) {
                            val chunk = getBuffer()
                            System.arraycopy(tempBuf, 0, chunk, 0, read)
                            if (read < CHUNK_BYTES) {
                                Arrays.fill(chunk, read, CHUNK_BYTES, 0.toByte())
                            }
                            // Cap queue to avoid unbounded latency drift, explicitly accounting for trimmed duration
                            while (internalQueue.size >= 8) {
                                internalQueue.poll()?.let {
                                    synchronizer.onBufferTrimmed(CHUNK_BYTES)
                                    totalTrimmedChunks.incrementAndGet()
                                    recycleBuffer(it)
                                }
                            }
                            internalQueue.offer(chunk)
                        } else {
                            delay(5)
                        }
                    } catch (e: Exception) {
                        if (isRunning.get()) {
                            Log.w(tag, "Error in internal audio reader", e)
                        }
                        delay(10)
                    }
                }
            }
        }

        // 2. Dedicated asynchronous reader for Microphone Audio
        if (config.audioSource.hasMic && micAudioRecord != null) {
            micReaderJob = scope.launch(Dispatchers.IO) {
                val tempBuf = ByteArray(CHUNK_BYTES)
                while (isRunning.get()) {
                    val micRecord = micAudioRecord ?: break
                    if (isPaused.get()) {
                        delay(10)
                        continue
                    }
                    try {
                        val read = micRecord.read(tempBuf, 0, CHUNK_BYTES)
                        if (read > 0) {
                            val chunk = getBuffer()
                            System.arraycopy(tempBuf, 0, chunk, 0, read)
                            if (read < CHUNK_BYTES) {
                                Arrays.fill(chunk, read, CHUNK_BYTES, 0.toByte())
                            }
                            while (micQueue.size >= 8) {
                                micQueue.poll()?.let {
                                    synchronizer.onBufferTrimmed(CHUNK_BYTES)
                                    totalTrimmedChunks.incrementAndGet()
                                    recycleBuffer(it)
                                }
                            }
                            micQueue.offer(chunk)
                        } else {
                            delay(5)
                        }
                    } catch (e: Exception) {
                        if (isRunning.get()) {
                            Log.w(tag, "Error in mic audio reader", e)
                        }
                        delay(10)
                    }
                }
            }
        }

        // 3. Master Mixing & Pacing Loop
        captureJob = scope.launch(Dispatchers.IO) {
            runMixerLoop()
        }
    }

    fun pause() {
        if (isPaused.compareAndSet(false, true)) {
            synchronizer.pause()
            Log.i(tag, "AudioCaptureManager paused")
        }
    }

    fun resume() {
        if (isPaused.compareAndSet(true, false)) {
            synchronizer.resume()
            Log.i(tag, "AudioCaptureManager resumed")
        }
    }

    fun stop() {
        isRunning.set(false)
        captureJob?.cancel()
        internalReaderJob?.cancel()
        micReaderJob?.cancel()

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

        internalQueue.clear()
        micQueue.clear()
        bufferPool.clear()
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

    private suspend fun runMixerLoop() {
        val silenceBuf = ByteArray(CHUNK_BYTES)
        val mixedBuf = ByteArray(CHUNK_BYTES)
        var totalEmittedSamples = 0L

        var nextChunkTimeNs = System.nanoTime()

        while (isRunning.get()) {
            if (isPaused.get()) {
                delay(10)
                nextChunkTimeNs = System.nanoTime()
                continue
            }

            val now = System.nanoTime()
            if (now < nextChunkTimeNs) {
                val waitMs = (nextChunkTimeNs - now) / 1_000_000L
                if (waitMs > 1) {
                    delay(waitMs)
                }
            }
            nextChunkTimeNs += CHUNK_INTERVAL_NS
            if (System.nanoTime() - nextChunkTimeNs > CHUNK_INTERVAL_NS * 5) {
                // Reset clock pacing if background scheduling delayed the loop
                nextChunkTimeNs = System.nanoTime() + CHUNK_INTERVAL_NS
            }

            val gameChunk = internalQueue.poll()
            val micChunk = micQueue.poll()

            val hasGame = gameChunk != null
            val hasMic = micChunk != null
            val shouldMuteMic = isPrivacyShieldActive.get() && privacyMicPolicy == pixl.rec.core.model.PrivacyMicPolicy.MUTE_MIC
            val effectiveHasMic = hasMic && !shouldMuteMic

            val gBuf = gameChunk ?: silenceBuf
            val mBuf = if (shouldMuteMic) silenceBuf else (micChunk ?: silenceBuf)

            // Throttled VU decibel level calculation (10Hz matching UI telemetry ticker)
            if (now - lastDbCalcTimeNs >= DB_CALC_INTERVAL_NS) {
                lastDbCalcTimeNs = now
                val gameDb = if (hasGame) PcmAudioMixer.calculateDbLevel(gBuf, CHUNK_BYTES, config.internalAudioGain) else -60f
                val micDb = if (effectiveHasMic) PcmAudioMixer.calculateDbLevel(mBuf, CHUNK_BYTES, config.micGain) else -60f
                listener.onAudioLevels(gameDb, micDb)
            }

            val outputBytes: Int
            val bufferToSend: ByteArray

            when (config.audioSource) {
                AudioSource.INTERNAL_AND_MIC -> {
                    if (hasGame && effectiveHasMic) {
                        outputBytes = PcmAudioMixer.mixStereo16Bit(
                            gBuf, CHUNK_BYTES,
                            mBuf, CHUNK_BYTES,
                            config.internalAudioGain, config.micGain,
                            mixedBuf
                        )
                        bufferToSend = mixedBuf
                    } else if (hasGame) {
                        outputBytes = PcmAudioMixer.applyGain16Bit(
                            gBuf, CHUNK_BYTES,
                            config.internalAudioGain,
                            mixedBuf
                        )
                        bufferToSend = mixedBuf
                    } else if (effectiveHasMic) {
                        outputBytes = PcmAudioMixer.applyGain16Bit(
                            mBuf, CHUNK_BYTES,
                            config.micGain,
                            mixedBuf
                        )
                        bufferToSend = mixedBuf
                    } else {
                        // Both streams currently silent (or mic muted by privacy shield): send digital silence to keep AAC encoder alive
                        outputBytes = CHUNK_BYTES
                        bufferToSend = silenceBuf
                    }
                }
                AudioSource.INTERNAL_ONLY -> {
                    if (hasGame) {
                        outputBytes = PcmAudioMixer.applyGain16Bit(
                            gBuf, CHUNK_BYTES,
                            config.internalAudioGain,
                            mixedBuf
                        )
                        bufferToSend = mixedBuf
                    } else {
                        // Game audio idle/quiet: output digital silence to guarantee immediate muxer startup
                        outputBytes = CHUNK_BYTES
                        bufferToSend = silenceBuf
                    }
                }
                AudioSource.MIC_ONLY -> {
                    if (effectiveHasMic) {
                        outputBytes = PcmAudioMixer.applyGain16Bit(
                            mBuf, CHUNK_BYTES,
                            config.micGain,
                            mixedBuf
                        )
                        bufferToSend = mixedBuf
                    } else {
                        outputBytes = CHUNK_BYTES
                        bufferToSend = silenceBuf
                    }
                }
                AudioSource.MUTE -> {
                    outputBytes = 0
                    bufferToSend = silenceBuf
                }
            }

            if (gameChunk != null) recycleBuffer(gameChunk)
            if (micChunk != null) recycleBuffer(micChunk)

            if (outputBytes > 0) {
                // Calibrated, drift-compensated presentation timestamp aligned with master session clock
                val ptsUs = synchronizer.computeNextChunkPtsUs(outputBytes, System.nanoTime())
                listener.onPcmAudioData(bufferToSend, outputBytes, ptsUs)
            }
        }
    }

    fun getAudioDriftMs(): Long = synchronizer.getAudioDriftMs()
}

