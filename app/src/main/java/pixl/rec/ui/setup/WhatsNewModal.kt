package pixl.rec.ui.setup

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

/**
 * Patch category badge types for release highlights.
 */
enum class PatchCategory(val label: String, val color: Color) {
    ADDED("ADDED", ToxicLime),
    CHANGED("CHANGED", HyperCyan),
    FIXED("FIXED", CyberYellow)
}

data class PatchNote(
    val title: String,
    val description: String,
    val category: PatchCategory
)

/**
 * Cyberpunk-themed modal presenting release highlights and changelog updates
 * following an application version bump.
 */
@Composable
fun WhatsNewModal(
    versionName: String,
    onDismiss: () -> Unit
) {
    val haptics = LocalHapticFeedback.current

    val patchNotes = remember {
        listOf(
            PatchNote(
                title = "Hardware A/V Sync Calibration",
                description = "Eliminated 50–90ms hardware encoder startup delay from video presentation timestamps, locking audio and video together with nano-precision.",
                category = PatchCategory.FIXED
            ),
            PatchNote(
                title = "OBS-Style A/V Sync Offset Tuning",
                description = "Fine-tune audio sync with a -200ms to +200ms slider and one-tap preset pills (-50ms, 0ms, +25ms, +50ms, +100ms) in Settings > Audio.",
                category = PatchCategory.ADDED
            ),
            PatchNote(
                title = "Instant Replay Buffer Engine",
                description = "Volatile RAM circular ring buffer continuously caching 15s–120s of gameplay with 1-tap tactical clip export to Scoped Storage.",
                category = PatchCategory.ADDED
            ),
            PatchNote(
                title = "Tactical Dual-Mode Screenshots",
                description = "2-3ms async hardware keyframe decoding during active gameplay plus standby invisible trampoline screen capture.",
                category = PatchCategory.ADDED
            ),
            PatchNote(
                title = "Media Vault Hybrid Hub",
                description = "Interactive 2x2 telemetry deck tracking recordings, streams, replays, and screenshots with 1-tap sliding filter navigation.",
                category = PatchCategory.ADDED
            )
        )
    }

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
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // 1. Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                                    text = "SYSTEM // PATCH NOTES",
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
                                        text = "v$versionName",
                                        color = ToxicLime,
                                        fontFamily = BitcountPropSingle,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = "WHAT'S NEW",
                                color = TextPrimary,
                                fontFamily = BitcountPropSingle,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Close icon button
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
                    text = "Summary of major enhancements, engine upgrades, and stability fixes in this release.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 2. Scrollable Patch Notes Items
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    patchNotes.forEach { note ->
                        PatchNoteCard(note = note)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3. Bottom Action Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ToxicLime.copy(alpha = 0.15f))
                        .border(1.5.dp, ToxicLime, RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(color = ToxicLime),
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onDismiss()
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ACKNOWLEDGE // CONTINUE",
                        color = ToxicLime,
                        fontFamily = BitcountPropSingle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun PatchNoteCard(note: PatchNote) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceCard)
            .border(1.dp, BorderStark, RoundedCornerShape(10.dp))
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = note.title,
                    color = TextPrimary,
                    fontFamily = BitcountPropSingle,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(note.category.color.copy(alpha = 0.15f))
                        .border(1.dp, note.category.color, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = note.category.label,
                        color = note.category.color,
                        fontFamily = BitcountPropSingle,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = note.description,
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}
