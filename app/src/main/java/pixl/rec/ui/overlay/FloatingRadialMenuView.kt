package pixl.rec.ui.overlay

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import pixl.rec.core.engine.UplinkHealth
import pixl.rec.core.model.RecorderState
import pixl.rec.service.RecordingService
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pixl.rec.R
import pixl.rec.core.model.HudStyleConfig
import pixl.rec.core.storage.StorageCalculator
import pixl.rec.core.storage.StudioMode
import pixl.rec.ui.components.HudNodeSurface
import pixl.rec.ui.components.rememberHudIconAnimation
import pixl.rec.ui.theme.BitcountPropSingle
import pixl.rec.ui.theme.BorderStark
import pixl.rec.ui.theme.CyberYellow
import pixl.rec.ui.theme.HyperCrimson
import pixl.rec.ui.theme.HyperCyan
import pixl.rec.ui.theme.SurfaceElevated
import pixl.rec.ui.theme.TextPrimary
import pixl.rec.ui.theme.TextSecondary
import pixl.rec.ui.theme.ToxicLime
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 5-Faceted Cyberpunk Angular Polygon Canopy Shape for Edge Docking (180° Fan).
 */
class FiveFacetPolygonCanopyShape(
    private val isDockedOnLeft: Boolean,
    private val extraOrbitDp: Float = 0f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path().apply {
            val w = size.width
            val h = size.height
            val hubX = if (isDockedOnLeft) with(density) { 22.dp.toPx() } else (w - with(density) { 22.dp.toPx() })
            val hubY = h / 2f

            val cornerAngles = if (isDockedOnLeft) {
                listOf(-80f, -52.5f, -17.5f, 17.5f, 52.5f, 80f)
            } else {
                listOf(260f, 232.5f, 197.5f, 162.5f, 127.5f, 100f)
            }

            val cornerDistances = listOf(
                with(density) { (82f + extraOrbitDp).dp.toPx() },
                with(density) { (88f + extraOrbitDp).dp.toPx() },
                with(density) { (92f + extraOrbitDp).dp.toPx() },
                with(density) { (92f + extraOrbitDp).dp.toPx() },
                with(density) { (88f + extraOrbitDp).dp.toPx() },
                with(density) { (82f + extraOrbitDp).dp.toPx() }
            )

            val wallY = with(density) { (81f + extraOrbitDp * 0.9f).dp.toPx() }

            if (isDockedOnLeft) {
                moveTo(0f, hubY - wallY)
                cornerAngles.forEachIndexed { i, deg ->
                    val rad = Math.toRadians(deg.toDouble())
                    val x = (hubX + cos(rad) * cornerDistances[i]).toFloat().coerceIn(0f, w)
                    val y = (hubY + sin(rad) * cornerDistances[i]).toFloat().coerceIn(0f, h)
                    lineTo(x, y)
                }
                lineTo(0f, hubY + wallY)
                close()
            } else {
                moveTo(w, hubY - wallY)
                cornerAngles.forEachIndexed { i, deg ->
                    val rad = Math.toRadians(deg.toDouble())
                    val x = (hubX + cos(rad) * cornerDistances[i]).toFloat().coerceIn(0f, w)
                    val y = (hubY + sin(rad) * cornerDistances[i]).toFloat().coerceIn(0f, h)
                    lineTo(x, y)
                }
                lineTo(w, hubY + wallY)
                close()
            }
        }
        return Outline.Generic(path)
    }
}

/**
 * Adaptive Cyberpunk Floating Radial HUD Menu.
 * - In Free Space: Expands into the 360° Isometric Hex-Pod with 6 radial chambers.
 * - On Edge Docking: Expands into the 180° Edge-Fan Canopy opening inwards into the screen.
 */
