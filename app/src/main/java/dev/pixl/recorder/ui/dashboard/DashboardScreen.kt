package dev.pixl.recorder.ui.dashboard

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pixl.recorder.R
import dev.pixl.recorder.core.model.AudioSource
import dev.pixl.recorder.core.model.CaptureTarget
import dev.pixl.recorder.core.model.PillRecallGesture
import dev.pixl.recorder.core.model.RecorderState
import dev.pixl.recorder.core.model.VideoCodec
import dev.pixl.recorder.core.storage.StorageCalculator
import dev.pixl.recorder.ui.components.BrutalistButton
import dev.pixl.recorder.ui.components.BrutalistButtonVariant
import dev.pixl.recorder.ui.components.BrutalistCard
import dev.pixl.recorder.ui.components.SteppedVuMeter
import dev.pixl.recorder.ui.components.TelemetryBadge
import dev.pixl.recorder.ui.theme.BorderHighlight
import dev.pixl.recorder.ui.theme.BorderStark
import dev.pixl.recorder.ui.theme.CyberYellow
import dev.pixl.recorder.ui.theme.Handjet
import dev.pixl.recorder.ui.theme.HyperCrimson
import dev.pixl.recorder.ui.theme.HyperCyan
import dev.pixl.recorder.ui.theme.LexendTera
import dev.pixl.recorder.ui.theme.ObsidianCanvas
import dev.pixl.recorder.ui.theme.SurfaceElevated
import dev.pixl.recorder.ui.theme.TextInverse
import dev.pixl.recorder.ui.theme.TextMuted
import dev.pixl.recorder.ui.theme.TextPrimary
import dev.pixl.recorder.ui.theme.TextSecondary
import dev.pixl.recorder.ui.theme.ToxicLime
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onRequestRecordPermission: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val recorderState by viewModel.recorderState.collectAsState()

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

            // 1. Top Bar Header
            HeaderBar(uiState = uiState)

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Hardware Capabilities & SoC Status
            HardwareSpecsCard(uiState = uiState)

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Hero Recording / Live Telemetry Card
            HeroRecordingCard(
                recorderState = recorderState,
                uiState = uiState,
                onStartClick = onRequestRecordPermission,
                onStopClick = { viewModel.stopRecording() },
                onPauseClick = { viewModel.pauseRecording() },
                onResumeClick = { viewModel.resumeRecording() }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Overlay & Clean Canvas Controls
            OverlayAndCleanCanvasSection(
                uiState = uiState,
                isRecordingActive = recorderState is RecorderState.Recording || recorderState is RecorderState.Paused,
                onToggleFloatingPill = { viewModel.toggleFloatingPill(it) },
                onToggleAutoHide = { viewModel.toggleAutoHidePill(it) },
                onSelectGesture = { viewModel.updatePillRecallGesture(it) },
                onToggleShake = { viewModel.toggleShakeToStop(it) },
                onToggleScreenOff = { viewModel.toggleStopOnScreenOff(it) },
                onSelectTarget = { viewModel.updateCaptureTarget(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 5. Codec, Resolution & Framerate Deck
            ConfigSection(
                uiState = uiState,
                isRecordingActive = recorderState is RecorderState.Recording || recorderState is RecorderState.Paused,
                onFramerateSelect = { viewModel.updateFramerate(it) },
                onResolutionSelect = { w, h -> viewModel.updateResolution(w, h) },
                onCodecSelect = { viewModel.updateVideoCodec(it) },
                onAudioSourceSelect = { viewModel.updateAudioSource(it) },
                onBitrateSelect = { viewModel.updateVideoBitrate(it) }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun HeaderBar(uiState: DashboardUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // App Identity Brand Badge (Pure Glowing Red Core Logo)
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_logo_core),
                contentDescription = "REC Logo Core",
                modifier = Modifier.size(38.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = "REC",
                    color = Color.White,
                    fontSize = 21.sp,
                    fontFamily = Handjet,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "by PixL",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontFamily = Handjet,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // Compact Header Badges (Single Non-Wrapping Row)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val refreshRate = uiState.capabilities?.display?.currentRefreshRate?.roundToInt() ?: 60
            TelemetryBadge(label = "FPS", value = "$refreshRate HZ", accentColor = ToxicLime, isHighlighted = true)
            val storageFormatted = StorageCalculator.formatBytes(uiState.availableStorageBytes)
            TelemetryBadge(label = "FREE", value = storageFormatted, accentColor = CyberYellow)
        }
    }
}

@Composable
private fun HardwareSpecsCard(uiState: DashboardUiState) {
    val display = uiState.capabilities?.display
    val width = display?.physicalWidth ?: 720
    val height = display?.physicalHeight ?: 1560
    val refreshRate = display?.currentRefreshRate?.roundToInt() ?: 60
    val hevcHw = uiState.capabilities?.isHevcHardwareSupported == true

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceElevated, RoundedCornerShape(10.dp))
            .border(1.5.dp, BorderStark, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
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
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = ToxicLime,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "HARDWARE ENGINE ACTIVE",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontFamily = Handjet,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${width}x${height} • ${refreshRate}Hz AMOLED • ${if (hevcHw) "HEVC ASIC" else "AVC ASIC"}",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        fontFamily = Handjet,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Box(
                modifier = Modifier
                    .background(ToxicLime.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .border(1.dp, ToxicLime, RoundedCornerShape(4.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "0% CPU",
                    color = ToxicLime,
                    fontSize = 12.sp,
                    fontFamily = Handjet,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun HeroRecordingCard(
    recorderState: RecorderState,
    uiState: DashboardUiState,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit
) {
    when (recorderState) {
        is RecorderState.Recording, is RecorderState.Paused -> {
            val isPaused = recorderState is RecorderState.Paused
            val durationMs = if (recorderState is RecorderState.Recording) recorderState.durationMs else (recorderState as RecorderState.Paused).durationMs
            val bytes = if (recorderState is RecorderState.Recording) recorderState.bytesWritten else (recorderState as RecorderState.Paused).bytesWritten
            val currentFps = if (recorderState is RecorderState.Recording) recorderState.currentFps else 0f
            val gameDb = if (recorderState is RecorderState.Recording) recorderState.gameAudioDb else -60f
            val micDb = if (recorderState is RecorderState.Recording) recorderState.micAudioDb else -60f

            val pulseTransition = rememberInfiniteTransition(label = "Pulse")
            val pulseAlpha by pulseTransition.animateFloat(
                initialValue = 1f,
                targetValue = 0.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "RecordDotAlpha"
            )

            BrutalistCard(
                title = if (isPaused) "RECORDING PAUSED" else "STREAMING TO STORAGE",
                titleTag = "LIVE",
                tagColor = if (isPaused) CyberYellow else HyperCrimson,
                tagTextColor = TextPrimary,
                borderColor = if (isPaused) CyberYellow else HyperCrimson
            ) {
                // Giant Dot-Matrix Digital Timer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .alpha(if (isPaused) 1f else pulseAlpha)
                            .background(if (isPaused) CyberYellow else HyperCrimson, CircleShape)
                            .border(2.dp, BorderHighlight, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = StorageCalculator.formatDuration(durationMs),
                        fontSize = 46.sp,
                        fontFamily = Handjet,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Realtime Telemetry Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TelemetryBadge(
                        label = "FPS",
                        value = if (currentFps > 0f) String.format(Locale.US, "%.1f", currentFps) else "${uiState.config.framerate}.0",
                        accentColor = ToxicLime,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryBadge(
                        label = "DATA",
                        value = StorageCalculator.formatBytes(bytes),
                        accentColor = HyperCyan,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryBadge(
                        label = "CODEC",
                        value = uiState.config.videoCodec.name,
                        accentColor = CyberYellow,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Chunky Stepped LED VU Visualizers
                SteppedVuMeter(label = "Internal Game Audio (48kHz)", dbLevel = gameDb)
                Spacer(modifier = Modifier.height(10.dp))
                SteppedVuMeter(label = "Microphone Audio (Stereo)", dbLevel = micDb)

                Spacer(modifier = Modifier.height(20.dp))

                // Tactile Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BrutalistButton(
                        text = if (isPaused) "Resume" else "Pause",
                        onClick = if (isPaused) onResumeClick else onPauseClick,
                        variant = BrutalistButtonVariant.SURFACE,
                        modifier = Modifier.weight(1f),
                        leadingIcon = {
                            Icon(
                                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                    BrutalistButton(
                        text = "Stop",
                        onClick = onStopClick,
                        variant = BrutalistButtonVariant.DANGER,
                        modifier = Modifier.weight(1.4f),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }
        }
        is RecorderState.Preparing -> {
            BrutalistCard(
                title = "HARDWARE ENGINE",
                titleTag = "INIT",
                tagColor = CyberYellow,
                borderColor = CyberYellow
            ) {
                Text(
                    text = "Allocating zero-copy GraphicBuffer surface & MediaCodec...",
                    color = TextSecondary,
                    fontFamily = Handjet,
                    fontSize = 15.sp
                )
            }
        }
        is RecorderState.Finished -> {
            BrutalistCard(
                title = "RECORDING SAVED",
                titleTag = "GALLERY READY",
                tagColor = ToxicLime,
                borderColor = ToxicLime
            ) {
                Text(
                    text = "MP4 committed directly to Movies/PixL-REC:",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontFamily = Handjet
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${recorderState.formattedSize} • ${StorageCalculator.formatDuration(recorderState.durationMs)}",
                    color = ToxicLime,
                    fontFamily = Handjet,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                BrutalistButton(
                    text = "RECORD AGAIN",
                    onClick = onStartClick,
                    variant = BrutalistButtonVariant.PRIMARY,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.FiberManualRecord,
                            contentDescription = null,
                            tint = TextInverse,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }
        else -> {
            // Idle Standby Hero Card
            BrutalistCard(
                title = "ZERO-COPY RECORDER",
                titleTag = "STANDBY",
                tagColor = ToxicLime,
                borderColor = BorderStark
            ) {
                Text(
                    text = "Direct GPU ──► MediaCodec hardware pipeline. Captures up to 120 FPS with nanosecond audio synchronization and zero CPU pixel copying.",
                    color = TextSecondary,
                    fontFamily = Handjet,
                    fontSize = 15.sp,
                    lineHeight = 19.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Storage estimation bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "ESTIMATED RATE:",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        fontFamily = Handjet,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f MB/MIN", uiState.config.estimatedMbPerMinute),
                        color = CyberYellow,
                        fontSize = 15.sp,
                        fontFamily = Handjet,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                BrutalistButton(
                    text = "START RECORDING",
                    onClick = onStartClick,
                    variant = BrutalistButtonVariant.PRIMARY,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.FiberManualRecord,
                            contentDescription = null,
                            tint = TextInverse,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverlayAndCleanCanvasSection(
    uiState: DashboardUiState,
    isRecordingActive: Boolean,
    onToggleFloatingPill: (Boolean) -> Unit,
    onToggleAutoHide: (Boolean) -> Unit,
    onSelectGesture: (PillRecallGesture) -> Unit,
    onToggleShake: (Boolean) -> Unit,
    onToggleScreenOff: (Boolean) -> Unit,
    onSelectTarget: (CaptureTarget) -> Unit
) {
    val config = uiState.config
    val isAndroid14Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    BrutalistCard(
        title = "CLEAN CANVAS & OVERLAY CONTROLS",
        titleTag = if (!config.showFloatingPill) "CLEAN CANVAS" else if (config.autoHidePill) "INVISIBLE GHOST" else "PILL ACTIVE",
        tagColor = if (!config.showFloatingPill) ToxicLime else if (config.autoHidePill) HyperCyan else CyberYellow,
        borderColor = BorderStark
    ) {
        // 1. Solution 1: Floating Pill Toggle (Clean Canvas Mode)
        SwitchRow(
            icon = if (config.showFloatingPill) Icons.Default.Visibility else Icons.Default.VisibilityOff,
            title = "Floating Game Pill Overlay",
            subtitle = if (config.showFloatingPill) "On-screen pill enabled during recording" else "Clean Canvas: Pill hidden (control via Shake/Notification)",
            checked = config.showFloatingPill,
            enabled = !isRecordingActive,
            onCheckedChange = onToggleFloatingPill
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Solution 2: Auto-Hide Pill into Invisible Ghost with Gesture Recall
        AnimatedVisibility(visible = config.showFloatingPill) {
            Column {
                SwitchRow(
                    icon = Icons.Default.Gesture,
                    title = "Auto-Hide Pill to Invisible",
                    subtitle = "Pill disappears completely after 2s; recall anytime with gesture",
                    checked = config.autoHidePill,
                    enabled = !isRecordingActive,
                    onCheckedChange = onToggleAutoHide
                )

                if (config.autoHidePill) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "RECALL GESTURE",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontFamily = Handjet,
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
                            SelectableTag(
                                text = gesture.displayName,
                                isSelected = isSelected,
                                enabled = !isRecordingActive,
                                onClick = { onSelectGesture(gesture) }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        // 3. Shake to Stop Gesture
        SwitchRow(
            icon = Icons.Default.Vibration,
            title = "Shake to Stop",
            subtitle = "Quick wrist flick stops and saves recording",
            checked = config.shakeToStop,
            enabled = !isRecordingActive,
            onCheckedChange = onToggleShake
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 4. Screen Off to Stop
        SwitchRow(
            icon = Icons.Default.PowerSettingsNew,
            title = "Screen Off to Stop",
            subtitle = "Pressing power button cleanly finalizes recording",
            checked = config.stopOnScreenOff,
            enabled = !isRecordingActive,
            onCheckedChange = onToggleScreenOff
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 5. Solution 3: Single App Capture (Android 14+)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isAndroid14Plus) SurfaceElevated else ObsidianCanvas, RoundedCornerShape(8.dp))
                .border(1.5.dp, if (isAndroid14Plus) BorderHighlight else BorderStark, RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            tint = if (isAndroid14Plus) ToxicLime else TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SINGLE-APP ISOLATED CAPTURE",
                            color = if (isAndroid14Plus) TextPrimary else TextMuted,
                            fontSize = 14.sp,
                            fontFamily = Handjet,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (!isAndroid14Plus) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF282834), RoundedCornerShape(4.dp))
                                .border(1.dp, Color(0xFF404054), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "REQUIRES ANDROID 14+",
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontFamily = Handjet,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isAndroid14Plus)
                        "Hardware-isolates target game window without capturing overlays, notifications, or status bar."
                    else
                        "Android 14 (API 34+) feature that isolates target game window from overlays. On your Android 11 device, use Clean Canvas mode or Invisible Pill.",
                    color = if (isAndroid14Plus) TextSecondary else TextMuted,
                    fontSize = 14.sp,
                    fontFamily = Handjet,
                    lineHeight = 17.sp
                )

                if (isAndroid14Plus) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CaptureTarget.entries.forEach { target ->
                            val isSelected = config.captureTarget == target
                            SelectableTag(
                                text = target.displayName,
                                isSelected = isSelected,
                                enabled = !isRecordingActive,
                                onClick = { onSelectTarget(target) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
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
            .border(1.5.dp, if (checked) CyberYellow else BorderStark, RoundedCornerShape(8.dp))
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
                tint = if (checked) CyberYellow else TextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    color = if (checked) TextPrimary else TextSecondary,
                    fontSize = 16.sp,
                    fontFamily = Handjet,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    color = TextMuted,
                    fontSize = 13.sp,
                    fontFamily = Handjet,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 16.sp,
                    letterSpacing = 0.3.sp
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
                checkedTrackColor = CyberYellow,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = ObsidianCanvas
            )
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfigSection(
    uiState: DashboardUiState,
    isRecordingActive: Boolean,
    onFramerateSelect: (Int) -> Unit,
    onResolutionSelect: (Int, Int) -> Unit,
    onCodecSelect: (VideoCodec) -> Unit,
    onAudioSourceSelect: (AudioSource) -> Unit,
    onBitrateSelect: (Int) -> Unit
) {
    val capabilities = uiState.capabilities
    val config = uiState.config

    // 1. Framerate Selection
    BrutalistCard(title = "CAPTURE FRAMERATE", titleTag = "${config.framerate} FPS") {
        val supportedFps = capabilities?.codecs?.get(config.videoCodec)?.supportedFramerates ?: listOf(30, 60, 90, 120)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(60, 90, 120, 144).forEach { fps ->
                val isSelected = config.framerate == fps
                val isAvailable = supportedFps.contains(fps) || fps <= (capabilities?.maxHardwareFps ?: 60)
                SelectableTag(
                    text = "$fps FPS",
                    isSelected = isSelected,
                    enabled = !isRecordingActive && isAvailable,
                    onClick = { onFramerateSelect(fps) }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // 2. Video Codec Selection
    BrutalistCard(title = "HARDWARE CODEC", titleTag = config.videoCodec.name) {
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
                SelectableTag(
                    text = "${codec.displayName}${if (isHardware) " (HW)" else ""}",
                    isSelected = isSelected,
                    enabled = !isRecordingActive && isAvailable,
                    onClick = { onCodecSelect(codec) }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // 3. Audio Source Routing
    BrutalistCard(title = "AUDIO ROUTING", titleTag = config.audioSource.name) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AudioSource.entries.forEach { source ->
                val isSelected = config.audioSource == source
                SelectableRow(
                    text = source.displayName,
                    isSelected = isSelected,
                    enabled = !isRecordingActive,
                    onClick = { onAudioSourceSelect(source) }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // 4. Target Video Bitrate
    BrutalistCard(title = "ENCODING BITRATE", titleTag = "${config.videoBitrate / 1_000_000} MBPS") {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(20, 35, 50, 80, 100).forEach { mbps ->
                val isSelected = config.videoBitrate == mbps * 1_000_000
                SelectableTag(
                    text = "$mbps Mbps",
                    isSelected = isSelected,
                    enabled = !isRecordingActive,
                    onClick = { onBitrateSelect(mbps) }
                )
            }
        }
    }
}

@Composable
private fun SelectableTag(
    text: String,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) CyberYellow else SurfaceElevated
    val border = if (isSelected) BorderHighlight else BorderStark
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
            fontSize = 14.sp,
            fontFamily = Handjet,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun SelectableRow(
    text: String,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) SurfaceElevated else ObsidianCanvas
    val border = if (isSelected) CyberYellow else BorderStark

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(8.dp))
            .border(1.5.dp, border, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = text,
            color = if (isSelected) TextPrimary else TextSecondary,
            fontSize = 15.sp,
            fontFamily = Handjet,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            letterSpacing = 0.5.sp
        )
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(CyberYellow, CircleShape)
                    .border(1.dp, BorderHighlight, CircleShape)
            )
        }
    }
}
