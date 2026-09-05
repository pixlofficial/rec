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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pixl.rec.core.model.QuickPreset
import pixl.rec.ui.theme.BitcountPropSingle
import pixl.rec.ui.theme.BorderStark
import pixl.rec.ui.theme.HyperCrimson
import pixl.rec.ui.theme.SurfaceElevated
import pixl.rec.ui.theme.TextInverse
import pixl.rec.ui.theme.TextMuted
import pixl.rec.ui.theme.TextPrimary
import pixl.rec.ui.theme.TextSecondary

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
        Triple(QuickPreset.BEST_QUALITY, "💎 BEST QUALITY", "NATIVE • MAX CLARITY"),
        Triple(QuickPreset.GAMING, "🎮 GAMING 60 FPS", "SMOOTH • LANDSCAPE"),
        Triple(QuickPreset.MAX_FPS, "🚀 MAX FPS", "PANEL MAX REFRESH"),
        Triple(QuickPreset.SMALL_SIZE, "💾 SMALL SIZE", "COMPACT • LOW MB")
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
                isSelected = activePreset == presets[0].first,
                enabled = enabled,
                onClick = { onPresetSelect(presets[0].first) },
                modifier = Modifier.weight(1f)
            )
            PresetCard(
                item = presets[1],
                isSelected = activePreset == presets[1].first,
                enabled = enabled,
                onClick = { onPresetSelect(presets[1].first) },
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
                isSelected = activePreset == presets[2].first,
                enabled = enabled,
                onClick = { onPresetSelect(presets[2].first) },
                modifier = Modifier.weight(1f)
            )
            PresetCard(
                item = presets[3],
                isSelected = activePreset == presets[3].first,
                enabled = enabled,
                onClick = { onPresetSelect(presets[3].first) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PresetCard(
    item: Triple<QuickPreset, String, String>,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (preset, title, subtitle) = item
    val bg = if (isSelected) TextPrimary else SurfaceElevated
    val border = if (isSelected) HyperCrimson else BorderStark
    val titleColor = if (isSelected) TextInverse else TextPrimary
    val subtitleColor = if (isSelected) TextInverse.copy(alpha = 0.7f) else TextSecondary

    Column(
        modifier = modifier
            .background(bg, RoundedCornerShape(8.dp))
            .border(1.5.dp, border, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            fontFamily = BitcountPropSingle,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = titleColor,
            maxLines = 1,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = subtitle,
            fontFamily = BitcountPropSingle,
            fontSize = 8.sp,
            fontWeight = FontWeight.Normal,
            color = subtitleColor,
            maxLines = 1
        )
    }
}
