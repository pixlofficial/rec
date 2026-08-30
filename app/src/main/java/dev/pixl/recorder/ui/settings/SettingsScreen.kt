package dev.pixl.recorder.ui.settings

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pixl.recorder.core.model.AudioSource
import dev.pixl.recorder.core.model.CaptureTarget
import dev.pixl.recorder.core.model.PillRecallGesture
import dev.pixl.recorder.core.model.RecorderState
import dev.pixl.recorder.core.model.VideoCodec
import dev.pixl.recorder.core.storage.StorageCalculator
import dev.pixl.recorder.ui.components.BrutalistCard
import dev.pixl.recorder.ui.components.SteppedVuMeter
import dev.pixl.recorder.ui.components.TelemetryBadge
import dev.pixl.recorder.ui.dashboard.DashboardViewModel
import dev.pixl.recorder.ui.theme.BitcountPropSingle
import dev.pixl.recorder.ui.theme.BorderHighlight
import dev.pixl.recorder.ui.theme.BorderStark
import dev.pixl.recorder.ui.theme.CyberYellow
import dev.pixl.recorder.ui.theme.HyperCrimson
import dev.pixl.recorder.ui.theme.HyperCyan
import dev.pixl.recorder.ui.theme.ObsidianCanvas
import dev.pixl.recorder.ui.theme.SurfaceElevated
import dev.pixl.recorder.ui.theme.TextInverse
import dev.pixl.recorder.ui.theme.TextMuted
import dev.pixl.recorder.ui.theme.TextPrimary
import dev.pixl.recorder.ui.theme.TextSecondary
import dev.pixl.recorder.ui.theme.ToxicLime
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: DashboardViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val recorderState by viewModel.recorderState.collectAsState()
    val isRecordingActive = recorderState is RecorderState.Recording || recorderState is RecorderState.Paused

    var selectedSubTab by remember { mutableStateOf(SettingsTab.VIDEO) }
    val scrollState = rememberScrollState()

    Scaffold(
        containerColor = ObsidianCanvas
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 1. Settings Header
            Text(
                text = "CONFIG // SETTINGS",
                color = TextPrimary,
                fontSize = 24.sp,
                fontFamily = BitcountPropSingle,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                text = "ZERO-COPY PIPELINE & HARDWARE DECK",
                color = TextSecondary,
                fontSize = 12.sp,
                fontFamily = BitcountPropSingle
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Segmented Sub-Tab Switcher (VIDEO, AUDIO, CONTROLS, STORAGE)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceElevated, RoundedCornerShape(10.dp))
                    .border(1.5.dp, BorderStark, RoundedCornerShape(10.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SettingsTab.entries.forEach { tab ->
                    val isSelected = selectedSubTab == tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                color = if (isSelected) TextPrimary else Color.Transparent,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { selectedSubTab = tab }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab.title,
                            color = if (isSelected) TextInverse else TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = BitcountPropSingle,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            letterSpacing = 0.5.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Sub-Tab Content
            when (selectedSubTab) {
                SettingsTab.VIDEO -> VideoSettingsSection(
                    uiState = uiState,
                    isRecordingActive = isRecordingActive,
                    viewModel = viewModel
                )
                SettingsTab.AUDIO -> AudioSettingsSection(
                    uiState = uiState,
                    isRecordingActive = isRecordingActive,
                    recorderState = recorderState,
                    viewModel = viewModel
                )
                SettingsTab.CONTROLS -> ControlsSettingsSection(
                    uiState = uiState,
                    isRecordingActive = isRecordingActive,
                    viewModel = viewModel
                )
                SettingsTab.STORAGE -> StorageSettingsSection(
                    uiState = uiState,
                    viewModel = viewModel
                )
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VideoSettingsSection(
    uiState: dev.pixl.recorder.ui.dashboard.DashboardUiState,
    isRecordingActive: Boolean,
    viewModel: DashboardViewModel
) {
    val config = uiState.config
    val capabilities = uiState.capabilities

    // 1. Framerate Selection
    BrutalistCard(title = "CAPTURE REFRESH RATE", titleTag = "${config.framerate} FPS") {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(30, 60, 90, 120).forEach { fps ->
                val isSelected = config.framerate == fps
                val isSupported = (capabilities?.display?.supportedRefreshRates?.any { it >= fps - 1 } ?: true) || fps <= 60
                SettingsTag(
                    text = "$fps FPS",
                    isSelected = isSelected,
                    enabled = !isRecordingActive && isSupported,
                    onClick = { viewModel.updateFramerate(fps) }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // 2. Codec Selection
    BrutalistCard(title = "ENCODER HARDWARE ASIC", titleTag = config.videoCodec.displayName) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VideoCodec.entries.forEach { codec ->
                val isSelected = config.videoCodec == codec
                val isHardware = capabilities?.codecs?.get(codec)?.isHardwareAccelerated == true
                val isAvailable = when (codec) {
                    VideoCodec.HEVC -> true
                    VideoCodec.AVC -> true
                    VideoCodec.AV1 -> capabilities?.isAv1HardwareSupported == true
                }
                SettingsTag(
                    text = "${codec.displayName}${if (isHardware) " (HW)" else ""}",
                    isSelected = isSelected,
                    enabled = !isRecordingActive && isAvailable,
                    onClick = { viewModel.updateVideoCodec(codec) }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // 3. Resolution Selection
    val display = capabilities?.display
    val nativeWidth = display?.physicalWidth ?: 720
    val nativeHeight = display?.physicalHeight ?: 1560

    BrutalistCard(title = "CAPTURE RESOLUTION", titleTag = "${config.width}x${config.height}") {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val resolutions = listOf(
                "NATIVE" to (nativeWidth to nativeHeight),
                "1080p FHD" to (1080 to 1920),
                "720p HD" to (720 to 1280)
            )
            resolutions.forEach { (label, res) ->
                val isSelected = config.width == res.first && config.height == res.second
                SettingsTag(
                    text = "$label (${res.first}p)",
                    isSelected = isSelected,
                    enabled = !isRecordingActive,
                    onClick = { viewModel.updateResolution(res.first, res.second) }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // 4. Bitrate Deck
    BrutalistCard(title = "ENCODING BITRATE", titleTag = "${config.videoBitrate / 1_000_000} MBPS") {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(20, 35, 50, 80, 100).forEach { mbps ->
                val isSelected = config.videoBitrate == mbps * 1_000_000
                SettingsTag(
                    text = "$mbps Mbps",
                    isSelected = isSelected,
                    enabled = !isRecordingActive,
                    onClick = { viewModel.updateVideoBitrate(mbps) }
                )
            }
        }
    }
}

@Composable
private fun AudioSettingsSection(
    uiState: dev.pixl.recorder.ui.dashboard.DashboardUiState,
    isRecordingActive: Boolean,
    recorderState: RecorderState,
    viewModel: DashboardViewModel
) {
    val config = uiState.config
    val gameDb = if (recorderState is RecorderState.Recording) recorderState.gameAudioDb else -60f
    val micDb = if (recorderState is RecorderState.Recording) recorderState.micAudioDb else -60f

    // 1. Audio Source Routing
    BrutalistCard(title = "AUDIO SOURCE ROUTING", titleTag = config.audioSource.name) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AudioSource.entries.forEach { source ->
                val isSelected = config.audioSource == source
                SettingsRow(
                    text = source.displayName,
                    isSelected = isSelected,
                    enabled = !isRecordingActive,
                    onClick = { viewModel.updateAudioSource(source) }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // 2. Real-time VU Levels
    BrutalistCard(title = "LIVE AUDIO VU VISUALIZER", titleTag = "48 KHZ STEREO") {
        SteppedVuMeter(label = "Internal Game Audio Loopback", dbLevel = gameDb)
        Spacer(modifier = Modifier.height(12.dp))
        SteppedVuMeter(label = "Microphone Audio (Stereo)", dbLevel = micDb)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControlsSettingsSection(
    uiState: dev.pixl.recorder.ui.dashboard.DashboardUiState,
    isRecordingActive: Boolean,
    viewModel: DashboardViewModel
) {
    val config = uiState.config

    BrutalistCard(
        title = "OVERLAY & GESTURE CONTROLS",
        titleTag = if (config.showFloatingPill) "PILL ON" else "CLEAN CANVAS"
    ) {
        // 1. Floating Pill Toggle
        SettingsSwitch(
            icon = if (config.showFloatingPill) Icons.Default.Visibility else Icons.Default.VisibilityOff,
            title = "Floating Game Pill Overlay",
            subtitle = if (config.showFloatingPill) "On-screen pill enabled during recording" else "Clean Canvas: Pill hidden (control via Shake/Notification)",
            checked = config.showFloatingPill,
            enabled = !isRecordingActive,
            onCheckedChange = { viewModel.toggleFloatingPill(it) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Auto-Hide Pill into Invisible Ghost
        AnimatedVisibility(visible = config.showFloatingPill) {
            Column {
                SettingsSwitch(
                    icon = Icons.Default.Gesture,
                    title = "Auto-Hide Pill to Invisible",
                    subtitle = "Pill disappears completely after 2s; recall anytime with gesture",
                    checked = config.autoHidePill,
                    enabled = !isRecordingActive,
                    onCheckedChange = { viewModel.toggleAutoHidePill(it) }
                )

                if (config.autoHidePill) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "RECALL GESTURE",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = BitcountPropSingle,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PillRecallGesture.entries.forEach { gesture ->
                            val isSelected = config.pillRecallGesture == gesture
                            SettingsTag(
                                text = gesture.displayName,
                                isSelected = isSelected,
                                enabled = !isRecordingActive,
                                onClick = { viewModel.updatePillRecallGesture(gesture) }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        // 3. Shake to Stop Gesture
        SettingsSwitch(
            icon = Icons.Default.Vibration,
            title = "Shake to Stop",
            subtitle = "Quick wrist flick stops and saves recording",
            checked = config.shakeToStop,
            enabled = !isRecordingActive,
            onCheckedChange = { viewModel.toggleShakeToStop(it) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 4. Stop on Screen Off
        SettingsSwitch(
            icon = Icons.Default.PowerSettingsNew,
            title = "Stop on Screen Off",
            subtitle = "Locks video cleanly when power button is pressed",
            checked = config.stopOnScreenOff,
            enabled = !isRecordingActive,
            onCheckedChange = { viewModel.toggleStopOnScreenOff(it) }
        )
    }
}

@Composable
private fun StorageSettingsSection(
    uiState: dev.pixl.recorder.ui.dashboard.DashboardUiState,
    viewModel: DashboardViewModel
) {
    val freeStorageFormatted = StorageCalculator.formatBytes(uiState.availableStorageBytes)
    val estimatedMb = uiState.config.estimatedMbPerMinute

    BrutalistCard(title = "STORAGE PIPELINE", titleTag = "SCOPED") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "TARGET FOLDER:",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = BitcountPropSingle
                )
                Text(
                    text = "Movies/PixL-REC",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontFamily = BitcountPropSingle,
                    fontWeight = FontWeight.Bold
                )
            }
            TelemetryBadge(label = "FREE", value = freeStorageFormatted, accentColor = CyberYellow)
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "ESTIMATED WRITE RATE:",
                color = TextSecondary,
                fontSize = 12.sp,
                fontFamily = BitcountPropSingle
            )
            Text(
                text = String.format(Locale.US, "%.1f MB/MIN", estimatedMb),
                color = ToxicLime,
                fontSize = 13.sp,
                fontFamily = BitcountPropSingle,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SettingsTag(
    text: String,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) TextPrimary else SurfaceElevated
    val border = if (isSelected) HyperCrimson else BorderStark
    val textColor = if (isSelected) TextInverse else if (enabled) TextPrimary else TextMuted

    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(8.dp))
            .border(1.5.dp, border, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = text.uppercase(),
            color = textColor,
            fontSize = 13.sp,
            fontFamily = BitcountPropSingle,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun SettingsRow(
    text: String,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) SurfaceElevated else ObsidianCanvas
    val border = if (isSelected) BorderHighlight else BorderStark

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(8.dp))
            .border(1.5.dp, border, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = text,
            color = if (isSelected) TextPrimary else TextSecondary,
            fontSize = 13.sp,
            fontFamily = BitcountPropSingle,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            letterSpacing = 0.5.sp
        )
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(BorderHighlight, CircleShape)
                    .border(1.dp, BorderStark, CircleShape)
            )
        }
    }
}

@Composable
private fun SettingsSwitch(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceElevated, RoundedCornerShape(8.dp))
            .border(1.5.dp, if (checked) BorderHighlight else BorderStark, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) BorderHighlight else TextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    color = if (checked) TextPrimary else TextSecondary,
                    fontSize = 14.sp,
                    fontFamily = BitcountPropSingle,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontFamily = BitcountPropSingle,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = TextInverse,
                checkedTrackColor = TextPrimary,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = ObsidianCanvas
            )
        )
    }
}
