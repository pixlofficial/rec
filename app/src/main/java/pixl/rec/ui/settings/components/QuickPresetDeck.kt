package pixl.rec.ui.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pixl.rec.R
import pixl.rec.core.model.QuickPreset
import pixl.rec.ui.theme.BitcountPropSingle
import pixl.rec.ui.theme.BorderStark
import pixl.rec.ui.theme.CyberYellow
import pixl.rec.ui.theme.GlowingCrimson
import pixl.rec.ui.theme.HyperCrimson
import pixl.rec.ui.theme.HyperCyan
import pixl.rec.ui.theme.SurfaceElevated
import pixl.rec.ui.theme.TextPrimary
import pixl.rec.ui.theme.TextSecondary
import pixl.rec.ui.theme.ToxicLime

private data class PresetItem(
    val preset: QuickPreset,
    val iconRes: Int,
    val iconColor: Color,
    val iconSize: Dp,
    val title: String,
    val subtitle: String
)

/**
 * 4 Universal Quick Presets arranged in an ergonomic 2x2 grid.
 */
@Composable
fun QuickPresetDeck(
    activePreset: QuickPreset,
    onPresetSelect: (QuickPreset) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val presets = listOf(
        PresetItem(QuickPreset.BEST_QUALITY, R.drawable.ic_pixel_diamond, HyperCyan, 15.dp, "BEST QUALITY", "NATIVE • MAX CLARITY"),
        PresetItem(QuickPreset.GAMING, R.drawable.ic_pixel_gamepad, ToxicLime, 15.dp, "GAMING 60 FPS", "SMOOTH • LANDSCAPE"),
        PresetItem(QuickPreset.MAX_FPS, R.drawable.ic_pixel_rocket, HyperCrimson, 15.dp, "MAX FPS", "PANEL MAX REFRESH"),
        PresetItem(QuickPreset.SMALL_SIZE, R.drawable.ic_pixel_disk, CyberYellow, 12.dp, "SMALL SIZE", "COMPACT • LOW MB")
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Row 1
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PresetCard(
                item = presets[0],
                isSelected = activePreset == presets[0].preset,
                enabled = enabled,
                onClick = { onPresetSelect(presets[0].preset) },
                modifier = Modifier.weight(1f)
            )
            PresetCard(
                item = presets[1],
                isSelected = activePreset == presets[1].preset,
                enabled = enabled,
                onClick = { onPresetSelect(presets[1].preset) },
                modifier = Modifier.weight(1f)
            )
        }

        // Row 2
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PresetCard(
                item = presets[2],
                isSelected = activePreset == presets[2].preset,
                enabled = enabled,
                onClick = { onPresetSelect(presets[2].preset) },
                modifier = Modifier.weight(1f)
            )
            PresetCard(
                item = presets[3],
                isSelected = activePreset == presets[3].preset,
                enabled = enabled,
                onClick = { onPresetSelect(presets[3].preset) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PresetCard(
    item: PresetItem,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val cardRadius = 8.dp
    val activeColor = HyperCrimson

    val bg = if (isSelected) {
        if (enabled) activeColor.copy(alpha = 0.18f) else activeColor.copy(alpha = 0.08f)
    } else {
        SurfaceElevated
    }

    val border = if (isSelected) {
        if (enabled) activeColor else activeColor.copy(alpha = 0.4f)
    } else {
        BorderStark
    }

    val titleColor = if (isSelected) GlowingCrimson else TextPrimary
    val subtitleColor = if (isSelected) GlowingCrimson.copy(alpha = 0.75f) else TextSecondary
    val iconTint = if (isSelected) GlowingCrimson else item.iconColor

    val textShadow = if (isSelected && enabled) {
        Shadow(
            color = activeColor.copy(alpha = 0.85f),
            blurRadius = 10f
        )
    } else {
        Shadow.None
    }

    Column(
        modifier = modifier
            .drawBehind {
                if (isSelected && enabled) {
                    // Faint outer ambient glow
                    drawRoundRect(
                        color = activeColor.copy(alpha = 0.16f),
                        topLeft = Offset(-2.dp.toPx(), -2.dp.toPx()),
                        size = Size(
                            width = size.width + 4.dp.toPx(),
                            height = size.height + 4.dp.toPx()
                        ),
                        cornerRadius = CornerRadius((cardRadius + 2.dp).toPx())
                    )
                }
            }
            .background(bg, RoundedCornerShape(cardRadius))
            .border(1.dp, border, RoundedCornerShape(cardRadius))
            .clickable(enabled = enabled) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = item.iconRes),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(item.iconSize)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = item.title,
                fontFamily = BitcountPropSingle,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = titleColor,
                style = TextStyle(shadow = textShadow),
                maxLines = 1,
                letterSpacing = 0.5.sp
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = item.subtitle,
            fontFamily = BitcountPropSingle,
            fontSize = 8.sp,
            fontWeight = FontWeight.Normal,
            color = subtitleColor,
            maxLines = 1
        )
    }
}
