package pixl.rec.ui.settings.sections

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pixl.rec.R
import pixl.rec.core.model.StreamPlatform
import pixl.rec.core.storage.ConfigPreferences
import pixl.rec.ui.components.SectionCard
import pixl.rec.ui.components.SlidingPillSelector
import pixl.rec.ui.dashboard.DashboardViewModel
import pixl.rec.ui.theme.BitcountPropSingle
import pixl.rec.ui.theme.BorderStark
import pixl.rec.ui.theme.CyberYellow
import pixl.rec.ui.theme.ElectricPurple
import pixl.rec.ui.theme.HyperCrimson
import pixl.rec.ui.theme.HyperCyan
import pixl.rec.ui.theme.ObsidianCanvas
import pixl.rec.ui.theme.SurfaceElevated
import pixl.rec.ui.theme.TextMuted
import pixl.rec.ui.theme.TextPrimary
import pixl.rec.ui.theme.TextSecondary
import pixl.rec.ui.theme.ToxicLime

private enum class PrimaryPlatformTab(val label: String) {
    YOUTUBE("YOUTUBE"),
    TWITCH("TWITCH"),
    KICK("KICK"),
    OTHER("OTHER")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamSettingsSection(
    viewModel: DashboardViewModel,
    isRecordingActive: Boolean,
    onNavigateToHudStudio: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val streamConfig by viewModel.streamConfig.collectAsState()
    var isKeyVisible by remember { mutableStateOf(false) }

    val activePrimaryTab = when (streamConfig.platform) {
        StreamPlatform.YOUTUBE -> PrimaryPlatformTab.YOUTUBE
        StreamPlatform.TWITCH -> PrimaryPlatformTab.TWITCH
        StreamPlatform.KICK -> PrimaryPlatformTab.KICK
        else -> PrimaryPlatformTab.OTHER
    }

    val platformAccentColor = when (activePrimaryTab) {
        PrimaryPlatformTab.YOUTUBE -> HyperCrimson
        PrimaryPlatformTab.TWITCH -> ElectricPurple
        PrimaryPlatformTab.KICK -> ToxicLime
        PrimaryPlatformTab.OTHER -> CyberYellow
    }

    // 1. Platform Preset Card
    SectionCard(title = "STREAMING PLATFORMS", titleTag = "PRESET") {
        Text(
            text = "Configure credentials, server endpoints, and streaming platform destinations.",
            color = TextSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        SlidingPillSelector(
            items = PrimaryPlatformTab.entries,
            selectedItem = activePrimaryTab,
            onItemSelected = { tab ->
                when (tab) {
                    PrimaryPlatformTab.YOUTUBE -> viewModel.selectStreamPlatform(StreamPlatform.YOUTUBE)
                    PrimaryPlatformTab.TWITCH -> viewModel.selectStreamPlatform(StreamPlatform.TWITCH)
                    PrimaryPlatformTab.KICK -> viewModel.selectStreamPlatform(StreamPlatform.KICK)
                    PrimaryPlatformTab.OTHER -> {
                        if (streamConfig.platform.isPrimary) {
                            val lastOther = ConfigPreferences.getLastOtherPlatform(context)
                            viewModel.selectStreamPlatform(lastOther)
                        }
                    }
                }
            },
            itemLabel = { it.label },
            height = 44.dp,
            activeColor = platformAccentColor,
            activeTextColor = platformAccentColor
        )

        // Smooth in-card expansion for EXTENDED DESTINATIONS when OTHER is active
        AnimatedVisibility(
            visible = activePrimaryTab == PrimaryPlatformTab.OTHER,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                Spacer(modifier = Modifier.height(14.dp))

                var isDropdownExpanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = isDropdownExpanded,
                    onExpandedChange = { isDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        value = "${streamConfig.platform.displayName.uppercase()}   [${streamConfig.platform.protocolTag}]",
                        onValueChange = {},
                        readOnly = true,
                        label = {
                            Text(
                                text = "EXTENDED DESTINATION",
                                fontFamily = BitcountPropSingle,
                                fontSize = 10.sp,
                                letterSpacing = 0.5.sp
                            )
                        },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded)
                        },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                            focusedBorderColor = CyberYellow,
                            unfocusedBorderColor = BorderStark,
                            focusedContainerColor = SurfaceElevated,
                            unfocusedContainerColor = SurfaceElevated,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedLabelColor = CyberYellow,
                            unfocusedLabelColor = TextSecondary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        textStyle = TextStyle(
                            fontFamily = BitcountPropSingle,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    )

                    ExposedDropdownMenu(
                        expanded = isDropdownExpanded,
                        onDismissRequest = { isDropdownExpanded = false },
                        modifier = Modifier
                            .background(SurfaceElevated)
                            .border(1.dp, BorderStark, RoundedCornerShape(8.dp))
                    ) {
                        StreamPlatform.EXTENDED_PLATFORMS.forEach { platform ->
                            val isSelected = streamConfig.platform == platform
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = platform.displayName,
                                            fontFamily = BitcountPropSingle,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) CyberYellow else TextPrimary
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (isSelected) CyberYellow.copy(alpha = 0.2f) else ObsidianCanvas)
                                                .border(0.5.dp, if (isSelected) CyberYellow else BorderStark, RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = platform.protocolTag,
                                                color = if (isSelected) CyberYellow else TextSecondary,
                                                fontSize = 9.sp,
                                                fontFamily = BitcountPropSingle,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    isDropdownExpanded = false
                                    viewModel.selectStreamPlatform(platform)
                                    ConfigPreferences.saveLastOtherPlatform(context, platform)
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // 2. Ingest Endpoint & Stream Key
    SectionCard(title = "INGEST & CREDENTIALS", titleTag = "KEYSTORE") {
        if (streamConfig.platform.isCustomEndpoint) {
            Text(
                text = if (streamConfig.platform == StreamPlatform.TIKTOK) "TIKTOK RTMP INGEST ENDPOINT:" else "CUSTOM RTMP / RTMPS ENDPOINT:",
                color = TextSecondary,
                fontSize = 11.sp,
                fontFamily = BitcountPropSingle
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = streamConfig.customEndpointUrl,
                onValueChange = { url ->
                    viewModel.saveCustomEndpoint(url)
                },
                placeholder = {
                    Text(
                        if (streamConfig.platform == StreamPlatform.TIKTOK) "rtmp://live-push.tiktok.com/live/" else "rtmp://your-server.com/live",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = HyperCyan,
                    unfocusedBorderColor = BorderStark,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
            if (streamConfig.platform == StreamPlatform.TIKTOK) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Requires TikTok Live Studio or 1,000+ follower creator RTMP stream key permissions.",
                    color = CyberYellow,
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp
                )
            }
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
            visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
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
                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(streamConfig.platform.dashboardUrl))
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

    // Broadcast Pill & HUD Studio Card (Point 2 Deep Link)
    SectionCard(
        title = "BROADCAST PILL & HUD STUDIO",
        titleTag = "CUSTOMIZE"
    ) {
        Text(
            text = "Customize the broadcast floating pill appearance, bi-directional laser sweep shine, and real-time Uplink Health Aura.",
            color = TextSecondary,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceElevated)
                .border(1.dp, CyberYellow.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onNavigateToHudStudio()
                }
                .padding(vertical = 12.dp, horizontal = 14.dp)
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
                        tint = CyberYellow,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "CUSTOMIZE STREAM PILL & LASER SHINE",
                            color = TextPrimary,
                            fontSize = 11.5.sp,
                            fontFamily = BitcountPropSingle,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Laser sweep speed, glow intensity & network health aura",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = CyberYellow,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}
