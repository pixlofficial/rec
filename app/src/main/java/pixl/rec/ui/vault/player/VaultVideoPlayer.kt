package pixl.rec.ui.vault.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
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
import pixl.rec.ui.theme.HyperCrimson
import pixl.rec.ui.theme.HyperCyan
import pixl.rec.ui.theme.ObsidianCanvas
import pixl.rec.ui.theme.SurfaceElevated
import pixl.rec.ui.theme.TextPrimary
import pixl.rec.ui.theme.TextSecondary
import pixl.rec.ui.vault.model.RecordingItem
import kotlin.math.roundToInt

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private enum class GestureHudType {
    BRIGHTNESS,
    VOLUME
}

@OptIn(UnstableApi::class)
@Composable
fun VaultVideoPlayer(
    item: RecordingItem,
    onClose: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    // Handle physical / gesture back press
    BackHandler(onBack = onClose)

    // Temporarily hide floating standby / recording overlay while in the video player
    DisposableEffect(Unit) {
        FloatingOverlayService.setTemporarilyHidden(true)
        onDispose {
            FloatingOverlayService.setTemporarilyHidden(false)
        }
    }

    // Configure robust buffer load control for local playback of high-res (up to 4K / 120 FPS) recordings
    val loadControl = remember {
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15_000, // minBufferMs (15s minimum buffer to prevent underrun starvation)
                50_000, // maxBufferMs (50s maximum buffer)
                250,    // bufferForPlaybackMs (fast 250ms initial startup)
                500     // bufferForPlaybackAfterRebufferMs (500ms resume)
            )
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()
    }

    // Initialize ExoPlayer with Movie AudioAttributes and exact seeking capability
    val exoPlayer = remember(context, item.uri) {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .setUsage(C.USAGE_MEDIA)
            .build()

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .setSeekParameters(SeekParameters.EXACT)
            .build().apply {
                val mediaItem = MediaItem.fromUri(item.uri)
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
            }
    }

    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(true) }
    var playbackErrorMessage by remember { mutableStateOf<String?>(null) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(item.durationMs.coerceAtLeast(1L)) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekFraction by remember { mutableFloatStateOf(0f) }
    var areControlsVisible by remember { mutableStateOf(true) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var isLooping by remember { mutableStateOf(false) }

    // Aspect ratio & rotation states
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    val isRecordingLandscape = remember(item.width, item.height) { item.width > item.height }
    var isLandscape by remember { mutableStateOf(isRecordingLandscape) }

    // Gestures state: Brightness (Left 50%) & Volume (Right 50%)
    val currentBrightnessState = remember {
        val initial = activity?.window?.attributes?.screenBrightness ?: -1f
        mutableFloatStateOf(if (initial >= 0f) initial else 0.5f)
    }
    val maxAudioVol = remember {
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }
    val currentVolumePercentState = remember {
        val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        mutableFloatStateOf(cur.toFloat() / maxAudioVol.toFloat())
    }
    var activeGestureHud by remember { mutableStateOf<GestureHudType?>(null) }
    var displayedGestureHud by remember { mutableStateOf(GestureHudType.BRIGHTNESS) }
    var hudDismissKey by remember { mutableLongStateOf(0L) }

    // Double-tap seek indicators
    var showLeftSeekRipple by remember { mutableStateOf(false) }
    var showRightSeekRipple by remember { mutableStateOf(false) }
    var rippleDismissKey by remember { mutableLongStateOf(0L) }

    // Auto-rotate if video recording is landscape
    LaunchedEffect(Unit) {
        if (isRecordingLandscape) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    // Manage System Bars & Clean Restoration on Exit
    DisposableEffect(activity) {
        val window = activity?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            val win = activity?.window
            if (win != null) {
                val insetsController = WindowCompat.getInsetsController(win, win.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                val lp = win.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                win.attributes = lp
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Auto-hide system status bar & navigation bar with controls
    LaunchedEffect(areControlsVisible, activity) {
        val window = activity?.window ?: return@LaunchedEffect
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (areControlsVisible) {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Auto-dismiss Gesture HUD pill after 1.2s
    LaunchedEffect(hudDismissKey) {
        if (activeGestureHud != null) {
            delay(1200)
            activeGestureHud = null
        }
    }

    // Auto-dismiss Double-Tap Ripple after 650ms
    LaunchedEffect(rippleDismissKey) {
        if (showLeftSeekRipple || showRightSeekRipple) {
            delay(650)
            showLeftSeekRipple = false
            showRightSeekRipple = false
        }
    }

    // Listen to Player state changes & errors
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) {
                    isBuffering = false
                    playbackErrorMessage = null
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> {
                        isBuffering = true
                    }
                    Player.STATE_READY -> {
                        isBuffering = false
                        playbackErrorMessage = null
                        val realDuration = exoPlayer.duration
                        if (realDuration > 0) {
                            durationMs = realDuration
                        }
                    }
                    Player.STATE_ENDED -> {
                        isPlaying = false
                        isBuffering = false
                    }
                    Player.STATE_IDLE -> {
                        isBuffering = false
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.e("VaultVideoPlayer", "Playback error: ${error.errorCodeName}", error)
                playbackErrorMessage = error.localizedMessage ?: "Playback error: ${error.errorCodeName}"
                isPlaying = false
                isBuffering = false
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

    // Auto-hide controls & status bar timer (3.0 seconds standard idle timeout)
    LaunchedEffect(areControlsVisible, isPlaying) {
        if (areControlsVisible && isPlaying && !isSeeking) {
            delay(3000)
            areControlsVisible = false
        }
    }

    // 100% Solid Opaque Fullscreen Container
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .zIndex(100f),
        contentAlignment = Alignment.Center
    ) {
        // 1. Hardware Accelerated Video Surface using TextureView
        AndroidView(
            factory = { ctx ->
                val view = LayoutInflater.from(ctx)
                    .inflate(R.layout.view_vault_player, null, false) as PlayerView
                view.apply {
                    player = exoPlayer
                    this.resizeMode = resizeMode
                }
            },
            update = { playerView ->
                if (playerView.player != exoPlayer) {
                    playerView.player = exoPlayer
                }
                if (playerView.resizeMode != resizeMode) {
                    playerView.resizeMode = resizeMode
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Gesture Detection Layer (Taps & Vertical Brightness/Volume Slides)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    var isLeftDrag = false
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            isLeftDrag = offset.x < size.width / 2f
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val deltaPercent = -dragAmount / (size.height.toFloat() * 0.75f)
                            if (isLeftDrag) {
                                val newBrightness = (currentBrightnessState.floatValue + deltaPercent).coerceIn(0.01f, 1.0f)
                                currentBrightnessState.floatValue = newBrightness
                                val win = activity?.window
                                if (win != null) {
                                    val lp = win.attributes
                                    lp.screenBrightness = newBrightness
                                    win.attributes = lp
                                }
                                displayedGestureHud = GestureHudType.BRIGHTNESS
                                activeGestureHud = GestureHudType.BRIGHTNESS
                                hudDismissKey++
                            } else {
                                val newPercent = (currentVolumePercentState.floatValue + deltaPercent).coerceIn(0f, 1f)
                                currentVolumePercentState.floatValue = newPercent
                                val targetVol = (newPercent * maxAudioVol).roundToInt().coerceIn(0, maxAudioVol)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                displayedGestureHud = GestureHudType.VOLUME
                                activeGestureHud = GestureHudType.VOLUME
                                hudDismissKey++
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            areControlsVisible = !areControlsVisible
                        },
                        onDoubleTap = { offset ->
                            val screenWidth = size.width
                            val xFraction = offset.x / screenWidth
                            if (xFraction < 0.35f) {
                                // Double-tap Left 35%: Seek -10s
                                val target = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                                exoPlayer.seekTo(target)
                                currentPositionMs = target
                                showLeftSeekRipple = true
                                showRightSeekRipple = false
                                rippleDismissKey++
                            } else if (xFraction > 0.65f) {
                                // Double-tap Right 35%: Seek +10s
                                val target = (exoPlayer.currentPosition + 10_000L).coerceAtMost(durationMs)
                                exoPlayer.seekTo(target)
                                currentPositionMs = target
                                showRightSeekRipple = true
                                showLeftSeekRipple = false
                                rippleDismissKey++
                            } else {
                                // Double-tap Center 30%: Toggle Play/Pause
                                if (isPlaying) {
                                    exoPlayer.pause()
                                } else {
                                    if (exoPlayer.playbackState == Player.STATE_ENDED) {
                                        exoPlayer.seekTo(0)
                                    }
                                    exoPlayer.play()
                                }
                            }
                        }
                    )
                }
        )

        // 3. Double-Tap Seek Ripples (+/- 10s Feedback)
        AnimatedVisibility(
            visible = showLeftSeekRipple,
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(300)),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.35f)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                HyperCrimson.copy(alpha = 0.28f),
                                Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_pixel_replay_10),
                        contentDescription = null,
                        tint = HyperCrimson,
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = "-10s",
                        color = TextPrimary,
                        fontFamily = BitcountPropSingle,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showRightSeekRipple,
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(300)),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.35f)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                HyperCrimson.copy(alpha = 0.28f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_pixel_forward_10),
                        contentDescription = null,
                        tint = HyperCrimson,
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = "+10s",
                        color = TextPrimary,
                        fontFamily = BitcountPropSingle,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 4. Gesture Feedback HUD (Edge-Anchored Telemetry Pillar: Icon -> Pixel Capsule Bar -> Percentage)
        AnimatedVisibility(
            visible = activeGestureHud != null,
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(300)),
            modifier = Modifier.fillMaxSize()
        ) {
            val isBrightness = displayedGestureHud == GestureHudType.BRIGHTNESS
            val percent = if (isBrightness) {
                currentBrightnessState.floatValue
            } else {
                currentVolumePercentState.floatValue
            }

            Box(modifier = Modifier.fillMaxSize()) {
                val totalRows = 80
                val activeRows = (percent * totalRows).roundToInt().coerceIn(0, totalRows)

                // Unified Edge Telemetry Pillar
                Column(
                    modifier = Modifier
                        .align(if (isBrightness) Alignment.CenterStart else Alignment.CenterEnd)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Top: Custom REC Pixel Mode Icon (24dp) with deep dark drop-shadow & silhouette
                    val iconRes = if (isBrightness) R.drawable.ic_pixel_brightness else R.drawable.ic_pixel_audio
                    val iconDesc = if (isBrightness) "Brightness" else "Volume"
                    Box(
                        modifier = Modifier.size(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Ambient dark shadow halo
                        Canvas(modifier = Modifier.size(32.dp)) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent),
                                    center = center + Offset(0f, 1.5f),
                                    radius = size.minDimension / 1.7f
                                )
                            )
                        }
                        // Directional dark silhouette drop-shadow (offset down-right)
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = null,
                            tint = Color.Black.copy(alpha = 0.95f),
                            modifier = Modifier
                                .size(24.dp)
                                .offset(x = 1.2.dp, y = 1.8.dp)
                        )
                        // Secondary dark halo for 360-degree contrast
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = null,
                            tint = Color.Black.copy(alpha = 0.60f),
                            modifier = Modifier
                                .size(24.dp)
                                .offset(x = (-0.8).dp, y = 0.5.dp)
                        )
                        // Foreground HyperCrimson Icon
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = iconDesc,
                            tint = HyperCrimson,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Center: Dense 5x80 Pixel-Capsule Bar with ambient & directional dark drop-shadows
                    Canvas(
                        modifier = Modifier
                            .width(26.dp)
                            .height(190.dp)
                    ) {
                        // 1. Soft diffused dark drop-shadow behind the entire capsule
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.65f),
                                    Color.Black.copy(alpha = 0.85f),
                                    Color.Black.copy(alpha = 0.65f),
                                    Color.Transparent
                                )
                            ),
                            topLeft = Offset(-4f, -4f),
                            size = Size(size.width + 8f, size.height + 8f),
                            cornerRadius = CornerRadius(14f, 14f)
                        )
                        // Core dark shadow behind the capsule bounds
                        drawRoundRect(
                            color = Color.Black.copy(alpha = 0.60f),
                            topLeft = Offset(1f, 1f),
                            size = Size(size.width - 2f, size.height),
                            cornerRadius = CornerRadius(10f, 10f)
                        )

                        val numCols = 5
                        val paddingX = 4f
                        val effectiveW = size.width - paddingX * 2f
                        val rowH = size.height / totalRows
                        val gapY = (rowH * 0.28f).coerceAtLeast(1f)
                        val squareH = rowH - gapY
                        val colW = effectiveW / numCols
                        val gapX = (colW * 0.25f).coerceAtLeast(1f)
                        val squareW = colW - gapX

                        for (r in 0 until totalRows) {
                            val y = size.height - (r + 1) * rowH + gapY / 2f
                            val isActive = r < activeRows

                            val cols = when (r) {
                                0, totalRows - 1 -> intArrayOf(1, 2, 3)
                                else -> intArrayOf(0, 1, 2, 3, 4)
                            }

                            for (c in cols) {
                                val x = paddingX + c * colW + gapX / 2f

                                // Crisp dark drop-shadow behind each individual square
                                drawRoundRect(
                                    color = Color.Black.copy(alpha = 0.95f),
                                    topLeft = Offset(x + 1.2f, y + 1.8f),
                                    size = Size(squareW, squareH),
                                    cornerRadius = CornerRadius(1.2f, 1.2f)
                                )

                                // Active illuminated square vs inactive ambient square
                                if (isActive) {
                                    drawRoundRect(
                                        color = HyperCrimson,
                                        topLeft = Offset(x, y),
                                        size = Size(squareW, squareH),
                                        cornerRadius = CornerRadius(1.2f, 1.2f)
                                    )
                                } else {
                                    drawRoundRect(
                                        color = Color.Black.copy(alpha = 0.65f),
                                        topLeft = Offset(x, y),
                                        size = Size(squareW, squareH),
                                        cornerRadius = CornerRadius(1.2f, 1.2f)
                                    )
                                    drawRoundRect(
                                        color = Color.White.copy(alpha = 0.30f),
                                        topLeft = Offset(x, y),
                                        size = Size(squareW, squareH),
                                        cornerRadius = CornerRadius(1.2f, 1.2f),
                                        style = Stroke(width = 0.9f)
                                    )
                                }
                            }
                        }
                    }

                    // Bottom: Digital Readout Percentage with dark drop-shadow
                    Box(
                        modifier = Modifier.padding(top = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Ambient dark shadow backdrop
                        Canvas(modifier = Modifier.size(width = 46.dp, height = 22.dp)) {
                            drawRoundRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color.Black.copy(alpha = 0.80f), Color.Transparent),
                                    center = center + Offset(0f, 1f),
                                    radius = size.width / 1.8f
                                ),
                                size = size
                            )
                        }
                        // Directional dark drop-shadow text
                        Text(
                            text = "${(percent * 100).toInt()}%",
                            color = Color.Black.copy(alpha = 0.95f),
                            fontFamily = BitcountPropSingle,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.offset(x = 1.2.dp, y = 1.5.dp)
                        )
                        // Foreground crisp text
                        Text(
                            text = "${(percent * 100).toInt()}%",
                            color = TextPrimary,
                            fontFamily = BitcountPropSingle,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            style = TextStyle(
                                shadow = Shadow(
                                    color = Color.Black,
                                    offset = Offset(0f, 1f),
                                    blurRadius = 3f
                                )
                            )
                        )
                    }
                }
            }
        }

        // 5. Buffering / Loading Indicator
        if (isBuffering && playbackErrorMessage == null) {
            Box(
                modifier = Modifier
                    .background(ObsidianCanvas.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                    .border(1.dp, BorderStark, RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "BUFFERING...",
                    color = HyperCrimson,
                    fontFamily = BitcountPropSingle,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 6. Error Banner with External Player Fallback
        playbackErrorMessage?.let { errMsg ->
            Box(
                modifier = Modifier
                    .padding(24.dp)
                    .background(ObsidianCanvas.copy(alpha = 0.95f), RoundedCornerShape(12.dp))
                    .border(1.5.dp, HyperCrimson, RoundedCornerShape(12.dp))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "DECODER // PLAYBACK ERROR",
                        color = HyperCrimson,
                        fontFamily = BitcountPropSingle,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errMsg,
                        color = TextSecondary,
                        fontFamily = BitcountPropSingle,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .background(SurfaceElevated, RoundedCornerShape(8.dp))
                            .border(1.dp, BorderStark, RoundedCornerShape(8.dp))
                            .clickable {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(item.uri, "video/mp4")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Open Video"))
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_pixel_external),
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "OPEN IN EXTERNAL PLAYER",
                            color = TextPrimary,
                            fontFamily = BitcountPropSingle,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // 7. Custom Cyberpunk HUD Overlay Controls
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

                // Center Controls: Rewind 10s, Play/Pause, Forward 10s
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
                        val target = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                        exoPlayer.seekTo(target)
                        currentPositionMs = target
                    },
                    onSeekForward = {
                        val target = (exoPlayer.currentPosition + 10_000L).coerceAtMost(durationMs)
                        exoPlayer.seekTo(target)
                        currentPositionMs = target
                    },
                    modifier = Modifier.align(Alignment.Center)
                )

                // Bottom HUD Deck: Timecode, Scrubber, Speed Pill Menu, Aspect Ratio, Loop, Rotate
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
                            exoPlayer.setSeekParameters(SeekParameters.CLOSEST_SYNC)
                        },
                        onSeekChange = { fraction ->
                            seekFraction = fraction
                            val targetMs = (fraction * durationMs).toLong()
                            currentPositionMs = targetMs
                            exoPlayer.seekTo(targetMs)
                        },
                        onSeekEnd = {
                            val targetMs = (seekFraction * durationMs).toLong()
                            currentPositionMs = targetMs
                            exoPlayer.setSeekParameters(SeekParameters.EXACT)
                            exoPlayer.seekTo(targetMs)
                            isSeeking = false
                        },
                        playbackSpeed = playbackSpeed,
                        onSpeedSelected = { speed ->
                            playbackSpeed = speed
                            exoPlayer.setPlaybackSpeed(speed)
                        },
                        isZoomed = resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                        onToggleAspectRatio = {
                            resizeMode = if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM) {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT
                            } else {
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            }
                        },
                        isLooping = isLooping,
                        onToggleLoop = {
                            isLooping = !isLooping
                            exoPlayer.repeatMode = if (isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                        },
                        isLandscape = isLandscape,
                        onToggleOrientation = {
                            isLandscape = !isLandscape
                            activity?.requestedOrientation = if (isLandscape) {
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            }
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
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF26263A).copy(alpha = 0.70f),
                                Color(0xFF141422).copy(alpha = 0.85f)
                            )
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.20f),
                                BorderStark.copy(alpha = 0.65f)
                            )
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
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
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF26263A).copy(alpha = 0.70f),
                                Color(0xFF141422).copy(alpha = 0.85f)
                            )
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.20f),
                                BorderStark.copy(alpha = 0.65f)
                            )
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
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
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF26263A).copy(alpha = 0.70f),
                                Color(0xFF141422).copy(alpha = 0.85f)
                            )
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.20f),
                                BorderStark.copy(alpha = 0.65f)
                            )
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
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
        // -10s Seek Backward
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
            Icon(
                painter = painterResource(id = R.drawable.ic_pixel_replay_10),
                contentDescription = null,
                tint = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier
                    .size(34.dp)
                    .offset(x = 1.dp, y = 1.5.dp)
            )
            Icon(
                painter = painterResource(id = R.drawable.ic_pixel_replay_10),
                contentDescription = "Seek -10s",
                tint = Color.White,
                modifier = Modifier.size(34.dp)
            )
        }

        // Giant Center Play/Pause
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

        // +10s Seek Forward
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
            Icon(
                painter = painterResource(id = R.drawable.ic_pixel_forward_10),
                contentDescription = null,
                tint = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier
                    .size(34.dp)
                    .offset(x = 1.dp, y = 1.5.dp)
            )
            Icon(
                painter = painterResource(id = R.drawable.ic_pixel_forward_10),
                contentDescription = "Seek +10s",
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
    isZoomed: Boolean,
    onToggleAspectRatio: () -> Unit,
    isLooping: Boolean,
    onToggleLoop: () -> Unit,
    isLandscape: Boolean,
    onToggleOrientation: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1E1E2C).copy(alpha = 0.82f),
                        Color(0xFF10101A).copy(alpha = 0.92f)
                    )
                ),
                shape = RoundedCornerShape(14.dp)
            )
            .border(
                width = 1.2.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.22f),
                        BorderStark.copy(alpha = 0.70f)
                    )
                ),
                shape = RoundedCornerShape(14.dp)
            )
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
                color = HyperCrimson,
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
                thumbColor = HyperCrimson,
                activeTrackColor = HyperCrimson,
                inactiveTrackColor = ObsidianCanvas
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
        )

        Spacer(modifier = Modifier.height(6.dp))

        // 3. Playback Controls Row: Speed Pill Dropdown + Actions Cluster (Aspect Ratio, Loop, Rotate)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Speed Dropdown Pill
            var speedMenuExpanded by remember { mutableStateOf(false) }

            Box {
                Row(
                    modifier = Modifier
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF28283E).copy(alpha = 0.70f),
                                    Color(0xFF141422).copy(alpha = 0.85f)
                                )
                            ),
                            shape = RoundedCornerShape(7.dp)
                        )
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.22f),
                                    BorderStark.copy(alpha = 0.65f)
                                )
                            ),
                            shape = RoundedCornerShape(7.dp)
                        )
                        .clickable { speedMenuExpanded = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_pixel_speed),
                        contentDescription = null,
                        tint = HyperCrimson,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${playbackSpeed}X",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontFamily = BitcountPropSingle,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "▾",
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }

                DropdownMenu(
                    expanded = speedMenuExpanded,
                    onDismissRequest = { speedMenuExpanded = false },
                    shape = RoundedCornerShape(12.dp),
                    containerColor = Color.Transparent,
                    shadowElevation = 16.dp,
                    border = BorderStroke(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.28f),
                                BorderStark.copy(alpha = 0.70f)
                            )
                        )
                    ),
                    modifier = Modifier
                        .width(142.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF1E1E30).copy(alpha = 0.85f),
                                    Color(0xFF0D0D18).copy(alpha = 0.92f)
                                )
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(vertical = 5.dp)
                ) {
                    // Header Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "SPEED",
                            color = TextSecondary,
                            fontFamily = BitcountPropSingle,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Icon(
                            painter = painterResource(id = R.drawable.ic_pixel_speed),
                            contentDescription = null,
                            tint = HyperCrimson,
                            modifier = Modifier.size(12.dp)
                        )
                    }

                    // Frosted Divider
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        BorderStark.copy(alpha = 0.8f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Speed Items
                    listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                        val isSelected = playbackSpeed == speed
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .background(
                                    if (isSelected) HyperCrimson.copy(alpha = 0.18f) else Color.Transparent,
                                    RoundedCornerShape(6.dp)
                                )
                                .then(
                                    if (isSelected) {
                                        Modifier.border(
                                            1.dp,
                                            HyperCrimson.copy(alpha = 0.55f),
                                            RoundedCornerShape(6.dp)
                                        )
                                    } else Modifier
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(color = HyperCrimson),
                                    onClick = {
                                        onSpeedSelected(speed)
                                        speedMenuExpanded = false
                                    }
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${speed}X",
                                color = if (isSelected) HyperCrimson else TextPrimary,
                                fontFamily = BitcountPropSingle,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp
                            )

                            if (isSelected) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_pixel_check),
                                    contentDescription = null,
                                    tint = HyperCrimson,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Actions Cluster: Aspect Ratio, Loop Toggle, Screen Rotate
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Aspect Ratio (Fit vs Zoom)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            if (isZoomed) HyperCrimson.copy(alpha = 0.22f) else Color(0xFF1A1A28).copy(alpha = 0.75f),
                            RoundedCornerShape(7.dp)
                        )
                        .border(
                            1.dp,
                            if (isZoomed) HyperCrimson else BorderStark.copy(alpha = 0.75f),
                            RoundedCornerShape(7.dp)
                        )
                        .clickable(onClick = onToggleAspectRatio),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_pixel_aspect_ratio),
                        contentDescription = if (isZoomed) "Fit to Screen" else "Zoom to Fill",
                        tint = if (isZoomed) HyperCrimson else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Loop Toggle
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            if (isLooping) HyperCrimson.copy(alpha = 0.22f) else Color(0xFF1A1A28).copy(alpha = 0.75f),
                            RoundedCornerShape(7.dp)
                        )
                        .border(
                            1.dp,
                            if (isLooping) HyperCrimson else BorderStark.copy(alpha = 0.75f),
                            RoundedCornerShape(7.dp)
                        )
                        .clickable(onClick = onToggleLoop),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_pixel_loop),
                        contentDescription = "Loop",
                        tint = if (isLooping) HyperCrimson else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Screen Rotate
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            if (isLandscape) HyperCrimson.copy(alpha = 0.22f) else Color(0xFF1A1A28).copy(alpha = 0.75f),
                            RoundedCornerShape(7.dp)
                        )
                        .border(
                            1.dp,
                            if (isLandscape) HyperCrimson else BorderStark.copy(alpha = 0.75f),
                            RoundedCornerShape(7.dp)
                        )
                        .clickable(onClick = onToggleOrientation),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_pixel_rotate),
                        contentDescription = "Rotate Screen",
                        tint = if (isLandscape) HyperCrimson else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
