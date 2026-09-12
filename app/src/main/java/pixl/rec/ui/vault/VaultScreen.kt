package pixl.rec.ui.vault

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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
import pixl.rec.ui.theme.ElectricPurple
import pixl.rec.ui.theme.HyperCrimson
import pixl.rec.ui.theme.HyperCyan
import pixl.rec.ui.theme.ObsidianCanvas
import pixl.rec.ui.theme.SurfaceElevated
import pixl.rec.ui.theme.SurfaceRaised
import pixl.rec.ui.theme.TextMuted
import pixl.rec.ui.theme.TextPrimary
import pixl.rec.ui.theme.TextSecondary
import pixl.rec.ui.theme.ToxicLime
import pixl.rec.ui.vault.components.ScreenshotLightboxModal
import pixl.rec.ui.vault.components.VaultCategoryMetricsStrip
import pixl.rec.ui.vault.model.VaultMediaItem
import pixl.rec.ui.vault.model.VaultMediaType
import pixl.rec.ui.vault.player.VaultVideoPlayer

/**
 * Filter categories for the Media Vault.
 */
enum class VaultFilter(val label: String) {
    ALL("ALL"),
    RECORDINGS("RECORDINGS"),
    STREAMS("STREAMS"),
    REPLAYS("REPLAYS"),
    SCREENSHOTS("SCREENSHOTS")
}

@Composable
fun VaultScreen(
    vaultViewModel: VaultViewModel,
    onRequestRecord: () -> Unit
) {
    val context = LocalContext.current
    val mediaItems by vaultViewModel.mediaItems.collectAsState()
    val summary by vaultViewModel.summary.collectAsState()
    val isLoading by vaultViewModel.isLoading.collectAsState()
    val activePlayerRecording by vaultViewModel.activePlayerRecording.collectAsState()
    val activeImagePreviewItem by vaultViewModel.activeImagePreviewItem.collectAsState()
    var selectedFilter by remember { mutableStateOf(VaultFilter.ALL) }

    val filteredItems = remember(mediaItems, selectedFilter) {
        when (selectedFilter) {
            VaultFilter.ALL -> mediaItems
            VaultFilter.RECORDINGS -> mediaItems.filter { it.mediaType == VaultMediaType.RECORDING }
            VaultFilter.STREAMS -> mediaItems.filter { it.mediaType == VaultMediaType.STREAM }
            VaultFilter.REPLAYS -> mediaItems.filter { it.mediaType == VaultMediaType.REPLAY }
            VaultFilter.SCREENSHOTS -> mediaItems.filter { it.mediaType == VaultMediaType.SCREENSHOT }
        }
    }

    // Auto-refresh when Vault screen is entered
    LaunchedEffect(Unit) {
        vaultViewModel.refreshRecordings()
    }

    val spinTransition = rememberInfiniteTransition(label = "VaultRefreshSpin")
    val spinAngle by spinTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SpinAngle"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        // 1. Vault Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "MEDIA VAULT",
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontFamily = BitcountPropSingle,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "LOCAL SCOPED STORAGE // MOVIES & PICTURES",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontFamily = BitcountPropSingle
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TelemetryBadge(
                    label = "TOTAL",
                    value = "${summary.totalCount} ITEMS",
                    accentColor = if (summary.totalCount > 0) ToxicLime else TextMuted
                )

                // Refresh Button
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(SurfaceElevated, RoundedCornerShape(6.dp))
                        .border(
                            1.5.dp,
                            if (isLoading) ToxicLime else BorderStark,
                            RoundedCornerShape(6.dp)
                        )
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true),
                            onClick = { vaultViewModel.refreshRecordings() }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Media",
                        tint = if (isLoading) ToxicLime else TextSecondary,
                        modifier = Modifier
                            .size(16.dp)
                            .rotate(if (isLoading) spinAngle else 0f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Interactive Category Telemetry Hub (2x2 Grid)
        VaultCategoryMetricsStrip(
            summary = summary,
            selectedFilter = selectedFilter,
            onSelectFilter = { selectedFilter = it }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Sliding Filter Chips: ALL | RECORDINGS | STREAMS | REPLAYS | SCREENSHOTS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VaultFilter.entries.forEach { filter ->
                val isSelected = selectedFilter == filter
                val count = when (filter) {
                    VaultFilter.ALL -> summary.totalCount
                    VaultFilter.RECORDINGS -> summary.recordingCount
                    VaultFilter.STREAMS -> summary.streamCount
                    VaultFilter.REPLAYS -> summary.replayCount
                    VaultFilter.SCREENSHOTS -> summary.screenshotCount
                }
                val chipAccent = when (filter) {
                    VaultFilter.ALL -> TextPrimary
                    VaultFilter.RECORDINGS -> CyberYellow
                    VaultFilter.STREAMS -> HyperCyan
                    VaultFilter.REPLAYS -> ToxicLime
                    VaultFilter.SCREENSHOTS -> ElectricPurple
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) chipAccent.copy(alpha = 0.18f) else SurfaceElevated)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) chipAccent else BorderStark,
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true),
                            onClick = { selectedFilter = filter }
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${filter.label} ($count)",
                        color = if (isSelected) chipAccent else TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = BitcountPropSingle,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 4. Adaptive Media Feed
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "SCANNING STORAGE...",
                    color = TextMuted,
                    fontSize = 14.sp,
                    fontFamily = BitcountPropSingle
                )
            }
        } else if (filteredItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceElevated, RoundedCornerShape(12.dp))
                        .border(1.5.dp, BorderStark, RoundedCornerShape(12.dp))
                        .padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FiberManualRecord,
                        contentDescription = "Empty",
                        tint = TextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (mediaItems.isEmpty()) "NO MEDIA YET" else "NO ${selectedFilter.label} FOUND",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontFamily = BitcountPropSingle,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (mediaItems.isEmpty()) "Captured recordings, streams, clips, and screenshots will appear here." else "No files match the selected ${selectedFilter.label} filter.",
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontFamily = BitcountPropSingle
                    )
                    if (mediaItems.isEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        ActionButton(
                            text = "START FIRST RECORDING",
                            variant = ActionButtonVariant.PRIMARY,
                            onClick = onRequestRecord
                        )
                    }
                }
            }
        } else if (selectedFilter == VaultFilter.SCREENSHOTS) {
            // Adaptive 3-Column Photo Grid for Screenshots
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(
                    items = filteredItems,
                    key = { it.uri.toString() }
                ) { item ->
                    ScreenshotGridTile(
                        item = item,
                        onClick = { vaultViewModel.openImagePreview(item) }
                    )
                }
                item(span = { GridItemSpan(3) }) {
                    Spacer(modifier = Modifier.height(116.dp))
                }
            }
        } else {
            // Cinematic Card List for Videos (and mixed ALL feed)
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = filteredItems,
                    key = { it.uri.toString() }
                ) { item ->
                    VaultMediaCard(
                        item = item,
                        onOpen = { vaultViewModel.playMedia(context, item) },
                        onShare = { vaultViewModel.shareMedia(context, item) },
                        onDelete = { vaultViewModel.deleteMedia(context, item) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(116.dp))
                }
            }
        }
    }

    // Full-screen in-app video player modal
    activePlayerRecording?.let { recording ->
        VaultVideoPlayer(
            item = recording,
            onClose = { vaultViewModel.closeInAppPlayer() },
            onShare = { vaultViewModel.shareRecording(context, recording) }
        )
    }

    // Full-screen high-res screenshot lightbox modal
    activeImagePreviewItem?.let { imageItem ->
        ScreenshotLightboxModal(
            item = imageItem,
            onDismiss = { vaultViewModel.closeImagePreview() },
            onShare = { vaultViewModel.shareMedia(context, imageItem) },
            onDelete = {
                vaultViewModel.deleteMedia(context, imageItem)
                vaultViewModel.closeImagePreview()
            }
        )
    }
}

