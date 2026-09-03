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
import pixl.rec.core.model.RecordingOrientation
import pixl.rec.core.storage.MediaStoreWriter
import pixl.rec.core.storage.StorageCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
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

    // Pending sample queue & buffer pool for samples received before muxer starts
    private val pendingSamples = mutableListOf<PendingSample>()
    private val pendingBufferPool = ArrayDeque<ByteArray>()

    private data class PendingSample(
        val trackIndex: Int,
        val data: ByteArray,
        val offset: Int,
        val size: Int,
        val presentationTimeUs: Long,
        val flags: Int
    )

    private fun getOrCreatePendingBuffer(size: Int): ByteArray {
        val existing = pendingBufferPool.removeFirstOrNull()
        return if (existing != null && existing.size >= size) existing else ByteArray(size)
    }

    private fun recyclePendingBuffer(buffer: ByteArray) {
        if (pendingBufferPool.size < 32) {
            pendingBufferPool.addLast(buffer)
        }
    }

    private val isRecording = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)

    private var startTimeMs = 0L
    private var totalPausedDurationMs = 0L
    private var pauseStartTimeMs = 0L
    private val totalBytesWritten = AtomicLong(0L)
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

            // 0. Compute Canvas Dimensions based on orientation policy & current rotation
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            val defaultDisplay = displayManager?.getDisplay(android.view.Display.DEFAULT_DISPLAY)
            val currentRotation = defaultDisplay?.rotation ?: android.view.Surface.ROTATION_0
            lastRecordedRotation = currentRotation

            val isLandscape = currentRotation == android.view.Surface.ROTATION_90 || currentRotation == android.view.Surface.ROTATION_270
            val portraitWidth = kotlin.math.min(config.width, config.height)
            val portraitHeight = kotlin.math.max(config.width, config.height)

            val (canvasWidth, canvasHeight) = when (config.recordingOrientation) {
                RecordingOrientation.AUTO -> if (isLandscape) {
                    portraitHeight to portraitWidth
                } else {
                    portraitWidth to portraitHeight
                }
                RecordingOrientation.LANDSCAPE -> portraitHeight to portraitWidth
                RecordingOrientation.PORTRAIT -> portraitWidth to portraitHeight
            }

            val activeConfig = config.copy(
                width = canvasWidth,
                height = canvasHeight
            ).withMacroblockAlignment()

            Log.i(tag, "Configuring recording canvas: ${activeConfig.width}x${activeConfig.height} (Policy: ${config.recordingOrientation.displayName}, Device Landscape: $isLandscape)")

            // 1. Initialize Video Encoder with active canvas configuration
            val vEncoder = VideoEncoder(activeConfig, object : VideoEncoder.OutputListener {
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

            val finalConfig = activeConfig.copy(
                width = vEncoder.configuredWidth,
                height = vEncoder.configuredHeight,
                framerate = vEncoder.configuredFramerate
            )

            // 2. Initialize MediaStore Scoped Storage Writer with final configured canvas
            val writer = MediaStoreWriter(context, finalConfig)
            mediaStoreWriter = writer
            mediaMuxer = writer.open()

            // 3. Initialize Audio Pipeline if enabled
            if (finalConfig.audioSource.hasAudio) {
                val aEncoder = AudioEncoder(finalConfig, object : AudioEncoder.OutputListener {
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
                    config = finalConfig,
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

            // 4. Start all pipelines with unified monotonic session baseline
            // MediaCodec MUST be in Executing state and draining buffers before VirtualDisplay is attached,
            // otherwise high refresh rates (60-120fps) will immediately overflow the input BufferQueue on Android 14+.
            val sessionBaseTimeNs = System.nanoTime()

            isRecording.set(true)
            isPaused.set(false)
            startTimeMs = System.currentTimeMillis()
            totalPausedDurationMs = 0L
            totalBytesWritten.set(0L)

            vEncoder.start(engineScope, sessionBaseTimeNs)
            audioEncoder?.start(engineScope)
            audioCaptureManager?.start(engineScope, sessionBaseTimeNs)

            // 5. Create VirtualDisplay piped directly to the actively running VideoEncoder Input Surface (Zero-Copy)
            val surface = vEncoder.inputSurface ?: throw IllegalStateException("Encoder surface is null")
            virtualDisplay = mediaProjection.createVirtualDisplay(
                "PixL-REC-Display",
                finalConfig.width,
                finalConfig.height,
                finalConfig.dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface,
                null,
                Handler(Looper.getMainLooper())
            )

            // Register Display Rotation Listener for rotation telemetry & logging
            val listener = object : DisplayManager.DisplayListener {
                override fun onDisplayAdded(displayId: Int) = Unit
                override fun onDisplayRemoved(displayId: Int) = Unit
                override fun onDisplayChanged(displayId: Int) {
                    if (displayId == android.view.Display.DEFAULT_DISPLAY) {
                        val activeDisplay = displayManager?.getDisplay(android.view.Display.DEFAULT_DISPLAY) ?: return
                        val newRotation = activeDisplay.rotation
                        if (newRotation != lastRecordedRotation) {
                            lastRecordedRotation = newRotation
                            Log.i(tag, "Physical display rotation changed to $newRotation (Canvas locked at ${activeConfig.width}x${activeConfig.height})")
                        }
                    }
                }
            }
            displayListener = listener
            displayManager?.registerDisplayListener(listener, Handler(Looper.getMainLooper()))

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
            _state.value = RecorderState.Paused(duration, totalBytesWritten.get())
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
        val finalBytes = totalBytesWritten.get()

        engineScope.launch(Dispatchers.IO) {
            try {
                // 1. Stop audio capture and encoders
                audioCaptureManager?.stop()
                audioEncoder?.stop()
                videoEncoder?.stop()

                // Wait for deterministic EOS signal flush from hardware encoders (up to 2000ms max)
                withTimeoutOrNull(2000L) {
                    val videoEos = async { videoEncoder?.awaitEos() }
                    val audioEos = async { audioEncoder?.awaitEos() }
                    videoEos.await()
                    audioEos.await()
                }

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

        pendingSamples.clear()
        pendingBufferPool.clear()

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
                        totalBytesWritten.addAndGet(sample.size.toLong())
                    } catch (e: Exception) {
                        Log.e(tag, "Error draining pending sample", e)
                    }
                }
                recyclePendingBuffer(sample.data)
            }
            pendingSamples.clear()
        }
    }

    private fun writeSample(logicalTrack: Int, buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        if (!isRecording.get()) return

        // Fast path: MediaMuxer is already active and tracks are configured.
        // MediaMuxer.writeSampleData() is internally thread-safe across different tracks.
        if (isMuxerStarted.get()) {
            val realTrack = if (logicalTrack == 0) videoTrackIndex else audioTrackIndex
            if (realTrack >= 0) {
                try {
                    mediaMuxer?.writeSampleData(realTrack, buffer, bufferInfo)
                    totalBytesWritten.addAndGet(bufferInfo.size.toLong())
                } catch (e: Exception) {
                    Log.e(tag, "Error writing sample data to track $realTrack", e)
                }
            }
            return
        }

        // Slow path: Muxer not yet started, queue under lock
        muxerLock.withLock {
            if (isMuxerStarted.get()) {
                val realTrack = if (logicalTrack == 0) videoTrackIndex else audioTrackIndex
                if (realTrack >= 0) {
                    try {
                        mediaMuxer?.writeSampleData(realTrack, buffer, bufferInfo)
                        totalBytesWritten.addAndGet(bufferInfo.size.toLong())
                    } catch (e: Exception) {
                        Log.e(tag, "Error writing sample data to track $realTrack", e)
                    }
                }
            } else {
                // Queue until muxer starts (max 100 frames)
                if (pendingSamples.size < 100) {
                    val bytes = getOrCreatePendingBuffer(bufferInfo.size)
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
                        bytesWritten = totalBytesWritten.get(),
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
