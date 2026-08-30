package dev.pixl.recorder.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pixl.recorder.ui.theme.BorderHighlight
import dev.pixl.recorder.ui.theme.BorderStark
import dev.pixl.recorder.ui.theme.CyberYellow
import dev.pixl.recorder.ui.theme.ShadowSolid
import dev.pixl.recorder.ui.theme.TextInverse
import dev.pixl.recorder.ui.theme.TextPrimary

enum class ActionButtonVariant {
    PRIMARY,   // White / Crisp Text
    DANGER,    // Hyper Crimson (Electric Red)
    SUCCESS,   // Toxic Lime
    WARNING,   // Cyber Yellow
    SURFACE    // Dark Slate
}

@Composable
fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ActionButtonVariant = ActionButtonVariant.PRIMARY,
    containerColor: Color? = null,
    contentColor: Color? = null,
    borderColor: Color = BorderHighlight,
    shape: Shape = RoundedCornerShape(10.dp),
    shadowOffset: Dp = 4.dp,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val currentOffset by animateDpAsState(
        targetValue = if (isPressed && enabled) shadowOffset else 0.dp,
        animationSpec = tween(durationMillis = 50),
        label = "ButtonPressOffset"
    )

    val resolvedBg = containerColor ?: when (variant) {
        ActionButtonVariant.PRIMARY -> dev.pixl.recorder.ui.theme.TextPrimary
        ActionButtonVariant.DANGER -> dev.pixl.recorder.ui.theme.HyperCrimson
        ActionButtonVariant.SUCCESS -> dev.pixl.recorder.ui.theme.ToxicLime
        ActionButtonVariant.WARNING -> CyberYellow
        ActionButtonVariant.SURFACE -> dev.pixl.recorder.ui.theme.SurfaceElevated
    }

    val resolvedContent = contentColor ?: when (variant) {
        ActionButtonVariant.PRIMARY -> TextInverse
        ActionButtonVariant.DANGER -> TextPrimary
        ActionButtonVariant.SUCCESS -> TextInverse
        ActionButtonVariant.WARNING -> TextInverse
        ActionButtonVariant.SURFACE -> TextPrimary
    }

    val actualBg = if (enabled) resolvedBg else resolvedBg.copy(alpha = 0.4f)
    val actualBorder = if (enabled) borderColor else BorderStark

    Box(
        modifier = modifier
    ) {
        // Hard Solid Shadow Layer
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = shadowOffset, y = shadowOffset)
                .background(
                    color = ShadowSolid,
                    shape = shape
                )
                .border(
                    width = 2.dp,
                    color = BorderStark,
                    shape = shape
                )
        )

        // Front Interactive Button Layer
        Box(
            modifier = Modifier
                .offset(x = currentOffset, y = currentOffset)
                .fillMaxWidth()
                .height(54.dp)
                .background(actualBg, shape)
                .border(2.dp, actualBorder, shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick
                )
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(
                    text = text.uppercase(),
                    color = resolvedContent,
                    fontSize = 17.sp,
                    fontFamily = dev.pixl.recorder.ui.theme.BitcountPropSingle,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}
