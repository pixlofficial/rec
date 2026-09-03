package pixl.rec.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import pixl.rec.core.model.PillRecallGesture
import pixl.rec.core.model.RecorderState
import pixl.rec.core.model.RecordingConfig
import pixl.rec.service.RecordingService
import pixl.rec.ui.theme.CyberYellow

/**
 * Floating Overlay Pill View for REC.
 * Adapts seamlessly between Standby and Active Recording states,
 * hosting the 360° Free-Space Hex-Pod and 180° Edge-Fan menus.
 */
@Composable
fun FloatingPillView(
    config: RecordingConfig = RecordingConfig(),
    isExpanded: Boolean = false,
    isDockedOnLeft: Boolean = false,
    isDockedOnRight: Boolean = false,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit = {},
    onExpandChanged: (Boolean) -> Unit = {},
    onCollapseComplete: () -> Unit = {},
    onRecordClick: () -> Unit = {},
    onReplayClick: () -> Unit = {},
    onScreenshotClick: () -> Unit = {},
    onVaultClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onStopClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit
) {
    val serviceState by RecordingService.serviceState.collectAsState()
    val isRecordingActive = serviceState is RecorderState.Recording || serviceState is RecorderState.Paused
    val isPaused = serviceState is RecorderState.Paused

    val haptics = LocalHapticFeedback.current
    var isInvisibleGhost by remember { mutableStateOf(config.hidePillDuringRecording) }

    val currentDuration = when (val s = serviceState) {
        is RecorderState.Recording -> s.durationMs
        is RecorderState.Paused -> s.durationMs
        else -> 0L
    }

    LaunchedEffect(serviceState) {
        if (serviceState is RecorderState.Recording && config.autoHidePill) {
            delay(2500)
            isInvisibleGhost = true
        } else if (serviceState !is RecorderState.Recording && serviceState !is RecorderState.Paused) {
            isInvisibleGhost = false
        }
    }

    fun recallPill() {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        isInvisibleGhost = false
    }

    if (isInvisibleGhost) {
        // --- INVISIBLE GHOST MODE (Zero On-Screen Capture with Gesture Recall) ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(config.pillRecallGesture) {
                    when (config.pillRecallGesture) {
                        PillRecallGesture.EDGE_SWIPE -> {
                            detectDragGestures { change, dragAmount ->
                                if (kotlin.math.abs(dragAmount.x) > 12f || kotlin.math.abs(dragAmount.y) > 12f) {
                                    change.consume()
                                    recallPill()
                                }
                            }
                        }
                        PillRecallGesture.EDGE_TAP -> {
                            detectTapGestures(onTap = { recallPill() })
                        }
                        PillRecallGesture.DOUBLE_TAP -> {
                            detectTapGestures(onDoubleTap = { recallPill() })
                        }
                    }
                }
                .padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = 36.dp)
                    .alpha(0.04f)
                    .background(CyberYellow, RoundedCornerShape(2.dp))
            )
        }
    } else {
        // --- ACTIVE / STANDBY RADIAL MENU (Hex-Pod in Free Space or Edge-Fan on Bezel) ---
        FloatingRadialMenuView(
            isExpanded = isExpanded,
            isDockedOnLeft = isDockedOnLeft,
            isDockedOnRight = isDockedOnRight,
            isRecordingActive = isRecordingActive,
            isPaused = isPaused,
            durationMs = currentDuration,
            hudConfig = if (isRecordingActive) config.recordingHudConfig else config.standbyHudConfig,
            onToggleExpand = { expanded ->
                onExpandChanged(expanded)
            },
            onCollapseComplete = onCollapseComplete,
            onDrag = onDrag,
            onDragEnd = onDragEnd,
            onRecordClick = onRecordClick,
            onPauseClick = onPauseClick,
            onResumeClick = onResumeClick,
            onStopClick = onStopClick,
            onGhostClick = { isInvisibleGhost = true },
            onReplayClick = onReplayClick,
            onScreenshotClick = onScreenshotClick,
            onVaultClick = onVaultClick,
            onSettingsClick = onSettingsClick
        )
    }
}
