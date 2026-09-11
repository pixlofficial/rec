package pixl.rec.core.stream

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pixl.rec.core.audio.AudioCaptureManager
import pixl.rec.core.engine.MultiStreamOutputTarget
import pixl.rec.core.engine.VideoEncoder
import pixl.rec.core.model.PrivacyMicPolicy
import pixl.rec.core.model.StreamConfig
import java.util.concurrent.atomic.AtomicLong

/**
 * Orchestrates the Privacy Shield state machine for live streaming.
 *
 * Enforces:
 * 1. Immediate in-flight video queue purge upon shield activation.
 * 2. Instant emission of a compliant black/privacy slate keyframe.
 * 3. Keepalive slate packet pacing (1 FPS) to keep RTMP sockets and player buffers healthy.
 * 4. Application of the configured [PrivacyMicPolicy] via [AudioCaptureManager].
 * 5. Clean recovery gating on fresh hardware IDR keyframe arrival upon feed resumption.
 * 6. Fail-closed safety: blocks screen video if slate injection cannot be confirmed.
 */
class PrivacyShieldController(
    private val scope: CoroutineScope,
    private val streamConfig: StreamConfig,
    private val multiStreamTarget: MultiStreamOutputTarget?,
    private val audioCaptureManager: AudioCaptureManager?,
    private val videoEncoder: VideoEncoder?,
    private val sessionBaseTimeNs: Long = System.nanoTime()
) {
    enum class State {
        LIVE,
        ACTIVATING,
        SHIELDED,
        DEACTIVATING,
        ERROR
    }

    private val tag = "PrivacyShieldController"

    private val _state = MutableStateFlow(State.LIVE)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _isShieldActive = MutableStateFlow(false)
    val isShieldActive: StateFlow<Boolean> = _isShieldActive.asStateFlow()

    private var keepaliveJob: Job? = null
    private val lastSlatePtsMs = AtomicLong(0L)

    val isHevc = streamConfig.effectiveSupportsHevc
    val canvasWidth = videoEncoder?.configuredWidth ?: 1920
    val canvasHeight = videoEncoder?.configuredHeight ?: 1080

    /**
     * Toggles privacy shield between LIVE and SHIELDED.
     */
    fun toggle() {
        if (_state.value == State.LIVE) {
            activate()
        } else if (_state.value == State.SHIELDED || _state.value == State.ERROR) {
            deactivate()
        }
    }

    /**
     * Activates Privacy Shield:
     * - Immediately purges un-transmitted video frames from all destination queues.
     * - Applies microphone policy (e.g. MUTE_MIC) in audio capture pipeline.
     * - Injects a fresh slate IDR keyframe.
     * - Spawns 1 FPS keepalive ticker to maintain monotonic timestamps.
     */
    fun activate() {
        if (_state.value == State.ACTIVATING || _state.value == State.SHIELDED) return

        _state.value = State.ACTIVATING
        _isShieldActive.value = true
        Log.i(tag, "Activating Privacy Shield...")

        try {
            // 1. Audio mixer mic policy
            audioCaptureManager?.setPrivacyShield(active = true, policy = streamConfig.privacyMicPolicy)

            // 2. Compute current session timestamp for slate keyframe
            val nowMs = ((System.nanoTime() - sessionBaseTimeNs) / 1_000_000L).coerceAtLeast(0L)
            lastSlatePtsMs.set(nowMs)

            // 3. Generate compliant slate keyframe
            val slatePacket = PrivacySlatePacketFactory.createSlatePacket(
                isHevc = isHevc,
                width = canvasWidth,
                height = canvasHeight,
                timestampMs = nowMs
            )

            // 4. Signal output target: purges in-flight queues and injects initial slate keyframe
            multiStreamTarget?.setPrivacyShield(active = true, slatePacket = slatePacket)

            // 5. Start 1 FPS keepalive ticker to keep RTMP connection alive without stale captured frames
            startKeepaliveTicker()

            _state.value = State.SHIELDED
            Log.i(tag, "Privacy Shield engaged successfully (Slate emitted at ${nowMs}ms, Mic policy=${streamConfig.privacyMicPolicy.displayName})")
        } catch (e: Exception) {
            Log.e(tag, "Failed to activate Privacy Shield cleanly; failing closed", e)
            _state.value = State.ERROR
            // Fail-closed: ensure live frames are still blocked even if slate generation errored
            multiStreamTarget?.setPrivacyShield(active = true, slatePacket = null)
        }
    }

    /**
     * Deactivates Privacy Shield:
     * - Stops slate keepalive ticker.
     * - Tells output target to gate live video until fresh keyframe.
     * - Requests dynamic IDR sync keyframe from hardware encoder.
     * - Restores microphone audio.
     */
    fun deactivate() {
        if (_state.value == State.DEACTIVATING || _state.value == State.LIVE) return

        _state.value = State.DEACTIVATING
        Log.i(tag, "Deactivating Privacy Shield; requesting fresh sync keyframe...")

        stopKeepaliveTicker()

        // 1. Output target gates on recovery keyframe before accepting live frames
        multiStreamTarget?.setPrivacyShield(active = false)

        // 2. Trigger fresh hardware IDR keyframe from video encoder
        videoEncoder?.requestSyncFrame()

        // 3. Restore audio mixer mic policy
        audioCaptureManager?.setPrivacyShield(active = false)

        _isShieldActive.value = false
        _state.value = State.LIVE
        Log.i(tag, "Privacy Shield deactivated; live broadcast restored")
    }

    private fun startKeepaliveTicker() {
        keepaliveJob?.cancel()
        keepaliveJob = scope.launch(Dispatchers.IO) {
            while (isActive && _isShieldActive.value) {
                delay(1000L) // 1 FPS slate keepalive
                if (!isActive || !_isShieldActive.value) break

                val nowMs = ((System.nanoTime() - sessionBaseTimeNs) / 1_000_000L).coerceAtLeast(lastSlatePtsMs.get() + 1000L)
                lastSlatePtsMs.set(nowMs)

                val keepalivePacket = PrivacySlatePacketFactory.createSlatePacket(
                    isHevc = isHevc,
                    width = canvasWidth,
                    height = canvasHeight,
                    timestampMs = nowMs
                )
                multiStreamTarget?.injectSlatePacket(keepalivePacket)
            }
        }
    }

    private fun stopKeepaliveTicker() {
        keepaliveJob?.cancel()
        keepaliveJob = null
    }

    fun release() {
        stopKeepaliveTicker()
        _isShieldActive.value = false
        _state.value = State.LIVE
    }
}