@Composable
fun FloatingRadialMenuView(
    isExpanded: Boolean,
    isDockedOnLeft: Boolean = false,
    isDockedOnRight: Boolean = false,
    isRecordingActive: Boolean = false,
    isPaused: Boolean = false,
    studioMode: StudioMode = StudioMode.RECORD,
    durationMs: Long = 0L,
    hudConfig: HudStyleConfig = HudStyleConfig(),
    onToggleExpand: (Boolean) -> Unit,
    onCollapseComplete: () -> Unit = {},
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
    onRecordClick: () -> Unit,
    onReplayClick: () -> Unit = {},
    onPauseClick: () -> Unit = {},
    onResumeClick: () -> Unit = {},
    onStopClick: () -> Unit = {},
    onGhostClick: () -> Unit = {},
    onScreenshotClick: () -> Unit,
    onVaultClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val isDockedOnEdge = isDockedOnLeft || isDockedOnRight

    val expansionProgress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        finishedListener = { progress ->
            if (progress == 0f) {
                onCollapseComplete()
            }
        },
        label = "RadialMenuExpansion"
    )

    val serviceState by RecordingService.serviceState.collectAsState()
    val isStreamingActive = serviceState is RecorderState.Recording && (serviceState as RecorderState.Recording).isStreaming
    val isStreamMode = studioMode == StudioMode.STREAM
    val isStreaming = isStreamingActive || isStreamMode
    val uplinkHealth by RecordingService.uplinkHealth.collectAsState()
    val isPrivacySlateActive by RecordingService.isPrivacySlateActive.collectAsState()

    val iconAnim = rememberHudIconAnimation(
        animation = hudConfig.animation,
        baseOpacity = hudConfig.iconOpacity
    )

    val strokeColor = Color(hudConfig.strokeColorHex)

    val boxAlignment = if (!isDockedOnEdge) {
        Alignment.Center
    } else if (isDockedOnLeft) {
        Alignment.CenterStart
    } else {
        Alignment.CenterEnd
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (isExpanded) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures {
                            onToggleExpand(false)
                        }
                    }
                } else {
                    Modifier
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = onDragEnd,
                                onDragCancel = onDragEnd,
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onDrag(dragAmount.x, dragAmount.y)
                                }
                            )
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (isRecordingActive && !isDockedOnEdge) {
                                    onStopClick()
                                } else {
                                    onToggleExpand(true)
                                }
                            }
                        )
                }
            ),
        contentAlignment = boxAlignment
    ) {
        // --- EXPANDABLE SATELLITE CANOPY ---
        if (expansionProgress > 0.01f) {
            if (isDockedOnEdge) {
                EdgeFanCanopy(
                    isDockedOnLeft = isDockedOnLeft,
                    expansionProgress = expansionProgress,
                    isRecordingActive = isRecordingActive,
                    isPaused = isPaused,
                    isStreaming = isStreaming,
                    uplinkHealth = uplinkHealth,
                    isPrivacySlateActive = isPrivacySlateActive,
                    onPrivacySlateToggle = { RecordingService.togglePrivacySlate() },
                    hudConfig = hudConfig,
                    onToggleExpand = onToggleExpand,
                    onRecordClick = onRecordClick,
                    onReplayClick = onReplayClick,
                    onPauseClick = onPauseClick,
                    onResumeClick = onResumeClick,
                    onStopClick = onStopClick,
                    onGhostClick = onGhostClick,
                    onScreenshotClick = onScreenshotClick,
                    onVaultClick = onVaultClick,
                    onSettingsClick = onSettingsClick
                )
            } else {
                FreeSpaceHexPodCanopy(
                    expansionProgress = expansionProgress,
                    isRecordingActive = isRecordingActive,
                    isPaused = isPaused,
                    durationMs = durationMs,
                    isStreaming = isStreaming,
                    uplinkHealth = uplinkHealth,
                    isPrivacySlateActive = isPrivacySlateActive,
                    onPrivacySlateToggle = { RecordingService.togglePrivacySlate() },
                    hudConfig = hudConfig,
                    onToggleExpand = onToggleExpand,
                    onRecordClick = onRecordClick,
                    onReplayClick = onReplayClick,
                    onPauseClick = onPauseClick,
                    onResumeClick = onResumeClick,
                    onGhostClick = onGhostClick,
                    onScreenshotClick = onScreenshotClick,
                    onSettingsClick = onSettingsClick
                )
            }
        }

        // --- STABLE CENTER HUD NODE HUB ---
        val density = LocalDensity.current
        val hubSlidePx = if (isDockedOnEdge) {
            with(density) {
                val slideDist = 34.dp.toPx()
                if (isDockedOnLeft) expansionProgress * slideDist else -expansionProgress * slideDist
            }
        } else {
            0f
        }

        val nodeVisualSize = hudConfig.sizeDp.dp

        Box(
            modifier = Modifier
                .padding(
                    start = if (isDockedOnLeft) 24.dp else 0.dp,
                    end = if (isDockedOnRight) 24.dp else 0.dp
                )
                .graphicsLayer { translationX = hubSlidePx }
                .size(nodeVisualSize)
                .then(
                    if (isExpanded) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (isRecordingActive && !isDockedOnEdge) {
                                    onToggleExpand(false)
                                    onStopClick()
                                } else {
                                    onToggleExpand(false)
                                }
                            }
                        )
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            HudNodeSurface(config = hudConfig) {
                val iconBoxSize = hudConfig.iconSizeDp.dp
                Box(
                    modifier = Modifier.size(iconBoxSize),
                    contentAlignment = Alignment.Center
                ) {
                    val rawAlpha: Float = if (!isExpanded) iconAnim.alpha else ((1f - expansionProgress) * hudConfig.iconOpacity)
                    Icon(
                        painter = painterResource(id = if (isStreaming) R.drawable.ic_pixel_stream else R.drawable.ic_pixel_record),
                        contentDescription = if (isStreaming) "PixL Live Stream" else "PixL Floating Menu",
                        tint = if (isPaused) CyberYellow else if (isStreaming) HyperCrimson else strokeColor,
                        modifier = Modifier
                            .matchParentSize()
                            .scale(if (!isExpanded) iconAnim.scale else 1.0f)
                            .alpha(rawAlpha.coerceIn(0f, 1f))
                    )
                    Icon(
                        painter = painterResource(id = if (isRecordingActive && !isDockedOnEdge) R.drawable.ic_pixel_stop else R.drawable.ic_pixel_hud_node),
                        contentDescription = if (isRecordingActive && !isDockedOnEdge) (if (isStreaming) "End Stream" else "Stop Recording") else "PixL HUD Node",
                        tint = if (isRecordingActive && !isDockedOnEdge) HyperCrimson else strokeColor,
                        modifier = Modifier
                            .matchParentSize()
                            .scale(if (isRecordingActive && !isDockedOnEdge) 1.0f else 1.45f)
                            .alpha(expansionProgress.coerceIn(0f, 1f))
                    )
                }
            }
        }
    }
}

