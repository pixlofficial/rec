package pixl.rec.ui.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pixl.rec.R
import pixl.rec.core.model.RecorderState
import pixl.rec.core.storage.StorageCalculator
import pixl.rec.ui.components.ActionButton
import pixl.rec.ui.components.ActionButtonVariant
import pixl.rec.ui.components.SectionCard
import pixl.rec.ui.components.SteppedVuMeter
import pixl.rec.ui.components.TelemetryBadge
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
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onRequestRecordPermission: () -> Unit,
    onNavigateToSettings: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val telemetry by viewModel.telemetry.collectAsState()
    val isRecording by viewModel.isRecordingActive.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.startStandbyMicMonitor()
                Lifecycle.Event.ON_PAUSE -> viewModel.stopStandbyMicMonitor()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopStandbyMicMonitor()
        }
    }

    val scrollState = rememberScrollState()

    // Smooth Micro-Entrance Transition from Native Splash
    val contentAlpha = remember { Animatable(0f) }
    val contentOffsetY = remember { Animatable(20f) }

    LaunchedEffect(Unit) {
        launch {
            contentAlpha.animateTo(1f, animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing))
        }
        launch {
            contentOffsetY.animateTo(0f, animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .graphicsLayer {
                alpha = contentAlpha.value
                translationY = contentOffsetY.value * density
            }
            .verticalScroll(scrollState)
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        // 1. Top Bar Header
        HeaderBar(uiState = uiState)

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Hardware Capabilities & SoC Status
        HardwareSpecsCard(uiState = uiState, telemetry = telemetry)

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Hero Recording Card
        HeroRecordingCard(
            viewModel = viewModel,
            uiState = uiState,
            onStartClick = onRequestRecordPermission,
            onStopClick = { viewModel.stopRecording() },
            onPauseClick = { viewModel.pauseRecording() },
            onResumeClick = { viewModel.resumeRecording() }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 4. Live Audio VU Visualizer (Internal & Mic)
        LiveAudioVuMeterCard(telemetry = telemetry, isRecording = isRecording)

        Spacer(modifier = Modifier.height(12.dp))

        // 5. Hardware Telemetry Quad Deck (CPU, Thermals, Write Speed, Frame Rate)
        TelemetryQuadDeck(telemetry = telemetry, uiState = uiState)

        Spacer(modifier = Modifier.height(12.dp))

        // 6. Live Stream Health & FPS Oscilloscope Graph
        StreamHealthGraphCard(telemetry = telemetry)

        Spacer(modifier = Modifier.height(12.dp))

        // 7. Active Profile Chips (Quick summary with 1-tap jump to Config)
        ActiveProfileChipsRow(
            uiState = uiState,
            onNavigateToSettings = onNavigateToSettings
        )

        Spacer(modifier = Modifier.height(116.dp))
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

            Column(
                verticalArrangement = Arrangement.spacedBy((-2).dp)
            ) {
                Text(
                    text = "REC",
                    color = Color.White,
                    fontSize = 26.sp,
                    lineHeight = 22.sp,
                    fontFamily = BitcountPropSingle,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = TextSecondary)) {
                            append("by ")
                        }
                        withStyle(SpanStyle(color = Color.White)) {
                            append("PixL")
                        }
                    },
                    fontSize = 13.sp,
                    lineHeight = 13.sp,
                    fontFamily = BitcountPropSingle,
                    fontWeight = FontWeight.Bold,
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
private fun HardwareSpecsCard(uiState: DashboardUiState, telemetry: TelemetryData) {
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
                    painter = painterResource(id = R.drawable.ic_pixel_chip),
                    contentDescription = "Hardware Engine",
                    tint = ToxicLime,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "HARDWARE ENGINE ACTIVE",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontFamily = BitcountPropSingle,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${width}x${height} • ${refreshRate}Hz AMOLED • ${if (hevcHw) "HEVC ASIC" else "AVC ASIC"}",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = BitcountPropSingle,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        softWrap = false
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
                    text = String.format(Locale.US, "%.1f%% CPU", telemetry.cpuUsagePercent),
                    color = ToxicLime,
                    fontSize = 11.sp,
                    fontFamily = BitcountPropSingle,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun HeroRecordingCard(
    viewModel: DashboardViewModel,
    uiState: DashboardUiState,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit
) {
    val recorderState by viewModel.recorderState.collectAsState()

    when (val state = recorderState) {
        is RecorderState.Recording, is RecorderState.Paused -> {
            val isPaused = state is RecorderState.Paused
            val durationMs = if (state is RecorderState.Recording) state.durationMs else (state as RecorderState.Paused).durationMs
            val bytes = if (state is RecorderState.Recording) state.bytesWritten else (state as RecorderState.Paused).bytesWritten
            val currentFps = if (state is RecorderState.Recording) state.currentFps else 0f

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

            SectionCard(
                title = if (isPaused) "RECORDING PAUSED" else "STREAMING TO STORAGE",
                titleTag = "LIVE",
                tagColor = if (isPaused) CyberYellow else HyperCrimson,
                tagTextColor = TextPrimary,
                borderColor = if (isPaused) CyberYellow else HyperCrimson
            ) {
                // Giant Digital High-Precision Timecode (HH:MM:SS.X)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .alpha(if (isPaused) 1f else pulseAlpha)
                            .background(if (isPaused) CyberYellow else HyperCrimson, CircleShape)
                            .border(2.dp, BorderHighlight, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = StorageCalculator.formatTimecode(durationMs),
                        fontSize = 32.sp,
                        fontFamily = BitcountPropSingle,
                        fontWeight = FontWeight.Bold,
                        color = if (isPaused) CyberYellow else HyperCrimson,
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        softWrap = false
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

                Spacer(modifier = Modifier.height(18.dp))

                // Tactile Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ActionButton(
                        text = if (isPaused) "Resume" else "Pause",
                        onClick = if (isPaused) onResumeClick else onPauseClick,
                        variant = ActionButtonVariant.SURFACE,
                        modifier = Modifier.weight(1f),
                        fontSize = 14.sp,
                        leadingIcon = {
                            Icon(
                                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                    ActionButton(
                        text = "Stop",
                        onClick = onStopClick,
                        variant = ActionButtonVariant.DANGER,
                        modifier = Modifier.weight(1f),
                        fontSize = 14.sp,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }
        }
        is RecorderState.Preparing -> {
            SectionCard(
                title = "HARDWARE ENGINE",
                titleTag = "INIT",
                tagColor = CyberYellow,
                borderColor = CyberYellow
            ) {
                Text(
                    text = "Allocating zero-copy GraphicBuffer surface & MediaCodec...",
                    color = TextSecondary,
                    fontFamily = BitcountPropSingle,
                    fontSize = 13.sp,
                    lineHeight = 17.sp
                )
            }
        }
        is RecorderState.Finished -> {
            SectionCard(
                title = "RECORDING SAVED",
                titleTag = "GALLERY READY",
                tagColor = ToxicLime,
                borderColor = ToxicLime
            ) {
                Text(
                    text = "MP4 committed directly to Movies/PixL-REC:",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontFamily = BitcountPropSingle
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${state.formattedSize} • ${StorageCalculator.formatDuration(state.durationMs)}",
                    color = ToxicLime,
                    fontFamily = BitcountPropSingle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                ActionButton(
                    text = "RECORD AGAIN",
                    onClick = onStartClick,
                    variant = ActionButtonVariant.PRIMARY,
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
            SectionCard(
                title = "ZERO-COPY RECORDER",
                titleTag = "STANDBY",
                tagColor = ToxicLime,
                borderColor = BorderStark
            ) {
                val inlineContent = mapOf(
                    "arrow" to InlineTextContent(
                        Placeholder(
                            width = 16.sp,
                            height = 12.sp,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                        )
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_pixel_arrow_right),
                            contentDescription = "to",
                            tint = TextSecondary,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                )

                Text(
                    text = buildAnnotatedString {
                        append("Direct GPU ")
                        appendInlineContent("arrow", "──►")
                        append(" MediaCodec hardware pipeline. Captures up to 120 FPS with nanosecond audio synchronization and zero CPU pixel copying.")
                    },
                    inlineContent = inlineContent,
                    color = TextSecondary,
                    fontFamily = BitcountPropSingle,
                    fontSize = 13.sp,
                    lineHeight = 17.sp
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
                        fontSize = 12.sp,
                        fontFamily = BitcountPropSingle,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f MB/MIN", uiState.config.estimatedMbPerMinute),
                        color = CyberYellow,
                        fontSize = 13.sp,
                        fontFamily = BitcountPropSingle,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                ActionButton(
                    text = "START RECORDING",
                    onClick = onStartClick,
                    variant = ActionButtonVariant.PRIMARY,
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

@Composable
private fun LiveAudioVuMeterCard(
    telemetry: TelemetryData,
    isRecording: Boolean
) {
    SectionCard(
        title = "LIVE AUDIO VU MONITOR",
        titleTag = "REAL-TIME",
        tagColor = ToxicLime,
        borderColor = BorderStark
    ) {
        SteppedVuMeter(
            label = "Internal Audio (48kHz)",
            dbLevel = telemetry.gameAudioDb,
            statusOverride = if (!isRecording) "STANDBY" else null
        )
        Spacer(modifier = Modifier.height(10.dp))
        SteppedVuMeter(
            label = "Microphone Audio (Stereo)",
            dbLevel = telemetry.micAudioDb
        )
    }
}

@Composable
private fun TelemetryQuadDeck(telemetry: TelemetryData, uiState: DashboardUiState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Row 1: CPU Overhead + Thermal State
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TelemetryCard(
                iconRes = R.drawable.ic_pixel_chip,
                label = "CPU OVERHEAD",
                value = String.format(Locale.US, "%.1f%%", telemetry.cpuUsagePercent),
                subtext = "OPTIMAL • <3% TARGET",
                accentColor = ToxicLime,
                modifier = Modifier.weight(1f)
            )

            val thermalColor = when (telemetry.thermalStatus) {
                "THROTTLING", "CRITICAL" -> HyperCrimson
                "WARM" -> CyberYellow
                else -> ToxicLime
            }

            TelemetryCard(
                iconRes = R.drawable.ic_pixel_thermal,
                label = "TEMPERATURE",
                value = String.format(Locale.US, "%.1f°C", telemetry.batteryTempCelsius),
                subtext = "${telemetry.thermalStatus} • BATTERY",
                accentColor = thermalColor,
                modifier = Modifier.weight(1f)
            )
        }

        // Row 2: Write Throughput + Frame Stability
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TelemetryCard(
                iconRes = R.drawable.ic_pixel_disk,
                label = "WRITE SPEED",
                value = String.format(Locale.US, "%.1f MB/s", telemetry.writeThroughputMbSec),
                subtext = "DIRECT SCOPED STORAGE",
                accentColor = HyperCyan,
                modifier = Modifier.weight(1f)
            )

            val fpsFormatted = String.format(Locale.US, "%.0f FPS", telemetry.currentFps)
            TelemetryCard(
                iconRes = R.drawable.ic_pixel_fps,
                label = "FRAME STABILITY",
                value = fpsFormatted,
                subtext = if (telemetry.droppedFrames == 0) "0 DROPPED • VPU SYNC" else "${telemetry.droppedFrames} DROPS",
                accentColor = CyberYellow,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TelemetryCard(
    iconRes: Int,
    label: String,
    value: String,
    subtext: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(SurfaceElevated, RoundedCornerShape(10.dp))
            .border(1.5.dp, BorderStark, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = label,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = BitcountPropSingle,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    softWrap = false
                )
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = value,
                color = TextPrimary,
                fontSize = 18.sp,
                fontFamily = BitcountPropSingle,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtext,
                color = accentColor,
                fontSize = 9.sp,
                fontFamily = BitcountPropSingle,
                fontWeight = FontWeight.Normal,
                lineHeight = 11.sp,
                letterSpacing = (-0.2).sp,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun StreamHealthGraphCard(telemetry: TelemetryData) {
    SectionCard(
        title = "TRI-CHANNEL OSCILLOSCOPE",
        titleTag = "MULTI-SYNC",
        tagColor = ToxicLime,
        borderColor = BorderStark
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(ObsidianCanvas, RoundedCornerShape(8.dp))
                    .border(1.dp, BorderStark, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val fpsPoints = telemetry.fpsHistory
                    val bitratePoints = telemetry.bitrateHistory
                    val audioPoints = telemetry.audioHistory
                    if (fpsPoints.isEmpty()) return@Canvas

                    // 1. Draw Grid Lines (Top, Mid, Baseline)
                    val gridColor = Color(0xFF1E1E28)
                    drawLine(color = gridColor, start = Offset(0f, 0f), end = Offset(w, 0f), strokeWidth = 1f)
                    drawLine(color = gridColor, start = Offset(0f, h / 2f), end = Offset(w, h / 2f), strokeWidth = 1f)
                    drawLine(color = gridColor, start = Offset(0f, h), end = Offset(w, h), strokeWidth = 1f)

                    val stepX = w / (fpsPoints.size - 1).coerceAtLeast(1)

                    fun buildTracePath(points: List<Float>): Path {
                        val p = Path()
                        points.forEachIndexed { i, fraction ->
                            val x = i * stepX
                            val y = (h * (1.05f - (fraction * 0.85f))).coerceIn(4f, h - 4f)
                            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                        }
                        return p
                    }

                    // 2. Trace 3: Audio Dynamics (Cyber Yellow)
                    if (audioPoints.isNotEmpty()) {
                        val audioPath = buildTracePath(audioPoints)
                        val fillAudio = Path().apply {
                            addPath(audioPath)
                            lineTo(w, h)
                            lineTo(0f, h)
                            close()
                        }
                        drawPath(
                            path = fillAudio,
                            brush = Brush.verticalGradient(
                                colors = listOf(CyberYellow.copy(alpha = 0.08f), Color.Transparent),
                                startY = 0f,
                                endY = h
                            )
                        )
                        drawPath(
                            path = audioPath,
                            color = CyberYellow.copy(alpha = 0.9f),
                            style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }

                    // 3. Trace 2: Bitrate Throughput (Hyper Cyan)
                    if (bitratePoints.isNotEmpty()) {
                        val bitratePath = buildTracePath(bitratePoints)
                        val fillBitrate = Path().apply {
                            addPath(bitratePath)
                            lineTo(w, h)
                            lineTo(0f, h)
                            close()
                        }
                        drawPath(
                            path = fillBitrate,
                            brush = Brush.verticalGradient(
                                colors = listOf(HyperCyan.copy(alpha = 0.12f), Color.Transparent),
                                startY = 0f,
                                endY = h
                            )
                        )
                        drawPath(
                            path = bitratePath,
                            color = HyperCyan.copy(alpha = 0.95f),
                            style = Stroke(width = 2.0.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }

                    // 4. Trace 1: FPS Stability (Toxic Lime)
                    val fpsPath = buildTracePath(fpsPoints)
                    val fillFps = Path().apply {
                        addPath(fpsPath)
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    }
                    drawPath(
                        path = fillFps,
                        brush = Brush.verticalGradient(
                            colors = listOf(ToxicLime.copy(alpha = 0.2f), Color.Transparent),
                            startY = 0f,
                            endY = h
                        )
                    )
                    drawPath(
                        path = fpsPath,
                        color = ToxicLime,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )

                    // Draw Tracking Head Dot on FPS Trace
                    val lastX = (fpsPoints.size - 1) * stepX
                    val lastY = (h * (1.05f - (fpsPoints.last() * 0.85f))).coerceIn(4f, h - 4f)
                    drawCircle(color = Color.White, radius = 4.dp.toPx(), center = Offset(lastX, lastY))
                    drawCircle(color = ToxicLime, radius = 2.dp.toPx(), center = Offset(lastX, lastY))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tri-Channel Legend Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // FPS (Toxic Lime)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(7.dp).background(ToxicLime, CircleShape))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = String.format(Locale.US, "%.0f FPS", telemetry.currentFps),
                        color = ToxicLime,
                        fontSize = 11.sp,
                        fontFamily = BitcountPropSingle,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Bitrate (Hyper Cyan)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(7.dp).background(HyperCyan, CircleShape))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = String.format(Locale.US, "%.1f MB/s", telemetry.writeThroughputMbSec),
                        color = HyperCyan,
                        fontSize = 11.sp,
                        fontFamily = BitcountPropSingle,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Audio (Cyber Yellow)
                val peakDb = maxOf(telemetry.gameAudioDb, telemetry.micAudioDb)
                val audioText = if (peakDb <= -55f) "SILENT" else String.format(Locale.US, "%.0f dB", peakDb)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(7.dp).background(CyberYellow, CircleShape))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "AUDIO $audioText",
                        color = CyberYellow,
                        fontSize = 11.sp,
                        fontFamily = BitcountPropSingle,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveProfileChipsRow(
    uiState: DashboardUiState,
    onNavigateToSettings: (() -> Unit)?
) {
    val config = uiState.config

    SectionCard(
        title = "ACTIVE PROFILE",
        titleTag = "TAP TO EDIT",
        tagColor = HyperCyan,
        borderColor = BorderStark
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ProfileChip(text = "${config.width}x${config.height}", onClick = { onNavigateToSettings?.invoke() })
            ProfileChip(text = "${config.framerate} FPS", onClick = { onNavigateToSettings?.invoke() })
            ProfileChip(text = "${config.videoBitrate / 1_000_000} MBPS", onClick = { onNavigateToSettings?.invoke() })
            ProfileChip(text = config.videoCodec.displayName, onClick = { onNavigateToSettings?.invoke() })
            ProfileChip(text = config.audioSource.displayName, onClick = { onNavigateToSettings?.invoke() })
            ProfileChip(text = config.recordingOrientation.displayName, onClick = { onNavigateToSettings?.invoke() })
        }
    }
}

@Composable
private fun ProfileChip(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .background(ObsidianCanvas, RoundedCornerShape(6.dp))
            .border(1.dp, BorderHighlight.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text.uppercase(),
            color = TextPrimary,
            fontSize = 11.sp,
            fontFamily = BitcountPropSingle,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}
