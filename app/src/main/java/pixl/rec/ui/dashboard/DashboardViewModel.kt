package pixl.rec.ui.dashboard

import android.app.Application
import android.content.Intent
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
import pixl.rec.core.storage.StorageCalculator
import pixl.rec.service.RecordingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DashboardUiState(
    val capabilities: DeviceCapabilities? = null,
    val config: RecordingConfig = RecordingConfig(),
    val availableStorageBytes: Long = 0L,
    val remainingMinutes: Double = 0.0,
    val isStorageLow: Boolean = false,
    val isPermissionDialogRequired: Boolean = false
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    val recorderState: StateFlow<RecorderState> = RecordingService.serviceState

    init {
        refreshHardwareCapabilities()
    }

    fun refreshHardwareCapabilities() {
        viewModelScope.launch {
            val probedCapabilities = CodecProbe.probeDevice(getApplication())
            val availableBytes = StorageCalculator.getAvailableStorageBytes()

            val initialConfig = RecordingConfig(
                width = probedCapabilities.recommendedWidth,
                height = probedCapabilities.recommendedHeight,
                dpi = probedCapabilities.display.densityDpi,
                framerate = probedCapabilities.recommendedFramerate,
                videoCodec = probedCapabilities.recommendedCodec,
                videoBitrate = 16_000_000,
                audioSource = AudioSource.INTERNAL_AND_MIC,
                showFloatingPill = true,
                autoHidePill = false,
                pillRecallGesture = PillRecallGesture.EDGE_SWIPE,
                shakeToStop = true,
                stopOnScreenOff = true,
                captureTarget = CaptureTarget.ENTIRE_SCREEN
            ).withMacroblockAlignment()

            val remainingMin = StorageCalculator.estimateRemainingMinutes(
                availableBytes = availableBytes,
                videoBitrateBps = initialConfig.videoBitrate,
                audioBitrateBps = initialConfig.audioBitrate
            )

            _uiState.value = DashboardUiState(
                capabilities = probedCapabilities,
                config = initialConfig,
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
        val updated = current.copy(width = width, height = height).withMacroblockAlignment()
        updateConfigAndStorage(updated)
    }

    fun updateRecordingOrientation(orientation: RecordingOrientation) {
        val current = _uiState.value.config
        val updated = current.copy(recordingOrientation = orientation)
        updateConfigAndStorage(updated)
    }

    fun updateVideoCodec(codec: VideoCodec) {
        val current = _uiState.value.config
        val updated = current.copy(videoCodec = codec)
        updateConfigAndStorage(updated)
    }

    fun updateAudioSource(source: AudioSource) {
        val current = _uiState.value.config
        val updated = current.copy(audioSource = source)
        updateConfigAndStorage(updated)
    }

    fun updateVideoBitrate(bitrateMbps: Int) {
        val current = _uiState.value.config
        val updated = current.copy(videoBitrate = bitrateMbps * 1_000_000)
        updateConfigAndStorage(updated)
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
}
