package pixl.rec.ui.dashboard

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import pixl.rec.core.engine.CodecProbe
import pixl.rec.core.model.AudioSource
import pixl.rec.core.model.CaptureTarget
import pixl.rec.core.model.DeviceCapabilities
import pixl.rec.core.model.PillRecallGesture
import pixl.rec.core.model.RecorderState
import pixl.rec.core.model.RecordingConfig
import pixl.rec.core.model.RecordingOrientation
import pixl.rec.core.model.VideoCodec
import pixl.rec.core.storage.ConfigPreferences
import pixl.rec.core.storage.StorageCalculator
import pixl.rec.service.FloatingOverlayService
import pixl.rec.service.RecordingService
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import pixl.rec.core.audio.PcmAudioMixer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

data class DashboardUiState(
    val capabilities: DeviceCapabilities? = null,
    val config: RecordingConfig = RecordingConfig(),
    val availableStorageBytes: Long = 0L,
    val remainingMinutes: Double = 0.0,
    val isStorageLow: Boolean = false,
    val isPermissionDialogRequired: Boolean = false
)

data class TelemetryData(
    val cpuUsagePercent: Float = 0.8f,
    val thermalStatus: String = "NOMINAL",
    val batteryTempCelsius: Float = 29.5f,
    val writeThroughputMbSec: Float = 0f,
    val currentFps: Float = 60f,
    val targetFps: Float = 60f,
    val droppedFrames: Int = 0,
    val gameAudioDb: Float = -60f,
    val micAudioDb: Float = -60f,
    val fpsHistory: List<Float> = List(30) { 1.0f },
    val bitrateHistory: List<Float> = List(30) { 0.5f },
    val audioHistory: List<Float> = List(30) { 0.2f }
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _telemetry = MutableStateFlow(TelemetryData())
    val telemetry: StateFlow<TelemetryData> = _telemetry.asStateFlow()

    val recorderState: StateFlow<RecorderState> = RecordingService.serviceState

    val isRecordingActive: StateFlow<Boolean> = RecordingService.serviceState
        .map { it is RecorderState.Recording || it is RecorderState.Paused }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private var standbyMicJob: Job? = null
    private val _standbyMicDb = MutableStateFlow(-60f)
    private var isStandbyMicRequested = false

    init {
        refreshHardwareCapabilities()
        startTelemetrySampler()

        viewModelScope.launch {
            isRecordingActive.collect { active ->
                if (active) {
                    // Recording started: pause/stop standby mic so ScreenRecorderEngine has exclusive mic access
                    standbyMicJob?.cancel()
                    standbyMicJob = null
                    _standbyMicDb.value = -60f
                } else if (isStandbyMicRequested) {
                    // Recording stopped: resume standby mic monitor if dashboard is still open
                    startStandbyMicMonitor()
                }
            }
        }
    }

    private fun startTelemetrySampler() {
        viewModelScope.launch {
            val powerManager = getApplication<Application>().getSystemService(Context.POWER_SERVICE) as? PowerManager
            val fpsQueue = ArrayDeque<Float>(30).apply { repeat(30) { add(1.0f) } }
            val bitrateQueue = ArrayDeque<Float>(30).apply { repeat(30) { add(0.5f) } }
            val audioQueue = ArrayDeque<Float>(30).apply { repeat(30) { add(0.2f) } }

            while (isActive) {
                val state = recorderState.value
                val isRec = state is RecorderState.Recording
                val isPaused = state is RecorderState.Paused
                val targetFramerate = _uiState.value.config.framerate.toFloat()
                val targetBitrateMbSec = (_uiState.value.config.videoBitrate + _uiState.value.config.audioBitrate) / 8_000_000f

                // 1. Thermal State
                val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
                    when (powerManager.currentThermalStatus) {
                        PowerManager.THERMAL_STATUS_NONE -> "NOMINAL"
                        PowerManager.THERMAL_STATUS_LIGHT -> "NORMAL"
                        PowerManager.THERMAL_STATUS_MODERATE -> "WARM"
                        PowerManager.THERMAL_STATUS_SEVERE -> "THROTTLING"
                        PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
                        else -> "NOMINAL"
                    }
                } else {
                    "NOMINAL"
                }

                // 2. Battery Temperature
                val batteryIntent = runCatching {
                    getApplication<Application>().registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                }.getOrNull()
                val rawTemp = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 290) ?: 290
                val batteryTemp = (rawTemp / 10.0f).coerceIn(15f, 65f)

                // 3. Audio & Hardware Performance Multi-Trace
                if (isRec) {
                    val rec = state as RecorderState.Recording
                    val durationSec = (rec.durationMs / 1000f).coerceAtLeast(0.1f)
                    val mbWritten = rec.bytesWritten / 1_000_000f
                    val throughput = mbWritten / durationSec
                    val fps = if (rec.currentFps > 0f) rec.currentFps else targetFramerate

                    // Trace 1: FPS normalized [0.1 .. 1.0]
                    val normFps = (fps / targetFramerate).coerceIn(0.1f, 1.0f)
                    if (fpsQueue.size >= 30) fpsQueue.removeFirst()
                    fpsQueue.add(normFps)

                    // Trace 2: Bitrate throughput normalized [0.1 .. 0.9]
                    val normBitrate = (throughput / (targetBitrateMbSec * 1.3f)).coerceIn(0.1f, 0.9f)
                    if (bitrateQueue.size >= 30) bitrateQueue.removeFirst()
                    bitrateQueue.add(normBitrate)

                    // Trace 3: Audio level normalized from dB [-60dB .. 0dB] -> [0.05 .. 0.95]
                    val peakDb = maxOf(rec.gameAudioDb, rec.micAudioDb).coerceIn(-60f, 0f)
                    val normAudio = ((peakDb + 60f) / 60f).coerceIn(0.05f, 0.95f)
                    if (audioQueue.size >= 30) audioQueue.removeFirst()
                    audioQueue.add(normAudio)

                    _telemetry.value = TelemetryData(
                        cpuUsagePercent = (1.1f + (Random.nextFloat() * 0.8f)),
                        thermalStatus = thermal,
                        batteryTempCelsius = batteryTemp,
                        writeThroughputMbSec = throughput,
                        currentFps = fps,
                        targetFps = targetFramerate,
                        droppedFrames = 0,
                        gameAudioDb = rec.gameAudioDb,
                        micAudioDb = rec.micAudioDb,
                        fpsHistory = fpsQueue.toList(),
                        bitrateHistory = bitrateQueue.toList(),
                        audioHistory = audioQueue.toList()
                    )
                } else if (isPaused) {
                    _telemetry.value = _telemetry.value.copy(
                        cpuUsagePercent = 0.6f,
                        thermalStatus = thermal,
                        batteryTempCelsius = batteryTemp,
                        writeThroughputMbSec = 0f
                    )
                } else {
                    // Standby Mode: Synthesize ambient multi-sync waves
                    val maxFps = _uiState.value.capabilities?.display?.currentRefreshRate ?: targetFramerate
                    val nowMs = System.currentTimeMillis()

                    val ambientFps = 0.98f + (kotlin.math.sin(nowMs / 400.0).toFloat() * 0.02f)
                    if (fpsQueue.size >= 30) fpsQueue.removeFirst()
                    fpsQueue.add(ambientFps)

                    val ambientBitrate = 0.52f + (kotlin.math.sin(nowMs / 700.0).toFloat() * 0.12f)
                    if (bitrateQueue.size >= 30) bitrateQueue.removeFirst()
                    bitrateQueue.add(ambientBitrate)

                    val liveMicDb = _standbyMicDb.value
                    val normAudio = if (liveMicDb > -58f) {
                        ((liveMicDb + 60f) / 60f).coerceIn(0.05f, 0.95f)
                    } else {
                        0.22f + (kotlin.math.cos(nowMs / 250.0).toFloat() * 0.15f)
                    }
                    if (audioQueue.size >= 30) audioQueue.removeFirst()
                    audioQueue.add(normAudio)

                    _telemetry.value = TelemetryData(
                        cpuUsagePercent = (0.5f + (Random.nextFloat() * 0.4f)),
                        thermalStatus = thermal,
                        batteryTempCelsius = batteryTemp,
                        writeThroughputMbSec = targetBitrateMbSec,
                        currentFps = maxFps,
                        targetFps = targetFramerate,
                        droppedFrames = 0,
                        gameAudioDb = -60f,
                        micAudioDb = liveMicDb,
                        fpsHistory = fpsQueue.toList(),
                        bitrateHistory = bitrateQueue.toList(),
                        audioHistory = audioQueue.toList()
                    )
                }

                delay(200) // 5Hz smooth UI telemetry update
            }
        }
    }

    fun refreshHardwareCapabilities() {
        viewModelScope.launch {
            val probedCapabilities = CodecProbe.probeDevice(getApplication())
            val availableBytes = StorageCalculator.getAvailableStorageBytes()

            val defaultHwConfig = RecordingConfig(
                width = probedCapabilities.recommendedWidth,
                height = probedCapabilities.recommendedHeight,
                dpi = probedCapabilities.display.densityDpi,
                framerate = probedCapabilities.recommendedFramerate,
                videoCodec = probedCapabilities.recommendedCodec,
                videoBitrate = 16_000_000,
                audioSource = AudioSource.INTERNAL_AND_MIC,
                showFloatingPill = true,
                alwaysOnFloatingPill = true,
                autoHidePill = false,
                pillRecallGesture = PillRecallGesture.EDGE_SWIPE,
                shakeToStop = true,
                stopOnScreenOff = true,
                captureTarget = CaptureTarget.ENTIRE_SCREEN
            ).withMacroblockAlignment()

            // Restore user persisted preferences over defaults with hardware capability validation
            val savedConfig = ConfigPreferences.loadConfig(getApplication(), defaultHwConfig)
            val maxDisplayHz = probedCapabilities.display.supportedRefreshRates.maxOrNull()
                ?: probedCapabilities.display.currentRefreshRate
            val supportedFps = CodecProbe.getSupportedFrameratesFor(
                codec = savedConfig.videoCodec,
                width = savedConfig.width,
                height = savedConfig.height,
                maxDisplayHz = maxDisplayHz
            )
            val validatedFps = if (supportedFps.contains(savedConfig.framerate)) {
                savedConfig.framerate
            } else {
                supportedFps.filter { it <= savedConfig.framerate }.maxOrNull() ?: 30
            }
            val maxBitrate = CodecProbe.getMaxBitrateFor(savedConfig.videoCodec)
            val validatedConfig = savedConfig.copy(
                framerate = validatedFps,
                videoBitrate = kotlin.math.min(savedConfig.videoBitrate, maxBitrate)
            )

            val remainingMin = StorageCalculator.estimateRemainingMinutes(
                availableBytes = availableBytes,
                videoBitrateBps = validatedConfig.videoBitrate,
                audioBitrateBps = validatedConfig.audioBitrate
            )

            _uiState.value = DashboardUiState(
                capabilities = probedCapabilities,
                config = validatedConfig,
                availableStorageBytes = availableBytes,
                remainingMinutes = remainingMin,
                isStorageLow = StorageCalculator.isStorageLow(availableBytes)
            )
        }
    }

    fun updateFramerate(fps: Int) {
        val current = _uiState.value.config
        val updated = current.copy(framerate = fps)
        updateConfigAndStorage(updated)
    }

    fun updateResolution(width: Int, height: Int) {
        val current = _uiState.value.config
        val aligned = current.copy(width = width, height = height).withMacroblockAlignment()

        val maxDisplayHz = _uiState.value.capabilities?.display?.supportedRefreshRates?.maxOrNull()
            ?: _uiState.value.capabilities?.display?.currentRefreshRate ?: 120f

        val supportedFps = CodecProbe.getSupportedFrameratesFor(
            codec = aligned.videoCodec,
            width = aligned.width,
            height = aligned.height,
            maxDisplayHz = maxDisplayHz
        )

        val clampedFps = if (supportedFps.contains(aligned.framerate)) {
            aligned.framerate
        } else {
            supportedFps.filter { it <= aligned.framerate }.maxOrNull() ?: 30
        }

        val updated = aligned.copy(framerate = clampedFps)
        updateConfigAndStorage(updated)
    }

    fun updateRecordingOrientation(orientation: RecordingOrientation) {
        val current = _uiState.value.config
        val minDim = kotlin.math.min(current.width, current.height)
        val maxDim = kotlin.math.max(current.width, current.height)
        val (newW, newH) = when (orientation) {
            RecordingOrientation.LANDSCAPE -> maxDim to minDim
            RecordingOrientation.PORTRAIT -> minDim to maxDim
            RecordingOrientation.AUTO -> current.width to current.height
        }
        val aligned = current.copy(
            width = newW,
            height = newH,
            recordingOrientation = orientation
        ).withMacroblockAlignment()

        val maxDisplayHz = _uiState.value.capabilities?.display?.supportedRefreshRates?.maxOrNull()
            ?: _uiState.value.capabilities?.display?.currentRefreshRate ?: 120f
        val supportedFps = CodecProbe.getSupportedFrameratesFor(
            codec = aligned.videoCodec,
            width = aligned.width,
            height = aligned.height,
            maxDisplayHz = maxDisplayHz
        )
        val clampedFps = if (supportedFps.contains(aligned.framerate)) {
            aligned.framerate
        } else {
            supportedFps.filter { it <= aligned.framerate }.maxOrNull() ?: 30
        }
        val updated = aligned.copy(framerate = clampedFps)
        updateConfigAndStorage(updated)
    }

    fun updateVideoCodec(codec: VideoCodec) {
        val current = _uiState.value.config
        val maxDisplayHz = _uiState.value.capabilities?.display?.supportedRefreshRates?.maxOrNull()
            ?: _uiState.value.capabilities?.display?.currentRefreshRate ?: 120f

        val supportedFps = CodecProbe.getSupportedFrameratesFor(
            codec = codec,
            width = current.width,
            height = current.height,
            maxDisplayHz = maxDisplayHz
        )

        val clampedFps = if (supportedFps.contains(current.framerate)) {
            current.framerate
        } else {
            supportedFps.filter { it <= current.framerate }.maxOrNull() ?: 30
        }

        val maxBitrate = CodecProbe.getMaxBitrateFor(codec)
        val clampedBitrate = kotlin.math.min(current.videoBitrate, maxBitrate)

        val updated = current.copy(
            videoCodec = codec,
            framerate = clampedFps,
            videoBitrate = clampedBitrate
        )
        updateConfigAndStorage(updated)
    }

    fun updateAudioSource(source: AudioSource) {
        val current = _uiState.value.config
        val updated = current.copy(audioSource = source)
        updateConfigAndStorage(updated)
    }

    fun updateVideoBitrate(bitrateMbps: Int) {
        val current = _uiState.value.config
        val maxCodecBitrate = CodecProbe.getMaxBitrateFor(current.videoCodec)
        val targetBps = bitrateMbps * 1_000_000
        val clampedBps = kotlin.math.min(targetBps, maxCodecBitrate)
        val updated = current.copy(videoBitrate = clampedBps)
        updateConfigAndStorage(updated)
    }

    fun toggleAlwaysOnFloatingPill(enabled: Boolean) {
        val current = _uiState.value.config
        val updated = current.copy(alwaysOnFloatingPill = enabled)
        updateConfigAndStorage(updated)
        if (enabled) {
            FloatingOverlayService.start(getApplication(), updated)
        } else {
            FloatingOverlayService.stop(getApplication())
        }
    }

    fun toggleFloatingPill(enabled: Boolean) {
        val current = _uiState.value.config
        val updated = current.copy(showFloatingPill = enabled)
        updateConfigAndStorage(updated)
    }

    fun toggleAutoHidePill(enabled: Boolean) {
        val current = _uiState.value.config
        val updated = current.copy(autoHidePill = enabled)
        updateConfigAndStorage(updated)
    }

    fun updatePillRecallGesture(gesture: PillRecallGesture) {
        val current = _uiState.value.config
        val updated = current.copy(pillRecallGesture = gesture)
        updateConfigAndStorage(updated)
    }

    fun toggleShakeToStop(enabled: Boolean) {
        val current = _uiState.value.config
        val updated = current.copy(shakeToStop = enabled)
        updateConfigAndStorage(updated)
    }

    fun toggleStopOnScreenOff(enabled: Boolean) {
        val current = _uiState.value.config
        val updated = current.copy(stopOnScreenOff = enabled)
        updateConfigAndStorage(updated)
    }

    fun updateCaptureTarget(target: CaptureTarget) {
        val current = _uiState.value.config
        val updated = current.copy(captureTarget = target)
        updateConfigAndStorage(updated)
    }

    fun updateStandbyHudConfig(hudConfig: pixl.rec.core.model.HudStyleConfig) {
        val current = _uiState.value.config
        val updated = current.copy(standbyHudConfig = hudConfig)
        updateConfigAndStorage(updated)
        if (updated.alwaysOnFloatingPill && (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M || android.provider.Settings.canDrawOverlays(getApplication()))) {
            FloatingOverlayService.start(getApplication(), updated)
        }
    }

    fun updateRecordingHudConfig(hudConfig: pixl.rec.core.model.HudStyleConfig) {
        val current = _uiState.value.config
        val updated = current.copy(recordingHudConfig = hudConfig)
        updateConfigAndStorage(updated)
        if (updated.alwaysOnFloatingPill && (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M || android.provider.Settings.canDrawOverlays(getApplication()))) {
            FloatingOverlayService.start(getApplication(), updated)
        }
    }

    fun updateHudSnapBehavior(snapBehavior: pixl.rec.core.model.HudSnapBehavior) {
        val current = _uiState.value.config
        val updated = current.copy(hudSnapBehavior = snapBehavior)
        updateConfigAndStorage(updated)
        if (updated.alwaysOnFloatingPill && (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M || android.provider.Settings.canDrawOverlays(getApplication()))) {
            FloatingOverlayService.start(getApplication(), updated)
        }
    }

    fun updateHudConfig(hudConfig: pixl.rec.core.model.HudStyleConfig) {
        updateStandbyHudConfig(hudConfig)
    }

    fun startRecording(resultCode: Int, resultData: Intent) {
        val currentConfig = _uiState.value.config
        RecordingService.startService(
            context = getApplication(),
            resultCode = resultCode,
            resultData = resultData,
            config = currentConfig
        )
    }

    fun stopRecording() {
        RecordingService.stopService(getApplication())
    }

    fun pauseRecording() {
        RecordingService.pauseService(getApplication())
    }

    fun resumeRecording() {
        RecordingService.resumeService(getApplication())
    }

    private fun updateConfigAndStorage(newConfig: RecordingConfig) {
        // Persist to SharedPreferences immediately
        ConfigPreferences.saveConfig(getApplication(), newConfig)

        val availableBytes = _uiState.value.availableStorageBytes
        val remainingMin = StorageCalculator.estimateRemainingMinutes(
            availableBytes = availableBytes,
            videoBitrateBps = newConfig.videoBitrate,
            audioBitrateBps = newConfig.audioBitrate
        )

        _uiState.value = _uiState.value.copy(
            config = newConfig,
            remainingMinutes = remainingMin
        )
    }

    fun startStandbyMicMonitor() {
        isStandbyMicRequested = true
        if (isRecordingActive.value) return
        if (standbyMicJob?.isActive == true) return

        val hasPermission = ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) return

        standbyMicJob = viewModelScope.launch(Dispatchers.IO) {
            val sampleRate = 48000
            val channelConfig = AudioFormat.CHANNEL_IN_STEREO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = (minBufferSize * 2).coerceAtLeast(2048)

            var audioRecord: AudioRecord? = null
            try {
                @SuppressLint("MissingPermission")
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )

                if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    audioRecord.release()
                    return@launch
                }

                audioRecord.startRecording()
                val pcmBuffer = ByteArray(bufferSize)

                while (isActive && isStandbyMicRequested && !isRecordingActive.value) {
                    val read = audioRecord.read(pcmBuffer, 0, pcmBuffer.size)
                    if (read > 0) {
                        val db = PcmAudioMixer.calculateDbLevel(pcmBuffer, read)
                        _standbyMicDb.value = db
                    }
                    delay(50) // ~20Hz update rate
                }
            } catch (e: Exception) {
                // Ignore transient audio initialization issues
            } finally {
                runCatching {
                    if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        audioRecord.stop()
                    }
                    audioRecord?.release()
                }
                _standbyMicDb.value = -60f
            }
        }
    }

    fun stopStandbyMicMonitor() {
        isStandbyMicRequested = false
        standbyMicJob?.cancel()
        standbyMicJob = null
        _standbyMicDb.value = -60f
    }

    override fun onCleared() {
        super.onCleared()
        stopStandbyMicMonitor()
    }
}
