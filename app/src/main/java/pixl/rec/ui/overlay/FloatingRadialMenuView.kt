package pixl.rec.ui.overlay

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import pixl.rec.R
import pixl.rec.ui.theme.BorderHighlight
import pixl.rec.ui.theme.BorderStark
import pixl.rec.ui.theme.CyberYellow
import pixl.rec.ui.theme.HyperCrimson
import pixl.rec.ui.theme.SurfaceElevated
import pixl.rec.ui.theme.SurfaceRaised
import pixl.rec.ui.theme.TextPrimary
import pixl.rec.ui.theme.TextSecondary
import pixl.rec.ui.theme.ToxicLime
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Cyberpunk Radial Dial HUD Overlay Menu (Icon-Only).
 *
 * Standby State: Compact borderless dot-matrix icon docked to the screen edge (2 rows visible).
 * Expanded State: 220dp radial dial HUD with 4 glowing orbital action nodes.
 */
@Composable
fun FloatingRadialMenuView(
    isExpanded: Boolean,
    onToggleExpand: (Boolean) -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
    onRecordClick: () -> Unit,
    onScreenshotClick: () -> Unit,
    onVaultClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    // Spring expansion progression: 0f (collapsed) to 1f (expanded)
    val expansionProgress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "RadialExpansion"
    )

    // Smooth rotation angle for dial bloom
    val dialRotation by animateFloatAsState(
        targetValue = if (isExpanded) 0f else -45f,
        animationSpec = tween(350, easing = FastOutSlowInEasing),
        label = "DialRotation"
    )

    // Subtle pulse animation for standby icon
    val pulseTransition = rememberInfiniteTransition(label = "StandbyPulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    val orbitRadiusPx = with(density) { 72.dp.toPx() }

    Box(
        modifier = modifier
            .size(if (isExpanded) 220.dp else 44.dp)
            .pointerInput(isExpanded) {
                if (!isExpanded) {
                    detectDragGestures(
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragEnd,
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Expanded Radial Orbital Nodes (Icon-Only)
        if (expansionProgress > 0.01f) {
            // Orbital Ring Guide
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .scale(expansionProgress)
                    .alpha(expansionProgress * 0.4f)
                    .border(1.dp, BorderHighlight, CircleShape)
            )

            // 1. TOP Node: 📸 Screenshot (CyberYellow) - angle = -90 deg (-PI/2)
            RadialNodeItem(
                icon = Icons.Default.CameraAlt,
                tint = CyberYellow,
                angleRad = -Math.PI / 2,
                orbitRadius = orbitRadiusPx * expansionProgress,
                rotationAngle = dialRotation,
                alpha = expansionProgress,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleExpand(false)
                    onScreenshotClick()
                }
            )

            // 2. RIGHT Node: 🔴 Start Record (HyperCrimson) - angle = 0 deg
            RadialNodeItem(
                icon = Icons.Default.FiberManualRecord,
                tint = HyperCrimson,
                angleRad = 0.0,
                orbitRadius = orbitRadiusPx * expansionProgress,
                rotationAngle = dialRotation,
                alpha = expansionProgress,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleExpand(false)
                    onRecordClick()
                }
            )

            // 3. BOTTOM Node: 🗃 Vault Gallery (ToxicLime) - angle = 90 deg (PI/2)
            RadialNodeItem(
                icon = Icons.Default.FolderOpen,
                tint = ToxicLime,
                angleRad = Math.PI / 2,
                orbitRadius = orbitRadiusPx * expansionProgress,
                rotationAngle = dialRotation,
                alpha = expansionProgress,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleExpand(false)
                    onVaultClick()
                }
            )

            // 4. LEFT Node: ⚙ Settings (TextPrimary) - angle = 180 deg (PI)
            RadialNodeItem(
                icon = Icons.Default.Settings,
                tint = TextPrimary,
                angleRad = Math.PI,
                orbitRadius = orbitRadiusPx * expansionProgress,
                rotationAngle = dialRotation,
                alpha = expansionProgress,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleExpand(false)
                    onSettingsClick()
                }
            )
        }

        // Center Action Bubble / Standby Icon
        Box(
            modifier = Modifier
                .size(44.dp)
                .scale(if (!isExpanded) pulseScale else 1f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleExpand(!isExpanded)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(
                    id = if (isExpanded) R.drawable.ic_pixel_close else R.drawable.ic_pixel_record
                ),
                contentDescription = if (isExpanded) "Close Menu" else "PixL Floating Menu",
                tint = HyperCrimson,
                modifier = Modifier
                    .size(44.dp)
                    .scale(1.5f)
            )
        }
    }
}

/**
 * Individual Icon-Only Orbital Action Node.
 */
@Composable
private fun RadialNodeItem(
    icon: ImageVector,
    tint: Color,
    angleRad: Double,
    orbitRadius: Float,
    rotationAngle: Float,
    alpha: Float,
    onClick: () -> Unit
) {
    val offsetX = (orbitRadius * cos(angleRad)).roundToInt()
    val offsetY = (orbitRadius * sin(angleRad)).roundToInt()

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX, offsetY) }
            .size(42.dp)
            .alpha(alpha.coerceIn(0f, 1f))
            .scale(alpha.coerceIn(0.5f, 1f))
            .rotate(rotationAngle)
            .clip(CircleShape)
            .background(SurfaceRaised)
            .border(1.dp, BorderStark, CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true, radius = 21.dp),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}
