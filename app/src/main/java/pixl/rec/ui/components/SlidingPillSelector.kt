package pixl.rec.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pixl.rec.ui.theme.BitcountPropSingle
import pixl.rec.ui.theme.BorderStark
import pixl.rec.ui.theme.HyperCrimson
import pixl.rec.ui.theme.SurfaceElevated
import pixl.rec.ui.theme.TextPrimary
import pixl.rec.ui.theme.TextSecondary

/**
 * Premium, non-wrapping segmented sliding pill component with spring physics,
 * glowing highlight, and haptic feedback.
 *
 * Guaranteed single-line layout where all options are evenly weighted.
 */
@Composable
fun <T> SlidingPillSelector(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    activeColor: Color = HyperCrimson,
    activeTextColor: Color = TextPrimary,
    inactiveTextColor: Color = TextSecondary,
    height: Dp = 40.dp,
    itemLabel: (T) -> String = { it.toString() }
) {
    if (items.isEmpty()) return

    val haptic = LocalHapticFeedback.current
    val selectedIndex = items.indexOf(selectedItem).coerceAtLeast(0)
    val cornerRadius = height / 2

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(SurfaceElevated, RoundedCornerShape(cornerRadius))
            .border(1.dp, BorderStark, RoundedCornerShape(cornerRadius))
            .padding(3.dp)
    ) {
        val totalWidth = maxWidth
        val itemCount = items.size.coerceAtLeast(1)
        val segmentWidth = totalWidth / itemCount
        val targetOffset = segmentWidth * selectedIndex

        val animatedOffset by animateDpAsState(
            targetValue = targetOffset,
            animationSpec = spring(
                dampingRatio = 0.78f,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "SlidingPillHighlightOffset"
        )

        // Sliding Highlight Pill
        Box(
            modifier = Modifier
                .offset(x = animatedOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .background(
                    color = if (enabled) activeColor else activeColor.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(cornerRadius - 3.dp)
                )
        )

        // Clickable Segment Items (Single Line)
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex
                val interactionSource = remember { MutableInteractionSource() }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(cornerRadius - 3.dp))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            enabled = enabled
                        ) {
                            if (item != selectedItem) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onItemSelected(item)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = itemLabel(item),
                        color = if (isSelected) activeTextColor else inactiveTextColor,
                        fontFamily = BitcountPropSingle,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = if (itemCount > 4) 11.sp else 12.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
