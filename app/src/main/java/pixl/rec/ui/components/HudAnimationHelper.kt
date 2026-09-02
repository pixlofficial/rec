package pixl.rec.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import pixl.rec.core.model.HudAnimation

/**
 * Animated scale and alpha states for HUD icon presets.
 */
data class HudIconAnimationState(
    val scale: Float,
    val alpha: Float
)

/**
 * Computes live scale and alpha values for the selected [HudAnimation] preset.
 */
@Composable
fun rememberHudIconAnimation(animation: HudAnimation, baseOpacity: Float): HudIconAnimationState {
    if (animation == HudAnimation.NONE) {
        return HudIconAnimationState(scale = 1.0f, alpha = baseOpacity)
    }

    val transition = rememberInfiniteTransition(label = "HudIconAnimTransition")

    return when (animation) {
        HudAnimation.NONE -> HudIconAnimationState(scale = 1.0f, alpha = baseOpacity)

        HudAnimation.BREATHE -> {
            val animAlpha by transition.animateFloat(
                initialValue = 0.35f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1400, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "BreatheAlpha"
            )
            HudIconAnimationState(scale = 1.0f, alpha = (animAlpha * baseOpacity).coerceIn(0f, 1f))
        }

        HudAnimation.PULSE -> {
            val animScale by transition.animateFloat(
                initialValue = 1.0f,
                targetValue = 1.12f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "PulseScale"
            )
            val animAlpha by transition.animateFloat(
                initialValue = 0.55f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "PulseAlpha"
            )
            HudIconAnimationState(scale = animScale, alpha = (animAlpha * baseOpacity).coerceIn(0f, 1f))
        }

        HudAnimation.HEARTBEAT -> {
            val animScale by transition.animateFloat(
                initialValue = 1.0f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1400
                        1.0f at 0
                        1.18f at 160 using FastOutSlowInEasing
                        1.02f at 300 using FastOutSlowInEasing
                        1.22f at 460 using FastOutSlowInEasing
                        1.0f at 700 using FastOutSlowInEasing
                        1.0f at 1400
                    },
                    repeatMode = RepeatMode.Restart
                ),
                label = "HeartbeatScale"
            )
            HudIconAnimationState(scale = animScale, alpha = baseOpacity)
        }

        HudAnimation.BLINK -> {
            val animAlpha by transition.animateFloat(
                initialValue = 1.0f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 900
                        1.0f at 0
                        1.0f at 500 using LinearEasing
                        0.15f at 501 using LinearEasing
                        0.15f at 900
                    },
                    repeatMode = RepeatMode.Restart
                ),
                label = "BlinkAlpha"
            )
            HudIconAnimationState(scale = 1.0f, alpha = (animAlpha * baseOpacity).coerceIn(0f, 1f))
        }
    }
}
