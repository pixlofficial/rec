package pixl.rec.ui.studio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pixl.rec.R
import pixl.rec.core.model.HudAnimation
import pixl.rec.core.model.HudShape
import pixl.rec.core.model.HudSnapBehavior
import pixl.rec.core.model.HudStyleConfig
import pixl.rec.core.model.LaserSweepInterval
import pixl.rec.core.model.StreamHudConfig
import pixl.rec.core.model.StrokeStyle
import pixl.rec.core.storage.StudioMode
import pixl.rec.ui.components.HudNodeSurface
import pixl.rec.ui.components.rememberHudIconAnimation
import pixl.rec.ui.dashboard.DashboardViewModel
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

enum class HudLabMode(val title: String, val iconRes: Int) {
    RECORD_LAB("RECORD LAB", R.drawable.ic_pixel_record),
    STREAM_LAB("STREAM LAB", R.drawable.ic_pixel_stream)
}

enum class StudioTab(val title: String) {
    STANDBY("STANDBY"),
    ACTIVE("ACTIVE")
}

private enum class PreviewUplinkState(val label: String, val color: Color) {
    CLEAN("🟢 CLEAN (120 FPS)", ToxicLime),
    CONGESTED("🟡 CONGESTED", CyberYellow),
    CRITICAL("🔴 CRITICAL", HyperCrimson)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HudStudioScreen(
    viewModel: DashboardViewModel,
    initialStudioMode: StudioMode = StudioMode.RECORD,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedLabMode by remember {
        mutableStateOf(if (initialStudioMode == StudioMode.STREAM) HudLabMode.STREAM_LAB else HudLabMode.RECORD_LAB)
    }
    var activeTab by remember { mutableStateOf(StudioTab.STANDBY) }
    var previewUplinkState by remember { mutableStateOf(PreviewUplinkState.CLEAN) }

    val streamHudConfig = uiState.config.streamHudConfig

    val currentConfig: HudStyleConfig = when (selectedLabMode) {
        HudLabMode.RECORD_LAB -> {
            if (activeTab == StudioTab.STANDBY) uiState.config.standbyHudConfig else uiState.config.recordingHudConfig
        }
        HudLabMode.STREAM_LAB -> {
            if (activeTab == StudioTab.STANDBY) streamHudConfig.standbyHud else streamHudConfig.activeHud
        }
    }

    fun updateCurrentConfig(updated: HudStyleConfig) {
        when (selectedLabMode) {
            HudLabMode.RECORD_LAB -> {
                if (activeTab == StudioTab.STANDBY) {
                    viewModel.updateStandbyHudConfig(updated)
                } else {
                    viewModel.updateRecordingHudConfig(updated)
                }
            }
            HudLabMode.STREAM_LAB -> {
                if (activeTab == StudioTab.STANDBY) {
                    viewModel.updateStreamHudConfig(streamHudConfig.copy(standbyHud = updated))
                } else {
                    viewModel.updateStreamHudConfig(streamHudConfig.copy(activeHud = updated))
                }
            }
        }
    }

    Scaffold(
        containerColor = ObsidianCanvas
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // 1. Top Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = "HUD THEME STUDIO",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontFamily = BitcountPropSingle,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = if (selectedLabMode == HudLabMode.RECORD_LAB) "STANDBY & RECORDING LAB" else "STANDBY & BROADCAST LAB",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = BitcountPropSingle
                        )
                    }
                }

                // Reset Action for Active Lab/Tab
                Box(
                    modifier = Modifier
                        .background(SurfaceElevated, RoundedCornerShape(6.dp))
                        .border(1.dp, BorderStark, RoundedCornerShape(6.dp))
                        .clickable {
                            if (selectedLabMode == HudLabMode.RECORD_LAB) {
                                updateCurrentConfig(HudStyleConfig())
                            } else {
                                viewModel.updateStreamHudConfig(StreamHudConfig())
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset",
                            tint = CyberYellow,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "RESET",
                            color = CyberYellow,
                            fontSize = 10.sp,
                            fontFamily = BitcountPropSingle,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Tier 1: Mode Selector (RECORD LAB vs STREAM LAB)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceElevated, RoundedCornerShape(8.dp))
                    .border(1.dp, BorderStark, RoundedCornerShape(8.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                HudLabMode.entries.forEach { lab ->
                    val isSelected = selectedLabMode == lab
                    val labColor = if (lab == HudLabMode.RECORD_LAB) HyperCrimson else CyberYellow

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) labColor.copy(alpha = 0.18f) else Color.Transparent)
                            .border(
                                width = if (isSelected) 1.5.dp else 0.dp,
                                color = if (isSelected) labColor else Color.Transparent,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { selectedLabMode = lab }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = lab.iconRes),
                                contentDescription = null,
                                tint = if (isSelected) labColor else TextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = lab.title,
                                color = if (isSelected) TextPrimary else TextSecondary,
                                fontSize = 12.sp,
                                fontFamily = BitcountPropSingle,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Tier 2: Session State Selector (STANDBY vs ACTIVE / RECORDING / LIVE)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceElevated, RoundedCornerShape(8.dp))
                    .border(1.dp, BorderStark, RoundedCornerShape(8.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                StudioTab.entries.forEach { tab ->
                    val isSelected = activeTab == tab
                    val tabColor = if (tab == StudioTab.STANDBY) HyperCyan else (if (selectedLabMode == HudLabMode.STREAM_LAB) CyberYellow else HyperCrimson)
                    val labelText = when {
                        tab == StudioTab.STANDBY -> "STANDBY"
                        selectedLabMode == HudLabMode.STREAM_LAB -> "LIVE BROADCAST"
                        else -> "ACTIVE RECORDING"
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) tabColor.copy(alpha = 0.18f) else Color.Transparent)
                            .border(
                                width = if (isSelected) 1.5.dp else 0.dp,
                                color = if (isSelected) tabColor else Color.Transparent,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { activeTab = tab }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(if (isSelected) tabColor else TextMuted, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                text = labelText,
                                color = if (isSelected) TextPrimary else TextSecondary,
                                fontSize = 11.5.sp,
                                fontFamily = BitcountPropSingle,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Hero Live Preview Showcase Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(SurfaceElevated, RoundedCornerShape(8.dp))
                    .border(1.5.dp, BorderStark, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val iconAnim = rememberHudIconAnimation(
                        animation = if (selectedLabMode == HudLabMode.STREAM_LAB && activeTab == StudioTab.ACTIVE) {
                            streamHudConfig.livePulseRhythm
                        } else {
                            currentConfig.animation
                        },
                        baseOpacity = currentConfig.iconOpacity
                    )

                    // Optional Uplink Health Aura container
                    val isAuraActive = selectedLabMode == HudLabMode.STREAM_LAB &&
                        activeTab == StudioTab.ACTIVE &&
                        streamHudConfig.enableUplinkHealthAura

                    val auraColor = previewUplinkState.color

                    Box(
                        modifier = if (isAuraActive) {
                            Modifier
                                .size((currentConfig.sizeDp + 14).dp)
                                .border(2.dp, auraColor.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                                .background(auraColor.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        } else {
                            Modifier
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        HudNodeSurface(config = currentConfig) {
                            val iconSize = currentConfig.iconSizeDp.dp

                            if (selectedLabMode == HudLabMode.RECORD_LAB) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_pixel_record),
                                    contentDescription = null,
                                    tint = HyperCrimson,
                                    modifier = Modifier
                                        .size(iconSize)
                                        .scale(iconAnim.scale)
                                        .alpha(iconAnim.alpha)
                                )
                            } else {
                                // STREAM LAB: Broadcast Tower Icon
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_pixel_stream),
                                        contentDescription = null,
                                        tint = HyperCrimson,
                                        modifier = Modifier
                                            .size(iconSize)
                                            .scale(iconAnim.scale)
                                            .alpha(iconAnim.alpha)
                                    )

                                    // Symmetrical Bi-Directional Laser Sweep Overlay in Standby
                                    if (activeTab == StudioTab.STANDBY && streamHudConfig.laserSweepInterval != LaserSweepInterval.OFF) {
                                        val laserTransition = rememberInfiniteTransition(label = "LaserSweep")
                                        val sweepProgress by laserTransition.animateFloat(
                                            initialValue = 0f,
                                            targetValue = 1f,
                                            animationSpec = infiniteRepeatable(
                                                animation = tween(
                                                    durationMillis = streamHudConfig.laserSweepInterval.intervalMs.toInt(),
                                                    easing = LinearEasing
                                                ),
                                                repeatMode = RepeatMode.Restart
                                            ),
                                            label = "LaserSweepPos"
                                        )

                                        Canvas(modifier = Modifier.size(iconSize)) {
                                            val w = size.width
                                            val h = size.height
                                            val cx = w / 2f
                                            val cy = h / 2f
                                            val radius = (w / 2f) * sweepProgress

                                            drawCircle(
                                                brush = Brush.radialGradient(
                                                    colors = listOf(
                                                        Color.White.copy(alpha = 0.85f * streamHudConfig.laserGlowIntensity),
                                                        HyperCrimson.copy(alpha = 0.65f * streamHudConfig.laserGlowIntensity),
                                                        Color.Transparent
                                                    ),
                                                    center = Offset(cx, cy),
                                                    radius = (radius * 1.3f).coerceAtLeast(1f)
                                                ),
                                                radius = (radius * 1.3f).coerceAtLeast(1f),
                                                center = Offset(cx, cy)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val statusText = when {
                        selectedLabMode == HudLabMode.RECORD_LAB && activeTab == StudioTab.STANDBY -> "LIVE STANDBY PREVIEW"
                        selectedLabMode == HudLabMode.RECORD_LAB -> "LIVE RECORDING (00:00:00)"
                        selectedLabMode == HudLabMode.STREAM_LAB && activeTab == StudioTab.STANDBY -> "STREAM STANDBY • ARMED (0ms GO LIVE)"
                        else -> "LIVE ON AIR • ${previewUplinkState.label}"
                    }

                    val statusColor = when {
                        selectedLabMode == HudLabMode.RECORD_LAB && activeTab == StudioTab.STANDBY -> HyperCyan
                        selectedLabMode == HudLabMode.RECORD_LAB -> CyberYellow
                        activeTab == StudioTab.STANDBY -> HyperCyan
                        else -> previewUplinkState.color
                    }

                    Text(
                        text = statusText,
                        color = statusColor,
                        fontSize = 11.sp,
                        fontFamily = BitcountPropSingle,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 5. Dedicated Section Controls based on Mode and Session
            if (selectedLabMode == HudLabMode.RECORD_LAB) {
                // --- RECORD LAB CONTROLS ---
                HudSnapCard(
                    currentConfig = currentConfig,
                    onUpdateConfig = { updateCurrentConfig(it) },
                    activeTabTitle = if (activeTab == StudioTab.STANDBY) "standby" else "recording"
                )

                Spacer(modifier = Modifier.height(16.dp))

                HudIconCard(
                    currentConfig = currentConfig,
                    onUpdateConfig = { updateCurrentConfig(it) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                HudSilhouetteCard(
                    currentConfig = currentConfig,
                    onUpdateConfig = { updateCurrentConfig(it) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                HudStrokeCard(
                    currentConfig = currentConfig,
                    onUpdateConfig = { updateCurrentConfig(it) }
                )
            } else {
                // --- STREAM LAB CONTROLS ---
                if (activeTab == StudioTab.STANDBY) {
                    // Stream Standby Controls: Laser Sweep & Laser Glow
                    StreamLaserSweepCard(
                        streamHudConfig = streamHudConfig,
                        onUpdateStreamHudConfig = { viewModel.updateStreamHudConfig(it) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    StreamLaserGlowCard(
                        streamHudConfig = streamHudConfig,
                        onUpdateStreamHudConfig = { viewModel.updateStreamHudConfig(it) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    HudSilhouetteCard(
                        currentConfig = currentConfig,
                        onUpdateConfig = { updateCurrentConfig(it) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    HudSnapCard(
                        currentConfig = currentConfig,
                        onUpdateConfig = { updateCurrentConfig(it) },
                        activeTabTitle = "broadcast standby"
                    )
                } else {
                    // Stream Active Controls: Live Pulse Rhythm & Uplink Health Aura
                    StreamPulseRhythmCard(
                        streamHudConfig = streamHudConfig,
                        onUpdateStreamHudConfig = { viewModel.updateStreamHudConfig(it) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    StreamUplinkAuraCard(
                        streamHudConfig = streamHudConfig,
                        previewState = previewUplinkState,
                        onPreviewStateChange = { previewUplinkState = it },
                        onUpdateStreamHudConfig = { viewModel.updateStreamHudConfig(it) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    HudSilhouetteCard(
                        currentConfig = currentConfig,
                        onUpdateConfig = { updateCurrentConfig(it) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    HudSnapCard(
                        currentConfig = currentConfig,
                        onUpdateConfig = { updateCurrentConfig(it) },
                        activeTabTitle = "live broadcast"
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ==========================================
// MODULAR CARDS FOR HUD STUDIO
// ==========================================

@Composable
private fun StreamLaserSweepCard(
    streamHudConfig: StreamHudConfig,
    onUpdateStreamHudConfig: (StreamHudConfig) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceElevated, RoundedCornerShape(8.dp))
            .border(1.dp, BorderStark, RoundedCornerShape(8.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_pixel_stream),
                    contentDescription = null,
                    tint = HyperCrimson,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "BI-DIRECTIONAL LASER SWEEP",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontFamily = BitcountPropSingle,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = streamHudConfig.laserSweepInterval.displayName.uppercase(),
                color = HyperCrimson,
                fontSize = 10.sp,
                fontFamily = BitcountPropSingle,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Symmetrical dual-laser sweep mimicking active electromagnetic broadcast radio waves.",
            color = TextMuted,
            fontSize = 11.sp,
            fontFamily = BitcountPropSingle,
            lineHeight = 15.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LaserSweepInterval.entries.forEach { interval ->
                val isSelected = streamHudConfig.laserSweepInterval == interval
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) HyperCrimson.copy(alpha = 0.18f) else ObsidianCanvas)
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) HyperCrimson else BorderStark,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable { onUpdateStreamHudConfig(streamHudConfig.copy(laserSweepInterval = interval)) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (interval) {
                            LaserSweepInterval.FAST -> "1.5S"
                            LaserSweepInterval.STANDARD -> "2.5S"
                            LaserSweepInterval.CALM -> "4.0S"
                            LaserSweepInterval.CONTINUOUS -> "BEACON"
                            LaserSweepInterval.OFF -> "OFF"
                        },
                        color = if (isSelected) TextPrimary else TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = BitcountPropSingle,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamLaserGlowCard(
    streamHudConfig: StreamHudConfig,
    onUpdateStreamHudConfig: (StreamHudConfig) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceElevated, RoundedCornerShape(8.dp))
            .border(1.dp, BorderStark, RoundedCornerShape(8.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LASER SHINE LUMINANCE",
                color = TextPrimary,
                fontSize = 13.sp,
                fontFamily = BitcountPropSingle,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${(streamHudConfig.laserGlowIntensity * 100).roundToInt()}%",
                color = HyperCrimson,
                fontSize = 11.sp,
                fontFamily = BitcountPropSingle,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Peak brightness and bloom effect for the standby laser ripple animation.",
            color = TextMuted,
            fontSize = 11.sp,
            fontFamily = BitcountPropSingle
        )

        Spacer(modifier = Modifier.height(10.dp))

        Slider(
            value = streamHudConfig.laserGlowIntensity,
            onValueChange = { onUpdateStreamHudConfig(streamHudConfig.copy(laserGlowIntensity = it)) },
            valueRange = 0.20f..1.0f,
            steps = 15,
            colors = SliderDefaults.colors(
                thumbColor = HyperCrimson,
                activeTrackColor = HyperCrimson,
                inactiveTrackColor = BorderStark
            )
        )
    }
}

@Composable
private fun StreamPulseRhythmCard(
    streamHudConfig: StreamHudConfig,
    onUpdateStreamHudConfig: (StreamHudConfig) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceElevated, RoundedCornerShape(8.dp))
            .border(1.dp, BorderStark, RoundedCornerShape(8.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LIVE BROADCAST PULSE RHYTHM",
                color = TextPrimary,
                fontSize = 13.sp,
                fontFamily = BitcountPropSingle,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = streamHudConfig.livePulseRhythm.displayName.uppercase(),
                color = CyberYellow,
                fontSize = 11.sp,
                fontFamily = BitcountPropSingle,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Live cadence rhythm for the floating broadcast pill while streaming.",
            color = TextMuted,
            fontSize = 11.sp,
            fontFamily = BitcountPropSingle
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            HudAnimation.entries.forEach { anim ->
                val isSelected = streamHudConfig.livePulseRhythm == anim
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) CyberYellow.copy(alpha = 0.18f) else ObsidianCanvas)
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) CyberYellow else BorderStark,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable { onUpdateStreamHudConfig(streamHudConfig.copy(livePulseRhythm = anim)) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = anim.displayName.uppercase(),
                        color = if (isSelected) TextPrimary else TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = BitcountPropSingle,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamUplinkAuraCard(
    streamHudConfig: StreamHudConfig,
    previewState: PreviewUplinkState,
    onPreviewStateChange: (PreviewUplinkState) -> Unit,
    onUpdateStreamHudConfig: (StreamHudConfig) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceElevated, RoundedCornerShape(8.dp))
            .border(1.dp, if (streamHudConfig.enableUplinkHealthAura) ToxicLime.copy(alpha = 0.5f) else BorderStark, RoundedCornerShape(8.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "UPLINK HEALTH AURA",
                    color = if (streamHudConfig.enableUplinkHealthAura) TextPrimary else TextSecondary,
                    fontSize = 13.sp,
                    fontFamily = BitcountPropSingle,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Real-time edge neon glow reflecting network quality without obstructing screen pixels.",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontFamily = BitcountPropSingle,
                    lineHeight = 15.sp
                )
            }

            Switch(
                checked = streamHudConfig.enableUplinkHealthAura,
                onCheckedChange = { onUpdateStreamHudConfig(streamHudConfig.copy(enableUplinkHealthAura = it)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = TextInverse,
                    checkedTrackColor = ToxicLime,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = ObsidianCanvas
                )
            )
        }

        AnimatedVisibility(
            visible = streamHudConfig.enableUplinkHealthAura,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "TEST SIMULATED NETWORK STATE:",
                    color = TextSecondary,
                    fontSize = 10.5.sp,
                    fontFamily = BitcountPropSingle,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PreviewUplinkState.entries.forEach { state ->
                        val isSelected = previewState == state
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) state.color.copy(alpha = 0.18f) else ObsidianCanvas)
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) state.color else BorderStark,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable { onPreviewStateChange(state) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = state.name,
                                color = if (isSelected) state.color else TextSecondary,
                                fontSize = 11.sp,
                                fontFamily = BitcountPropSingle,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HudSnapCard(
    currentConfig: HudStyleConfig,
    onUpdateConfig: (HudStyleConfig) -> Unit,
    activeTabTitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceElevated, RoundedCornerShape(8.dp))
            .border(1.dp, BorderStark, RoundedCornerShape(8.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_pixel_gesture),
                    contentDescription = null,
                    tint = CyberYellow,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "EDGE SNAPPING BEHAVIOR",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontFamily = BitcountPropSingle,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = currentConfig.snapBehavior.displayName.uppercase(),
                color = CyberYellow,
                fontSize = 10.sp,
                fontFamily = BitcountPropSingle,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Controls magnetic physics when dragging or dropping in $activeTabTitle mode.",
            color = TextMuted,
            fontSize = 11.sp,
            fontFamily = BitcountPropSingle
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HudSnapBehavior.entries.forEach { option ->
                val isSelected = currentConfig.snapBehavior == option
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) BorderHighlight.copy(alpha = 0.15f) else ObsidianCanvas)
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) BorderHighlight else BorderStark,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable { onUpdateConfig(currentConfig.copy(snapBehavior = option)) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (option) {
                            HudSnapBehavior.PROXIMITY_SNAP -> "PROXIMITY"
                            HudSnapBehavior.ALWAYS_SNAP_EDGE -> "ALWAYS"
                            HudSnapBehavior.FREE_FLOAT -> "FREE"
                        },
                        color = if (isSelected) TextPrimary else TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = BitcountPropSingle,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun HudIconCard(
    currentConfig: HudStyleConfig,
    onUpdateConfig: (HudStyleConfig) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceElevated, RoundedCornerShape(8.dp))
            .border(1.dp, BorderStark, RoundedCornerShape(8.dp))
            .padding(14.dp)
    ) {
        Text(
            text = "ICON & ANIMATION",
            color = TextPrimary,
            fontSize = 13.sp,
            fontFamily = BitcountPropSingle,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Configure record shutter icon size, opacity & live animation",
            color = TextMuted,
            fontSize = 11.sp,
            fontFamily = BitcountPropSingle
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Subheading & Slider: ICON SIZE
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ICON SIZE",
                color = TextSecondary,
                fontSize = 11.sp,
                fontFamily = BitcountPropSingle,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${currentConfig.iconSizeDp} DP",
                color = CyberYellow,
                fontSize = 11.sp,
                fontFamily = BitcountPropSingle,
                fontWeight = FontWeight.Bold
            )
        }

        Slider(
            value = currentConfig.iconSizeDp.toFloat().coerceIn(15f, 44f),
            onValueChange = { onUpdateConfig(currentConfig.copy(iconSizeDp = it.roundToInt())) },
            valueRange = 15f..44f,
            steps = 28,
            colors = SliderDefaults.colors(
                thumbColor = TextPrimary,
                activeTrackColor = TextPrimary,
                inactiveTrackColor = BorderStark
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Subheading & Slider: ICON OPACITY
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ICON OPACITY",
                color = TextSecondary,
                fontSize = 11.sp,
                fontFamily = BitcountPropSingle,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${(currentConfig.iconOpacity * 100).roundToInt()}%",
                color = TextPrimary,
                fontSize = 11.sp,
                fontFamily = BitcountPropSingle,
                fontWeight = FontWeight.Bold
            )
        }

        Slider(
            value = currentConfig.iconOpacity,
            onValueChange = { onUpdateConfig(currentConfig.copy(iconOpacity = it)) },
            valueRange = 0.20f..1.0f,
            steps = 15,
            colors = SliderDefaults.colors(
                thumbColor = TextPrimary,
                activeTrackColor = TextPrimary,
                inactiveTrackColor = BorderStark
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Subheading & Selector: ANIMATION STYLE
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ANIMATION STYLE",
                color = TextSecondary,
                fontSize = 11.sp,
                fontFamily = BitcountPropSingle,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = currentConfig.animation.displayName.uppercase(),
                color = CyberYellow,
                fontSize = 11.sp,
                fontFamily = BitcountPropSingle,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            HudAnimation.entries.forEach { anim ->
                val isSelected = currentConfig.animation == anim
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) BorderHighlight.copy(alpha = 0.15f) else ObsidianCanvas)
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) BorderHighlight else BorderStark,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable { onUpdateConfig(currentConfig.copy(animation = anim)) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = anim.displayName.uppercase(),
                        color = if (isSelected) TextPrimary else TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = BitcountPropSingle,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun HudSilhouetteCard(
    currentConfig: HudStyleConfig,
    onUpdateConfig: (HudStyleConfig) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceElevated, RoundedCornerShape(8.dp))
            .border(1.dp, if (currentConfig.hasBackground) BorderHighlight else BorderStark, RoundedCornerShape(8.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "BACKGROUND & SILHOUETTE",
                    color = if (currentConfig.hasBackground) TextPrimary else TextSecondary,
                    fontSize = 13.sp,
                    fontFamily = BitcountPropSingle,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (currentConfig.hasBackground) "Using custom shape background silhouette" else "Background disabled (pure icon)",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontFamily = BitcountPropSingle
                )
            }

            Switch(
                checked = currentConfig.hasBackground,
                onCheckedChange = { onUpdateConfig(currentConfig.copy(hasBackground = it)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = TextInverse,
                    checkedTrackColor = TextPrimary,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = ObsidianCanvas
                )
            )
        }

        AnimatedVisibility(
            visible = currentConfig.hasBackground,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "SHAPE GEOMETRY",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = BitcountPropSingle,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HudShape.entries.forEach { shape ->
                        val isSelected = currentConfig.shape == shape
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) BorderHighlight.copy(alpha = 0.15f) else ObsidianCanvas)
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) BorderHighlight else BorderStark,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable { onUpdateConfig(currentConfig.copy(shape = shape)) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = shape.displayName.uppercase(),
                                color = if (isSelected) TextPrimary else TextSecondary,
                                fontSize = 11.sp,
                                fontFamily = BitcountPropSingle,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Node Silhouette Size Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "NODE SIZE",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = BitcountPropSingle,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${currentConfig.nodeSizeDp} DP",
                        color = CyberYellow,
                        fontSize = 11.sp,
                        fontFamily = BitcountPropSingle,
                        fontWeight = FontWeight.Bold
                    )
                }

                Slider(
                    value = currentConfig.nodeSizeDp.toFloat().coerceIn(36f, 56f),
                    onValueChange = { onUpdateConfig(currentConfig.copy(nodeSizeDp = it.roundToInt())) },
                    valueRange = 36f..56f,
                    steps = 19,
                    colors = SliderDefaults.colors(
                        thumbColor = TextPrimary,
                        activeTrackColor = TextPrimary,
                        inactiveTrackColor = BorderStark
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Background Opacity Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BACKGROUND OPACITY",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = BitcountPropSingle,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${(currentConfig.backgroundOpacity * 100).roundToInt()}%",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontFamily = BitcountPropSingle,
                        fontWeight = FontWeight.Bold
                    )
                }

                Slider(
                    value = currentConfig.backgroundOpacity,
                    onValueChange = { onUpdateConfig(currentConfig.copy(backgroundOpacity = it)) },
                    valueRange = 0.10f..1.0f,
                    steps = 17,
                    colors = SliderDefaults.colors(
                        thumbColor = TextPrimary,
                        activeTrackColor = TextPrimary,
                        inactiveTrackColor = BorderStark
                    )
                )
            }
        }
    }
}

@Composable
private fun HudStrokeCard(
    currentConfig: HudStyleConfig,
    onUpdateConfig: (HudStyleConfig) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceElevated, RoundedCornerShape(8.dp))
            .border(1.dp, if (currentConfig.hasStroke) BorderHighlight else BorderStark, RoundedCornerShape(8.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "BORDER STROKE",
                    color = if (currentConfig.hasStroke) TextPrimary else TextSecondary,
                    fontSize = 13.sp,
                    fontFamily = BitcountPropSingle,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (currentConfig.hasStroke) "Outline border & cyber path effects active" else "Stroke disabled",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontFamily = BitcountPropSingle
                )
            }

            Switch(
                checked = currentConfig.hasStroke,
                onCheckedChange = { onUpdateConfig(currentConfig.copy(hasStroke = it)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = TextInverse,
                    checkedTrackColor = TextPrimary,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = ObsidianCanvas
                )
            )
        }

        AnimatedVisibility(
            visible = currentConfig.hasStroke,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                Spacer(modifier = Modifier.height(14.dp))

                // Stroke Weight Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "STROKE WEIGHT",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = BitcountPropSingle,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f DP", currentConfig.strokeWidthDp),
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontFamily = BitcountPropSingle,
                        fontWeight = FontWeight.Bold
                    )
                }

                Slider(
                    value = currentConfig.strokeWidthDp,
                    onValueChange = { onUpdateConfig(currentConfig.copy(strokeWidthDp = ((it * 10f).roundToInt() / 10f))) },
                    valueRange = 0.5f..3.5f,
                    steps = 29,
                    colors = SliderDefaults.colors(
                        thumbColor = TextPrimary,
                        activeTrackColor = TextPrimary,
                        inactiveTrackColor = BorderStark
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "STROKE PATTERN",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = BitcountPropSingle,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StrokeStyle.entries.forEach { style ->
                        val isSelected = currentConfig.strokeStyle == style
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) BorderHighlight.copy(alpha = 0.15f) else ObsidianCanvas)
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) BorderHighlight else BorderStark,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable { onUpdateConfig(currentConfig.copy(strokeStyle = style)) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = style.displayName.uppercase(),
                                color = if (isSelected) TextPrimary else TextSecondary,
                                fontSize = 11.sp,
                                fontFamily = BitcountPropSingle,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "STROKE OPACITY",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = BitcountPropSingle,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${(currentConfig.strokeOpacity * 100).roundToInt()}%",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontFamily = BitcountPropSingle,
                        fontWeight = FontWeight.Bold
                    )
                }

                Slider(
                    value = currentConfig.strokeOpacity,
                    onValueChange = { onUpdateConfig(currentConfig.copy(strokeOpacity = it)) },
                    valueRange = 0.10f..1.0f,
                    steps = 17,
                    colors = SliderDefaults.colors(
                        thumbColor = TextPrimary,
                        activeTrackColor = TextPrimary,
                        inactiveTrackColor = BorderStark
                    )
                )
            }
        }
    }
}
