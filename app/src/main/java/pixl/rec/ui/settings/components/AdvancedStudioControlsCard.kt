package pixl.rec.ui.settings.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import pixl.rec.core.model.BitrateMode
import pixl.rec.core.model.ColorRange
import pixl.rec.core.model.RecordingConfig
import pixl.rec.ui.theme.BitcountPropSingle
import pixl.rec.ui.theme.BorderHighlight
import pixl.rec.ui.theme.BorderStark
import pixl.rec.ui.theme.HyperCrimson
import pixl.rec.ui.theme.ObsidianCanvas
import pixl.rec.ui.theme.SurfaceElevated
import pixl.rec.ui.theme.TextInverse
import pixl.rec.ui.theme.TextMuted
import pixl.rec.ui.theme.TextPrimary
import pixl.rec.ui.theme.TextSecondary

/**
 * Pro-grade collapsible accordion housing advanced encoder controls,
 * overclocking, GOP mastering, color dynamic range, and intra-refresh.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdvancedStudioControlsCard(
    config: RecordingConfig,
    isRecordingActive: Boolean,
    onToggleOverclock: (Boolean) -> Unit,
    onUpdateBitrateMode: (BitrateMode) -> Unit,
    onUpdateKeyframeInterval: (Float) -> Unit,
    onUpdateColorRange: (ColorRange) -> Unit,
    onToggleIntraRefresh: (Boolean) -> Unit,
    onUpdateCustomBitrate: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    var showCustomBitrateDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceElevated, RoundedCornerShape(12.dp))
            .border(1.5.dp, if (config.allowExperimentalFps) HyperCrimson else BorderStark, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        // Collapsible Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "⚙️ ADVANCED STUDIO CONTROLS",
                    fontFamily = BitcountPropSingle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = if (config.allowExperimentalFps) HyperCrimson else TextPrimary,
                    letterSpacing = 1.sp
                )
                if (config.allowExperimentalFps) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(HyperCrimson, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "OC ACTIVE",
                            fontFamily = BitcountPropSingle,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = TextInverse
                        )
                    }
                }
            }

            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.padding(top = 16.dp)) {
                // 1. Overclock Mode
                StudioSwitchRow(
                    title = "FORCE EXPERIMENTAL FRAMERATES",
                    subtitle = "Bypass OEM silicon caps to push 60/90/120 FPS at high resolutions. May drop frames on heavy 3D games.",
                    checked = config.allowExperimentalFps,
                    enabled = !isRecordingActive,
                    isWarning = true,
                    onCheckedChange = onToggleOverclock
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 2. Intra-Refresh Mode
                StudioSwitchRow(
                    title = "INTRA-REFRESH (SMOOTH FRAMETIMES)",
                    subtitle = "Progressively refreshes macroblock columns instead of heavy I-frames. Eliminates gaming micro-stutter.",
                    checked = config.enableIntraRefresh,
                    enabled = !isRecordingActive,
                    isWarning = false,
                    onCheckedChange = onToggleIntraRefresh
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 3. Bitrate Mode
                Text(
                    text = "BITRATE RATE-CONTROL MODE",
                    fontFamily = BitcountPropSingle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BitrateMode.entries.forEach { mode ->
                        val isSelected = config.bitrateMode == mode
                        StudioChip(
                            text = mode.displayName,
                            isSelected = isSelected,
                            enabled = !isRecordingActive,
                            onClick = { onUpdateBitrateMode(mode) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 4. Keyframe Interval
                Text(
                    text = "KEYFRAME / GOP INTERVAL",
                    fontFamily = BitcountPropSingle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val intervals = listOf(
                        0.5f to "0.5s (FAST EDIT)",
                        1.0f to "1.0s (STANDARD)",
                        2.0f to "2.0s (COMPACT)"
                    )
                    intervals.forEach { (sec, label) ->
                        val isSelected = kotlin.math.abs(config.iFrameIntervalSeconds - sec) < 0.05f
                        StudioChip(
                            text = label,
                            isSelected = isSelected,
                            enabled = !isRecordingActive,
                            onClick = { onUpdateKeyframeInterval(sec) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 5. Color Dynamic Range
                Text(
                    text = "COLOR DYNAMIC RANGE",
                    fontFamily = BitcountPropSingle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ColorRange.entries.forEach { range ->
                        val isSelected = config.colorRange == range
                        StudioChip(
                            text = range.displayName,
                            isSelected = isSelected,
                            enabled = !isRecordingActive,
                            onClick = { onUpdateColorRange(range) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 6. Custom Exact Bitrate
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ObsidianCanvas, RoundedCornerShape(8.dp))
                        .border(1.dp, BorderStark, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "CUSTOM BITRATE PRECISION",
                            fontFamily = BitcountPropSingle,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = "${config.videoBitrate / 1_000_000} MBPS ACTIVE",
                            fontFamily = BitcountPropSingle,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = TextPrimary
                        )
                    }
                    StudioChip(
                        text = "SET EXACT MBPS...",
                        isSelected = false,
                        enabled = !isRecordingActive,
                        onClick = { showCustomBitrateDialog = true }
                    )
                }
            }
        }
    }

    if (showCustomBitrateDialog) {
        CustomBitrateDialog(
            currentMbps = config.videoBitrate / 1_000_000,
            onDismiss = { showCustomBitrateDialog = false },
            onConfirm = { mbps ->
                onUpdateCustomBitrate(mbps * 1_000_000)
                showCustomBitrateDialog = false
            }
        )
    }
}

@Composable
private fun StudioSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    isWarning: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ObsidianCanvas, RoundedCornerShape(8.dp))
            .border(1.dp, if (checked && isWarning) HyperCrimson else BorderStark, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = BitcountPropSingle,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = if (checked && isWarning) HyperCrimson else TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontFamily = BitcountPropSingle,
                fontSize = 9.sp,
                color = TextSecondary,
                lineHeight = 12.sp
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = TextInverse,
                checkedTrackColor = if (isWarning) HyperCrimson else TextPrimary,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = ObsidianCanvas
            )
        )
    }
}

@Composable
private fun StudioChip(
    text: String,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .background(
                if (isSelected) TextPrimary else ObsidianCanvas,
                RoundedCornerShape(6.dp)
            )
            .border(
                1.5.dp,
                if (isSelected) HyperCrimson else BorderStark,
                RoundedCornerShape(6.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            fontFamily = BitcountPropSingle,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            color = if (isSelected) TextInverse else TextPrimary
        )
    }
}

@Composable
private fun CustomBitrateDialog(
    currentMbps: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var text by remember { mutableStateOf(currentMbps.toString()) }
    val parsed = text.toIntOrNull() ?: 0

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceElevated, RoundedCornerShape(12.dp))
                .border(2.dp, BorderStark, RoundedCornerShape(12.dp))
                .padding(20.dp)
        ) {
            Column {
                Text(
                    text = "MANUAL BITRATE PRECISION",
                    fontFamily = BitcountPropSingle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Enter exact target video bitrate in Megabits per second (1–120 Mbps):",
                    fontFamily = BitcountPropSingle,
                    fontSize = 10.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { ch -> ch.isDigit() }.take(3) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HyperCrimson,
                        unfocusedBorderColor = BorderStark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = ObsidianCanvas),
                        modifier = Modifier.border(1.dp, BorderStark, RoundedCornerShape(8.dp))
                    ) {
                        Text("CANCEL", fontFamily = BitcountPropSingle, color = TextSecondary, fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            if (parsed in 1..120) {
                                onConfirm(parsed)
                            }
                        },
                        enabled = parsed in 1..120,
                        colors = ButtonDefaults.buttonColors(containerColor = HyperCrimson)
                    ) {
                        Text("APPLY", fontFamily = BitcountPropSingle, color = TextInverse, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
