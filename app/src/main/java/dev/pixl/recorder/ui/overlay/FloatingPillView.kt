package dev.pixl.recorder.ui.overlay

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import dev.pixl.recorder.ui.theme.BrutalistSurface
import dev.pixl.recorder.ui.theme.CyberYellow
import dev.pixl.recorder.ui.theme.HyperCrimson
import dev.pixl.recorder.ui.theme.ShadowSolid
import dev.pixl.recorder.ui.theme.TextPrimary

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
    val durationMs = when (val s = serviceState) {
        is RecorderState.Recording -> s.durationMs
        is RecorderState.Paused -> s.durationMs
        else -> 0L
    }

    val pulseTransition = rememberInfiniteTransition(label = "PillPulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PillPulseAlpha"
    )

    if (isCollapsed) {
        // Collapsed Micro-Dot Mode (6dp glowing dot hugging screen edge at 20% opacity)
        Box(
            modifier = Modifier
                .size(24.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                }
                .clickable { isCollapsed = false }
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .alpha(if (isPaused) 0.6f else 0.3f)
                    .background(if (isPaused) CyberYellow else HyperCrimson, CircleShape)
                    .border(1.dp, BorderHighlight, CircleShape)
            )
        }
    } else {
        // Expanded Brutalist Game Pill
        Box(
            modifier = Modifier
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                }
        ) {
            // Shadow
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(ShadowSolid, RoundedCornerShape(20.dp))
                    .border(1.5.dp, BorderStark, RoundedCornerShape(20.dp))
            )

            // Front Surface
            Row(
                modifier = Modifier
                    .background(BrutalistSurface, RoundedCornerShape(20.dp))
                    .border(2.dp, if (isPaused) CyberYellow else HyperCrimson, RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Record Dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .alpha(if (isPaused) 1f else pulseAlpha)
                        .background(if (isPaused) CyberYellow else HyperCrimson, CircleShape)
                )

                // Timer
                Text(
                    text = StorageCalculator.formatDuration(durationMs),
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black
                )

                // Pause / Resume Button
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(Color(0xFF222232), CircleShape)
                        .border(1.dp, BorderStark, CircleShape)
                        .clickable { if (isPaused) onResumeClick() else onPauseClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = "Pause/Resume",
                        tint = TextPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Stop Button
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(HyperCrimson, CircleShape)
                        .border(1.dp, BorderHighlight, CircleShape)
                        .clickable { onStopClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop",
                        tint = TextPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Minimize / Collapse Button
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(Color(0xFF222232), CircleShape)
                        .border(1.dp, BorderStark, CircleShape)
                        .clickable { isCollapsed = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloseFullscreen,
                        contentDescription = "Collapse",
                        tint = dev.pixl.recorder.ui.theme.TextSecondary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}
