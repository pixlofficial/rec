package pixl.rec.ui.overlay

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import pixl.rec.R
import pixl.rec.ui.theme.BorderStark
import pixl.rec.ui.theme.CyberYellow
import pixl.rec.ui.theme.HyperCrimson
import pixl.rec.ui.theme.HyperCyan
import pixl.rec.ui.theme.SurfaceElevated
import pixl.rec.ui.theme.TextPrimary
import pixl.rec.ui.theme.ToxicLime
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 5-Faceted Cyberpunk Angular Polygon Canopy Shape.
 * Provides 5 distinct faceted chambers with sharp outer chamfers.
 */
class FiveFacetPolygonCanopyShape(private val isDockedOnLeft: Boolean) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path().apply {
            val w = size.width
            val h = size.height
            val hubX = if (isDockedOnLeft) with(density) { 22.dp.toPx() } else (w - with(density) { 22.dp.toPx() })
            val hubY = h / 2f

            val cornerAngles = if (isDockedOnLeft) {
                listOf(-65f, -39f, -13f, 13f, 39f, 65f)
            } else {
                listOf(245f, 219f, 193f, 167f, 141f, 115f)
            }

            val cornerDistances = listOf(
                with(density) { 108.dp.toPx() },
                with(density) { 128.dp.toPx() },
                with(density) { 136.dp.toPx() },
                with(density) { 136.dp.toPx() },
                with(density) { 128.dp.toPx() },
                with(density) { 108.dp.toPx() }
            )

            if (isDockedOnLeft) {
                moveTo(0f, hubY - with(density) { 102.dp.toPx() })
                cornerAngles.forEachIndexed { i, deg ->
                    val rad = Math.toRadians(deg.toDouble())
                    val x = (hubX + cos(rad) * cornerDistances[i]).toFloat().coerceIn(0f, w)
                    val y = (hubY + sin(rad) * cornerDistances[i]).toFloat().coerceIn(0f, h)
                    lineTo(x, y)
                }
                lineTo(0f, hubY + with(density) { 102.dp.toPx() })
                close()
            } else {
                moveTo(w, hubY - with(density) { 102.dp.toPx() })
                cornerAngles.forEachIndexed { i, deg ->
                    val rad = Math.toRadians(deg.toDouble())
                    val x = (hubX + cos(rad) * cornerDistances[i]).toFloat().coerceIn(0f, w)
                    val y = (hubY + sin(rad) * cornerDistances[i]).toFloat().coerceIn(0f, h)
                    lineTo(x, y)
                }
                lineTo(w, hubY + with(density) { 102.dp.toPx() })
                close()
            }
        }
        return Outline.Generic(path)
    }
}

/**
 * Cyberpunk 5-Chamber Faceted Polygon HUD Overlay Menu (Custom Pixel Icons, No Circular Containers).
 *
 * 5 Dedicated Action Chambers:
 * 1. 📸 Screenshot (CyberYellow) - Custom Pixel Camera Icon
 * 2. ⚡ Instant Replay (HyperCyan) - Custom Pixel Lightning Bolt Icon
 * 3. 🔴 Master Record (HyperCrimson) - Custom Pixel Record Shutter
 * 4. 🗃 Vault Gallery (ToxicLime) - Custom Pixel Folder Icon
 * 5. ⚙ Settings (TextPrimary) - Custom Pixel Gear Icon
 */
