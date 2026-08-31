package pixl.rec.ui.vault.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import pixl.rec.R
import pixl.rec.core.storage.StorageCalculator
import pixl.rec.service.FloatingOverlayService
import pixl.rec.ui.theme.BitcountPropSingle
import pixl.rec.ui.theme.BorderStark
import pixl.rec.ui.theme.HyperCyan
import pixl.rec.ui.theme.ObsidianCanvas
import pixl.rec.ui.theme.SurfaceElevated
import pixl.rec.ui.theme.TextPrimary
import pixl.rec.ui.theme.TextSecondary
import pixl.rec.ui.theme.ToxicLime
import pixl.rec.ui.vault.model.RecordingItem

@OptIn(UnstableApi::class)
@Composable
fun VaultVideoPlayer(
    item: RecordingItem,
    onClose: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Handle physical / gesture back press
    BackHandler(onBack = onClose)

    // Temporarily hide floating standby / recording overlay while in the video player
    DisposableEffect(Unit) {
        FloatingOverlayService.setTemporarilyHidden(true)
        onDispose {
            FloatingOverlayService.setTemporarilyHidden(false)
        }
    }

    // Configure ultra-low buffer load control for instantaneous seeking on local files
    val loadControl = remember {
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                500,  // minBufferMs (minimal buffering for local storage)
                1500, // maxBufferMs
                50,   // bufferForPlaybackMs (instant response upon seeking)
                50    // bufferForPlaybackAfterRebufferMs (instant playback resume)
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }

    // Initialize ExoPlayer with closest keyframe sync seeking for zero-freeze scrubbing
    val exoPlayer = remember(context, item.uri) {
        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setSeekParameters(SeekParameters.CLOSEST_SYNC)
            .build().apply {
                val mediaItem = MediaItem.fromUri(item.uri)
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
            }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(item.durationMs.coerceAtLeast(1L)) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekFraction by remember { mutableFloatStateOf(0f) }
    var areControlsVisible by remember { mutableStateOf(true) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var isLooping by remember { mutableStateOf(false) }

    // Listen to Player state changes
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    val realDuration = exoPlayer.duration
                    if (realDuration > 0) {
                        durationMs = realDuration
                    }
                } else if (state == Player.STATE_ENDED) {
                    isPlaying = false
                }
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Auto-progress tracker ticker (50ms for smooth millisecond timecode)
    LaunchedEffect(exoPlayer) {
        while (isActive) {
            if (!isSeeking) {
                currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                val dur = exoPlayer.duration
                if (dur > 0) {
                    durationMs = dur
                }
            }
            delay(50)
        }
    }

    // Auto-hide controls timer (3.5 seconds)
    LaunchedEffect(areControlsVisible, isPlaying) {
        if (areControlsVisible && isPlaying && !isSeeking) {
            delay(3500)
            areControlsVisible = false
        }
    }

    // 100% Solid Opaque Fullscreen Container (Zero leak-through)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .zIndex(100f)
            .pointerInput(Unit) {
                detectTapGestures {
                    areControlsVisible = !areControlsVisible
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // 1. Hardware Accelerated Video Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    player = exoPlayer
                    setBackgroundColor(android.graphics.Color.BLACK)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        )

        // 2. Custom Cyberpunk HUD Overlay Controls
        AnimatedVisibility(
            visible = areControlsVisible,
            enter = fadeIn(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.85f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.90f)
                            )
                        )
                    )
            ) {
                // Top HUD Bar: Close, Title, Specs, Share, External
                TopHudBar(
                    item = item,
                    onClose = onClose,
                    onShare = onShare,
                    onOpenExternal = {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(item.uri, "video/mp4")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Open Video"))
                    },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // Center Controls: Rewind 5s, Play/Pause, Forward 5s (Icon only in white with faint dark shadow)
                CenterPlaybackControls(
                    isPlaying = isPlaying,
                    onPlayPause = {
                        if (isPlaying) {
                            exoPlayer.pause()
                        } else {
                            if (exoPlayer.playbackState == Player.STATE_ENDED) {
                                exoPlayer.seekTo(0)
                            }
                            exoPlayer.play()
                        }
                    },
                    onSeekBackward = {
                        val target = (exoPlayer.currentPosition - 5000L).coerceAtLeast(0L)
                        exoPlayer.seekTo(target)
                    },
                    onSeekForward = {
                        val target = (exoPlayer.currentPosition + 5000L).coerceAtMost(durationMs)
                        exoPlayer.seekTo(target)
                    },
                    modifier = Modifier.align(Alignment.Center)
                )

                // Bottom HUD Deck: Timecode, Scrubber, Speed Chips, Loop
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BottomHudDeck(
                        currentPositionMs = currentPositionMs,
                        durationMs = durationMs,
                        isSeeking = isSeeking,
                        seekFraction = seekFraction,
                        onSeekStart = {
                            isSeeking = true
                        },
                        onSeekChange = { fraction ->
                            seekFraction = fraction
                            currentPositionMs = (fraction * durationMs).toLong()
                        },
                        onSeekEnd = {
                            val targetMs = (seekFraction * durationMs).toLong()
                            currentPositionMs = targetMs
                            exoPlayer.seekTo(targetMs)
                            isSeeking = false
                        },
                        playbackSpeed = playbackSpeed,
                        onSpeedSelected = { speed ->
                            playbackSpeed = speed
                            exoPlayer.setPlaybackSpeed(speed)
                        },
                        isLooping = isLooping,
                        onToggleLoop = {
                            isLooping = !isLooping
                            exoPlayer.repeatMode = if (isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                        },
                        modifier = Modifier.widthIn(max = 680.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TopHudBar(
    item: RecordingItem,
    onClose: () -> Unit,
    onShare: () -> Unit,
    onOpenExternal: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            // Close Button (Pixel 'X')
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(SurfaceElevated, RoundedCornerShape(8.dp))
                    .border(1.5.dp, BorderStark, RoundedCornerShape(8.dp))
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_pixel_close),
                    contentDescription = "Close Player",
                    tint = TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayName,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontFamily = BitcountPropSingle,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${item.width}x${item.height} • ${item.formattedSize}",
                    color = HyperCyan,
                    fontSize = 11.sp,
                    fontFamily = BitcountPropSingle,
                    fontWeight = FontWeight.Normal
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Share Button (Pixel Share Icon)
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(SurfaceElevated, RoundedCornerShape(8.dp))
                    .border(1.5.dp, BorderStark, RoundedCornerShape(8.dp))
                    .clickable(onClick = onShare),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_pixel_share),
                    contentDescription = "Share",
                    tint = TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Open in External App Button (Pixel External Window Icon)
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(SurfaceElevated, RoundedCornerShape(8.dp))
                    .border(1.5.dp, BorderStark, RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenExternal),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_pixel_external),
                    contentDescription = "Open External",
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun CenterPlaybackControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(36.dp)
    ) {
        // -5s Seek Backward (Icon only in white with dark contrast shadow)
        Box(
            modifier = Modifier
                .size(56.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, radius = 28.dp),
                    onClick = onSeekBackward
                ),
            contentAlignment = Alignment.Center
        ) {
            // Dark shadow underneath for contrast against bright video frames
            Icon(
                painter = painterResource(id = R.drawable.ic_pixel_replay_5),
                contentDescription = null,
                tint = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier
                    .size(34.dp)
                    .offset(x = 1.dp, y = 1.5.dp)
            )
            Icon(
                painter = painterResource(id = R.drawable.ic_pixel_replay_5),
                contentDescription = "Seek -5s",
                tint = Color.White,
                modifier = Modifier.size(34.dp)
            )
        }

        // Giant Center Play/Pause (Icon only in white with dark contrast shadow)
        Box(
            modifier = Modifier
                .size(72.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, radius = 36.dp),
                    onClick = onPlayPause
                ),
            contentAlignment = Alignment.Center
        ) {
            // Dark shadow underneath for contrast against bright video frames
            Icon(
                painter = painterResource(id = if (isPlaying) R.drawable.ic_pixel_pause else R.drawable.ic_pixel_play),
                contentDescription = null,
                tint = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier
                    .size(50.dp)
                    .offset(x = 1.5.dp, y = 2.dp)
            )
            Icon(
                painter = painterResource(id = if (isPlaying) R.drawable.ic_pixel_pause else R.drawable.ic_pixel_play),
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(50.dp)
            )
        }

        // +5s Seek Forward (Icon only in white with dark contrast shadow)
        Box(
            modifier = Modifier
                .size(56.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, radius = 28.dp),
                    onClick = onSeekForward
                ),
            contentAlignment = Alignment.Center
        ) {
            // Dark shadow underneath for contrast against bright video frames
            Icon(
                painter = painterResource(id = R.drawable.ic_pixel_forward_5),
                contentDescription = null,
                tint = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier
                    .size(34.dp)
                    .offset(x = 1.dp, y = 1.5.dp)
            )
            Icon(
                painter = painterResource(id = R.drawable.ic_pixel_forward_5),
                contentDescription = "Seek +5s",
                tint = Color.White,
                modifier = Modifier.size(34.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BottomHudDeck(
    currentPositionMs: Long,
    durationMs: Long,
    isSeeking: Boolean,
    seekFraction: Float,
    onSeekStart: () -> Unit,
    onSeekChange: (Float) -> Unit,
    onSeekEnd: () -> Unit,
    playbackSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    isLooping: Boolean,
    onToggleLoop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceElevated.copy(alpha = 0.95f), RoundedCornerShape(12.dp))
            .border(1.5.dp, BorderStark, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // 1. Digital Timecode Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = StorageCalculator.formatTimecode(currentPositionMs),
                color = ToxicLime,
                fontSize = 13.sp,
                fontFamily = BitcountPropSingle,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )

            Text(
                text = StorageCalculator.formatTimecode(durationMs),
                color = TextSecondary,
                fontSize = 13.sp,
                fontFamily = BitcountPropSingle,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // 2. Cyberpunk Neon Scrubber Slider
        val currentFraction = if (isSeeking) seekFraction else (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

        Slider(
            value = currentFraction,
            onValueChange = { fraction ->
                if (!isSeeking) onSeekStart()
                onSeekChange(fraction)
            },
            onValueChangeFinished = onSeekEnd,
            colors = SliderDefaults.colors(
                thumbColor = ToxicLime,
                activeTrackColor = ToxicLime,
                inactiveTrackColor = ObsidianCanvas
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
        )

        Spacer(modifier = Modifier.height(6.dp))

        // 3. Playback Speed Selector & Loop Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Speed Chips
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0.25f, 0.5f, 1.0f, 1.5f, 2.0f).forEach { speed ->
                    val isSelected = playbackSpeed == speed
                    Box(
                        modifier = Modifier
                            .background(
                                if (isSelected) ToxicLime else ObsidianCanvas,
                                RoundedCornerShape(4.dp)
                            )
                            .border(
                                1.dp,
                                if (isSelected) ToxicLime else BorderStark,
                                RoundedCornerShape(4.dp)
                            )
                            .clickable { onSpeedSelected(speed) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${speed}X",
                            color = if (isSelected) Color.Black else TextSecondary,
                            fontSize = 10.sp,
                            fontFamily = BitcountPropSingle,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Loop Toggle
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(
                        if (isLooping) HyperCyan.copy(alpha = 0.2f) else ObsidianCanvas,
                        RoundedCornerShape(6.dp)
                    )
                    .border(
                        1.dp,
                        if (isLooping) HyperCyan else BorderStark,
                        RoundedCornerShape(6.dp)
                    )
                    .clickable(onClick = onToggleLoop),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isLooping) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    contentDescription = "Loop",
                    tint = if (isLooping) HyperCyan else TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
