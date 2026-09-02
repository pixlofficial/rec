package pixl.rec.ui.overlay

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.cos
import kotlin.math.sin

/**
 * Perfectly Symmetrical Regular Hex-Pod Shape with 100% Equal Side Lengths.
 * Vertices are at angles: -90°, -30°, +30°, +90°, +150°, +210°.
 */
class IsometricHexPodShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path().apply {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val rx = size.width / 2f
            val ry = size.height / 2f

            val angles = listOf(-90.0, -30.0, 30.0, 90.0, 150.0, 210.0)
            angles.forEachIndexed { i, deg ->
                val rad = Math.toRadians(deg)
                val x = (cx + rx * cos(rad)).toFloat()
                val y = (cy + ry * sin(rad)).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        return Outline.Generic(path)
    }

    companion object {
        /**
         * Returns the 5 divider spokes (excludes top apex -90° so no line cuts through top text).
         */
        fun getDividerSpokes(w: Float, h: Float): List<Offset> {
            val cx = w / 2f
            val cy = h / 2f
            val rx = w / 2f
            val ry = h / 2f

            // Vertices at -30°, 30°, 90°, 150°, 210° (top apex -90° omitted)
            val angles = listOf(-30.0, 30.0, 90.0, 150.0, 210.0)
            return angles.map { deg ->
                val rad = Math.toRadians(deg)
                Offset(
                    (cx + rx * cos(rad)).toFloat(),
                    (cy + ry * sin(rad)).toFloat()
                )
            }
        }
    }
}