/**
 * 360° Isometric Hex-Pod Glass Canopy & Facets (Open Space).
 */
@Composable
private fun FreeSpaceHexPodCanopy(
    expansionProgress: Float,
    isRecordingActive: Boolean,
    isPaused: Boolean,
    durationMs: Long,
    isStreaming: Boolean = false,
    uplinkHealth: UplinkHealth = UplinkHealth.CLEAN,
    isPrivacySlateActive: Boolean = false,
    onPrivacySlateToggle: () -> Unit = {},
    hudConfig: HudStyleConfig = HudStyleConfig(),
    onToggleExpand: (Boolean) -> Unit,
    onRecordClick: () -> Unit,
    onReplayClick: () -> Unit = {},
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onGhostClick: () -> Unit,
    onScreenshotClick: () -> Unit,
    onSettingsClick: () -> Unit = {}
) {
    val haptics = LocalHapticFeedback.current
    val extraSize = if (hudConfig.hasBackground && hudConfig.sizeDp > 44) (hudConfig.sizeDp - 44) else 0
    val freeExtra = extraSize * 1.2f
    val podWidth = (156f + freeExtra).dp
    val podHeight = (176f + freeExtra).dp
    val hexPodShape = remember(freeExtra) { IsometricHexPodShape() }

    val uplinkColor = when (uplinkHealth) {
        UplinkHealth.CLEAN -> ToxicLime
        UplinkHealth.ADAPTING -> CyberYellow
        UplinkHealth.CONGESTED -> HyperCrimson
    }

    Box(
        modifier = Modifier
            .size(width = podWidth, height = podHeight)
            .scale(expansionProgress)
            .alpha(expansionProgress)
            .clip(hexPodShape)
            .background(SurfaceElevated.copy(alpha = 0.94f))
            .border(width = 1.5.dp, color = BorderStark, shape = hexPodShape)
    )

    // Dotted Cyber Spoke Dividers (5 Radial Dividers separating the chambers; NO line through top text)
    Canvas(
        modifier = Modifier
            .size(width = podWidth, height = podHeight)
            .alpha(expansionProgress)
    ) {
        val w = size.width
        val h = size.height
        val centerX = w / 2f
        val centerY = h / 2f
        val spokes = IsometricHexPodShape.getDividerSpokes(w, h)
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)

        spokes.forEach { v ->
            drawLine(
                color = Color(0x33FFFFFF),
                start = Offset(centerX, centerY),
                end = v,
                strokeWidth = 1.dp.toPx(),
                pathEffect = dashEffect
            )
        }
    }

    // --- 5 Radial Facets ---
    // 1. TOP FACET: Live Digital Timer or Uplink Health Dot & Duration
    Box(
        modifier = Modifier
            .offset(y = (-45 - freeExtra * 0.45f).dp)
            .scale(expansionProgress)
            .alpha(expansionProgress),
        contentAlignment = Alignment.Center
    ) {
        if (isStreaming) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(uplinkColor, CircleShape)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "LIVE ${StorageCalculator.formatDuration(durationMs)}",
                    color = HyperCyan,
                    fontSize = 10.5.sp,
                    fontFamily = BitcountPropSingle,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        } else {
            Text(
                text = if (isRecordingActive) StorageCalculator.formatDuration(durationMs) else "STANDBY",
                color = if (isRecordingActive) (if (isPaused) CyberYellow else HyperCrimson) else HyperCyan,
                fontSize = 11.sp,
                fontFamily = BitcountPropSingle,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }

    // 2. LEFT CHAMBER: Invisible Ghost Mode or Privacy Slate in Stream Mode
    Box(
        modifier = Modifier
            .offset(x = (-46 - freeExtra * 0.5f).dp)
            .scale(expansionProgress)
            .alpha(expansionProgress)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 20.dp, color = if (isStreaming) (if (isPrivacySlateActive) HyperCrimson else HyperCyan) else HyperCyan),
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (isStreaming) {
                        onPrivacySlateToggle()
                    } else {
                        onToggleExpand(false)
                        onGhostClick()
                    }
                }
            )
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_pixel_eye_off),
            contentDescription = if (isStreaming) (if (isPrivacySlateActive) "Resume Feed" else "Privacy Slate") else "Ghost Stealth Mode",
            tint = if (isStreaming) (if (isPrivacySlateActive) HyperCrimson else HyperCyan) else HyperCyan,
            modifier = Modifier.size(24.dp)
        )
    }

    // 3. RIGHT CHAMBER: Pause/Resume (REC/STREAM) or Start Record (Standby)
    Box(
        modifier = Modifier
            .offset(x = (46 + freeExtra * 0.5f).dp)
            .scale(expansionProgress)
            .alpha(expansionProgress)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 20.dp, color = if (isStreaming) HyperCyan else if (isRecordingActive) CyberYellow else HyperCrimson),
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleExpand(false)
                    if (isRecordingActive) {
                        if (isPaused) onResumeClick() else onPauseClick()
                    } else {
                        onRecordClick()
                    }
                }
            )
    ) {
        Icon(
            painter = painterResource(
                id = if (isRecordingActive) {
                    if (isPaused) R.drawable.ic_pixel_play else R.drawable.ic_pixel_pause
                } else {
                    R.drawable.ic_pixel_record
                }
            ),
            contentDescription = if (isRecordingActive) "Pause/Resume" else "Start Record",
            tint = if (isStreaming) (if (isPaused) ToxicLime else HyperCyan) else if (isRecordingActive) (if (isPaused) ToxicLime else TextPrimary) else HyperCrimson,
            modifier = Modifier.size(24.dp)
        )
    }

    // 4. BOTTOM-LEFT CHAMBER: Instant Replay (REC) or Config Settings (Standby)
    Box(
        modifier = Modifier
            .offset(x = (-26 - freeExtra * 0.4f).dp, y = (42 + freeExtra * 0.45f).dp)
            .scale(expansionProgress)
            .alpha(expansionProgress)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 20.dp, color = if (isRecordingActive) HyperCyan else CyberYellow),
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleExpand(false)
                    if (isRecordingActive) onReplayClick() else onSettingsClick()
                }
            )
    ) {
        Icon(
            painter = painterResource(id = if (isRecordingActive) R.drawable.ic_pixel_lightning else R.drawable.ic_pixel_settings),
            contentDescription = if (isRecordingActive) "Instant Replay" else "Config Settings",
            tint = if (isRecordingActive) HyperCyan else CyberYellow,
            modifier = Modifier.size(24.dp)
        )
    }

    // 5. BOTTOM-RIGHT CHAMBER: Screenshot / Camera - Centered in Bottom-Right Chamber
    Box(
        modifier = Modifier
            .offset(x = (26 + freeExtra * 0.4f).dp, y = (42 + freeExtra * 0.45f).dp)
            .scale(expansionProgress)
            .alpha(expansionProgress)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 20.dp, color = CyberYellow),
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleExpand(false)
                    onScreenshotClick()
                }
            )
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_pixel_camera),
            contentDescription = "Screenshot",
            tint = CyberYellow,
            modifier = Modifier.size(26.dp)
        )
    }
}

