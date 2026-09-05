package pixl.rec.ui.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import pixl.rec.R
import pixl.rec.ui.theme.BitcountPropSingle
import pixl.rec.ui.theme.BorderStark
import pixl.rec.ui.theme.HyperCrimson
import pixl.rec.ui.theme.ObsidianCanvas
import pixl.rec.ui.theme.TextMuted
import pixl.rec.ui.theme.TextPrimary
import pixl.rec.ui.theme.TextSecondary

/**
 * Cyberpunk HUD On-Screen Countdown Overlay displaying animated geometric vector digits
 * before recording session begins.
 *
 * Includes tap-to-cancel safety and haptic feedback.
 */
@Composable
fun CountdownOverlayView(
    countdownSeconds: Int = 0,
    onCountdownComplete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (countdownSeconds <= 0) {
        LaunchedEffect(Unit) {
            onCountdownComplete()
        }
        return
    }

    var secondsRemaining by remember { mutableIntStateOf(countdownSeconds) }
    val haptic = LocalHapticFeedback.current
    val scaleAnim = remember { Animatable(1.35f) }
    val alphaAnim = remember { Animatable(0.2f) }

    // Rotating HUD Ring Animation
    val infiniteTransition = rememberInfiniteTransition(label = "HudRingRotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "HudRingAngle"
    )

    // Countdown tick loop
    LaunchedEffect(Unit) {
        for (sec in countdownSeconds downTo 0) {
            secondsRemaining = sec
            haptic.performHapticFeedback(
                if (sec == 0) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove
            )

            // Trigger scale punch & fade
            scaleAnim.snapTo(1.35f)
            alphaAnim.snapTo(0.3f)
            scaleAnim.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(350, easing = FastOutSlowInEasing)
            )
            alphaAnim.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(200)
            )

            if (sec > 0) {
                delay(650L) // Remaining time for 1 second tick
            } else {
                delay(300L) // Short celebration flash on REC
                onCountdownComplete()
            }
        }
    }

    // Full-screen dismissable backdrop
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onCancel()
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Cyberpunk HUD Circular Container
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .scale(scaleAnim.value),
                contentAlignment = Alignment.Center
            ) {
                // Outer Rotating Bracket Canvas
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = BorderStark,
                        radius = size.minDimension / 2f,
                        style = Stroke(width = 2.dp.toPx())
                    )

                    val sweepAngle = 50f
                    for (i in 0 until 4) {
                        val startAngle = rotationAngle + (i * 90f) + 20f
                        drawArc(
                            color = HyperCrimson,
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                // Inner Dark Tech Plate
                Box(
                    modifier = Modifier
                        .size(126.dp)
                        .background(ObsidianCanvas, CircleShape)
                        .border(1.5.dp, HyperCrimson.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    // Vector Digit Icon
                    val iconRes = when (secondsRemaining) {
                        5 -> R.drawable.ic_digit_5
                        4 -> R.drawable.ic_digit_4
                        3 -> R.drawable.ic_digit_3
                        2 -> R.drawable.ic_digit_2
                        1 -> R.drawable.ic_digit_1
                        0 -> R.drawable.ic_digit_rec
                        else -> null
                    }

                    if (iconRes != null) {
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = "Countdown $secondsRemaining",
                            tint = HyperCrimson,
                            modifier = Modifier.size(72.dp)
                        )
                    } else {
                        // Fallback typography for arbitrary counts (e.g. 10s)
                        Text(
                            text = "$secondsRemaining",
                            fontFamily = BitcountPropSingle,
                            fontWeight = FontWeight.Black,
                            fontSize = 46.sp,
                            color = HyperCrimson
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Subtitle Guidance
            Box(
                modifier = Modifier
                    .background(ObsidianCanvas.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                    .border(1.dp, BorderStark, RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (secondsRemaining == 0) "RECORDING START" else "HARDWARE PIPELINE ENGAGING",
                        color = if (secondsRemaining == 0) HyperCrimson else TextPrimary,
                        fontFamily = BitcountPropSingle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "TAP ANYWHERE TO CANCEL",
                        color = TextMuted,
                        fontFamily = BitcountPropSingle,
                        fontSize = 10.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}
