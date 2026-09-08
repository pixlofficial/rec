package pixl.rec.ui.setup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import pixl.rec.R
import pixl.rec.core.model.StreamConfig
import pixl.rec.core.model.StreamDestination
import pixl.rec.core.model.StreamPlatform
import pixl.rec.core.storage.SecureStreamPreferences
import pixl.rec.core.storage.StorageCalculator
import pixl.rec.core.stream.RtmpConnection
import pixl.rec.ui.theme.BitcountPropSingle
import pixl.rec.ui.theme.BorderStark
import pixl.rec.ui.theme.CyberYellow
import pixl.rec.ui.theme.HyperCrimson
import pixl.rec.ui.theme.HyperCyan
import pixl.rec.ui.theme.ObsidianCanvas
import pixl.rec.ui.theme.SurfaceCard
import pixl.rec.ui.theme.SurfaceElevated
import pixl.rec.ui.theme.TextMuted
import pixl.rec.ui.theme.TextPrimary
import pixl.rec.ui.theme.TextSecondary
import pixl.rec.ui.theme.ToxicLime

private enum class ProbeStatus {
    IDLE,
    PROBING,
    SUCCESS,
    FAILED
}

/**
 * Cyberpunk-styled Live Stream Pre-Flight & Multi-Destination Broadcast Studio Modal.
 * Facilitates zero-friction multi-destination broadcasting directly on-device across
 * YouTube, Twitch, Kick, and Custom RTMP with hardware Keystore credential storage,
 * individual socket probe tests, uplink bandwidth calculation, and dual-output toggles.
 */
