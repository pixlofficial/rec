@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package pixl.rec.ui.settings.sections

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pixl.rec.R
import pixl.rec.core.game.GameDetector
import pixl.rec.core.model.PillRecallGesture
import pixl.rec.core.model.RecordingConfig
import pixl.rec.core.storage.ConfigSerializer
import pixl.rec.ui.components.SectionCard
import pixl.rec.ui.components.SlidingPillSelector
import pixl.rec.ui.dashboard.DashboardUiState
import pixl.rec.ui.dashboard.DashboardViewModel
import pixl.rec.ui.settings.components.ImportConfigDialog
import pixl.rec.ui.settings.components.SettingsSwitch
import pixl.rec.ui.settings.components.SettingsTag
import pixl.rec.ui.theme.BitcountPropSingle
import pixl.rec.ui.theme.BorderHighlight
import pixl.rec.ui.theme.BorderStark
import pixl.rec.ui.theme.HyperCrimson
import pixl.rec.ui.theme.HyperCyan
import pixl.rec.ui.theme.ObsidianCanvas
import pixl.rec.ui.theme.SurfaceElevated
import pixl.rec.ui.theme.TextMuted
import pixl.rec.ui.theme.TextPrimary
import pixl.rec.ui.theme.TextSecondary
import pixl.rec.ui.theme.ToxicLime

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GeneralSettingsSection(
    uiState: DashboardUiState,
    viewModel: DashboardViewModel,
    isStreamingEnabled: Boolean,
    isRecordingActive: Boolean,
    smartGameOptRequester: BringIntoViewRequester,
    cardShineProgress: Animatable<Float, *>,
    pillTraceProgress: Animatable<Float, *>,
    onRequestUsagePermission: () -> Unit,
    onNavigateToHudStudio: () -> Unit = {},
    onNavigateToStream: () -> Unit = {}
) {
    val config = uiState.config
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

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
                val imported = result.getOrThrow()
                pendingImportConfig = imported
                showImportDialog = true
            }.onFailure { e ->
                Toast.makeText(context, "Invalid config: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // 1. Live Streaming Studio Master Disarm Switch
    SectionCard(title = "LIVE STREAMING", titleTag = "STUDIO") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = "ENABLE LIVE STREAMING",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontFamily = BitcountPropSingle,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isStreamingEnabled) {
                        "Unlocks the Stream tab and live broadcasting to YouTube, Twitch, Kick, and RTMP."
                    } else {
                        "Hides all streaming tabs and overlays. REC operates as a 100% offline, private recorder."
                    },
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

        if (isStreamingEnabled) {
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(HyperCyan.copy(alpha = 0.1f))
                    .border(1.dp, HyperCyan.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    .clickable { onNavigateToStream() }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "CONFIGURE BROADCAST DESTINATIONS →",
                        color = HyperCyan,
                        fontSize = 11.sp,
                        fontFamily = BitcountPropSingle,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "STREAM TAB",
                        color = HyperCyan.copy(alpha = 0.8f),
                        fontSize = 10.sp,
                        fontFamily = BitcountPropSingle
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // 2. Overlay & Gesture Controls (Folded from former CONTROLS)
    SectionCard(
        title = "OVERLAY & GESTURE CONTROLS",
        titleTag = if (config.alwaysOnFloatingPill) "STANDBY ON" else if (config.showFloatingPill) "REC PILL ON" else "CLEAN CANVAS"
    ) {
        // Countdown
        Text(
            text = "RECORDING COUNTDOWN",
            color = TextSecondary,
            fontSize = 11.sp,
            fontFamily = BitcountPropSingle,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        SlidingPillSelector(
            items = listOf(0, 3, 5),
            selectedItem = config.countdownSeconds,
            itemIcon = { if (it == 0) R.drawable.ic_pixel_none else null },
            itemLabel = { if (it == 0) "NONE" else "${it}s" },
            enabled = !isRecordingActive,
            onItemSelected = { viewModel.updateCountdownSeconds(it) }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // HUD Theme Studio Hero Card
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

        // Standby Floating Pill Toggle
        SettingsSwitch(
            iconRes = if (config.alwaysOnFloatingPill) R.drawable.ic_pixel_eye else R.drawable.ic_pixel_eye_off,
            title = "Standby Floating Pill Overlay",
            subtitle = if (config.alwaysOnFloatingPill) "Edge-docked bubble & radial HUD menu active on screen" else "Standby bubble disabled",
            checked = config.alwaysOnFloatingPill,
            enabled = !isRecordingActive,
            onCheckedChange = { viewModel.toggleAlwaysOnFloatingPill(it) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Live Recording Floating Pill Toggle
        SettingsSwitch(
            iconRes = if (config.showFloatingPill) R.drawable.ic_pixel_eye else R.drawable.ic_pixel_eye_off,
            title = "Live Recording Pill Overlay",
            subtitle = if (config.showFloatingPill) "On-screen pill enabled during recording" else "Clean Canvas: Pill hidden during recording",
            checked = config.showFloatingPill,
            enabled = !isRecordingActive,
            onCheckedChange = { viewModel.toggleFloatingPill(it) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Auto-Hide Pill into Invisible Ghost
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

        // Shake to Stop Gesture
        SettingsSwitch(
            iconRes = R.drawable.ic_pixel_vibrate,
            title = "Shake to Stop",
            subtitle = "Quick wrist flick stops and saves recording",
            checked = config.shakeToStop,
            enabled = !isRecordingActive,
            onCheckedChange = { viewModel.toggleShakeToStop(it) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Stop on Screen Off
        SettingsSwitch(
            iconRes = R.drawable.ic_pixel_power,
            title = "Stop on Screen Off",
            subtitle = "Locks video cleanly when power button is pressed",
            checked = config.stopOnScreenOff,
            enabled = !isRecordingActive,
            onCheckedChange = { viewModel.toggleStopOnScreenOff(it) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Smart Game Optimization
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

    Spacer(modifier = Modifier.height(14.dp))

    // 4. Profile Backup & Restore Card
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
            properties = DialogProperties(dismissOnClickOutside = true)
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