/**
 * 180° Edge-Fan Canopy & Facets (Bezel Docked).
 */
@Composable
private fun EdgeFanCanopy(
    isDockedOnLeft: Boolean,
    expansionProgress: Float,
    isRecordingActive: Boolean,
    isPaused: Boolean,
    isStreaming: Boolean = false,
    uplinkHealth: UplinkHealth = UplinkHealth.CLEAN,
    isPrivacySlateActive: Boolean = false,
    onPrivacySlateToggle: () -> Unit = {},
    hudConfig: HudStyleConfig = HudStyleConfig(),
    onToggleExpand: (Boolean) -> Unit,
    onRecordClick: () -> Unit,
    onReplayClick: () -> Unit = {},
    onPauseClick: () -> Unit,
    onResumeClick: () -> Unit,
    onStopClick: () -> Unit,
    onGhostClick: () -> Unit,
    onScreenshotClick: () -> Unit,
    onVaultClick: () -> Unit,
    onSettingsClick: () -> Unit = {}
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    val extraSize = if (hudConfig.hasBackground && hudConfig.sizeDp > 44) (hudConfig.sizeDp - 44) else 0
    val extraOrbitDp = extraSize * 1.75f

    val canopyWidthDp = (118f + extraOrbitDp).dp
    val canopyHeightDp = (210f + extraOrbitDp * 1.8f).dp

    val canopyShape = remember(isDockedOnLeft, extraOrbitDp) { FiveFacetPolygonCanopyShape(isDockedOnLeft, extraOrbitDp) }
    val canopyWidthPx = with(density) { canopyWidthDp.toPx() }
    val slideOffsetPx = if (isDockedOnLeft) {
        -(1f - expansionProgress) * canopyWidthPx
    } else {
        (1f - expansionProgress) * canopyWidthPx
    }

    Box(
        modifier = Modifier
            .padding(
                start = if (isDockedOnLeft) 46.dp else 0.dp,
                end = if (!isDockedOnLeft) 46.dp else 0.dp
            )
            .size(width = canopyWidthDp, height = canopyHeightDp)
            .graphicsLayer {
                translationX = slideOffsetPx
                alpha = expansionProgress.coerceIn(0f, 1f)
            },
        contentAlignment = if (isDockedOnLeft) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(canopyShape)
                .background(SurfaceElevated.copy(alpha = 0.92f))
                .border(width = 1.5.dp, color = BorderStark, shape = canopyShape)
        )

        // Dotted cyber spoke dividers
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val w = size.width
            val h = size.height
            val hubCenterX = if (isDockedOnLeft) 22.dp.toPx() else (w - 22.dp.toPx())
            val hubCenterY = h / 2f
            val dividerAngles = if (isDockedOnLeft) listOf(-52.5f, -17.5f, 17.5f, 52.5f) else listOf(232.5f, 197.5f, 162.5f, 127.5f)
            val dividerDistances = listOf(
                (88f + extraOrbitDp).dp.toPx(),
                (92f + extraOrbitDp).dp.toPx(),
                (92f + extraOrbitDp).dp.toPx(),
                (88f + extraOrbitDp).dp.toPx()
            )

            dividerAngles.forEachIndexed { i, deg ->
                val rad = Math.toRadians(deg.toDouble())
                val endX = (hubCenterX + cos(rad) * dividerDistances[i]).toFloat().coerceIn(0f, w)
                val endY = (hubCenterY + sin(rad) * dividerDistances[i]).toFloat().coerceIn(0f, h)

                drawLine(
                    color = Color(0x33FFFFFF),
                    start = Offset(hubCenterX, hubCenterY),
                    end = Offset(endX, endY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                )
            }
        }

        val nodeOrbitRadius = with(density) { (58f + extraOrbitDp).dp.toPx() }

        // Node 1: Config in Standby, Instant Replay in Active Recording (Top Chamber)
        FanNodeItem(
            iconResId = if (isRecordingActive) R.drawable.ic_pixel_lightning else R.drawable.ic_pixel_settings,
            tint = if (isRecordingActive) HyperCyan else CyberYellow,
            angleDeg = if (isDockedOnLeft) -70f else 250f,
            orbitRadius = nodeOrbitRadius,
            isDockedOnLeft = isDockedOnLeft,
            iconSize = 25.dp,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggleExpand(false)
                if (isRecordingActive) onReplayClick() else onSettingsClick()
            }
        )

        // Node 2: Privacy Slate (Stream) or Pause/Resume (REC) or Ghost (Standby)
        FanNodeItem(
            iconResId = if (isStreaming) R.drawable.ic_pixel_eye_off else if (isRecordingActive) (if (isPaused) R.drawable.ic_pixel_play else R.drawable.ic_pixel_pause) else R.drawable.ic_pixel_eye_off,
            tint = if (isStreaming) (if (isPrivacySlateActive) HyperCrimson else HyperCyan) else if (isRecordingActive) (if (isPaused) ToxicLime else HyperCyan) else HyperCyan,
            angleDeg = if (isDockedOnLeft) -35f else 215f,
            orbitRadius = nodeOrbitRadius,
            isDockedOnLeft = isDockedOnLeft,
            iconSize = 26.dp,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                if (isStreaming) {
                    onPrivacySlateToggle()
                } else {
                    onToggleExpand(false)
                    if (isRecordingActive) {
                        if (isPaused) onResumeClick() else onPauseClick()
                    } else {
                        onGhostClick()
                    }
                }
            }
        )

        // Node 3: Start Record / End Stream / Stop Recording (Center Apex Chamber)
        FanNodeItem(
            iconResId = if (isRecordingActive) R.drawable.ic_pixel_stop else if (isStreaming) R.drawable.ic_pixel_stream else R.drawable.ic_pixel_record,
            tint = HyperCrimson,
            angleDeg = if (isDockedOnLeft) 0f else 180f,
            orbitRadius = nodeOrbitRadius,
            isDockedOnLeft = isDockedOnLeft,
            iconSize = if (isRecordingActive) 24.dp else 20.dp,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggleExpand(false)
                if (isRecordingActive) onStopClick() else onRecordClick()
            }
        )

        // Node 4: Vault Gallery (Lower-Right Chamber)
        FanNodeItem(
            iconResId = R.drawable.ic_pixel_vault,
            tint = ToxicLime,
            angleDeg = if (isDockedOnLeft) 35f else 145f,
            orbitRadius = nodeOrbitRadius,
            isDockedOnLeft = isDockedOnLeft,
            iconSize = 25.dp,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggleExpand(false)
                onVaultClick()
            }
        )

        // Node 5: Screenshot (Bottom Chamber)
        FanNodeItem(
            iconResId = R.drawable.ic_pixel_camera,
            tint = CyberYellow,
            angleDeg = if (isDockedOnLeft) 70f else 110f,
            orbitRadius = nodeOrbitRadius,
            isDockedOnLeft = isDockedOnLeft,
            iconSize = 26.dp,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggleExpand(false)
                onScreenshotClick()
            }
        )
    }
}

@Composable
private fun FanNodeItem(
    iconResId: Int,
    tint: Color,
    angleDeg: Float,
    orbitRadius: Float,
    isDockedOnLeft: Boolean,
    iconSize: Dp = 26.dp,
    onClick: () -> Unit
) {
    val density = LocalDensity.current
    val angleRad = Math.toRadians(angleDeg.toDouble())
    val hubCorrectionX = with(density) { if (isDockedOnLeft) (22.dp - 20.dp).toPx() else -(22.dp - 20.dp).toPx() }
    val offsetX = (hubCorrectionX + orbitRadius * cos(angleRad)).roundToInt()
    val offsetY = (orbitRadius * sin(angleRad)).roundToInt()

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX, offsetY) }
            .size(40.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 20.dp, color = tint),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconResId),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}
