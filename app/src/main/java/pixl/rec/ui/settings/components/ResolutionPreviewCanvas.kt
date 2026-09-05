package pixl.rec.ui.settings.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pixl.rec.core.engine.ResolutionCalculator
import pixl.rec.ui.theme.BitcountPropSingle
import pixl.rec.ui.theme.BorderStark
import pixl.rec.ui.theme.HyperCrimson
import pixl.rec.ui.theme.ObsidianCanvas
import pixl.rec.ui.theme.SurfaceElevated
import pixl.rec.ui.theme.TextPrimary
import pixl.rec.ui.theme.TextSecondary

/**
 * WYSIWYG Visual Preview Canvas showing the centered proportional screen rectangle
 * of the currently selected resolution.
 */
@Composable
fun ResolutionPreviewCanvas(
    width: Int,
    height: Int,
    modifier: Modifier = Modifier
) {
    val isLandscape = width > height
    val isSquare = width == height

    // Proportional dimensions for the miniature preview frame
    val targetBoxWidth = when {
        isSquare -> 76.dp
        isLandscape -> 132.dp
        else -> {
            val ratio = width.toFloat() / height.toFloat().coerceAtLeast(1f)
            (92f * ratio).coerceIn(48f, 65f).dp
        }
    }

    val targetBoxHeight = when {
        isSquare -> 76.dp
        isLandscape -> {
            val ratio = height.toFloat() / width.toFloat().coerceAtLeast(1f)
            (132f * ratio).coerceIn(48f, 72f).dp
        }
        else -> 92.dp
    }

    val animWidth by animateDpAsState(
        targetValue = targetBoxWidth,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "animWidth"
    )

    val animHeight by animateDpAsState(
        targetValue = targetBoxHeight,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "animHeight"
    )

    val ratioLabel = ResolutionCalculator.getAspectRatioLabel(width, height)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(ObsidianCanvas, RoundedCornerShape(10.dp))
            .border(1.dp, BorderStark, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        // Centered Proportional Preview Rectangle
        Box(
            modifier = Modifier
                .size(width = animWidth, height = animHeight)
                .background(SurfaceElevated, RoundedCornerShape(6.dp))
                .border(1.5.dp, HyperCrimson, RoundedCornerShape(6.dp))
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (isLandscape || isSquare) {
                    Text(
                        text = "${width}x${height}",
                        fontFamily = BitcountPropSingle,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = ratioLabel,
                        fontFamily = BitcountPropSingle,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Normal,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                } else {
                    Text(
                        text = "$width",
                        fontFamily = BitcountPropSingle,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = "x",
                        fontFamily = BitcountPropSingle,
                        fontSize = 7.sp,
                        color = TextSecondary,
                        maxLines = 1
                    )
                    Text(
                        text = "$height",
                        fontFamily = BitcountPropSingle,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = ratioLabel.substringBefore(" •"),
                        fontFamily = BitcountPropSingle,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Normal,
                        color = HyperCrimson,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