@Composable
fun FloatingRadialMenuView(
    isExpanded: Boolean,
    isDockedOnLeft: Boolean = true,
    onToggleExpand: (Boolean) -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
    onRecordClick: () -> Unit,
    onReplayClick: () -> Unit = {},
    onScreenshotClick: () -> Unit,
    onVaultClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    // Synchronized expansion progression: 0f (collapsed) to 1f (expanded)
    val expansionProgress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "FanExpansion"
    )

    // Subtle pulse animation for standby icon (active only when collapsed)
    val pulseScale = if (!isExpanded) {
        val pulseTransition = rememberInfiniteTransition(label = "StandbyPulse")
        val scale by pulseTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "PulseScale"
        )
        scale
    } else {
        1f
    }

    val canopyShape = remember(isDockedOnLeft) { FiveFacetPolygonCanopyShape(isDockedOnLeft) }

    // Generous standby touch hit box: 68dp wide x 72dp high
    val currentWidth = if (isExpanded || expansionProgress > 0.01f) 140.dp else 68.dp
    val currentHeight = if (isExpanded || expansionProgress > 0.01f) 240.dp else 72.dp

    Box(
        modifier = modifier
            .size(width = currentWidth, height = currentHeight)
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
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (!isExpanded) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggleExpand(true)
                    }
                }
            ),
        contentAlignment = if (isDockedOnLeft) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        // 5-Faceted Angular Polygon Frosted Glass Canopy
        if (expansionProgress > 0.01f) {
            Box(
                modifier = Modifier
                    .size(width = 140.dp, height = 240.dp)
                    .scale(expansionProgress)
                    .alpha(expansionProgress)
                    .clip(canopyShape)
                    .background(SurfaceElevated.copy(alpha = 0.92f))
                    .border(width = 1.5.dp, color = BorderStark, shape = canopyShape)
            )

            // Subtle Dark Cyber Dotted Chamber Divider Lines (4 dividers separating 5 chambers)
            Canvas(
                modifier = Modifier
                    .size(width = 140.dp, height = 240.dp)
                    .alpha(expansionProgress)
            ) {
                val w = size.width
                val h = size.height
                val hubCenterX = if (isDockedOnLeft) 22.dp.toPx() else (w - 22.dp.toPx())
                val hubCenterY = h / 2f

                val dividerAngles = if (isDockedOnLeft) {
                    listOf(-39f, -13f, 13f, 39f)
                } else {
                    listOf(219f, 193f, 167f, 141f)
                }

                val maxRayDistances = listOf(124.dp.toPx(), 132.dp.toPx(), 132.dp.toPx(), 124.dp.toPx())
                val dotSpacing = 5.5.dp.toPx()
                val dotRadius = 1.25.dp.toPx()
                val dotColor = BorderStark.copy(alpha = 0.55f * expansionProgress)

                dividerAngles.forEachIndexed { index, angleDeg ->
                    val rad = Math.toRadians(angleDeg.toDouble())
                    val cosA = cos(rad).toFloat()
                    val sinA = sin(rad).toFloat()
                    val maxDist = maxRayDistances[index] * expansionProgress
                    var currentDist = 24.dp.toPx()

                    while (currentDist <= maxDist) {
                        val px = hubCenterX + cosA * currentDist
                        val py = hubCenterY + sinA * currentDist
                        drawCircle(
                            color = dotColor,
                            radius = dotRadius,
                            center = Offset(px, py)
                        )
                        currentDist += dotSpacing
                    }
                }
            }

            // 5 Action Nodes (Containerless, Pure Custom Pixel Icons):
            // Node 1: 📸 Screenshot (CyberYellow) - Custom Pixel Camera
            FanNodeItem(
                iconResId = R.drawable.ic_pixel_camera,
                tint = CyberYellow,
                angleDeg = if (isDockedOnLeft) -52f else 232f,
                orbitRadius = with(density) { 78.dp.toPx() },
                expansionProgress = expansionProgress,
                isDockedOnLeft = isDockedOnLeft,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleExpand(false)
                    onScreenshotClick()
                }
            )

            // Node 2: ⚡ Instant Replay (HyperCyan) - Custom Pixel Lightning Bolt
            FanNodeItem(
                iconResId = R.drawable.ic_pixel_lightning,
                tint = HyperCyan,
                angleDeg = if (isDockedOnLeft) -26f else 206f,
                orbitRadius = with(density) { 82.dp.toPx() },
                expansionProgress = expansionProgress,
                isDockedOnLeft = isDockedOnLeft,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleExpand(false)
                    onReplayClick()
                }
            )

            // Node 3: 🔴 Start Record (HyperCrimson) - Custom Pixel Record Shutter (Center Apex)
            FanNodeItem(
                iconResId = R.drawable.ic_pixel_record,
                tint = HyperCrimson,
                angleDeg = if (isDockedOnLeft) 0f else 180f,
                orbitRadius = with(density) { 86.dp.toPx() },
                expansionProgress = expansionProgress,
                isDockedOnLeft = isDockedOnLeft,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleExpand(false)
                    onRecordClick()
                }
            )

            // Node 4: 🗃 Vault Gallery (ToxicLime) - Custom Pixel Folder
            FanNodeItem(
                iconResId = R.drawable.ic_pixel_vault,
                tint = ToxicLime,
                angleDeg = if (isDockedOnLeft) 26f else 154f,
                orbitRadius = with(density) { 82.dp.toPx() },
                expansionProgress = expansionProgress,
                isDockedOnLeft = isDockedOnLeft,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleExpand(false)
                    onVaultClick()
                }
            )

            // Node 5: ⚙ Settings (TextPrimary) - Custom Pixel Gear
            FanNodeItem(
                iconResId = R.drawable.ic_pixel_settings,
                tint = TextPrimary,
                angleDeg = if (isDockedOnLeft) 52f else 128f,
                orbitRadius = with(density) { 78.dp.toPx() },
                expansionProgress = expansionProgress,
                isDockedOnLeft = isDockedOnLeft,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleExpand(false)
                    onSettingsClick()
                }
            )
        }

        // Center Action Hub Button / Standby Icon with smooth crossfade
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
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .scale(1.5f),
                contentAlignment = Alignment.Center
            ) {
                // Standby Record Dot-Matrix Icon
                Icon(
                    painter = painterResource(id = R.drawable.ic_pixel_record),
                    contentDescription = "PixL Floating Menu",
                    tint = HyperCrimson,
                    modifier = Modifier
                        .matchParentSize()
                        .alpha((1f - expansionProgress).coerceIn(0f, 1f))
                )
                // Expanded Hexagonal Close Icon
                Icon(
                    painter = painterResource(id = R.drawable.ic_pixel_close),
                    contentDescription = "Close Menu",
                    tint = HyperCrimson,
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(expansionProgress.coerceIn(0f, 1f))
                )
            }
        }
    }
}

/**
 * Containerless Pure Pixel Icon Action Node.
 */
@Composable
private fun FanNodeItem(
    iconResId: Int,
    tint: Color,
    angleDeg: Float,
    orbitRadius: Float,
    expansionProgress: Float,
    isDockedOnLeft: Boolean,
    onClick: () -> Unit
) {
    val angleRad = Math.toRadians(angleDeg.toDouble())
    val offsetX = (orbitRadius * cos(angleRad) * expansionProgress).roundToInt()
    val offsetY = (orbitRadius * sin(angleRad) * expansionProgress).roundToInt()

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX, offsetY) }
            .size(40.dp)
            .scale(expansionProgress)
            .alpha(expansionProgress)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 22.dp, color = tint),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconResId),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(30.dp)
        )
    }
}
