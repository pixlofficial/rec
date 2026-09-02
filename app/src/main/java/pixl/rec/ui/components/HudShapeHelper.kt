package pixl.rec.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pixl.rec.core.model.HudShape
import pixl.rec.core.model.HudStyleConfig
import pixl.rec.core.model.StrokeStyle

object HudShapeHelper {

    /**
     * Equal-sided 8-sided regular polygon (Octagon).
     * Chamfer ratio = 1 - 1/(sqrt(2) + 1) ≈ 0.2929.
     */
    val OctagonShape: Shape = GenericShape { size, _ ->
        val w = size.width
        val h = size.height
        val cX = w * 0.2929f
        val cY = h * 0.2929f

        moveTo(cX, 0f)
        lineTo(w - cX, 0f)
        lineTo(w, cY)
        lineTo(w, h - cY)
        lineTo(w - cX, h)
        lineTo(cX, h)
        lineTo(0f, h - cY)
        lineTo(0f, cY)
        close()
    }

    /**
     * Symmetrical 6-sided techno node (Hexagon).
     * Top apex at -90° (w/2, 0), bottom apex at +90° (w/2, h),
     * with vertical side edges. When docked against screen bezel,
     * exactly 3 full sides are visible outside with zero cut horizontal sides.
     */
    val HexagonShape: Shape = GenericShape { size, _ ->
        val cx = size.width / 2f
        val cy = size.height / 2f
        val rx = size.width / 2f
        val ry = size.height / 2f

        val angles = listOf(-90.0, -30.0, 30.0, 90.0, 150.0, 210.0)
        angles.forEachIndexed { i, deg ->
            val rad = Math.toRadians(deg)
            val x = (cx + rx * kotlin.math.cos(rad)).toFloat()
            val y = (cy + ry * kotlin.math.sin(rad)).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    val CircleShapeType: Shape = CircleShape

    fun getShape(shape: HudShape): Shape {
        return when (shape) {
            HudShape.OCTAGON -> OctagonShape
            HudShape.CIRCLE -> CircleShapeType
            HudShape.HEXAGON -> HexagonShape
        }
    }

    fun createShapePath(shape: HudShape, width: Float, height: Float): Path {
        val path = Path()
        when (shape) {
            HudShape.OCTAGON -> {
                val cX = width * 0.2929f
                val cY = height * 0.2929f
                path.moveTo(cX, 0f)
                path.lineTo(width - cX, 0f)
                path.lineTo(width, cY)
                path.lineTo(width, height - cY)
                path.lineTo(width - cX, height)
                path.lineTo(cX, height)
                path.lineTo(0f, height - cY)
                path.lineTo(0f, cY)
                path.close()
            }
            HudShape.HEXAGON -> {
                val cx = width / 2f
                val cy = height / 2f
                val rx = width / 2f
                val ry = height / 2f

                val angles = listOf(-90.0, -30.0, 30.0, 90.0, 150.0, 210.0)
                angles.forEachIndexed { i, deg ->
                    val rad = Math.toRadians(deg)
                    val x = (cx + rx * kotlin.math.cos(rad)).toFloat()
                    val y = (cy + ry * kotlin.math.sin(rad)).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
            }
            HudShape.CIRCLE -> {
                path.addOval(androidx.compose.ui.geometry.Rect(0f, 0f, width, height))
            }
        }
        return path
    }
}

/**
 * Reusable HUD Node Surface rendering the custom shape, fill, and stroke style.
 */
@Composable
fun HudNodeSurface(
    config: HudStyleConfig,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val nodeSize: Dp = config.sizeDp.dp
    val shape = if (config.hasBackground) HudShapeHelper.getShape(config.shape) else CircleShape
    val bgColor = Color(config.backgroundColorHex).copy(alpha = config.backgroundOpacity)
    val strokeColor = Color(config.strokeColorHex).copy(alpha = config.strokeOpacity)
    val strokeWidthDp = config.strokeWidthDp

    Box(
        modifier = modifier
            .size(nodeSize)
            .then(if (config.hasBackground) Modifier.clip(shape) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        // Canvas drawing the background fill & custom stroke with path effects
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = strokeWidthDp.dp.toPx().coerceAtLeast(1f)
            val inset = strokePx / 2f
            val w = (size.width - strokePx).coerceAtLeast(1f)
            val h = (size.height - strokePx).coerceAtLeast(1f)

            val path = if (config.hasBackground) {
                HudShapeHelper.createShapePath(config.shape, w, h)
            } else {
                Path().apply { addOval(androidx.compose.ui.geometry.Rect(0f, 0f, w, h)) }
            }
            path.translate(androidx.compose.ui.geometry.Offset(inset, inset))

            // 1. Fill Background (if enabled)
            if (config.hasBackground) {
                drawPath(
                    path = path,
                    color = bgColor
                )
            }

            // 2. Draw Stroke (Solid, True Circular Dots, Balanced Dashes) if enabled
            if (config.hasStroke && strokeWidthDp > 0f) {
                val (pathEffect, cap) = when (config.strokeStyle) {
                    StrokeStyle.SOLID -> Pair(null, StrokeCap.Butt)
                    // StrokeCap.Round with 0f dash length produces perfect circular dots
                    StrokeStyle.DOTTED -> Pair(
                        PathEffect.dashPathEffect(floatArrayOf(0f, strokePx * 2.6f), 0f),
                        StrokeCap.Round
                    )
                    // Balanced proportional techno dashes
                    StrokeStyle.DASHED -> Pair(
                        PathEffect.dashPathEffect(floatArrayOf(strokePx * 3.5f, strokePx * 2.5f), 0f),
                        StrokeCap.Square
                    )
                }

                drawPath(
                    path = path,
                    color = strokeColor,
                    style = Stroke(
                        width = strokePx,
                        cap = cap,
                        pathEffect = pathEffect
                    )
                )
            }
        }

        // Inner Content (Fixed REC Icon / Timer)
        content()
    }
}
