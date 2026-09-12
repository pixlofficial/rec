package pixl.rec.ui.settings.sections

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pixl.rec.R
import pixl.rec.core.engine.CodecProbe
import pixl.rec.core.model.StreamPlatform
import pixl.rec.core.model.VideoCodec
import pixl.rec.core.storage.StorageCalculator
import pixl.rec.ui.components.SectionCard
import pixl.rec.ui.components.SlidingPillSelector
import pixl.rec.ui.components.TelemetryBadge
import pixl.rec.ui.dashboard.DashboardUiState
import pixl.rec.ui.dashboard.DashboardViewModel
import pixl.rec.ui.settings.components.AdvancedStudioControlsCard
import pixl.rec.ui.settings.components.SettingsTag
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

enum class OutputSubTab(val title: String) {
    RECORDING("RECORDING"),
    STREAMING("STREAMING"),
    REPLAY_BUFFER("REPLAY BUFFER")
}

@Composable
fun OutputSettingsSection(
    uiState: DashboardUiState,
    isRecordingActive: Boolean,
    isStreamingEnabled: Boolean,
    viewModel: DashboardViewModel,
    onNavigateToGeneral: () -> Unit = {}
) {
    val config = uiState.config
    val streamConfig by viewModel.streamConfig.collectAsState()
    val capabilities = uiState.capabilities
    val context = LocalContext.current

    var selectedSubTab by remember { mutableStateOf(OutputSubTab.RECORDING) }

    // 0. Sub-Navigation Tabs: [ RECORDING | STREAMING | REPLAY BUFFER ]
    SlidingPillSelector(
        items = OutputSubTab.entries,
        selectedItem = selectedSubTab,
        onItemSelected = { selectedSubTab = it },
        itemLabel = { it.title },
        height = 38.dp
    )

    Spacer(modifier = Modifier.height(14.dp))

    when (selectedSubTab) {
        OutputSubTab.RECORDING -> {
            // --- SUB-TAB 1: LOCAL RECORDING OUTPUT ---
            // 1. Hardware Codec Card
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
                    text = "Local Vault Encoder: ${if (isHardware) "Dedicated ASIC (Zero-Copy Surface)" else "Software Fallback"}",
                    color = if (isHardware) ToxicLime else CyberYellow,
                    fontSize = 11.sp,
                    fontFamily = BitcountPropSingle
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Local Encoding Bitrate Card
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

            // 3. Local Storage Pipeline (Scoped MediaStore)
            val freeStorageFormatted = StorageCalculator.formatBytes(uiState.availableStorageBytes)
            val remainingMinutes = StorageCalculator.estimateRemainingMinutes(
                availableBytes = uiState.availableStorageBytes,
                videoBitrateBps = config.videoBitrate,
                audioBitrateBps = config.audioBitrate
            )
            val estimatedHours = remainingMinutes / 60.0

            SectionCard(title = "LOCAL STORAGE PIPELINE", titleTag = "SCOPED") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "DESTINATION DIRECTORY:",
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

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = String.format(
                        Locale.US,
                        "Estimated capacity: ~%.1f hours remaining at current bitrate (%d Mbps).",
                        estimatedHours,
                        config.videoBitrate / 1_000_000
                    ),
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = BitcountPropSingle
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. Advanced Studio Controls (Collapsible Accordion)
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

        OutputSubTab.STREAMING -> {
            // --- SUB-TAB 2: BROADCAST STREAMING OUTPUT ---
            if (!isStreamingEnabled) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceElevated)
                        .border(1.5.dp, BorderHighlight, RoundedCornerShape(10.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "LIVE STREAMING IS DISARMED",
                            fontFamily = BitcountPropSingle,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = HyperCyan
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Enable Live Streaming in General settings to configure broadcast bitrates, adaptive rate adaptation, and multi-platform negotiation.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .background(HyperCyan, RoundedCornerShape(6.dp))
                                .clickable { onNavigateToGeneral() }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "GO TO GENERAL SETTINGS →",
                                color = ObsidianCanvas,
                                fontFamily = BitcountPropSingle,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                // 1. Contextual Codec Negotiation & Effective Stream Codec Card
                val isEffectiveHevc = streamConfig.effectiveSupportsHevc
                SectionCard(
                    title = "STREAM CODEC NEGOTIATION",
                    titleTag = if (isEffectiveHevc) "HEVC (HVC1)" else "AVC (H.264)"
                ) {
                    if (streamConfig.platform.supportsHevc) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "ENHANCED RTMP (HEVC / H.265)",
                                    color = TextPrimary,
                                    fontSize = 12.5.sp,
                                    fontFamily = BitcountPropSingle,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (streamConfig.platform == StreamPlatform.YOUTUBE) {
                                        "FourCC 'hvc1' encapsulation for 1440p YouTube Live streaming at 40% lower bandwidth."
                                    } else {
                                        "FourCC 'hvc1' encapsulation. Ensure custom RTMP ingest server supports Enhanced RTMP."
                                    },
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp
                                )
                            }
                            Switch(
                                checked = streamConfig.useEnhancedHevc,
                                onCheckedChange = { viewModel.updateStreamConfig(streamConfig.copy(useEnhancedHevc = it)) },
                                colors = SwitchDefaults.colors(checkedThumbColor = HyperCyan, checkedTrackColor = HyperCyan.copy(alpha = 0.25f))
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "BROADCAST CODEC",
                                    color = TextPrimary,
                                    fontSize = 12.5.sp,
                                    fontFamily = BitcountPropSingle,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${streamConfig.platform.displayName} ingest servers strictly require AVC (H.264). Enhanced RTMP is not supported.",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(SurfaceElevated)
                                    .border(1.dp, BorderStark, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "AVC / H.264",
                                    color = HyperCyan,
                                    fontFamily = BitcountPropSingle,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Effective Codec Decision Badge
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isEffectiveHevc) ToxicLime.copy(alpha = 0.1f) else CyberYellow.copy(alpha = 0.1f))
                            .border(1.dp, if (isEffectiveHevc) ToxicLime.copy(alpha = 0.4f) else CyberYellow.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(
                                text = if (isEffectiveHevc) "EFFECTIVE ENCODER: HEVC (H.265)" else "EFFECTIVE ENCODER: AVC (H.264)",
                                color = if (isEffectiveHevc) ToxicLime else CyberYellow,
                                fontSize = 11.sp,
                                fontFamily = BitcountPropSingle,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isEffectiveHevc) {
                                    "Active destinations support Enhanced RTMP. High-efficiency HEVC zero-copy encoding active."
                                } else {
                                    "Locked to AVC (H.264) to guarantee zero playback glitches across active destinations (e.g. Twitch/Kick)."
                                },
                                color = TextSecondary,
                                fontSize = 10.5.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2. Stream Uplink Bitrate & Pacing Card
                SectionCard(
                    title = "STREAM BITRATE & PACING",
                    titleTag = "${streamConfig.videoBitrate / 1_000_000} MBPS"
                ) {
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

                    // Broadcast Audio Bitrate Selector
                    Text(
                        text = "BROADCAST AUDIO BITRATE",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = BitcountPropSingle,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val audioBitrates = listOf(96_000, 128_000, 160_000, 192_000)
                    SlidingPillSelector(
                        items = audioBitrates,
                        selectedItem = streamConfig.audioBitrate,
                        onItemSelected = { viewModel.updateStreamConfig(streamConfig.copy(audioBitrate = it)) },
                        itemLabel = { "${it / 1000} kbps" },
                        height = 34.dp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Keyframe Interval: ${streamConfig.keyframeIntervalSeconds.toInt()}s GOP (Strict RTMP monotonic timestamp pacing).",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = BitcountPropSingle
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 3. Adaptive Bitrate Control (ABR) Card
                SectionCard(title = "ADAPTIVE BITRATE CONTROL", titleTag = "ABR") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "DYNAMIC RATE ADAPTATION",
                                color = TextPrimary,
                                fontSize = 12.5.sp,
                                fontFamily = BitcountPropSingle,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Dynamically adjusts hardware MediaCodec bitrate in real-time during network backpressure to prevent stream disconnection.",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                        Switch(
                            checked = streamConfig.enableAbr,
                            onCheckedChange = { viewModel.updateStreamConfig(streamConfig.copy(enableAbr = it)) },
                            colors = SwitchDefaults.colors(checkedThumbColor = HyperCyan, checkedTrackColor = HyperCyan.copy(alpha = 0.25f))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 4. Local Master Archive Card
                SectionCard(title = "LOCAL MASTER ARCHIVE", titleTag = "VAULT") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "DUAL MASTER VAULT ARCHIVE",
                                color = TextPrimary,
                                fontSize = 12.5.sp,
                                fontFamily = BitcountPropSingle,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (streamConfig.saveLocalMasterArchive) {
                                    "Simultaneously write an uncompressed master MP4 copy to local storage while streaming."
                                } else {
                                    "Pure Stream Mode. Transmits directly over RTMP with zero local disk footprint."
                                },
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                        Switch(
                            checked = streamConfig.saveLocalMasterArchive,
                            onCheckedChange = { viewModel.updateStreamConfig(streamConfig.copy(saveLocalMasterArchive = it)) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ToxicLime,
                                checkedTrackColor = ToxicLime.copy(alpha = 0.25f),
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = SurfaceElevated
                            )
                        )
                    }
                }
            }
        }

        OutputSubTab.REPLAY_BUFFER -> {
            // --- SUB-TAB 3: INSTANT REPLAY BUFFER ---
            SectionCard(
                title = "INSTANT REPLAY BUFFER",
                titleTag = if (config.enableReplayBuffer) "${config.replayBufferDurationSeconds}S READY" else "OFF"
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(ToxicLime.copy(alpha = 0.1f))
                        .border(1.dp, ToxicLime.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_pixel_lightning),
                            contentDescription = null,
                            tint = ToxicLime,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (config.enableReplayBuffer) "REPLAY BUFFER ENGINE ACTIVE" else "REPLAY BUFFER DISABLED",
                                color = ToxicLime,
                                fontSize = 12.sp,
                                fontFamily = BitcountPropSingle,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Continuous in-memory circular ring buffer for zero-disk retroactive clip capture.",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Master Switch Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "ENABLE REPLAY BUFFER",
                            color = TextPrimary,
                            fontSize = 12.5.sp,
                            fontFamily = BitcountPropSingle,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Allocate volatile RAM to keep a continuous rolling window of gameplay ready to clip in 0ms.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                    }
                    Switch(
                        checked = config.enableReplayBuffer,
                        onCheckedChange = { viewModel.updateEnableReplayBuffer(it) },
                        enabled = !isRecordingActive,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ToxicLime,
                            checkedTrackColor = ToxicLime.copy(alpha = 0.25f),
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = SurfaceElevated
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "REPLAY BUFFER DURATION",
                    color = if (config.enableReplayBuffer) TextPrimary else TextMuted,
                    fontSize = 12.sp,
                    fontFamily = BitcountPropSingle,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Amount of volatile RAM allocated to retain your recent gameplay before a clip trigger.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                val durationOptions = listOf(15, 30, 60, 120)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    durationOptions.forEach { sec ->
                        val isSelected = config.replayBufferDurationSeconds == sec
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected && config.enableReplayBuffer) TextPrimary else SurfaceElevated)
                                .border(
                                    1.dp,
                                    if (isSelected && config.enableReplayBuffer) ToxicLime else BorderStark,
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable(enabled = !isRecordingActive && config.enableReplayBuffer) {
                                    viewModel.updateReplayBufferDuration(sec)
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${sec}s",
                                color = if (isSelected && config.enableReplayBuffer) TextInverse else if (config.enableReplayBuffer) TextPrimary else TextMuted,
                                fontSize = 12.sp,
                                fontFamily = BitcountPropSingle,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                val estRamMb = ((config.videoBitrate / 8_000_000.0) * config.replayBufferDurationSeconds).roundToInt().coerceAtLeast(15)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ESTIMATED RAM OVERHEAD:",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = BitcountPropSingle
                    )
                    Text(
                        text = if (config.enableReplayBuffer) "~$estRamMb MB RAM" else "0 MB (DISABLED)",
                        color = if (config.enableReplayBuffer) ToxicLime else TextMuted,
                        fontSize = 12.sp,
                        fontFamily = BitcountPropSingle,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(SurfaceElevated)
                        .border(1.dp, BorderStark, RoundedCornerShape(6.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Text(
                            text = "HOW TO CLIP IN-GAME:",
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontFamily = BitcountPropSingle,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "When activated, a dedicated Instant Replay lightning button appears on the Floating Radial HUD and Compact Pill. Tapping it writes the preceding ${config.replayBufferDurationSeconds} seconds directly to your Scoped Storage Vault in 0ms without stopping recording.",
                            color = TextMuted,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}
