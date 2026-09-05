package pixl.rec.ui.settings

import android.os.Build
import android.widget.Toast
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import pixl.rec.R
import pixl.rec.core.engine.CodecProbe
import pixl.rec.core.engine.ResolutionCalculator
import pixl.rec.core.model.AudioSource
import pixl.rec.core.model.PillRecallGesture
import pixl.rec.core.model.QuickPreset
import pixl.rec.core.model.RecorderState
import pixl.rec.core.model.RecordingOrientation
import pixl.rec.core.model.VideoCodec
import pixl.rec.core.storage.ConfigPreferences
import pixl.rec.core.storage.StorageCalculator
import pixl.rec.ui.components.SectionCard
import pixl.rec.ui.components.SlidingPillSelector
import pixl.rec.ui.components.SteppedVuMeter
import pixl.rec.ui.components.TelemetryBadge
import pixl.rec.ui.dashboard.DashboardViewModel
import pixl.rec.ui.settings.components.AdvancedStudioControlsCard
import pixl.rec.ui.settings.components.QuickPresetDeck
import pixl.rec.ui.settings.components.ResolutionPreviewCanvas
import pixl.rec.ui.theme.BitcountPropSingle
import pixl.rec.ui.theme.BorderHighlight
import pixl.rec.ui.theme.BorderStark
import pixl.rec.ui.theme.CyberYellow
import pixl.rec.ui.theme.HyperCrimson
import pixl.rec.ui.theme.HyperCyan
import pixl.rec.ui.theme.ObsidianCanvas
import pixl.rec.ui.theme.SurfaceElevated
import pixl.rec.ui.theme.TextInverse
import pixl.rec.ui.theme.TextMuted
import pixl.rec.ui.theme.TextPrimary
import pixl.rec.ui.theme.TextSecondary
import pixl.rec.ui.theme.ToxicLime
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: DashboardViewModel,
    onNavigateToHudStudio: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val recorderState by viewModel.recorderState.collectAsState()
    val isRecordingActive = recorderState is RecorderState.Recording || recorderState is RecorderState.Paused

    var selectedSubTab by remember { mutableStateOf(SettingsTab.VIDEO) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState)
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        // 1. Settings Header
        Text(
            text = "CONFIG // SETTINGS",
            color = TextPrimary,
            fontSize = 18.sp,
            fontFamily = BitcountPropSingle,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
        Text(
            text = "HARDWARE ENCODER & ENGINE PROFILES",
            color = TextSecondary,
            fontSize = 11.sp,
            fontFamily = BitcountPropSingle
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Sub-Navigation Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceElevated, RoundedCornerShape(8.dp))
                .border(1.5.dp, BorderStark, RoundedCornerShape(8.dp))
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
                viewModel = viewModel,
                onNavigateToHudStudio = onNavigateToHudStudio
            )
            SettingsTab.STORAGE -> StorageSettingsSection(
                uiState = uiState,
                viewModel = viewModel
            )
        }

        Spacer(modifier = Modifier.height(116.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun VideoSettingsSection(
    uiState: pixl.rec.ui.dashboard.DashboardUiState,
    isRecordingActive: Boolean,
    viewModel: DashboardViewModel
) {
    val config = uiState.config
    val capabilities = uiState.capabilities

    val context = LocalContext.current
    val maxDisplayHz = capabilities?.display?.supportedRefreshRates?.maxOrNull()
        ?: capabilities?.display?.currentRefreshRate ?: 120f

    // 1. Quick Presets Deck
    SectionCard(title = "QUICK PRESETS", titleTag = config.activePreset.displayName) {
        QuickPresetDeck(
            activePreset = config.activePreset,
            onPresetSelect = { preset -> viewModel.applyQuickPreset(preset) },
            enabled = !isRecordingActive
        )
    }

    Spacer(modifier = Modifier.height(14.dp))

    // 2. Resolution & Orientation Deck with WYSIWYG Preview Canvas & Material 3 Dropdown
    val display = capabilities?.display
    val nativeWidth = display?.physicalWidth ?: 1080
    val nativeHeight = display?.physicalHeight ?: 2400
    val isLandscape = config.width > config.height

    val presets = remember(nativeWidth, nativeHeight, isLandscape) {
        ResolutionCalculator.getPresetsForDevice(nativeWidth, nativeHeight, isLandscape)
    }

    var isDropdownExpanded by remember { mutableStateOf(false) }
    var pendingTier by remember { mutableStateOf<ResolutionCalculator.ResolutionTierItem?>(null) }
    var showAutoTuneDialog by remember { mutableStateOf(false) }

    fun handleResolutionSelect(tier: ResolutionCalculator.ResolutionTierItem) {
        if (config.width == tier.width && config.height == tier.height) return

        val newShortDim = min(tier.width, tier.height)
        val recBitrateMbps = ResolutionCalculator.getRecommendedBitrateMbps(newShortDim)
        val currentBitrateMbps = config.videoBitrate / 1_000_000
        val isDismissed = ConfigPreferences.isAutoTuneBitrateDismissed(context)

        if (currentBitrateMbps != recBitrateMbps && !isDismissed) {
            pendingTier = tier
            showAutoTuneDialog = true
        } else {
            viewModel.updateResolution(tier.width, tier.height)
        }
    }

    SectionCard(title = "RESOLUTION & ORIENTATION", titleTag = "${config.width}×${config.height}") {
        // Orientation Tabs: PORTRAIT | LANDSCAPE
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceElevated, RoundedCornerShape(8.dp))
                .border(1.dp, BorderStark, RoundedCornerShape(8.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val isCurrentLandscape = config.width > config.height
            listOf(
                false to "PORTRAIT",
                true to "LANDSCAPE"
            ).forEach { (isLand, label) ->
                val isSelected = isCurrentLandscape == isLand
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            color = if (isSelected) TextPrimary else SurfaceElevated,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) HyperCrimson else BorderStark,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable(enabled = !isRecordingActive) {
                            if (!isSelected) {
                                viewModel.updateRecordingOrientation(
                                    if (isLand) RecordingOrientation.LANDSCAPE else RecordingOrientation.PORTRAIT
                                )
                            }
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontFamily = BitcountPropSingle,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) ObsidianCanvas else TextSecondary,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        ResolutionPreviewCanvas(
            width = config.width,
            height = config.height
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Full-Width Material 3 Exposed Dropdown Menu
        val selectedTier = presets.find { it.width == config.width && it.height == config.height }
        ExposedDropdownMenuBox(
            expanded = isDropdownExpanded,
            onExpandedChange = { if (!isRecordingActive) isDropdownExpanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                value = selectedTier?.let { "${it.displayDimensionString}   •   ${it.label.uppercase()}" }
                    ?: "${config.width} × ${config.height}",
                onValueChange = {},
                readOnly = true,
                label = {
                    Text(
                        text = "OUTPUT RESOLUTION",
                        fontFamily = BitcountPropSingle,
                        fontSize = 10.sp,
                        letterSpacing = 0.5.sp
                    )
                },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded)
                },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                    focusedBorderColor = HyperCrimson,
                    unfocusedBorderColor = BorderStark,
                    focusedContainerColor = SurfaceElevated,
                    unfocusedContainerColor = SurfaceElevated,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedLabelColor = HyperCrimson,
                    unfocusedLabelColor = TextSecondary
                ),
                shape = RoundedCornerShape(8.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = BitcountPropSingle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            )

            ExposedDropdownMenu(
                expanded = isDropdownExpanded,
                onDismissRequest = { isDropdownExpanded = false },
                modifier = Modifier
                    .background(SurfaceElevated)
                    .border(1.dp, BorderStark, RoundedCornerShape(8.dp))
            ) {
                presets.forEach { tier ->
                    val isSelected = config.width == tier.width && config.height == tier.height
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = tier.displayDimensionString,
                                    fontFamily = BitcountPropSingle,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) HyperCrimson else TextPrimary
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = "${tier.label.uppercase()} (${tier.tag})",
                                    fontFamily = BitcountPropSingle,
                                    fontSize = 11.sp,
                                    color = if (isSelected) HyperCrimson else TextSecondary
                                )
                            }
                        },
                        onClick = {
                            isDropdownExpanded = false
                            handleResolutionSelect(tier)
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
    }

    // Auto-Tune Bitrate Confirmation Dialog
    if (showAutoTuneDialog && pendingTier != null) {
        val tier = pendingTier!!
        val newShortDim = min(tier.width, tier.height)
        val recBitrateMbps = ResolutionCalculator.getRecommendedBitrateMbps(newShortDim)
        val currentBitrateMbps = config.videoBitrate / 1_000_000

        AutoTuneBitrateDialog(
            newTierTag = "${tier.label} (${tier.tag})",
            newWidth = tier.width,
            newHeight = tier.height,
            currentBitrateMbps = currentBitrateMbps,
            recommendedBitrateMbps = recBitrateMbps,
            onDismiss = {
                showAutoTuneDialog = false
                pendingTier = null
            },
            onKeep = { doNotAskAgain ->
                if (doNotAskAgain) {
                    ConfigPreferences.setAutoTuneBitrateDismissed(context, true)
                }
                viewModel.updateResolution(tier.width, tier.height)
                showAutoTuneDialog = false
                pendingTier = null
            },
            onApply = { doNotAskAgain ->
                if (doNotAskAgain) {
                    ConfigPreferences.setAutoTuneBitrateDismissed(context, true)
                }
                viewModel.updateResolution(tier.width, tier.height)
                viewModel.updateVideoBitrate(recBitrateMbps)
                showAutoTuneDialog = false
                pendingTier = null
            }
        )
    }

    Spacer(modifier = Modifier.height(14.dp))

    // 3. Framerate Selection with Wide Sliding Pill
    val supportedFpsList: List<Int> = remember(config.videoCodec, config.width, config.height, maxDisplayHz) {
        CodecProbe.getSupportedFrameratesFor(
            codec = config.videoCodec,
            width = config.width,
            height = config.height,
            maxDisplayHz = maxDisplayHz
        )
    }

    val availableRates = remember(maxDisplayHz) {
        listOf(30, 60, 90, 120, 144, 165).filter { it <= (maxDisplayHz + 1).toInt() }.ifEmpty { listOf(30) }
    }

    SectionCard(
        title = "CAPTURE REFRESH RATE",
        titleTag = if (config.allowExperimentalFps) "${config.framerate} FPS ⚡ OVERCLOCK" else "${config.framerate} FPS"
    ) {
        SlidingPillSelector(
            items = availableRates,
            selectedItem = if (availableRates.contains(config.framerate)) config.framerate else availableRates.first(),
            itemLabel = { "$it" },
            enabled = !isRecordingActive,
            onItemSelected = { fps ->
                val toastMsg = viewModel.requestFramerate(fps)
                if (toastMsg != null) {
                    Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                }
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        val motionTier = when {
            config.framerate >= 144 -> "ULTRA-HIGH REFRESH"
            config.framerate >= 120 -> "EXTREME MOTION"
            config.framerate >= 90 -> "ULTRA SMOOTH"
            config.framerate >= 60 -> "FLUID MOTION"
            else -> "POWER SAVER"
        }

        Text(
            text = "Status: ${config.framerate} FPS • $motionTier (Syncs to ${maxDisplayHz.toInt()}Hz Display)",
            color = TextSecondary,
            fontSize = 11.sp,
            fontFamily = BitcountPropSingle
        )
    }

    Spacer(modifier = Modifier.height(14.dp))

    // 4. Bitrate Deck with Wide Sliding Pill & Telemetry Readout
    val bitrateOptions = listOf(8, 16, 28, 50, 80)
    val currentBitrateMbps = (config.videoBitrate / 1_000_000).coerceIn(8, 80)
    val maxCodecBitrateMbps = CodecProbe.getMaxBitrateFor(config.videoCodec) / 1_000_000

    SectionCard(title = "ENCODING BITRATE", titleTag = "${config.videoBitrate / 1_000_000} MBPS") {
        SlidingPillSelector(
            items = bitrateOptions,
            selectedItem = currentBitrateMbps,
            itemLabel = { "$it" },
            enabled = !isRecordingActive,
            onItemSelected = { mbps ->
                if (mbps <= maxCodecBitrateMbps || config.allowExperimentalFps) {
                    viewModel.updateVideoBitrate(mbps)
                } else {
                    Toast.makeText(
                        context,
                        "${config.videoCodec.displayName} hardware limit: $maxCodecBitrateMbps Mbps",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        val estMbPer10Min = StorageCalculator.calculateMbPerMinute(config.videoBitrate, config.audioBitrate) * 10
        Text(
            text = String.format(
                Locale.US,
                "Target: %d Mbps • Est. Write Rate: ~%.0f MB / 10 min",
                config.videoBitrate / 1_000_000,
                estMbPer10Min
            ),
            color = TextSecondary,
            fontSize = 11.sp,
            fontFamily = BitcountPropSingle
        )
    }

    Spacer(modifier = Modifier.height(14.dp))

    // 5. Codec Selection with Dynamic AV1 Discovery
    val availableCodecs = remember(capabilities?.isAv1HardwareSupported) {
        if (capabilities?.isAv1HardwareSupported == true) {
            listOf(VideoCodec.AV1, VideoCodec.HEVC, VideoCodec.AVC)
        } else {
            listOf(VideoCodec.HEVC, VideoCodec.AVC)
        }
    }

    SectionCard(title = "HARDWARE CODEC", titleTag = config.videoCodec.displayName) {
        SlidingPillSelector(
            items = availableCodecs,
            selectedItem = config.videoCodec,
            itemLabel = { it.displayName },
            enabled = !isRecordingActive,
            onItemSelected = { viewModel.updateVideoCodec(it) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        val isHardware = capabilities?.codecs?.get(config.videoCodec)?.isHardwareAccelerated == true
        Text(
            text = "Hardware Engine: ${if (isHardware) "Dedicated ASIC (Zero-Copy Surface)" else "Software Encoder Fallback"}",
            color = if (isHardware) ToxicLime else CyberYellow,
            fontSize = 11.sp,
            fontFamily = BitcountPropSingle
        )
    }

    Spacer(modifier = Modifier.height(14.dp))

    // 6. Pro-Grade Advanced Studio Controls (Collapsible Accordion)
    AdvancedStudioControlsCard(
        config = config,
        isRecordingActive = isRecordingActive,
        onToggleOverclock = { viewModel.toggleExperimentalFps(it) },
        onUpdateBitrateMode = { viewModel.updateBitrateMode(it) },
        onUpdateKeyframeInterval = { viewModel.updateKeyframeInterval(it) },
        onUpdateColorRange = { viewModel.updateColorRange(it) },
        onToggleIntraRefresh = { viewModel.toggleIntraRefresh(it) },
        onUpdateCustomBitrate = { viewModel.updateCustomBitrate(it) }
    )
}

@Composable
private fun AudioSettingsSection(
    uiState: pixl.rec.ui.dashboard.DashboardUiState,
    isRecordingActive: Boolean,
    recorderState: RecorderState,
    viewModel: DashboardViewModel
) {
    val config = uiState.config
    val gameDb = if (recorderState is RecorderState.Recording) recorderState.gameAudioDb else -60f
    val micDb = if (recorderState is RecorderState.Recording) recorderState.micAudioDb else -60f

    val isGameAudioActive = config.audioSource == AudioSource.INTERNAL_AND_MIC || config.audioSource == AudioSource.INTERNAL_ONLY
    val isMicAudioActive = config.audioSource == AudioSource.INTERNAL_AND_MIC || config.audioSource == AudioSource.MIC_ONLY

    // 1. Audio Routing & Studio Controls
    SectionCard(title = "AUDIO STUDIO", titleTag = "48 KHZ STEREO AAC") {
        SlidingPillSelector(
            items = listOf(
                AudioSource.INTERNAL_AND_MIC,
                AudioSource.INTERNAL_ONLY,
                AudioSource.MIC_ONLY,
                AudioSource.MUTE
            ),
            selectedItem = config.audioSource,
            itemLabel = {
                when (it) {
                    AudioSource.INTERNAL_AND_MIC -> "Both"
                    AudioSource.INTERNAL_ONLY -> "Game"
                    AudioSource.MIC_ONLY -> "Mic"
                    AudioSource.MUTE -> "Mute"
                }
            },
            enabled = !isRecordingActive,
            onItemSelected = { viewModel.updateAudioSource(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Game Volume Slider (0 - 100%)
        val gamePercent = (config.internalAudioGain * 100).roundToInt()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "GAME VOLUME",
                fontFamily = BitcountPropSingle,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isGameAudioActive) TextPrimary else TextMuted
            )
            Text(
                text = "$gamePercent%",
                fontFamily = BitcountPropSingle,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isGameAudioActive) HyperCrimson else TextMuted
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Slider(
            value = config.internalAudioGain,
            onValueChange = { viewModel.updateInternalAudioGain(it) },
            valueRange = 0f..1f,
            enabled = !isRecordingActive && isGameAudioActive,
            colors = SliderDefaults.colors(
                thumbColor = HyperCrimson,
                activeTrackColor = HyperCrimson,
                inactiveTrackColor = BorderStark,
                disabledThumbColor = TextMuted,
                disabledActiveTrackColor = BorderStark.copy(alpha = 0.3f)
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Mic Gain Slider (0 - 200%, with dB boost calculation)
        val micPercent = (config.micGain * 100).roundToInt()
        val boostDb = if (config.micGain > 1.0f) {
            " (+${String.format(Locale.US, "%.1f", 20 * kotlin.math.log10(config.micGain))} dB Boost)"
        } else if (config.micGain < 1.0f && config.micGain > 0f) {
            " (${String.format(Locale.US, "%.1f", 20 * kotlin.math.log10(config.micGain))} dB)"
        } else ""

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MIC GAIN",
                fontFamily = BitcountPropSingle,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isMicAudioActive) TextPrimary else TextMuted
            )
            Text(
                text = "$micPercent%$boostDb",
                fontFamily = BitcountPropSingle,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isMicAudioActive) HyperCyan else TextMuted
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Slider(
            value = config.micGain,
            onValueChange = { viewModel.updateMicGain(it) },
            valueRange = 0f..2f,
            enabled = !isRecordingActive && isMicAudioActive,
            colors = SliderDefaults.colors(
                thumbColor = HyperCyan,
                activeTrackColor = HyperCyan,
                inactiveTrackColor = BorderStark,
                disabledThumbColor = TextMuted,
                disabledActiveTrackColor = BorderStark.copy(alpha = 0.3f)
            )
        )
    }

    Spacer(modifier = Modifier.height(14.dp))

    // 2. Real-time VU Levels
    SectionCard(title = "LIVE AUDIO VU VISUALIZER", titleTag = "48 KHZ STEREO") {
        SteppedVuMeter(
            label = "Internal Audio Loopback",
            dbLevel = gameDb,
            statusOverride = if (!isRecordingActive) "STANDBY" else if (!isGameAudioActive) "MUTED" else null
        )
        Spacer(modifier = Modifier.height(12.dp))
        SteppedVuMeter(
            label = "Microphone Audio (Stereo)",
            dbLevel = micDb,
            statusOverride = if (!isMicAudioActive) "MUTED" else null
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ControlsSettingsSection(
    uiState: pixl.rec.ui.dashboard.DashboardUiState,
    isRecordingActive: Boolean,
    viewModel: DashboardViewModel,
    onNavigateToHudStudio: () -> Unit = {}
) {
    val config = uiState.config

    // 1. Recording Countdown HUD Section
    SectionCard(
        title = "RECORDING COUNTDOWN",
        titleTag = if (config.countdownSeconds == 0) "NONE" else "${config.countdownSeconds}S HUD"
    ) {
        SlidingPillSelector(
            items = listOf(0, 3, 5),
            selectedItem = config.countdownSeconds,
            itemLabel = {
                when (it) {
                    0 -> "NONE"
                    else -> "${it}s"
                }
            },
            enabled = !isRecordingActive,
            onItemSelected = { viewModel.updateCountdownSeconds(it) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = when (config.countdownSeconds) {
                0 -> "Instant capture start upon permission grant (Zero Delay)"
                else -> "${config.countdownSeconds}-second HUD timer with geometric digit animation & tap-to-cancel safety"
            },
            color = TextSecondary,
            fontSize = 11.sp,
            fontFamily = BitcountPropSingle
        )
    }

    Spacer(modifier = Modifier.height(14.dp))

    // 2. Overlay & Gesture Controls Section
    SectionCard(
        title = "OVERLAY & GESTURE CONTROLS",
        titleTag = if (config.alwaysOnFloatingPill) "STANDBY ON" else if (config.showFloatingPill) "REC PILL ON" else "CLEAN CANVAS"
    ) {
        // 0. HUD Theme Studio Hero Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceElevated, RoundedCornerShape(10.dp))
                .border(1.5.dp, BorderHighlight, RoundedCornerShape(10.dp))
                .clickable { onNavigateToHudStudio() }
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(ObsidianCanvas, RoundedCornerShape(8.dp))
                            .border(1.dp, HyperCrimson, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_logo_core),
                            contentDescription = null,
                            tint = HyperCrimson,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "HUD THEME STUDIO",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontFamily = BitcountPropSingle,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Shapes, Hex Glow, Icons & Snapping",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = BitcountPropSingle
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .background(HyperCrimson, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "OPEN LAB →",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontFamily = BitcountPropSingle,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 1. Standby Floating Pill Toggle
        SettingsSwitch(
            iconRes = if (config.alwaysOnFloatingPill) R.drawable.ic_pixel_eye else R.drawable.ic_pixel_eye_off,
            title = "Standby Floating Pill Overlay",
            subtitle = if (config.alwaysOnFloatingPill) "Edge-docked bubble & radial HUD menu active on screen" else "Standby bubble disabled",
            checked = config.alwaysOnFloatingPill,
            enabled = !isRecordingActive,
            onCheckedChange = { viewModel.toggleAlwaysOnFloatingPill(it) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Live Recording Floating Pill Toggle
        SettingsSwitch(
            iconRes = if (config.showFloatingPill) R.drawable.ic_pixel_eye else R.drawable.ic_pixel_eye_off,
            title = "Live Recording Pill Overlay",
            subtitle = if (config.showFloatingPill) "On-screen pill enabled during recording" else "Clean Canvas: Pill hidden during recording",
            checked = config.showFloatingPill,
            enabled = !isRecordingActive,
            onCheckedChange = { viewModel.toggleFloatingPill(it) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Auto-Hide Pill into Invisible Ghost
        AnimatedVisibility(visible = config.showFloatingPill) {
            Column {
                SettingsSwitch(
                    iconRes = R.drawable.ic_pixel_gesture,
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

        // 4. Shake to Stop Gesture
        SettingsSwitch(
            iconRes = R.drawable.ic_pixel_vibrate,
            title = "Shake to Stop",
            subtitle = "Quick wrist flick stops and saves recording",
            checked = config.shakeToStop,
            enabled = !isRecordingActive,
            onCheckedChange = { viewModel.toggleShakeToStop(it) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 5. Stop on Screen Off
        SettingsSwitch(
            iconRes = R.drawable.ic_pixel_power,
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
    uiState: pixl.rec.ui.dashboard.DashboardUiState,
    viewModel: DashboardViewModel
) {
    val freeStorageFormatted = StorageCalculator.formatBytes(uiState.availableStorageBytes)
    val estimatedMb = uiState.config.estimatedMbPerMinute

    SectionCard(title = "STORAGE PIPELINE", titleTag = "SCOPED") {
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
    onClick: () -> Unit,
    onDisabledClick: (() -> Unit)? = null
) {
    val bg = if (isSelected) TextPrimary else SurfaceElevated
    val border = if (isSelected) HyperCrimson else if (enabled) BorderStark else BorderStark.copy(alpha = 0.4f)
    val textColor = if (isSelected) TextInverse else if (enabled) TextPrimary else TextMuted.copy(alpha = 0.5f)

    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(8.dp))
            .border(1.5.dp, border, RoundedCornerShape(8.dp))
            .clickable {
                if (enabled) onClick() else onDisabledClick?.invoke()
            }
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
private fun SettingsSwitch(
    iconRes: Int,
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
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = if (checked) BorderHighlight else TextSecondary,
                modifier = Modifier.size(26.dp)
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

@Composable
private fun AutoTuneBitrateDialog(
    newTierTag: String,
    newWidth: Int,
    newHeight: Int,
    currentBitrateMbps: Int,
    recommendedBitrateMbps: Int,
    onDismiss: () -> Unit,
    onKeep: (doNotAskAgain: Boolean) -> Unit,
    onApply: (doNotAskAgain: Boolean) -> Unit
) {
    var doNotAskAgain by remember { mutableStateOf(false) }

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
                    text = "⚡ AUTO-TUNE BITRATE?",
                    fontFamily = BitcountPropSingle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = HyperCrimson,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Switched to $newTierTag ($newWidth × $newHeight).\nRecommended bitrate is $recommendedBitrateMbps Mbps for balanced quality and file size.",
                    fontFamily = BitcountPropSingle,
                    fontSize = 12.sp,
                    color = TextPrimary,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Checkbox: Don't ask again
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { doNotAskAgain = !doNotAskAgain }
                        .padding(vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .background(if (doNotAskAgain) HyperCrimson else SurfaceElevated, RoundedCornerShape(4.dp))
                            .border(1.5.dp, if (doNotAskAgain) HyperCrimson else BorderStark, RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (doNotAskAgain) {
                            Text(
                                text = "✓",
                                color = ObsidianCanvas,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Don't ask again",
                        fontFamily = BitcountPropSingle,
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Box(
                        modifier = Modifier
                            .background(SurfaceElevated, RoundedCornerShape(6.dp))
                            .border(1.dp, BorderStark, RoundedCornerShape(6.dp))
                            .clickable { onKeep(doNotAskAgain) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "KEEP ${currentBitrateMbps}M",
                            fontFamily = BitcountPropSingle,
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .background(TextPrimary, RoundedCornerShape(6.dp))
                            .border(1.5.dp, HyperCrimson, RoundedCornerShape(6.dp))
                            .clickable { onApply(doNotAskAgain) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "APPLY ${recommendedBitrateMbps}M",
                            fontFamily = BitcountPropSingle,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextInverse
                        )
                    }
                }
            }
        }
    }
}
