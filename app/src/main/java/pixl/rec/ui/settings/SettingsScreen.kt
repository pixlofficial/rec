@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package pixl.rec.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.lerp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import pixl.rec.core.game.GameDetector
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import pixl.rec.core.model.AudioSource
import pixl.rec.core.model.PillRecallGesture
import pixl.rec.core.model.StreamConfig
import pixl.rec.core.model.StreamPlatform
import pixl.rec.core.model.QuickPreset
import pixl.rec.core.model.RecorderState
import pixl.rec.core.model.RecordingConfig
import pixl.rec.core.model.RecordingOrientation
import pixl.rec.core.model.VideoCodec
import pixl.rec.core.storage.ConfigPreferences
import pixl.rec.core.storage.ConfigSerializer
import pixl.rec.core.storage.StorageCalculator
import pixl.rec.ui.settings.components.ImportConfigDialog
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
import pixl.rec.ui.theme.SurfaceRaised
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

    val isStreamingEnabled by viewModel.isLiveStreamingEnabled.collectAsState()
    var selectedSubTab by remember { mutableStateOf(SettingsTab.GENERAL) }
    val availableTabs = remember(isStreamingEnabled) {
        if (isStreamingEnabled) {
            SettingsTab.entries
        } else {
            listOf(SettingsTab.GENERAL, SettingsTab.VIDEO, SettingsTab.AUDIO, SettingsTab.CONTROLS)
        }
    }

    androidx.compose.runtime.LaunchedEffect(isStreamingEnabled) {
        if (!isStreamingEnabled && selectedSubTab == SettingsTab.STREAM) {
            selectedSubTab = SettingsTab.GENERAL
        }
    }

    val scrollState = rememberScrollState()

    val context = LocalContext.current
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val coroutineScope = rememberCoroutineScope()
    val cardShineProgress = remember { Animatable(0f) }
    val pillTraceProgress = remember { Animatable(0f) }
    var showGamingOptDialog by remember { mutableStateOf(false) }
    var awaitingUsagePermission by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (awaitingUsagePermission && GameDetector.hasUsageAccessPermission(context)) {
                    awaitingUsagePermission = false
                    viewModel.toggleSmartGameOptimization(true)
                    Toast.makeText(context, "🎮 Smart Game Optimization Enabled", Toast.LENGTH_SHORT).show()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (showGamingOptDialog) {
        GamingOptimizationDialog(
            onDismiss = { doNotAskAgain ->
                if (doNotAskAgain) {
                    ConfigPreferences.setGamingPresetPromptDismissed(context, true)
                }
                showGamingOptDialog = false
                viewModel.applyQuickPreset(QuickPreset.GAMING)
            },
            onConfigure = { doNotAskAgain ->
                if (doNotAskAgain) {
                    ConfigPreferences.setGamingPresetPromptDismissed(context, true)
                }
                showGamingOptDialog = false
                viewModel.applyQuickPreset(QuickPreset.GAMING)
                selectedSubTab = SettingsTab.CONTROLS
                coroutineScope.launch {
                    delay(200)
                    if (scrollState.maxValue == 0) {
                        delay(150)
                    }
                    scrollState.animateScrollTo(scrollState.maxValue, tween(500))
                    try {
                        bringIntoViewRequester.bringIntoView()
                    } catch (_: Exception) {}

                    cardShineProgress.snapTo(0f)
                    pillTraceProgress.snapTo(0f)

                    // Phase 1: Slanted red beam passes across the card (left to right)
                    val shineJob = launch {
                        cardShineProgress.animateTo(1f, tween(550))
                    }

                    // Phase 2: As red shine reaches the toggle pill, ignite border trace
                    delay(300)
                    pillTraceProgress.animateTo(1.3f, tween(650))

                    shineJob.join()
                    cardShineProgress.snapTo(0f)
                    pillTraceProgress.snapTo(0f)
                }
            }
        )
    }

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
            fontSize = 24.sp,
            fontFamily = BitcountPropSingle,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Text(
            text = "HARDWARE ENCODER & ENGINE PROFILES",
            color = TextSecondary,
            fontSize = 12.sp,
            fontFamily = BitcountPropSingle
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Sub-Navigation Tabs
        SlidingPillSelector(
            items = availableTabs,
            selectedItem = selectedSubTab,
            onItemSelected = { selectedSubTab = it },
            itemLabel = { it.title },
            height = 42.dp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Sub-Tab Content
        when (selectedSubTab) {
            SettingsTab.GENERAL -> GeneralSettingsSection(
                uiState = uiState,
                viewModel = viewModel,
                isStreamingEnabled = isStreamingEnabled
            )
            SettingsTab.VIDEO -> VideoSettingsSection(
                uiState = uiState,
                isRecordingActive = isRecordingActive,
                viewModel = viewModel,
                onSelectGamingPreset = { showGamingOptDialog = true }
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
                smartGameOptRequester = bringIntoViewRequester,
                cardShineProgress = cardShineProgress,
                pillTraceProgress = pillTraceProgress,
                onRequestUsagePermission = {
                    awaitingUsagePermission = true
                    GameDetector.openUsageAccessSettings(context)
                    Toast.makeText(
                        context,
                        "Grant Usage Access to enable Game Auto-Detection",
                        Toast.LENGTH_LONG
                    ).show()
                },
                onNavigateToHudStudio = onNavigateToHudStudio
            )
            SettingsTab.STREAM -> StreamSettingsSection(
                viewModel = viewModel,
                isRecordingActive = isRecordingActive
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
    viewModel: DashboardViewModel,
    onSelectGamingPreset: () -> Unit
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
            onPresetSelect = { preset ->
                if (preset == QuickPreset.GAMING) {
                    val isDismissed = ConfigPreferences.isGamingPresetPromptDismissed(context)
                    if (!config.smartGameOptimization && !isDismissed) {
                        onSelectGamingPreset()
                    } else {
                        viewModel.applyQuickPreset(preset)
                    }
                } else {
                    viewModel.applyQuickPreset(preset)
                }
            },
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
        val isCurrentLandscape = config.width > config.height
        val orientationOptions = listOf("PORTRAIT", "LANDSCAPE")
        val selectedOrientation = if (isCurrentLandscape) "LANDSCAPE" else "PORTRAIT"

        SlidingPillSelector(
            items = orientationOptions,
            selectedItem = selectedOrientation,
            itemLabel = { it },
            enabled = !isRecordingActive,
            onItemSelected = { selected ->
                val targetLand = (selected == "LANDSCAPE")
                viewModel.updateRecordingOrientation(
                    if (targetLand) RecordingOrientation.LANDSCAPE else RecordingOrientation.PORTRAIT
                )
            }
        )

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

    val isFpsOverclocked = config.allowExperimentalFps && config.framerate > maxDisplayHz

    SectionCard(
        title = "CAPTURE REFRESH RATE",
        titleTag = "${config.framerate} FPS",
        tagIcon = if (isFpsOverclocked) R.drawable.ic_pixel_lightning else null
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

    val isInternalAudioActive = config.audioSource == AudioSource.INTERNAL_AND_MIC || config.audioSource == AudioSource.INTERNAL_ONLY
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
                    AudioSource.INTERNAL_ONLY -> "Internal"
                    AudioSource.MIC_ONLY -> "Mic"
                    AudioSource.MUTE -> "Mute"
                }
            },
            enabled = !isRecordingActive,
            onItemSelected = { viewModel.updateAudioSource(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Internal Volume Slider (0 - 100%)
        val internalPercent = (config.internalAudioGain * 100).roundToInt()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "INTERNAL VOLUME",
                fontFamily = BitcountPropSingle,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isInternalAudioActive) TextPrimary else TextMuted
            )
            Text(
                text = "$internalPercent%",
                fontFamily = BitcountPropSingle,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isInternalAudioActive) HyperCrimson else TextMuted
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Slider(
            value = config.internalAudioGain,
            onValueChange = { viewModel.updateInternalAudioGain(it) },
            valueRange = 0f..1f,
            enabled = !isRecordingActive && isInternalAudioActive,
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
            statusOverride = if (!isRecordingActive) "STANDBY" else if (!isInternalAudioActive) "MUTED" else null
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
    smartGameOptRequester: BringIntoViewRequester,
    cardShineProgress: Animatable<Float, *>,
    pillTraceProgress: Animatable<Float, *>,
    onRequestUsagePermission: () -> Unit,
    onNavigateToHudStudio: () -> Unit = {}
) {
    val config = uiState.config
    val context = LocalContext.current

    // 1. Recording Countdown HUD Section
    SectionCard(
        title = "RECORDING COUNTDOWN",
        titleTag = if (config.countdownSeconds == 0) "NONE" else "${config.countdownSeconds}S HUD"
    ) {
        SlidingPillSelector(
            items = listOf(0, 3, 5),
            selectedItem = config.countdownSeconds,
            itemIcon = { if (it == 0) R.drawable.ic_pixel_none else null },
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

        Spacer(modifier = Modifier.height(10.dp))

        // 6. Smart Game Optimization
        Box(
            modifier = Modifier
                .bringIntoViewRequester(smartGameOptRequester)
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .drawWithContent {
                    drawContent()
                    val p = cardShineProgress.value
                    if (p in 0.001f..0.999f) {
                        val w = size.width
                        val h = size.height
                        val beamWidth = w * 0.45f
                        val centerX = -beamWidth + (w + beamWidth * 2f) * p

                        val startOffset = Offset(centerX - beamWidth * 0.5f, 0f)
                        val endOffset = Offset(centerX + beamWidth * 0.5f, h)

                        val shineBrush = Brush.linearGradient(
                            0.0f to Color.Transparent,
                            0.3f to HyperCrimson.copy(alpha = 0.25f),
                            0.5f to Color(0xFFFF4D4D).copy(alpha = 0.75f),
                            0.7f to HyperCrimson.copy(alpha = 0.25f),
                            1.0f to Color.Transparent,
                            start = startOffset,
                            end = endOffset
                        )
                        drawRect(brush = shineBrush, blendMode = BlendMode.Screen)
                    }
                }
        ) {
            SettingsSwitch(
                iconRes = R.drawable.ic_pixel_gamepad,
                title = "Smart Game Optimization",
                subtitle = "Auto-detects games to lock landscape, 60 FPS cap & low overhead",
                checked = config.smartGameOptimization,
                enabled = !isRecordingActive,
                pillTraceProgress = pillTraceProgress.value,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        if (GameDetector.hasUsageAccessPermission(context)) {
                            viewModel.toggleSmartGameOptimization(true)
                        } else {
                            onRequestUsagePermission()
                        }
                    } else {
                        viewModel.toggleSmartGameOptimization(false)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Notification Shade Controls Section
        SectionCard(
            title = "NOTIFICATION SHADE CONTROLS",
            titleTag = if (config.standbyNotification && config.recordingNotification) "ALL ACTIVE"
            else if (config.standbyNotification) "STANDBY ON"
            else if (config.recordingNotification) "REC ONLY"
            else "OFF"
        ) {
            SettingsSwitch(
                iconRes = if (config.standbyNotification) R.drawable.ic_notification else R.drawable.ic_pixel_close,
                title = "Standby Quick Controls",
                subtitle = if (config.standbyNotification) "Persistent notification in shade with 1-tap Record & Tool controls when idle" else "Standby notification disabled",
                checked = config.standbyNotification,
                enabled = !isRecordingActive,
                onCheckedChange = { viewModel.toggleStandbyNotification(it) }
            )

            Spacer(modifier = Modifier.height(10.dp))

            SettingsSwitch(
                iconRes = if (config.recordingNotification) R.drawable.ic_pixel_record else R.drawable.ic_pixel_power,
                title = "Recording Status Controls",
                subtitle = if (config.recordingNotification) "Live timer, pause, and stop controls in notification shade during recording" else "Clean Canvas: Minimized silent recording service",
                checked = config.recordingNotification,
                enabled = !isRecordingActive,
                onCheckedChange = { viewModel.toggleRecordingNotification(it) }
            )
        }
    }
}

@Composable
private fun GeneralSettingsSection(
    uiState: pixl.rec.ui.dashboard.DashboardUiState,
    viewModel: DashboardViewModel,
    isStreamingEnabled: Boolean
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val freeStorageFormatted = StorageCalculator.formatBytes(uiState.availableStorageBytes)
    val estimatedMb = uiState.config.estimatedMbPerMinute

    var pendingImportConfig by remember { mutableStateOf<RecordingConfig?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    // SAF Create Document for Export
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(viewModel.exportConfigJson().toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "Config exported successfully", Toast.LENGTH_SHORT).show()
            }.onFailure { e ->
                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // SAF Open Document for Import
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val jsonStr = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader().readText()
                } ?: throw IllegalArgumentException("Could not read file from storage")

                val result = ConfigSerializer.importFromJson(jsonStr, uiState.capabilities)
                val config = result.getOrThrow()
                pendingImportConfig = config
                showImportDialog = true
            }.onFailure { e ->
                Toast.makeText(context, "Invalid config: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // 0. Live Streaming Studio Master Disarm Switch
    SectionCard(title = "LIVE STREAMING STUDIO", titleTag = "MASTER") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = "ENABLE BROADCAST DECK",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontFamily = BitcountPropSingle,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isStreamingEnabled) "Streaming tab and broadcast shutter are armed." else "Disabled. REC operates as a dedicated offline recorder.",
                    color = TextSecondary,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp
                )
            }
            Switch(
                checked = isStreamingEnabled,
                onCheckedChange = { viewModel.toggleLiveStreaming(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = HyperCyan,
                    checkedTrackColor = HyperCyan.copy(alpha = 0.25f),
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = SurfaceElevated
                )
            )
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // 1. Storage Pipeline Card
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

    Spacer(modifier = Modifier.height(14.dp))

    // 2. Profile Backup & Restore Card
    SectionCard(title = "PROFILE BACKUP & RESTORE", titleTag = "JSON") {
        Text(
            text = "Export your complete recording parameters, bitrates, audio gains, and HUD customization as a portable JSON file, or restore a profile.",
            color = TextSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Primary Export & Import Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Export Config Button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ToxicLime.copy(alpha = 0.12f))
                    .border(1.5.dp, ToxicLime, RoundedCornerShape(8.dp))
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        val defaultFilename = viewModel.generateDefaultExportFilename()
                        exportLauncher.launch(defaultFilename)
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = null,
                        tint = ToxicLime,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "EXPORT CONFIG",
                        color = ToxicLime,
                        fontFamily = BitcountPropSingle,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Import Config Button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(HyperCyan.copy(alpha = 0.12f))
                    .border(1.5.dp, HyperCyan, RoundedCornerShape(8.dp))
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        importLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FileUpload,
                        contentDescription = null,
                        tint = HyperCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "IMPORT CONFIG",
                        color = HyperCyan,
                        fontFamily = BitcountPropSingle,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Secondary Quick Actions: Share via and Reset to Defaults
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Share Config
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceElevated)
                    .border(1.dp, BorderStark, RoundedCornerShape(8.dp))
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, viewModel.exportConfigJson())
                            putExtra(Intent.EXTRA_TITLE, "REC Configuration Profile")
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share REC Config JSON"))
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SHARE CONFIG",
                        color = TextSecondary,
                        fontFamily = BitcountPropSingle,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Reset to Defaults
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceElevated)
                    .border(1.dp, BorderStark, RoundedCornerShape(8.dp))
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showResetConfirmDialog = true
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = null,
                        tint = HyperCrimson.copy(alpha = 0.85f),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "RESET DEFAULTS",
                        color = HyperCrimson.copy(alpha = 0.85f),
                        fontFamily = BitcountPropSingle,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Import Preview Dialog
    if (showImportDialog && pendingImportConfig != null) {
        ImportConfigDialog(
            config = pendingImportConfig!!,
            onConfirm = {
                viewModel.applyFullConfig(pendingImportConfig!!)
                showImportDialog = false
                pendingImportConfig = null
                Toast.makeText(context, "Profile applied successfully", Toast.LENGTH_SHORT).show()
            },
            onDismiss = {
                showImportDialog = false
                pendingImportConfig = null
            }
        )
    }

    // Reset Confirmation Dialog
    if (showResetConfirmDialog) {
        Dialog(
            onDismissRequest = { showResetConfirmDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = true)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ObsidianCanvas)
                    .border(1.dp, BorderStark, RoundedCornerShape(14.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Text(
                        text = "RESET ALL SETTINGS?",
                        color = HyperCrimson,
                        fontFamily = BitcountPropSingle,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "This will restore all video, audio, controls, and HUD parameters to factory default values.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SurfaceElevated)
                                .border(1.dp, BorderStark, RoundedCornerShape(8.dp))
                                .clickable { showResetConfirmDialog = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "CANCEL",
                                color = TextSecondary,
                                fontFamily = BitcountPropSingle,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1.2f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(HyperCrimson.copy(alpha = 0.2f))
                                .border(1.5.dp, HyperCrimson, RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.resetConfigToDefaults()
                                    showResetConfirmDialog = false
                                    Toast.makeText(context, "Settings restored to defaults", Toast.LENGTH_SHORT).show()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "RESET DEFAULTS",
                                color = HyperCrimson,
                                fontFamily = BitcountPropSingle,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamSettingsSection(
    viewModel: DashboardViewModel,
    isRecordingActive: Boolean
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val streamConfig by viewModel.streamConfig.collectAsState()
    var isKeyVisible by remember { mutableStateOf(false) }

    // 1. Platform Preset Card
    SectionCard(title = "BROADCAST PLATFORM", titleTag = "PRESET") {
        Text(
            text = "Select your target streaming destination for pre-configured ingest server endpoints and optimized bitrate ceilings.",
            color = TextSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StreamPlatform.entries.forEach { platform ->
                val isSelected = streamConfig.platform == platform
                val accentColor = when (platform) {
                    StreamPlatform.YOUTUBE -> HyperCrimson
                    StreamPlatform.TWITCH -> HyperCyan
                    StreamPlatform.KICK -> ToxicLime
                    StreamPlatform.CUSTOM -> CyberYellow
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) accentColor.copy(alpha = 0.15f) else SurfaceElevated)
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) accentColor else BorderStark,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.updateStreamConfig(
                                streamConfig.copy(
                                    platform = platform,
                                    videoBitrate = platform.defaultVideoBitrate
                                )
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (platform) {
                            StreamPlatform.YOUTUBE -> "YOUTUBE"
                            StreamPlatform.TWITCH -> "TWITCH"
                            StreamPlatform.KICK -> "KICK"
                            StreamPlatform.CUSTOM -> "CUSTOM"
                        },
                        color = if (isSelected) accentColor else TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = BitcountPropSingle,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // 2. Ingest Endpoint & Stream Key
    SectionCard(title = "INGEST & CREDENTIALS", titleTag = "KEYSTORE") {
        if (streamConfig.platform == StreamPlatform.CUSTOM) {
            Text(
                text = "CUSTOM RTMP / RTMPS ENDPOINT:",
                color = TextSecondary,
                fontSize = 11.sp,
                fontFamily = BitcountPropSingle
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = streamConfig.customEndpointUrl,
                onValueChange = { url ->
                    viewModel.updateStreamConfig(streamConfig.copy(customEndpointUrl = url))
                },
                placeholder = { Text("rtmp://your-server.com/live", color = TextMuted, fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = HyperCyan,
                    unfocusedBorderColor = BorderStark,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SERVER ENDPOINT:",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = BitcountPropSingle
                )
                Text(
                    text = streamConfig.activeEndpointUrl,
                    color = HyperCyan,
                    fontSize = 11.sp,
                    fontFamily = BitcountPropSingle,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Stream Key input row
        Text(
            text = "STREAM KEY:",
            color = TextSecondary,
            fontSize = 11.sp,
            fontFamily = BitcountPropSingle
        )
        Spacer(modifier = Modifier.height(6.dp))

        OutlinedTextField(
            value = streamConfig.streamKey,
            onValueChange = { key ->
                viewModel.saveStreamKey(key.trim())
            },
            visualTransformation = if (isKeyVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
            trailingIcon = {
                Icon(
                    imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = "Toggle visibility",
                    tint = TextSecondary,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { isKeyVisible = !isKeyVisible }
                )
            },
            placeholder = { Text("Paste secret stream key...", color = TextMuted, fontSize = 12.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = HyperCyan,
                unfocusedBorderColor = BorderStark,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Quick Credential Actions: [ GET KEY ] and [ PASTE ]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Get Key via Deep link
            if (streamConfig.platform.dashboardUrl.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceElevated)
                        .border(1.dp, BorderStark, RoundedCornerShape(8.dp))
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            try {
                                val browserIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(streamConfig.platform.dashboardUrl))
                                context.startActivity(browserIntent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = HyperCyan, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "GET KEY", color = HyperCyan, fontFamily = BitcountPropSingle, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Paste from clipboard
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceElevated)
                    .border(1.dp, BorderStark, RoundedCornerShape(8.dp))
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                        if (!clipText.isNullOrBlank()) {
                            viewModel.saveStreamKey(clipText)
                            Toast.makeText(context, "Stream key pasted", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Clipboard empty", Toast.LENGTH_SHORT).show()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.ContentPaste, contentDescription = null, tint = ToxicLime, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "PASTE", color = ToxicLime, fontFamily = BitcountPropSingle, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Clear Key
            if (streamConfig.streamKey.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .weight(0.7f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceElevated)
                        .border(1.dp, BorderStark, RoundedCornerShape(8.dp))
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.saveStreamKey("")
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = HyperCrimson, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "CLEAR", color = HyperCrimson, fontFamily = BitcountPropSingle, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Hardware-backed encryption badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(ObsidianCanvas)
                .border(1.dp, BorderStark, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = ToxicLime, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "ENCRYPTED WITH ANDROID KEYSTORE (AES-256-GCM)",
                color = TextSecondary,
                fontSize = 10.sp,
                fontFamily = BitcountPropSingle
            )
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // 3. Broadcast Video & Encoding Controls Card
    SectionCard(title = "INGEST CONTROLS", titleTag = "VIDEO") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "TARGET UPLINK BITRATE:", color = TextSecondary, fontSize = 11.sp, fontFamily = BitcountPropSingle)
            Text(
                text = "${streamConfig.videoBitrate / 1_000_000} MBPS (${streamConfig.videoBitrate / 1_000} KBPS)",
                color = HyperCyan,
                fontSize = 12.sp,
                fontFamily = BitcountPropSingle,
                fontWeight = FontWeight.Bold
            )
        }

        Slider(
            value = streamConfig.videoBitrate.toFloat(),
            onValueChange = { newBitrate ->
                viewModel.updateStreamConfig(streamConfig.copy(videoBitrate = newBitrate.roundToInt()))
            },
            valueRange = 2_000_000f..18_000_000f,
            steps = 31,
            colors = SliderDefaults.colors(thumbColor = HyperCyan, activeTrackColor = HyperCyan)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Enhanced RTMP (HEVC) Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(text = "ENHANCED RTMP (HEVC / H.265)", color = TextPrimary, fontSize = 12.5.sp, fontFamily = BitcountPropSingle, fontWeight = FontWeight.Bold)
                Text(text = "FourCC 'hvc1' encapsulation for 1440p YouTube Live streaming at 40% lower bandwidth.", color = TextSecondary, fontSize = 11.sp, lineHeight = 14.sp)
            }
            Switch(
                checked = streamConfig.useEnhancedHevc && streamConfig.platform.supportsHevc,
                enabled = streamConfig.platform.supportsHevc,
                onCheckedChange = { viewModel.updateStreamConfig(streamConfig.copy(useEnhancedHevc = it)) },
                colors = SwitchDefaults.colors(checkedThumbColor = HyperCyan, checkedTrackColor = HyperCyan.copy(alpha = 0.25f))
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Dual Master Archive Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(text = "DUAL MASTER VAULT ARCHIVE", color = TextPrimary, fontSize = 12.5.sp, fontFamily = BitcountPropSingle, fontWeight = FontWeight.Bold)
                Text(text = "Simultaneously write an uncompressed master MP4 copy to local storage while streaming.", color = TextSecondary, fontSize = 11.sp, lineHeight = 14.sp)
            }
            Switch(
                checked = streamConfig.saveLocalMasterArchive,
                onCheckedChange = { viewModel.updateStreamConfig(streamConfig.copy(saveLocalMasterArchive = it)) },
                colors = SwitchDefaults.colors(checkedThumbColor = ToxicLime, checkedTrackColor = ToxicLime.copy(alpha = 0.25f))
            )
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // 4. Adaptive Bitrate Control (ABR) Card
    SectionCard(title = "ADAPTIVE BITRATE CONTROL", titleTag = "ABR") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(text = "DYNAMIC RATE ADAPTATION", color = TextPrimary, fontSize = 12.5.sp, fontFamily = BitcountPropSingle, fontWeight = FontWeight.Bold)
                Text(text = "Dynamically adjusts hardware MediaCodec bitrate in real-time during network backpressure to prevent stream disconnection.", color = TextSecondary, fontSize = 11.sp, lineHeight = 14.sp)
            }
            Switch(
                checked = streamConfig.enableAbr,
                onCheckedChange = { viewModel.updateStreamConfig(streamConfig.copy(enableAbr = it)) },
                colors = SwitchDefaults.colors(checkedThumbColor = HyperCyan, checkedTrackColor = HyperCyan.copy(alpha = 0.25f))
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
    pillTraceProgress: Float = 0f,
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

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.wrapContentSize()
        ) {
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

            if (pillTraceProgress > 0f) {
                Canvas(
                    modifier = Modifier.size(52.dp, 32.dp)
                ) {
                    val strokeWidth = 2.5.dp.toPx()
                    val cornerRadius = 16.dp.toPx()

                    val inset = strokeWidth / 2f
                    val rectWidth = size.width - strokeWidth
                    val rectHeight = size.height - strokeWidth

                    val fullPath = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = inset,
                                top = inset,
                                right = inset + rectWidth,
                                bottom = inset + rectHeight,
                                cornerRadius = CornerRadius(cornerRadius, cornerRadius)
                            )
                        )
                    }

                    val pathMeasure = PathMeasure()
                    pathMeasure.setPath(fullPath, true)
                    val totalLength = pathMeasure.length

                    if (pillTraceProgress <= 1.0f) {
                        val headDist = pillTraceProgress * totalLength
                        val tailLength = totalLength * 0.45f
                        val startDist = (headDist - tailLength).coerceAtLeast(0f)

                        val segmentPath = Path()
                        pathMeasure.getSegment(startDist, headDist, segmentPath, true)

                        // Outer soft crimson neon glow
                        drawPath(
                            path = segmentPath,
                            color = HyperCrimson.copy(alpha = 0.5f),
                            style = Stroke(width = strokeWidth * 2.2f, cap = StrokeCap.Round)
                        )
                        // Inner intense bright red tracer core
                        drawPath(
                            path = segmentPath,
                            color = Color(0xFFFF5252),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    } else {
                        // Dissolving glow across entire border
                        val fadeAlpha = (1f - (pillTraceProgress - 1.0f) / 0.3f).coerceIn(0f, 1f)
                        drawPath(
                            path = fullPath,
                            color = HyperCrimson.copy(alpha = 0.45f * fadeAlpha),
                            style = Stroke(width = strokeWidth * 2f, cap = StrokeCap.Round)
                        )
                        drawPath(
                            path = fullPath,
                            color = Color(0xFFFF5252).copy(alpha = 0.9f * fadeAlpha),
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                }
            }
        }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_pixel_lightning),
                        contentDescription = null,
                        tint = HyperCrimson,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "AUTO-TUNE BITRATE?",
                        fontFamily = BitcountPropSingle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = HyperCrimson,
                        letterSpacing = 0.5.sp
                    )
                }

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
                            Icon(
                                painter = painterResource(id = R.drawable.ic_pixel_check),
                                contentDescription = "Checked",
                                tint = ObsidianCanvas,
                                modifier = Modifier.size(12.dp)
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

@Composable
private fun GamingOptimizationDialog(
    onDismiss: (doNotAskAgain: Boolean) -> Unit,
    onConfigure: (doNotAskAgain: Boolean) -> Unit
) {
    var doNotAskAgain by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { onDismiss(doNotAskAgain) },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianCanvas.copy(alpha = 0.75f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss(doNotAskAgain) },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .background(SurfaceRaised, RoundedCornerShape(12.dp))
                    .border(1.5.dp, ToxicLime, RoundedCornerShape(12.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_pixel_gamepad),
                            contentDescription = null,
                            tint = ToxicLime,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SMART GAME OPTIMIZATION",
                            fontFamily = BitcountPropSingle,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = ToxicLime,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Would you like REC to automatically detect when games launch, lock landscape canvas, enforce a 60 FPS cap on 120Hz panels, and minimize background overhead?",
                        fontFamily = BitcountPropSingle,
                        fontSize = 12.sp,
                        color = TextPrimary,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Checkbox: Do not show again
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { doNotAskAgain = !doNotAskAgain }
                            .padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(if (doNotAskAgain) ToxicLime else SurfaceElevated, RoundedCornerShape(4.dp))
                                .border(1.5.dp, if (doNotAskAgain) ToxicLime else BorderStark, RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (doNotAskAgain) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_pixel_check),
                                    contentDescription = "Checked",
                                    tint = ObsidianCanvas,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Do not show again",
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
                                .clickable { onDismiss(doNotAskAgain) }
                                .padding(horizontal = 14.dp, vertical = 9.dp)
                        ) {
                            Text(
                                text = "LATER",
                                fontFamily = BitcountPropSingle,
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Box(
                            modifier = Modifier
                                .background(TextPrimary, RoundedCornerShape(6.dp))
                                .border(1.5.dp, ToxicLime, RoundedCornerShape(6.dp))
                                .clickable { onConfigure(doNotAskAgain) }
                                .padding(horizontal = 16.dp, vertical = 9.dp)
                        ) {
                            Text(
                                text = "YES, CONFIGURE",
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
}
