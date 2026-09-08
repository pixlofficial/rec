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
import pixl.rec.core.model.StreamPlatform
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
 * Cyberpunk-styled Live Stream Pre-Flight & Setup Modal.
 * Facilitates quick zero-friction platform configuration, Keystore-backed stream key entry,
 * 1-second background socket probe test, and dual-output toggles.
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

    var activePlatform by remember { mutableStateOf(streamConfig.platform) }
    var streamKey by remember { mutableStateOf(streamConfig.streamKey) }
    var customEndpoint by remember { mutableStateOf(streamConfig.customEndpointUrl) }
    var keyVisible by remember { mutableStateOf(false) }

    var enableAbr by remember { mutableStateOf(streamConfig.enableAbr) }
    var saveMasterArchive by remember { mutableStateOf(streamConfig.saveLocalMasterArchive) }
    var useEnhancedHevc by remember { mutableStateOf(streamConfig.useEnhancedHevc) }

    var probeStatus by remember { mutableStateOf(ProbeStatus.IDLE) }
    var probeMessage by remember { mutableStateOf("") }

    val currentEndpoint = if (activePlatform == StreamPlatform.CUSTOM) {
        customEndpoint.trim()
    } else {
        activePlatform.defaultEndpoint
    }

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
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.88f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(ObsidianCanvas)
                    .border(1.5.dp, HyperCyan.copy(alpha = 0.65f), RoundedCornerShape(24.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Prevent dismissal on modal content tap
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                ) {
                    // Header Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(HyperCyan.copy(alpha = 0.15f))
                                    .border(1.dp, HyperCyan.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_pixel_stream),
                                    contentDescription = null,
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
                                    text = "ZERO-COPY LIVE RTMP ENGINE",
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

                    // Scrollable Configuration Deck
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // 1. Platform Selector
                        Text(
                            text = "DESTINATION PLATFORM",
                            color = TextSecondary,
                            fontFamily = BitcountPropSingle,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            StreamPlatform.entries.forEach { platform ->
                                val isSelected = activePlatform == platform
                                val activeColor = if (isSelected) HyperCyan else TextMuted
                                val activeBg = if (isSelected) HyperCyan.copy(alpha = 0.12f) else SurfaceElevated

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(activeBg)
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) HyperCyan.copy(alpha = 0.8f) else BorderStark,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            activePlatform = platform
                                            probeStatus = ProbeStatus.IDLE
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = platform.displayName,
                                        color = activeColor,
                                        fontFamily = BitcountPropSingle,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Custom Ingest Endpoint Input (if CUSTOM platform)
                        if (activePlatform == StreamPlatform.CUSTOM) {
                            Text(
                                text = "RTMP INGEST URL",
                                color = TextSecondary,
                                fontFamily = BitcountPropSingle,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = customEndpoint,
                                onValueChange = {
                                    customEndpoint = it
                                    probeStatus = ProbeStatus.IDLE
                                },
                                placeholder = {
                                    Text("rtmp://live.myserver.com/app", color = TextMuted, fontSize = 12.sp)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                textStyle = TextStyle(color = TextPrimary, fontSize = 12.sp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = HyperCyan,
                                    unfocusedBorderColor = BorderStark,
                                    focusedContainerColor = SurfaceElevated,
                                    unfocusedContainerColor = SurfaceElevated
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        // 2. Stream Key Section
                        Text(
                            text = "STREAM KEY (ENCRYPTED KEYSTORE)",
                            color = TextSecondary,
                            fontFamily = BitcountPropSingle,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = streamKey,
                            onValueChange = {
                                streamKey = it
                                probeStatus = ProbeStatus.IDLE
                            },
                            placeholder = {
                                Text("Paste or enter stream key", color = TextMuted, fontSize = 12.sp)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                            trailingIcon = {
                                Icon(
                                    imageVector = if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle Visibility",
                                    tint = TextSecondary,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable {
                                            keyVisible = !keyVisible
                                        }
                                )
                            },
                            textStyle = TextStyle(color = TextPrimary, fontSize = 12.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = HyperCyan,
                                unfocusedBorderColor = BorderStark,
                                focusedContainerColor = SurfaceElevated,
                                unfocusedContainerColor = SurfaceElevated
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Quick Key Helpers: [ GET KEY ] & [ PASTE ]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Get Key button
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceElevated)
                                    .border(1.dp, BorderStark, RoundedCornerShape(8.dp))
                                    .clickable {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        val url = when (activePlatform) {
                                            StreamPlatform.YOUTUBE -> "https://studio.youtube.com/channel/UC/livestreaming"
                                            StreamPlatform.TWITCH -> "https://dashboard.twitch.tv/settings/stream"
                                            StreamPlatform.KICK -> "https://kick.com/dashboard/settings/stream"
                                            StreamPlatform.CUSTOM -> null
                                        }
                                        if (url != null) {
                                            runCatching {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = null,
                                        tint = HyperCyan,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "GET KEY",
                                        color = HyperCyan,
                                        fontFamily = BitcountPropSingle,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Paste Button
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceElevated)
                                    .border(1.dp, BorderStark, RoundedCornerShape(8.dp))
                                    .clickable {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                                        val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                                        if (!clipText.isNullOrBlank()) {
                                            streamKey = clipText
                                            probeStatus = ProbeStatus.IDLE
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = null,
                                        tint = ToxicLime,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "PASTE",
                                        color = ToxicLime,
                                        fontFamily = BitcountPropSingle,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 3. Pre-Flight Connection Probe
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
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "INGEST PRE-FLIGHT TEST",
                                            color = TextPrimary,
                                            fontFamily = BitcountPropSingle,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Tests socket handshake and endpoint reachability",
                                            color = TextSecondary,
                                            fontSize = 10.sp
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                if (probeStatus == ProbeStatus.PROBING) SurfaceElevated else HyperCyan.copy(alpha = 0.15f)
                                            )
                                            .border(1.dp, HyperCyan.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                            .clickable(enabled = probeStatus != ProbeStatus.PROBING) {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                probeStatus = ProbeStatus.PROBING
                                                probeMessage = "Testing RTMP handshake..."

                                                scope.launch {
                                                    val result = RtmpConnection.testProbe(currentEndpoint)
                                                    if (result.isSuccess) {
                                                        probeStatus = ProbeStatus.SUCCESS
                                                        probeMessage = "Ingest Reachable & Handshake OK"
                                                    } else {
                                                        probeStatus = ProbeStatus.FAILED
                                                        probeMessage = result.exceptionOrNull()?.localizedMessage ?: "Connection Failed"
                                                    }
                                                }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (probeStatus == ProbeStatus.PROBING) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(12.dp),
                                                strokeWidth = 1.5.dp,
                                                color = HyperCyan
                                            )
                                        } else {
                                            Text(
                                                text = "TEST",
                                                color = HyperCyan,
                                                fontFamily = BitcountPropSingle,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                AnimatedVisibility(visible = probeStatus != ProbeStatus.IDLE) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val statusColor = when (probeStatus) {
                                        ProbeStatus.SUCCESS -> ToxicLime
                                        ProbeStatus.FAILED -> HyperCrimson
                                        ProbeStatus.PROBING -> CyberYellow
                                        ProbeStatus.IDLE -> TextMuted
                                    }
                                    Text(
                                        text = "• $probeMessage",
                                        color = statusColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // 4. Feature Toggles
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
                                Text(
                                    text = "Simultaneously saves uncompressed MP4 to Vault",
                                    color = TextSecondary,
                                    fontSize = 10.sp
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
                                    text = "Auto-throttles video bitrate on network congestion",
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
                                    text = "Enables H.265 fourCC for 1440p YouTube Live",
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(HyperCyan)
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                val updated = streamConfig.copy(
                                    platform = activePlatform,
                                    customEndpointUrl = customEndpoint.trim(),
                                    streamKey = streamKey.trim(),
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
                            text = "READY TO STREAM",
                            color = ObsidianCanvas,
                            fontFamily = BitcountPropSingle,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}
