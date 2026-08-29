package dev.pixl.recorder.ui.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pixl.recorder.core.model.RecorderState
import dev.pixl.recorder.core.storage.StorageCalculator
import dev.pixl.recorder.service.RecordingService
import dev.pixl.recorder.ui.theme.BorderHighlight
import dev.pixl.recorder.ui.theme.BorderStark
import dev.pixl.recorder.ui.theme.CyberYellow
import dev.pixl.recorder.ui.theme.HyperCrimson
import dev.pixl.recorder.ui.theme.HyperCyan
import dev.pixl.recorder.ui.theme.ObsidianCanvas
import dev.pixl.recorder.ui.theme.ShadowSolid
import dev.pixl.recorder.ui.theme.TextInverse
import dev.pixl.recorder.ui.theme.TextPrimary
import dev.pixl.recorder.ui.theme.ToxicLime
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Ultra-slick Dark Neo-Brutalist Floating Game Pill Overlay.
 * Supports smooth spring dragging and collapsible 6dp pulsing micro-dot HUD widget for gaming.
 */
@Composable
fun FloatingPillView(
    onDrag: (dx: Float, dy: Float) -> Unit,
    onStopClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit
) {
    var isCollapsed by remember { mutableStateOf(false) }
    val serviceState by RecordingService.serviceState.collectAsState()

    val isPaused = serviceState is RecorderState.Paused
    val stateDurationMs = when (val s = serviceState) {
        is RecorderState.Recording -> s.durationMs
        is RecorderState.Paused -> s.durationMs
        else -> 0L
    }

    // Local smooth clock to guarantee timer progression even with service flow latency
    var localTimerMs by remember { mutableLongStateOf(0L) }
    var recordingStartTimestamp by remember { mutableLongStateOf(0L) }

    LaunchedEffect(serviceState) {
        if (serviceState is RecorderState.Recording && recordingStartTimestamp == 0L) {
            recordingStartTimestamp = System.currentTimeMillis() - stateDurationMs
        }
    }

    LaunchedEffect(isPaused) {
        while (isActive) {
            if (!isPaused && recordingStartTimestamp > 0L) {
                localTimerMs = (System.currentTimeMillis() - recordingStartTimestamp).coerceAtLeast(stateDurationMs)
            } else if (stateDurationMs > 0L) {
                localTimerMs = stateDurationMs
            }
            delay(100)
        }
    }

    // Pulse animations
    val pulseTransition = rememberInfiniteTransition(label = "RadarPulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "RadarPulseAlpha"
    )
    val radarScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RadarScale"
    )

    val currentDuration = if (localTimerMs > 0L) localTimerMs else stateDurationMs
    val accentColor = if (isPaused) CyberYellow else HyperCrimson

    if (isCollapsed) {
        // --- 1. COLLAPSED MICRO-DOT HUD MODE (Ultra-compact glowing dot for gaming) ---
        Box(
            modifier = Modifier
                .size(44.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                }
                .clickable { isCollapsed = false },
            contentAlignment = Alignment.Center
        ) {
            // Radar pulse ring
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .scale(if (isPaused) 1f else radarScale)
                    .alpha(if (isPaused) 0f else (1.8f - radarScale).coerceIn(0f, 0.6f))
                    .background(accentColor, CircleShape)
            )

            // Outer Core
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(ObsidianCanvas, CircleShape)
                    .border(2.dp, accentColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Inner Glowing Dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .alpha(if (isPaused) 1f else pulseAlpha)
                        .background(accentColor, CircleShape)
                )
            }
        }
    } else {
        // --- 2. EXPANDED CYBERPUNK NEO-BRUTALIST PILL MODE ---
        Box(
            modifier = Modifier
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                }
        ) {
            // Hard Drop Shadow
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = 3.dp, y = 3.dp)
                    .background(ShadowSolid, RoundedCornerShape(24.dp))
                    .border(1.5.dp, BorderStark, RoundedCornerShape(24.dp))
            )

            // Front Main Pill Surface
            Row(
                modifier = Modifier
                    .background(ObsidianCanvas, RoundedCornerShape(24.dp))
                    .border(2.dp, accentColor, RoundedCornerShape(24.dp))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Live Pulsing Record Badge
                Box(
                    modifier = Modifier
                        .background(
                            if (isPaused) CyberYellow.copy(alpha = 0.2f) else HyperCrimson.copy(alpha = 0.2f),
                            RoundedCornerShape(6.dp)
                        )
                        .border(1.dp, accentColor, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .alpha(if (isPaused) 1f else pulseAlpha)
                                .background(accentColor, CircleShape)
                        )
                        Text(
                            text = if (isPaused) "PAUSED" else "REC",
                            color = accentColor,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                // Digital Monospace Timer Counter
                Text(
                    text = StorageCalculator.formatDuration(currentDuration),
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )

                // 1. Pause / Resume Button
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(if (isPaused) ToxicLime else HyperCyan, CircleShape)
                        .border(1.5.dp, BorderHighlight, CircleShape)
                        .clickable { if (isPaused) onResumeClick() else onPauseClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = "Pause/Resume",
                        tint = TextInverse,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // 2. Stop Recording Button
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(HyperCrimson, CircleShape)
                        .border(1.5.dp, BorderHighlight, CircleShape)
                        .clickable { onStopClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop",
                        tint = TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // 3. Minimize to Micro-Dot Button
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color(0xFF20202E), CircleShape)
                        .border(1.5.dp, BorderStark, CircleShape)
                        .clickable { isCollapsed = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloseFullscreen,
                        contentDescription = "Minimize",
                        tint = dev.pixl.recorder.ui.theme.TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
