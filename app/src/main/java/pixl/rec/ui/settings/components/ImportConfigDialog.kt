package pixl.rec.ui.settings.components

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import pixl.rec.R
import pixl.rec.core.model.RecordingConfig
import pixl.rec.ui.theme.BitcountPropSingle
import pixl.rec.ui.theme.BorderStark
import pixl.rec.ui.theme.CyberYellow
import pixl.rec.ui.theme.HyperCrimson
import pixl.rec.ui.theme.HyperCyan
import pixl.rec.ui.theme.ObsidianCanvas
import pixl.rec.ui.theme.SurfaceCard
import pixl.rec.ui.theme.SurfaceElevated
import pixl.rec.ui.theme.TextPrimary
import pixl.rec.ui.theme.TextSecondary
import pixl.rec.ui.theme.ToxicLime
import java.util.Locale

/**
 * Cyberpunk modal for inspecting and confirming imported configuration profiles.
 */
@Composable
fun ImportConfigDialog(
    config: RecordingConfig,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = LocalHapticFeedback.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(16.dp))
                .background(ObsidianCanvas)
                .border(1.dp, BorderStark, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // 1. Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(HyperCyan.copy(alpha = 0.15f))
                                .border(1.dp, HyperCyan, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_pixel_rocket),
                                contentDescription = null,
                                tint = HyperCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "SYSTEM // IMPORT PROFILE",
                                    color = HyperCyan,
                                    fontFamily = BitcountPropSingle,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .background(ToxicLime.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                        .border(0.5.dp, ToxicLime.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "VALID",
                                        color = ToxicLime,
                                        fontFamily = BitcountPropSingle,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = "CONFIRM IMPORT",
                                color = TextPrimary,
                                fontFamily = BitcountPropSingle,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Close Button
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(SurfaceElevated)
                            .border(1.dp, BorderStark, CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(bounded = false, radius = 16.dp, color = TextPrimary),
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onDismiss()
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✕",
                            color = TextSecondary,
                            fontFamily = BitcountPropSingle,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Review the profile specifications below before applying them to your active recorder configuration.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 2. Spec Summary Cards
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Video Spec Card
                    SpecCard(
                        category = "VIDEO MATRIX",
                        accentColor = HyperCyan,
                        items = listOf(
                            "Resolution" to "${config.width}×${config.height} (${config.recordingOrientation.displayName})",
                            "Framerate" to "${config.framerate} FPS (DPI: ${config.dpi})",
                            "Video Codec" to "${config.videoCodec.name} (${config.bitrateMode.name})",
                            "Video Bitrate" to String.format(Locale.US, "%.1f Mbps", config.videoBitrate / 1_000_000f),
                            "Keyframe Interval" to String.format(Locale.US, "%.1fs", config.iFrameIntervalSeconds)
                        )
                    )

                    // Audio Spec Card
                    SpecCard(
                        category = "AUDIO PIPELINE",
                        accentColor = CyberYellow,
                        items = listOf(
                            "Audio Source" to config.audioSource.displayName,
                            "Audio Bitrate" to "${config.audioBitrate / 1000} kbps (${config.audioSampleRate / 1000} kHz)",
                            "Audio Channels" to if (config.audioChannelCount == 2) "Stereo (2-ch)" else "Mono (1-ch)",
                            "Gain Balance" to String.format(Locale.US, "Mic: %.1fx  •  Internal: %.1fx", config.micGain, config.internalAudioGain)
                        )
                    )

                    // HUD & Controls Spec Card
                    SpecCard(
                        category = "HUD & AUTOMATION",
                        accentColor = ToxicLime,
                        items = listOf(
                            "Floating HUD" to if (config.showFloatingPill) "Enabled (Always-On: ${if (config.alwaysOnFloatingPill) "Yes" else "No"})" else "Disabled",
                            "Docking Magnet" to config.hudSnapBehavior.displayName,
                            "Standby HUD" to "${config.standbyHudConfig.shape.displayName} (${config.standbyHudConfig.iconSizeDp}dp icon)",
                            "Recording HUD" to "${config.recordingHudConfig.shape.displayName} (Anim: ${config.recordingHudConfig.animation.displayName})",
                            "Shake to Stop" to if (config.shakeToStop) "Enabled" else "Disabled",
                            "Notifications" to if (config.standbyNotification) "Standby On" else "Silent"
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3. Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Cancel
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceElevated)
                            .border(1.dp, BorderStark, RoundedCornerShape(8.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(color = TextPrimary),
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onDismiss()
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "CANCEL",
                            color = TextSecondary,
                            fontFamily = BitcountPropSingle,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    // Apply
                    Box(
                        modifier = Modifier
                            .weight(1.4f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ToxicLime.copy(alpha = 0.15f))
                            .border(1.5.dp, ToxicLime, RoundedCornerShape(8.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(color = ToxicLime),
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onConfirm()
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "APPLY PROFILE",
                            color = ToxicLime,
                            fontFamily = BitcountPropSingle,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecCard(
    category: String,
    accentColor: Color,
    items: List<Pair<String, String>>
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceCard)
            .border(1.dp, BorderStark, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = category,
                    color = accentColor,
                    fontFamily = BitcountPropSingle,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            items.forEach { (label, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = BitcountPropSingle
                    )
                    Text(
                        text = value,
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
