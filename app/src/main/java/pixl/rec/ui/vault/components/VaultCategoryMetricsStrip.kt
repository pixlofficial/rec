package pixl.rec.ui.vault.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pixl.rec.ui.theme.BitcountPropSingle
import pixl.rec.ui.theme.BorderStark
import pixl.rec.ui.theme.SurfaceElevated
import pixl.rec.ui.theme.TextMuted
import pixl.rec.ui.theme.TextPrimary
import pixl.rec.ui.theme.TextSecondary
import pixl.rec.ui.vault.VaultFilter
import pixl.rec.ui.vault.model.VaultMediaType
import pixl.rec.ui.vault.model.VaultSummary

/**
 * Interactive 2x2 Telemetry Metrics Deck for the Media Vault.
 * Provides high-level storage transparency and one-tap category filtering.
 */
@Composable
fun VaultCategoryMetricsStrip(
    summary: VaultSummary,
    selectedFilter: VaultFilter,
    onSelectFilter: (VaultFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = listOf(
        Pair(VaultMediaType.RECORDING, VaultFilter.RECORDINGS),
        Pair(VaultMediaType.STREAM, VaultFilter.STREAMS),
        Pair(VaultMediaType.REPLAY, VaultFilter.REPLAYS),
        Pair(VaultMediaType.SCREENSHOT, VaultFilter.SCREENSHOTS)
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Row 1: RECORDINGS & STREAMS
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CategoryMetricTile(
                type = categories[0].first,
                targetFilter = categories[0].second,
                count = summary.recordingCount,
                bytes = summary.recordingBytes,
                isSelected = selectedFilter == categories[0].second,
                onClick = { onSelectFilter(categories[0].second) },
                modifier = Modifier.weight(1f)
            )
            CategoryMetricTile(
                type = categories[1].first,
                targetFilter = categories[1].second,
                count = summary.streamCount,
                bytes = summary.streamBytes,
                isSelected = selectedFilter == categories[1].second,
                onClick = { onSelectFilter(categories[1].second) },
                modifier = Modifier.weight(1f)
            )
        }

        // Row 2: REPLAYS & SCREENSHOTS
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CategoryMetricTile(
                type = categories[2].first,
                targetFilter = categories[2].second,
                count = summary.replayCount,
                bytes = summary.replayBytes,
                isSelected = selectedFilter == categories[2].second,
                onClick = { onSelectFilter(categories[2].second) },
                modifier = Modifier.weight(1f)
            )
            CategoryMetricTile(
                type = categories[3].first,
                targetFilter = categories[3].second,
                count = summary.screenshotCount,
                bytes = summary.screenshotBytes,
                isSelected = selectedFilter == categories[3].second,
                onClick = { onSelectFilter(categories[3].second) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CategoryMetricTile(
    type: VaultMediaType,
    targetFilter: VaultFilter,
    count: Int,
    bytes: Long,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = type.accentColor
    val animatedBg by animateColorAsState(
        targetValue = if (isSelected) accent.copy(alpha = 0.16f) else SurfaceElevated,
        animationSpec = tween(200),
        label = "tileBg"
    )
    val animatedBorder by animateColorAsState(
        targetValue = if (isSelected) accent else BorderStark,
        animationSpec = tween(200),
        label = "tileBorder"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(animatedBg)
            .border(1.dp, animatedBorder, RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = type.badgeIconText,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.padding(start = 5.dp))
                    Text(
                        text = type.filterLabel,
                        color = if (isSelected) accent else TextPrimary,
                        fontSize = 11.sp,
                        fontFamily = BitcountPropSingle,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                // Count Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) accent.copy(alpha = 0.25f) else BorderStark)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "$count",
                        color = if (isSelected) accent else TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = BitcountPropSingle,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (count > 0) pixl.rec.core.storage.StorageCalculator.formatBytes(bytes) else "EMPTY",
                color = if (count > 0) TextSecondary else TextMuted,
                fontSize = 10.5.sp,
                fontFamily = BitcountPropSingle
            )
        }
    }
}