@Composable
fun StreamSetupModal(
    streamConfig: StreamConfig,
    onSaveConfig: (StreamConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    val initialDestinations = remember(streamConfig) {
        if (streamConfig.destinations.isNotEmpty()) {
            streamConfig.destinations
        } else {
            StreamPlatform.entries.map { p ->
                val isEnabled = p == streamConfig.platform
                val key = if (p == streamConfig.platform) streamConfig.streamKey else SecureStreamPreferences.getPlatformStreamKey(context, p)
                val customUrl = if (p == StreamPlatform.CUSTOM) streamConfig.customEndpointUrl else ""
                StreamDestination(
                    platform = p,
                    customEndpointUrl = customUrl,
                    streamKey = key,
                    enabled = isEnabled
                )
            }
        }
    }

    var destinations by remember { mutableStateOf(initialDestinations) }
    var probeStatusMap by remember { mutableStateOf(mapOf<StreamPlatform, Pair<ProbeStatus, String>>()) }
    var keysVisibleMap by remember { mutableStateOf(mapOf<StreamPlatform, Boolean>()) }

    var enableAbr by remember { mutableStateOf(streamConfig.enableAbr) }
    var saveMasterArchive by remember { mutableStateOf(streamConfig.saveLocalMasterArchive) }
    var useEnhancedHevc by remember { mutableStateOf(streamConfig.useEnhancedHevc) }

    val activeDestinations = destinations.filter { it.isConfigured }
    val totalActiveConfigured = activeDestinations.size
    val totalActiveEnabled = destinations.count { it.enabled }
    val totalBandwidthBps = destinations.filter { it.enabled }.sumOf {
        it.platform.defaultVideoBitrate.toLong() + 256_000L
    }.coerceAtLeast(streamConfig.videoBitrate.toLong() + 256_000L)
    val totalBandwidthMbps = totalBandwidthBps.toFloat() / 1_000_000f
    val anyRequiresAvc = destinations.any { it.enabled && !it.platform.supportsHevc }
    val estimatedBytesPerHour = (totalBandwidthBps / 8L) * 3600L
    val estimatedRateText = StorageCalculator.formatBytes(estimatedBytesPerHour)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .fillMaxHeight(0.92f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(ObsidianCanvas)
                    .border(1.5.dp, HyperCyan.copy(alpha = 0.65f), RoundedCornerShape(24.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Intercept clicks inside modal
                    )
                    .padding(20.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(HyperCyan.copy(alpha = 0.15f))
                                    .border(1.dp, HyperCyan.copy(alpha = 0.6f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_pixel_stream),
                                    contentDescription = "Stream Icon",
                                    tint = HyperCyan,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "BROADCAST STUDIO",
                                    color = TextPrimary,
                                    fontFamily = BitcountPropSingle,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "ZERO-COPY MULTISTREAM ENGINE",
                                    color = HyperCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        // Close button
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(SurfaceElevated)
                                .border(1.dp, BorderStark, CircleShape)
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onDismiss()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_pixel_close),
                                contentDescription = "Close",
                                tint = TextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // 1. Section Header: Broadcast Destinations
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "BROADCAST DESTINATIONS",
                                    color = TextSecondary,
                                    fontFamily = BitcountPropSingle,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp
                                )
                                Text(
                                    text = "Select one or more targets for direct parallel stream",
                                    color = HyperCyan,
                                    fontSize = 9.sp
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(HyperCyan.copy(alpha = 0.12f))
                                    .border(1.dp, HyperCyan.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "$totalActiveEnabled / ${destinations.size} ACTIVE",
                                    color = HyperCyan,
                                    fontFamily = BitcountPropSingle,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Destination Cards
                        destinations.forEachIndexed { index, dest ->
                            val isEnabled = dest.enabled
                            val isKeyVisible = keysVisibleMap[dest.platform] ?: false
                            val probeInfo = probeStatusMap[dest.platform] ?: (ProbeStatus.IDLE to "")
                            val currentProbeStatus = probeInfo.first
                            val probeMsg = probeInfo.second

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isEnabled) SurfaceCard else SurfaceElevated.copy(alpha = 0.4f))
                                    .border(
                                        1.dp,
                                        if (isEnabled) HyperCyan.copy(alpha = 0.6f) else BorderStark,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(12.dp)
                            ) {
                                Column {
                                    // Card Header Row: Platform Name + Switch
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
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (isEnabled && dest.isConfigured) ToxicLime
                                                        else if (isEnabled) CyberYellow
                                                        else TextMuted
                                                    )
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(
                                                    text = dest.platform.displayName,
                                                    color = if (isEnabled) TextPrimary else TextMuted,
                                                    fontFamily = BitcountPropSingle,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = if (dest.platform.supportsHevc) "Supports Enhanced RTMP (HEVC/AVC)" else "Requires AVC (H.264)",
                                                    color = TextSecondary,
                                                    fontSize = 9.sp
                                                )
                                            }
                                        }

                                        Switch(
                                            checked = isEnabled,
                                            onCheckedChange = { checked ->
                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                destinations = destinations.toMutableList().also { list ->
                                                    list[index] = dest.copy(enabled = checked)
                                                }
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = ObsidianCanvas,
                                                checkedTrackColor = HyperCyan,
                                                uncheckedThumbColor = TextMuted,
                                                uncheckedTrackColor = SurfaceElevated
                                            )
                                        )
                                    }

                                    // Expandable Input Area when Destination is Enabled
                                    AnimatedVisibility(visible = isEnabled) {
                                        Column {
                                            Spacer(modifier = Modifier.height(10.dp))

                                            // Custom RTMP Ingest URL
                                            if (dest.platform == StreamPlatform.CUSTOM) {
                                                Text(
                                                    text = "RTMP INGEST URL",
                                                    color = TextSecondary,
                                                    fontFamily = BitcountPropSingle,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                OutlinedTextField(
                                                    value = dest.customEndpointUrl,
                                                    onValueChange = { newUrl ->
                                                        destinations = destinations.toMutableList().also { list ->
                                                            list[index] = dest.copy(customEndpointUrl = newUrl)
                                                        }
                                                        probeStatusMap = probeStatusMap - dest.platform
                                                    },
                                                    placeholder = {
                                                        Text("rtmp://live.myserver.com/app", color = TextMuted, fontSize = 11.sp)
                                                    },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    singleLine = true,
                                                    textStyle = TextStyle(color = TextPrimary, fontSize = 11.sp),
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        focusedBorderColor = HyperCyan,
                                                        unfocusedBorderColor = BorderStark,
                                                        focusedContainerColor = SurfaceElevated,
                                                        unfocusedContainerColor = SurfaceElevated
                                                    ),
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                            }

                                            // Stream Key Input
                                            Text(
                                                text = "STREAM KEY (ENCRYPTED KEYSTORE)",
                                                color = TextSecondary,
                                                fontFamily = BitcountPropSingle,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))

                                            OutlinedTextField(
                                                value = dest.streamKey,
                                                onValueChange = { newKey ->
                                                    destinations = destinations.toMutableList().also { list ->
                                                        list[index] = dest.copy(streamKey = newKey)
                                                    }
                                                    probeStatusMap = probeStatusMap - dest.platform
                                                },
                                                placeholder = {
                                                    Text("Paste or enter stream key", color = TextMuted, fontSize = 11.sp)
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true,
                                                visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                                keyboardOptions = KeyboardOptions(
                                                    keyboardType = KeyboardType.Password,
                                                    imeAction = ImeAction.Done
                                                ),
                                                keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                                                trailingIcon = {
                                                    Icon(
                                                        imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                        contentDescription = "Toggle Visibility",
                                                        tint = TextSecondary,
                                                        modifier = Modifier
                                                            .size(18.dp)
                                                            .clickable {
                                                                keysVisibleMap = keysVisibleMap + (dest.platform to !isKeyVisible)
                                                            }
                                                    )
                                                },
                                                textStyle = TextStyle(color = TextPrimary, fontSize = 11.sp),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = HyperCyan,
                                                    unfocusedBorderColor = BorderStark,
                                                    focusedContainerColor = SurfaceElevated,
                                                    unfocusedContainerColor = SurfaceElevated
                                                ),
                                                shape = RoundedCornerShape(8.dp)
                                            )

                                            Spacer(modifier = Modifier.height(8.dp))

                                            // Quick Helpers: [ GET KEY ], [ PASTE ], [ TEST INGEST ]
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                // Get Key button
                                                val getUrl = when (dest.platform) {
                                                    StreamPlatform.YOUTUBE -> "https://studio.youtube.com/channel/UC/livestreaming"
                                                    StreamPlatform.TWITCH -> "https://dashboard.twitch.tv/settings/stream"
                                                    StreamPlatform.KICK -> "https://kick.com/dashboard/settings/stream"
                                                    StreamPlatform.CUSTOM -> null
                                                }
                                                if (getUrl != null) {
                                                    Box(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(32.dp)
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .background(SurfaceElevated)
                                                            .border(1.dp, BorderStark, RoundedCornerShape(6.dp))
                                                            .clickable {
                                                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(getUrl)).apply {
                                                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                                }
                                                                runCatching { context.startActivity(intent) }
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(
                                                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                                                contentDescription = null,
                                                                tint = HyperCyan,
                                                                modifier = Modifier.size(11.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(
                                                                text = "GET KEY",
                                                                color = HyperCyan,
                                                                fontFamily = BitcountPropSingle,
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                }

                                                // Paste button
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(32.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(SurfaceElevated)
                                                        .border(1.dp, BorderStark, RoundedCornerShape(6.dp))
                                                        .clickable {
                                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                                            val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                                                            if (!clipText.isNullOrBlank()) {
                                                                destinations = destinations.toMutableList().also { list ->
                                                                    list[index] = dest.copy(streamKey = clipText)
                                                                }
                                                                probeStatusMap = probeStatusMap - dest.platform
                                                            }
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            imageVector = Icons.Default.ContentPaste,
                                                            contentDescription = null,
                                                            tint = ToxicLime,
                                                            modifier = Modifier.size(11.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = "PASTE",
                                                            color = ToxicLime,
                                                            fontFamily = BitcountPropSingle,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }

                                                // Test Ingest button
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(32.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(HyperCyan.copy(alpha = 0.15f))
                                                        .border(1.dp, HyperCyan.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                                        .clickable(enabled = currentProbeStatus != ProbeStatus.PROBING) {
                                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            probeStatusMap = probeStatusMap + (dest.platform to (ProbeStatus.PROBING to "Testing socket handshake..."))

                                                            scope.launch {
                                                                val endpoint = dest.activeEndpointUrl
                                                                val result = RtmpConnection.testProbe(endpoint)
                                                                if (result.isSuccess) {
                                                                    probeStatusMap = probeStatusMap + (dest.platform to (ProbeStatus.SUCCESS to "Handshake OK"))
                                                                } else {
                                                                    probeStatusMap = probeStatusMap + (dest.platform to (ProbeStatus.FAILED to (result.exceptionOrNull()?.localizedMessage ?: "Failed")))
                                                                }
                                                            }
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (currentProbeStatus == ProbeStatus.PROBING) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(12.dp),
                                                            strokeWidth = 1.5.dp,
                                                            color = HyperCyan
                                                        )
                                                    } else {
                                                        Text(
                                                            text = "TEST INGEST",
                                                            color = HyperCyan,
                                                            fontFamily = BitcountPropSingle,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }

                                            // Probe result message
                                            if (currentProbeStatus != ProbeStatus.IDLE) {
                                                Spacer(modifier = Modifier.height(6.dp))
                                                val statusColor = when (currentProbeStatus) {
                                                    ProbeStatus.SUCCESS -> ToxicLime
                                                    ProbeStatus.FAILED -> HyperCrimson
                                                    ProbeStatus.PROBING -> CyberYellow
                                                    ProbeStatus.IDLE -> TextMuted
                                                }
                                                Text(
                                                    text = "• $probeMsg",
                                                    color = statusColor,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 2. Multistream Telemetry & Codec Advisory Banner
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceCard)
                                .border(1.dp, BorderStark, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "MULTISTREAM TELEMETRY",
                                        color = TextPrimary,
                                        fontFamily = BitcountPropSingle,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "~${"%.1f".format(totalBandwidthMbps)} MBPS UPLINK",
                                        color = if (totalBandwidthMbps > 15f) CyberYellow else HyperCyan,
                                        fontFamily = BitcountPropSingle,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                if (anyRequiresAvc) {
                                    Text(
                                        text = "⚡ CODEC LOCKED TO AVC (H.264) • Required for Twitch / Kick compatibility across all live destinations.",
                                        color = CyberYellow,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                } else {
                                    Text(
                                        text = "✨ ENHANCED RTMP (HEVC) READY • High-efficiency 1440p streaming supported by all selected platforms.",
                                        color = ToxicLime,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                if (totalActiveConfigured > 1) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Zero GPU duplication: screen pixels encoded once; network packets duplicated across $totalActiveConfigured sockets with <0.5% CPU load.",
                                        color = TextSecondary,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // 3. Global Toggles
                        // Dual Vault Master Archive
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceCard)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "DUAL MASTER VAULT ARCHIVE",
                                    color = TextPrimary,
                                    fontFamily = BitcountPropSingle,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (saveMasterArchive) {
                                        "Saves local master copy (~$estimatedRateText/hr)"
                                    } else {
                                        "Pure Stream Mode • Zero disk space used"
                                    },
                                    color = if (saveMasterArchive) ToxicLime else HyperCyan,
                                    fontSize = 10.sp,
                                    fontFamily = BitcountPropSingle
                                )
                            }
                            Switch(
                                checked = saveMasterArchive,
                                onCheckedChange = { saveMasterArchive = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = ObsidianCanvas,
                                    checkedTrackColor = ToxicLime,
                                    uncheckedThumbColor = TextMuted,
                                    uncheckedTrackColor = SurfaceElevated
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Adaptive Bitrate Engine (ABR)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceCard)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "ADAPTIVE BITRATE (ABR)",
                                    color = TextPrimary,
                                    fontFamily = BitcountPropSingle,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Auto-throttles video bitrate on network congestion across destinations",
                                    color = TextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                            Switch(
                                checked = enableAbr,
                                onCheckedChange = { enableAbr = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = ObsidianCanvas,
                                    checkedTrackColor = HyperCyan,
                                    uncheckedThumbColor = TextMuted,
                                    uncheckedTrackColor = SurfaceElevated
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Enhanced RTMP (HEVC)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceCard)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "ENHANCED RTMP (HEVC)",
                                    color = TextPrimary,
                                    fontFamily = BitcountPropSingle,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Enables H.265 fourCC for YouTube Live / Custom RTMP",
                                    color = TextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                            Switch(
                                checked = useEnhancedHevc,
                                onCheckedChange = { useEnhancedHevc = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = ObsidianCanvas,
                                    checkedTrackColor = HyperCyan,
                                    uncheckedThumbColor = TextMuted,
                                    uncheckedTrackColor = SurfaceElevated
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Bottom Action Button: [ SAVE & APPLY ]
                    val canStream = totalActiveConfigured > 0
                    val buttonBg = if (canStream) HyperCyan else SurfaceElevated
                    val buttonTextColor = if (canStream) ObsidianCanvas else TextMuted
                    val buttonText = if (totalActiveConfigured > 1) {
                        "READY TO MULTISTREAM ($totalActiveConfigured DESTINATIONS)"
                    } else if (totalActiveConfigured == 1) {
                        "READY TO STREAM (${activeDestinations.first().platform.displayName.uppercase()})"
                    } else {
                        "CONFIGURE AT LEAST 1 DESTINATION"
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(buttonBg)
                            .clickable(enabled = canStream) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                val primaryPlatform = activeDestinations.firstOrNull()?.platform ?: destinations.first().platform
                                val customEndpoint = destinations.find { it.platform == StreamPlatform.CUSTOM }?.customEndpointUrl ?: ""
                                val primaryStreamKey = destinations.find { it.platform == primaryPlatform }?.streamKey ?: ""

                                val updated = streamConfig.copy(
                                    platform = primaryPlatform,
                                    customEndpointUrl = customEndpoint.trim(),
                                    streamKey = primaryStreamKey.trim(),
                                    destinations = destinations,
                                    enableAbr = enableAbr,
                                    saveLocalMasterArchive = saveMasterArchive,
                                    useEnhancedHevc = useEnhancedHevc
                                )
                                onSaveConfig(updated)
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = buttonText,
                            color = buttonTextColor,
                            fontFamily = BitcountPropSingle,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}
