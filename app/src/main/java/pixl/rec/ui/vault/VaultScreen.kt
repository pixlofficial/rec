package pixl.rec.ui.vault

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pixl.rec.ui.components.ActionButton
import pixl.rec.ui.components.ActionButtonVariant
import pixl.rec.ui.components.SectionCard
import pixl.rec.ui.components.TelemetryBadge
import pixl.rec.ui.theme.BitcountPropSingle
import pixl.rec.ui.theme.BorderHighlight
import pixl.rec.ui.theme.BorderStark
import pixl.rec.ui.theme.CyberYellow
import pixl.rec.ui.theme.HyperCrimson
import pixl.rec.ui.theme.ObsidianCanvas
import pixl.rec.ui.theme.SurfaceElevated
import pixl.rec.ui.theme.SurfaceRaised
import pixl.rec.ui.theme.TextInverse
import pixl.rec.ui.theme.TextMuted
import pixl.rec.ui.theme.TextPrimary
import pixl.rec.ui.theme.TextSecondary
import pixl.rec.ui.theme.ToxicLime
import pixl.rec.ui.vault.model.RecordingItem

@Composable
fun VaultScreen(
    vaultViewModel: VaultViewModel,
    onRequestRecord: () -> Unit
) {
    val context = LocalContext.current
    val recordings by vaultViewModel.recordings.collectAsState()
    val isLoading by vaultViewModel.isLoading.collectAsState()

    Scaffold(
        containerColor = ObsidianCanvas
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 1. Vault Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "RECORDING VAULT",
                        color = TextPrimary,
                        fontSize = 24.sp,
                        fontFamily = BitcountPropSingle,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "LOCAL SCOPED STORAGE // MOVIES",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontFamily = BitcountPropSingle
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TelemetryBadge(
                        label = "CLIPS",
                        value = "${recordings.size}",
                        accentColor = if (recordings.isNotEmpty()) ToxicLime else TextMuted
                    )

                    IconButton(
                        onClick = { vaultViewModel.refreshRecordings() },
                        modifier = Modifier
                            .size(36.dp)
                            .background(SurfaceElevated, RoundedCornerShape(8.dp))
                            .border(1.dp, BorderStark, RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Recordings",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Content List or Empty State
            if (recordings.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    SectionCard(
                        title = "ZERO RECORDINGS",
                        titleTag = "STANDBY",
                        tagColor = Color.White
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No local screen recordings found.",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontFamily = BitcountPropSingle,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Videos captured with REC will automatically appear here in full quality.",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontFamily = BitcountPropSingle,
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            ActionButton(
                                text = "START FIRST RECORDING",
                                onClick = onRequestRecord,
                                variant = ActionButtonVariant.PRIMARY,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.FiberManualRecord,
                                        contentDescription = null,
                                        tint = HyperCrimson,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = recordings,
                        key = { it.id }
                    ) { item ->
                        RecordingCard(
                            item = item,
                            onPlay = { vaultViewModel.playRecording(context, item) },
                            onShare = { vaultViewModel.shareRecording(context, item) },
                            onDelete = { vaultViewModel.deleteRecording(context, item) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingCard(
    item: RecordingItem,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceElevated, RoundedCornerShape(12.dp))
            .border(1.5.dp, BorderStark, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column {
            // Thumbnail & Play Overlay Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Thumbnail Box
                Box(
                    modifier = Modifier
                        .size(width = 100.dp, height = 64.dp)
                        .background(SurfaceRaised, RoundedCornerShape(8.dp))
                        .border(1.dp, BorderStark, RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onPlay),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.thumbnail != null) {
                        Image(
                            bitmap = item.thumbnail.asImageBitmap(),
                            contentDescription = item.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(ObsidianCanvas.copy(alpha = 0.7f), CircleShape)
                            .border(1.dp, BorderHighlight, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Metadata Column
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = item.displayName,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontFamily = BitcountPropSingle,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${item.formattedSize} • ${item.formattedDuration}",
                        color = ToxicLime,
                        fontSize = 12.sp,
                        fontFamily = BitcountPropSingle,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.formattedDate,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = BitcountPropSingle
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons Row (Play, Share, Delete)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Share Button
                IconButton(
                    onClick = onShare,
                    modifier = Modifier
                        .size(32.dp)
                        .background(SurfaceRaised, RoundedCornerShape(6.dp))
                        .border(1.dp, BorderStark, RoundedCornerShape(6.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = TextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Delete Button
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(32.dp)
                        .background(SurfaceRaised, RoundedCornerShape(6.dp))
                        .border(1.dp, BorderStark, RoundedCornerShape(6.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = HyperCrimson,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
