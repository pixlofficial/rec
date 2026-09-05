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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pixl.rec.ui.theme.BitcountPropSingle
import pixl.rec.ui.theme.BorderStark
import pixl.rec.ui.theme.HyperCrimson
import pixl.rec.ui.theme.SurfaceElevated
import pixl.rec.ui.theme.TextSecondary

private val GlowingCrimson = Color(0xFFFF3864)

/**
 * Premium, non-wrapping segmented sliding selector component with spring physics,
 * chiseled rectangular highlight with smooth corners, translucent crimson fill,
 * laser stroke with faint ambient glow, and haptic feedback.
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
    activeTextColor: Color = GlowingCrimson,
    inactiveTextColor: Color = TextSecondary,
    height: Dp = 40.dp,
    itemIcon: ((T) -> Int?)? = null,
    itemLabel: (T) -> String = { it.toString() }
) {
    if (items.isEmpty()) return

    val haptic = LocalHapticFeedback.current
    val selectedIndex = items.indexOf(selectedItem).coerceAtLeast(0)
    val containerRadius = 8.dp
    val highlightRadius = 6.dp

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(SurfaceElevated, RoundedCornerShape(containerRadius))
            .border(1.dp, BorderStark, RoundedCornerShape(containerRadius))
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

        // Sliding Highlight Rectangle with smooth corners, translucent fill, red border & faint ambient glow
        Box(
            modifier = Modifier
                .offset(x = animatedOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .drawBehind {
                    if (enabled) {
                        // Faint outer ambient glow
                        drawRoundRect(
                            color = activeColor.copy(alpha = 0.16f),
                            topLeft = Offset(-2.dp.toPx(), -2.dp.toPx()),
                            size = Size(
                                width = size.width + 4.dp.toPx(),
                                height = size.height + 4.dp.toPx()
                            ),
                            cornerRadius = CornerRadius((highlightRadius + 2.dp).toPx())
                        )
                    }
                }
                .background(
                    color = if (enabled) activeColor.copy(alpha = 0.18f) else activeColor.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(highlightRadius)
                )
                .border(
                    width = 1.dp,
                    color = if (enabled) activeColor else activeColor.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(highlightRadius)
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
                        .clip(RoundedCornerShape(highlightRadius))
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
                    val iconRes = itemIcon?.invoke(item)
                    if (iconRes != null) {
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = itemLabel(item),
                            tint = if (isSelected) activeTextColor else inactiveTextColor,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        val textShadow = if (isSelected && enabled) {
                            Shadow(
                                color = activeColor.copy(alpha = 0.85f),
                                blurRadius = 10f
                            )
                        } else {
                            Shadow.None
                        }

                        Text(
                            text = itemLabel(item),
                            color = if (isSelected) activeTextColor else inactiveTextColor,
                            fontFamily = BitcountPropSingle,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = if (itemCount > 4) 11.sp else 12.sp,
                            style = TextStyle(
                                shadow = textShadow
                            ),
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