/**
 * High-density square image tile for the 3-column screenshots grid.
 */
@Composable
private fun ScreenshotGridTile(
    item: VaultMediaItem,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceRaised)
            .border(1.dp, BorderStark, RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onClick
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        if (item.thumbnail != null) {
            Image(
                bitmap = item.thumbnail.asImageBitmap(),
                contentDescription = item.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "📸",
                    fontSize = 20.sp
                )
            }
        }

        // Subdued Bottom Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(ObsidianCanvas.copy(alpha = 0.75f))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = item.formattedSize,
                color = TextSecondary,
                fontSize = 9.sp,
                fontFamily = BitcountPropSingle,
                maxLines = 1
            )
        }
    }
}

/**
 * Full-width cinematic card for Video & Media items.
 */
@Composable
private fun VaultMediaCard(
    item: VaultMediaItem,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val accent = item.mediaType.accentColor

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceElevated, RoundedCornerShape(12.dp))
            .border(1.dp, BorderStark, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onOpen
            )
            .padding(14.dp)
    ) {
        Column {
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
                        .clip(RoundedCornerShape(8.dp)),
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

                    if (item.isVideo) {
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
                    } else {
                        Text(
                            text = "📸",
                            fontSize = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Metadata Column
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(accent.copy(alpha = 0.16f), RoundedCornerShape(4.dp))
                                .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "${item.mediaType.badgeIconText} ${item.mediaType.badgeText}",
                                color = accent,
                                fontSize = 9.sp,
                                fontFamily = BitcountPropSingle,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Text(
                            text = item.displayName,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontFamily = BitcountPropSingle,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (item.formattedDuration.isNotEmpty()) {
                            "${item.formattedSize} • ${item.formattedDuration}"
                        } else {
                            item.formattedSize + if (item.formattedResolution.isNotEmpty()) " • ${item.formattedResolution}" else ""
                        },
                        color = accent,
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

            // Action Buttons Row (Play/View, Share, Delete)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButton(
                    text = if (item.isVideo) "PLAY" else "VIEW",
                    leadingIcon = {
                        Icon(
                            imageVector = if (item.isVideo) Icons.Default.PlayArrow else Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    variant = ActionButtonVariant.PRIMARY,
                    onClick = onOpen,
                    modifier = Modifier.weight(1f)
                )

                ActionButton(
                    text = "SHARE",
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    variant = ActionButtonVariant.SURFACE,
                    onClick = onShare,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(36.dp)
                        .background(SurfaceRaised, RoundedCornerShape(8.dp))
                        .border(1.dp, BorderStark, RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = HyperCrimson,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
