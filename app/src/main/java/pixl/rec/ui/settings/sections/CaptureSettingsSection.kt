package pixl.rec.ui.settings.sections

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pixl.rec.R
import pixl.rec.core.engine.CodecProbe
import pixl.rec.core.engine.ResolutionCalculator
import pixl.rec.core.model.QuickPreset
import pixl.rec.core.model.RecordingOrientation
import pixl.rec.core.storage.ConfigPreferences
import pixl.rec.ui.components.SectionCard
import pixl.rec.ui.components.SlidingPillSelector
import pixl.rec.ui.dashboard.DashboardUiState
import pixl.rec.ui.dashboard.DashboardViewModel
import pixl.rec.ui.settings.components.AutoTuneBitrateDialog
import pixl.rec.ui.settings.components.QuickPresetDeck
import pixl.rec.ui.settings.components.ResolutionPreviewCanvas
import pixl.rec.ui.theme.BitcountPropSingle
import pixl.rec.ui.theme.BorderStark
import pixl.rec.ui.theme.HyperCrimson
import pixl.rec.ui.theme.SurfaceElevated
import pixl.rec.ui.theme.TextPrimary
import pixl.rec.ui.theme.TextSecondary
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureSettingsSection(
    uiState: DashboardUiState,
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

    // 2. Resolution & Orientation Deck with Preview Canvas & Dropdown
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
                textStyle = TextStyle(
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
}
