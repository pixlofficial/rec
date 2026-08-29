package dev.pixl.recorder.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pixl.recorder.core.model.AudioSource
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

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Active Recording Hero / Standby Card
            HeroRecordingCard(
                recorderState = recorderState,
                uiState = uiState,
                onStartClick = onRequestRecordPermission,
                onStopClick = { viewModel.stopRecording() },
                onPauseClick = { viewModel.pauseRecording() },
                onResumeClick = { viewModel.resumeRecording() }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Hardware Codec & FPS Settings Grid
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
        // App Identity
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .background(CyberYellow, RoundedCornerShape(6.dp))
                    .border(2.dp, BorderHighlight, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "REC",
                    color = TextInverse,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "BY PIXL",
                color = TextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        // Hardware Refresh Rate & Storage Badges
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val refreshRate = uiState.capabilities?.display?.currentRefreshRate?.roundToInt() ?: 60
            TelemetryBadge(label = "DISPLAY", value = "$refreshRate HZ", accentColor = ToxicLime)
            val storageFormatted = StorageCalculator.formatBytes(uiState.availableStorageBytes)
            TelemetryBadge(label = "FREE", value = storageFormatted, accentColor = CyberYellow)
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
                    animation = tween(600),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "RecordDotAlpha"
            )

            BrutalistCard(
                title = if (isPaused) "RECORDING PAUSED" else "ACTIVE ZERO-COPY STREAM",
                titleTag = "LIVE",
                tagColor = if (isPaused) CyberYellow else HyperCrimson,
                tagTextColor = TextPrimary,
                borderColor = if (isPaused) CyberYellow else HyperCrimson
            ) {
                // Giant Monospace Timer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .alpha(if (isPaused) 1f else pulseAlpha)
                            .background(if (isPaused) CyberYellow else HyperCrimson, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = StorageCalculator.formatDuration(durationMs),
                        fontSize = 38.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Realtime Telemetry Pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TelemetryBadge(
                        label = "FPS",
                        value = String.format(Locale.US, "%.1f", currentFps),
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

                // Stepped VU Meters
                SteppedVuMeter(label = "Internal Game Audio", dbLevel = gameDb)
                Spacer(modifier = Modifier.height(8.dp))
                SteppedVuMeter(label = "Microphone Audio", dbLevel = micDb)

                Spacer(modifier = Modifier.height(20.dp))

                // Action Controls
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
                        modifier = Modifier.weight(1.5f),
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
                titleTag = "PREPARING",
                tagColor = CyberYellow,
                borderColor = CyberYellow
            ) {
                Text(
                    text = "Initializing zero-copy GPU Surface & MediaCodec...",
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
            }
        }
        is RecorderState.Finished -> {
            BrutalistCard(
                title = "RECORDING SAVED",
                titleTag = "SUCCESS",
                tagColor = ToxicLime,
                borderColor = ToxicLime
            ) {
                Text(
                    text = "MP4 committed directly to Gallery:",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${recorderState.formattedSize} • ${StorageCalculator.formatDuration(recorderState.durationMs)}",
                    color = ToxicLime,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                BrutalistButton(
                    text = "START NEW RECORDING",
                    onClick = onStartClick,
                    variant = BrutalistButtonVariant.PRIMARY
                )
            }
        }
        else -> {
            // Idle Standby
            BrutalistCard(
                title = "ZERO-COPY ENGINE",
                titleTag = "STANDBY",
                tagColor = ToxicLime,
                borderColor = BorderStark
            ) {
                Text(
                    text = "Hardware accelerated 120+ FPS screen capture with nano-PTS audio synchronization and ~0% video CPU load.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
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

    Spacer(modifier = Modifier.height(16.dp))

    // 2. Video Codec Selection
    BrutalistCard(title = "VIDEO CODEC", titleTag = config.videoCodec.name) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VideoCodec.entries.forEach { codec ->
                val isSelected = config.videoCodec == codec
                val isHardware = capabilities?.codecs?.get(codec)?.isHardwareAccelerated == true
                val isAvailable = when (codec) {
                    VideoCodec.HEVC -> isHardware
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

    Spacer(modifier = Modifier.height(16.dp))

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

    Spacer(modifier = Modifier.height(16.dp))

    // 4. Video Bitrate
    BrutalistCard(title = "TARGET BITRATE", titleTag = "${config.videoBitrate / 1_000_000} MBPS") {
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
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = text.uppercase(),
            color = textColor,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black
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
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(CyberYellow, CircleShape)
            )
        }
    }
}
