package pixl.rec.core.engine

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.Log
import pixl.rec.core.audio.AudioCaptureManager
import pixl.rec.core.model.RecorderState
import pixl.rec.core.model.RecordingConfig
import pixl.rec.core.storage.MediaStoreWriter
import pixl.rec.core.storage.StorageCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Master engine orchestrating the zero-copy video encoder, audio capture pipeline,
 * and Scoped Storage MP4 multiplexer into a unified telemetry state machine.
 */
class ScreenRecorderEngine(
    private val context: Context,
    private val config: RecordingConfig,
    private val mediaProjection: MediaProjection
) {
    private val tag = "ScreenRecorderEngine"
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<RecorderState>(RecorderState.Idle)
    val state: StateFlow<RecorderState> = _state.asStateFlow()

    private var videoEncoder: VideoEncoder? = null
    private var audioEncoder: AudioEncoder? = null
    private var audioCaptureManager: AudioCaptureManager? = null
    private var mediaStoreWriter: MediaStoreWriter? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var displayListener: DisplayManager.DisplayListener? = null
    private var lastRecordedRotation: Int = -1

    private var mediaMuxer: MediaMuxer? = null
    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private val isMuxerStarted = AtomicBoolean(false)
    private val muxerLock = ReentrantLock()

    // Pending sample queue for samples received before muxer starts
    private val pendingSamples = mutableListOf<PendingSample>()

    private data class PendingSample(
        val trackIndex: Int,
        val data: ByteArray,
        val offset: Int,
        val size: Int,
        val presentationTimeUs: Long,
        val flags: Int
    )

    private val isRecording = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)

    private var startTimeMs = 0L
    private var totalPausedDurationMs = 0L
    private var pauseStartTimeMs = 0L
    private var totalBytesWritten = 0L
    private var currentFps = 0f
    private var gameAudioDb = -60f
    private var micAudioDb = -60f

    private var telemetryTickerJob: Job? = null

    /**
     * Prepares hardware encoders, audio capture pipelines, and Scoped Storage descriptors.
     */
    fun start() {
        if (isRecording.get()) return
        _state.value = RecorderState.Preparing()

        try {
            // Reset state
            videoTrackIndex = -1
            audioTrackIndex = -1
            isMuxerStarted.set(false)
            pendingSamples.clear()

            // 1. Initialize MediaStore Scoped Storage Writer
            val writer = MediaStoreWriter(context, config)
            mediaStoreWriter = writer
            mediaMuxer = writer.open()

            // 2. Initialize Video Encoder
            val vEncoder = VideoEncoder(config, object : VideoEncoder.OutputListener {
                override fun onVideoFormatChanged(format: MediaFormat) {
                    handleVideoFormat(format)
                }

                override fun onVideoSampleData(buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
                    writeSample(0, buffer, bufferInfo) // Track 0 is video
                }

                override fun onVideoError(e: Throwable) {
                    handleError("Video encoder error: ${e.message}", e)
                }

                override fun onVideoFpsMeasured(fps: Float) {
                    currentFps = fps
                }
            })
            vEncoder.prepare()
            videoEncoder = vEncoder

            // 3. Initialize Audio Pipeline if enabled
            if (config.audioSource.hasAudio) {
                val aEncoder = AudioEncoder(config, object : AudioEncoder.OutputListener {
                    override fun onAudioFormatChanged(format: MediaFormat) {
                        handleAudioFormat(format)
                    }

                    override fun onAudioSampleData(buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
                        writeSample(1, buffer, bufferInfo) // Track 1 is audio
                    }

                    override fun onAudioError(e: Throwable) {
                        Log.e(tag, "Audio encoder error", e)
                    }
                })
                aEncoder.prepare()
                audioEncoder = aEncoder

                val aCapture = AudioCaptureManager(
                    context = context,
                    config = config,
                    mediaProjection = mediaProjection,
                    listener = object : AudioCaptureManager.AudioDataListener {
                        override fun onPcmAudioData(pcmBytes: ByteArray, length: Int, ptsUs: Long) {
                            audioEncoder?.enqueuePcmData(pcmBytes, length, ptsUs)
                        }

                        override fun onAudioLevels(gameDb: Float, micDb: Float) {
                            gameAudioDb = gameDb
                            micAudioDb = micDb
                        }

                        override fun onAudioError(e: Throwable) {
                            Log.w(tag, "Audio capture error: ${e.message}")
                        }
                    }
                )
                aCapture.prepare()
                audioCaptureManager = aCapture
            }

            // 4. Create VirtualDisplay piped directly to VideoEncoder Input Surface (Zero-Copy)
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            val defaultDisplay = displayManager?.getDisplay(android.view.Display.DEFAULT_DISPLAY)
            val currentRotation = defaultDisplay?.rotation ?: android.view.Surface.ROTATION_0
            lastRecordedRotation = currentRotation

            val isLandscape = currentRotation == android.view.Surface.ROTATION_90 || currentRotation == android.view.Surface.ROTATION_270
            val portraitWidth = kotlin.math.min(config.width, config.height)
            val portraitHeight = kotlin.math.max(config.width, config.height)
            val (initialWidth, initialHeight) = if (isLandscape) {
                portraitHeight to portraitWidth
            } else {
                portraitWidth to portraitHeight
            }

            val surface = vEncoder.inputSurface ?: throw IllegalStateException("Encoder surface is null")
            virtualDisplay = mediaProjection.createVirtualDisplay(
                "PixL-REC-Display",
                initialWidth,
                initialHeight,
                config.dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface,
                null,
                Handler(Looper.getMainLooper())
            )

            // Register Real-Time Display Rotation Listener for Automatic Dynamic Adaptation
            val listener = object : DisplayManager.DisplayListener {
                override fun onDisplayAdded(displayId: Int) = Unit
                override fun onDisplayRemoved(displayId: Int) = Unit
                override fun onDisplayChanged(displayId: Int) {
                    if (displayId == android.view.Display.DEFAULT_DISPLAY) {
                        val activeDisplay = displayManager?.getDisplay(android.view.Display.DEFAULT_DISPLAY) ?: return
                        val newRotation = activeDisplay.rotation
                        if (newRotation != lastRecordedRotation) {
                            lastRecordedRotation = newRotation
                            val isLand = newRotation == android.view.Surface.ROTATION_90 || newRotation == android.view.Surface.ROTATION_270
                            val (w, h) = if (isLand) {
                                portraitHeight to portraitWidth
                            } else {
                                portraitWidth to portraitHeight
                            }
                            Log.i(tag, "Display rotation changed to $newRotation -> Resizing VirtualDisplay to ${w}x${h} @ ${config.dpi} DPI")
                            virtualDisplay?.resize(w, h, config.dpi)
                        }
                    }
                }
            }
            displayListener = listener
            displayManager?.registerDisplayListener(listener, Handler(Looper.getMainLooper()))

            // 5. Start all pipelines
            isRecording.set(true)
            isPaused.set(false)
            startTimeMs = System.currentTimeMillis()
            totalPausedDurationMs = 0L
            totalBytesWritten = 0L

            vEncoder.start(engineScope)
            audioEncoder?.start(engineScope)
            audioCaptureManager?.start(engineScope)

            startTelemetryTicker()
            Log.i(tag, "ScreenRecorderEngine started successfully")
        } catch (e: Exception) {
            Log.e(tag, "Failed to start ScreenRecorderEngine", e)
            handleError("Failed to initialize recording pipeline: ${e.message}", e)
        }
    }

    /**
     * Pauses active recording stream.
     */
    fun pause() {
        if (isRecording.get() && isPaused.compareAndSet(false, true)) {
            pauseStartTimeMs = System.currentTimeMillis()
            videoEncoder?.pause()
            audioCaptureManager?.pause()
            val duration = getRecordedDurationMs()
            _state.value = RecorderState.Paused(duration, totalBytesWritten)
            Log.i(tag, "ScreenRecorderEngine paused at ${duration}ms")
        }
    }

    /**
     * Resumes active recording stream.
     */
    fun resume() {
        if (isRecording.get() && isPaused.compareAndSet(true, false)) {
            val pausedDelta = System.currentTimeMillis() - pauseStartTimeMs
            totalPausedDurationMs += pausedDelta
            videoEncoder?.resume()
            audioCaptureManager?.resume()
            Log.i(tag, "ScreenRecorderEngine resumed after ${pausedDelta}ms pause")
        }
    }

    /**
     * Gracefully finishes encoding, finalizes Scoped Storage MP4, and resets state.
     */
    fun stop() {
        if (!isRecording.get()) return
        _state.value = RecorderState.Stopping
        isRecording.set(false)
        telemetryTickerJob?.cancel()

        val finalDurationMs = getRecordedDurationMs()
        val finalBytes = totalBytesWritten

        engineScope.launch(Dispatchers.IO) {
            try {
                // 1. Stop audio capture and encoders
                audioCaptureManager?.stop()
                audioEncoder?.stop()
                videoEncoder?.stop()

                // Small delay to allow codec EOS flushing
                delay(150)

                // 2. Release Virtual Display & unregister rotation listener
                displayListener?.let {
                    try {
                        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
                        displayManager?.unregisterDisplayListener(it)
                    } catch (e: Exception) {
                        Log.w(tag, "Error unregistering displayListener", e)
                    }
                    displayListener = null
                }
                virtualDisplay?.release()
                virtualDisplay = null

                // 3. Stop Muxer safely
                muxerLock.withLock {
                    if (isMuxerStarted.get()) {
                        try {
                            mediaMuxer?.stop()
                        } catch (e: Exception) {
                            Log.w(tag, "Error stopping MediaMuxer", e)
                        }
                        try {
                            mediaMuxer?.release()
                        } catch (e: Exception) {
                            Log.w(tag, "Error releasing MediaMuxer", e)
                        }
                        isMuxerStarted.set(false)
                    }
                }

                // 4. Finalize MediaStore record with accurate duration
                val uri = mediaStoreWriter?.currentUri
                mediaStoreWriter?.finish(finalDurationMs, finalBytes)

                // 5. Release Encoders
                videoEncoder?.release()
                audioEncoder?.release()
                audioCaptureManager?.release()
                videoEncoder = null
                audioEncoder = null
                audioCaptureManager = null
                mediaStoreWriter = null

                val formattedSize = StorageCalculator.formatBytes(finalBytes)
                _state.value = RecorderState.Finished(
                    uri = uri,
                    durationMs = finalDurationMs,
                    totalBytes = finalBytes,
                    formattedSize = formattedSize
                )
                Log.i(tag, "ScreenRecorderEngine successfully finished: $uri ($formattedSize, ${finalDurationMs / 1000}s)")
            } catch (e: Exception) {
                Log.e(tag, "Error stopping ScreenRecorderEngine", e)
                handleError("Failed to finalize recording: ${e.message}", e)
            }
        }
    }

    /**
     * Aborts recording session immediately, releasing all hardware resources and deleting incomplete files.
     */
    fun release() {
        isRecording.set(false)
        telemetryTickerJob?.cancel()
        engineScope.cancel()

        displayListener?.let {
            try {
                val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
                displayManager?.unregisterDisplayListener(it)
            } catch (e: Exception) {
                Log.w(tag, "Error unregistering displayListener on release", e)
            }
            displayListener = null
        }

        try {
            virtualDisplay?.release()
        } catch (e: Exception) {
            Log.w(tag, "Error releasing VirtualDisplay", e)
        }

        try {
            videoEncoder?.release()
            audioEncoder?.release()
            audioCaptureManager?.release()
        } catch (e: Exception) {
            Log.w(tag, "Error releasing encoders", e)
        }

        muxerLock.withLock {
            if (isMuxerStarted.get()) {
                try {
                    mediaMuxer?.stop()
                    mediaMuxer?.release()
                } catch (e: Exception) {
                    Log.w(tag, "Error releasing MediaMuxer on release", e)
                }
                isMuxerStarted.set(false)
            }
        }

        mediaStoreWriter?.cancel()
        _state.value = RecorderState.Idle
    }

    private fun handleVideoFormat(format: MediaFormat) {
        muxerLock.withLock {
            val muxer = mediaMuxer ?: return
            if (videoTrackIndex < 0) {
                videoTrackIndex = muxer.addTrack(format)
                Log.i(tag, "Added Video track: index $videoTrackIndex")
                checkAndStartMuxer()
            }
        }
    }

    private fun handleAudioFormat(format: MediaFormat) {
        muxerLock.withLock {
            val muxer = mediaMuxer ?: return
            if (audioTrackIndex < 0) {
                audioTrackIndex = muxer.addTrack(format)
                Log.i(tag, "Added Audio track: index $audioTrackIndex")
                checkAndStartMuxer()
            }
        }
    }

    private fun checkAndStartMuxer() {
        val hasAudio = config.audioSource.hasAudio
        val isVideoReady = videoTrackIndex >= 0
        val isAudioReady = !hasAudio || audioTrackIndex >= 0

        if (isVideoReady && isAudioReady && !isMuxerStarted.get()) {
            val muxer = mediaMuxer ?: return
            muxer.start()
            isMuxerStarted.set(true)
            Log.i(tag, "MediaMuxer started with all configured tracks (Video: $videoTrackIndex, Audio: $audioTrackIndex)")

            // Drain queued pending samples
            for (sample in pendingSamples) {
                val realTrack = if (sample.trackIndex == 0) videoTrackIndex else audioTrackIndex
                if (realTrack >= 0) {
                    try {
                        val byteBuf = ByteBuffer.wrap(sample.data, sample.offset, sample.size)
                        val info = MediaCodec.BufferInfo().apply {
                            set(sample.offset, sample.size, sample.presentationTimeUs, sample.flags)
                        }
                        muxer.writeSampleData(realTrack, byteBuf, info)
                        totalBytesWritten += sample.size
                    } catch (e: Exception) {
                        Log.e(tag, "Error draining pending sample", e)
                    }
                }
            }
            pendingSamples.clear()
        }
    }

    private fun writeSample(logicalTrack: Int, buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        if (!isRecording.get()) return

        muxerLock.withLock {
            if (isMuxerStarted.get()) {
                val realTrack = if (logicalTrack == 0) videoTrackIndex else audioTrackIndex
                if (realTrack >= 0) {
                    try {
                        mediaMuxer?.writeSampleData(realTrack, buffer, bufferInfo)
                        totalBytesWritten += bufferInfo.size
                    } catch (e: Exception) {
                        Log.e(tag, "Error writing sample data to track $realTrack", e)
                    }
                }
            } else {
                // Queue until muxer starts (max 100 frames)
                if (pendingSamples.size < 100) {
                    val bytes = ByteArray(bufferInfo.size)
                    val oldPos = buffer.position()
                    buffer.position(bufferInfo.offset)
                    buffer.get(bytes, 0, bufferInfo.size)
                    buffer.position(oldPos)

                    pendingSamples.add(
                        PendingSample(
                            trackIndex = logicalTrack,
                            data = bytes,
                            offset = 0,
                            size = bufferInfo.size,
                            presentationTimeUs = bufferInfo.presentationTimeUs,
                            flags = bufferInfo.flags
                        )
                    )
                }
            }
        }
    }

    private fun startTelemetryTicker() {
        telemetryTickerJob?.cancel()
        telemetryTickerJob = engineScope.launch {
            while (isActive && isRecording.get()) {
                if (!isPaused.get()) {
                    val duration = getRecordedDurationMs()
                    _state.value = RecorderState.Recording(
                        durationMs = duration,
                        bytesWritten = totalBytesWritten,
                        currentFps = currentFps,
                        gameAudioDb = gameAudioDb,
                        micAudioDb = micAudioDb,
                        isPaused = false
                    )
                }
                delay(100) // Smooth 10Hz ticker
            }
        }
    }

    private fun getRecordedDurationMs(): Long {
        if (startTimeMs == 0L) return 0L
        val now = if (isPaused.get()) pauseStartTimeMs else System.currentTimeMillis()
        return (now - startTimeMs - totalPausedDurationMs).coerceAtLeast(0L)
    }

    private fun handleError(message: String, throwable: Throwable? = null) {
        _state.value = RecorderState.Error(message, throwable)
        release()
    }
}
