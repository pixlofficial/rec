package pixl.rec.service

import android.animation.ValueAnimator
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import pixl.rec.core.model.RecorderState
import pixl.rec.core.model.RecordingConfig
import pixl.rec.ui.CapturePermissionActivity
import pixl.rec.ui.MainActivity
import pixl.rec.ui.overlay.FloatingPillView
import pixl.rec.ui.overlay.FloatingRadialMenuView
import pixl.rec.ui.theme.RECTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlin.math.roundToInt

/**
 * Window Overlay Service hosting the edge-snapping Standby Bubble & Cyberpunk Radial Menu,
 * and the live recording telemetry pill.
 *
 * Full multi-orientation (Portrait & Landscape) support with unified physical screen coordinates
 * and generous touch hit-box padding for effortless one-handed gestures.
 */
class FloatingOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private lateinit var layoutParams: WindowManager.LayoutParams
    private val configState = mutableStateOf(RecordingConfig())
    private val config: RecordingConfig
        get() = configState.value
    private val isDockedOnLeftState = mutableStateOf(true)
    private val isDockedOnRightState = mutableStateOf(false)
    private var preExpandX: Int = 0
    private var preExpandY: Int = 0
    private var standbyDockY: Int = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val passedConfig = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_CONFIG, RecordingConfig::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_CONFIG) as? RecordingConfig
            }
            if (passedConfig != null) {
                configState.value = passedConfig
            }
        }
        _isTemporarilyHidden.value = false
        removeMenuOverlay()
        if (overlayView == null) {
            createOverlayView()
        } else {
            overlayView?.visibility = View.VISIBLE
        }
        return START_STICKY
    }

    private fun dpToPx(dp: Float): Int {
        return (dp * resources.displayMetrics.density).roundToInt()
    }

    private fun getScreenMetrics(): DisplayMetrics {
        val dm = DisplayMetrics()
        val wm = windowManager
        if (wm != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = wm.currentWindowMetrics.bounds
                dm.widthPixels = bounds.width()
                dm.heightPixels = bounds.height()
                dm.density = resources.displayMetrics.density
            } else {
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealMetrics(dm)
            }
        } else {
            dm.widthPixels = resources.displayMetrics.widthPixels
            dm.heightPixels = resources.displayMetrics.heightPixels
            dm.density = resources.displayMetrics.density
        }
        return dm
    }

    data class DockBounds(
        val minX: Int,
        val maxX: Int,
        val minY: Int,
        val maxY: Int,
        val screenWidth: Int,
        val screenHeight: Int
    )

    private fun getDockBounds(): DockBounds {
        val dm = getScreenMetrics()
        val viewHeight = dpToPx(72f)

        var insetTop = 0
        var insetBottom = 0
        var insetLeft = 0
        var insetRight = 0
        var navBottom = 0
        var navLeft = 0
        var navRight = 0

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val rootInsets = overlayView?.rootWindowInsets ?: windowManager?.currentWindowMetrics?.windowInsets
            if (rootInsets != null) {
                val sysBars = rootInsets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                )
                val navBars = rootInsets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.navigationBars()
                )
                insetTop = sysBars.top
                insetBottom = sysBars.bottom
                insetLeft = sysBars.left
                insetRight = sysBars.right
                navBottom = navBars.bottom
                navLeft = navBars.left
                navRight = navBars.right
            }
        }

        // Generous bottom safety margin so pill NEVER overlaps or sits directly against phone navigation bar
        val bottomNavMargin = when {
            navBottom > 0 -> navBottom + dpToPx(28f)
            insetBottom > 0 -> insetBottom + dpToPx(28f)
            else -> {
                val navId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
                val navH = if (navId > 0) resources.getDimensionPixelSize(navId) else 0
                if (navH > 0) navH + dpToPx(28f) else dpToPx(72f)
            }
        }

        val topMargin = (if (insetTop > 0) insetTop else dpToPx(24f)) + dpToPx(12f)
        val minY = topMargin
        val maxY = (dm.heightPixels - bottomNavMargin - viewHeight).coerceAtLeast(minY)

        // Left edge: 116dp window docks at -46dp, providing an expansive 70dp on-screen touch hitbox
        val minX = if (navLeft > 0) {
            navLeft + dpToPx(8f)
        } else {
            -dpToPx(PILL_BEZEL_OFFSET_DP)
        }

        // Right edge: 116dp window docks at screenWidth - 70dp, providing an expansive 70dp on-screen touch hitbox
        val maxX = if (navRight > 0) {
            dm.widthPixels - navRight - dpToPx(PILL_WIDTH_DP) - dpToPx(8f)
        } else {
            dm.widthPixels - dpToPx(PILL_ONSCREEN_TOUCH_DP)
        }

        return DockBounds(
            minX = minX,
            maxX = maxX,
            minY = minY,
            maxY = maxY,
            screenWidth = dm.widthPixels,
            screenHeight = dm.heightPixels
        )
    }

    private val serviceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        serviceScope.launch {
            isTemporarilyHidden.collect { hidden ->
                overlayView?.visibility = if (hidden) android.view.View.GONE else android.view.View.VISIBLE
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        standbyDockY = dpToPx(300f)

        isDockedOnLeftState.value = true
        isDockedOnRightState.value = false

        layoutParams = WindowManager.LayoutParams(
            dpToPx(PILL_WIDTH_DP),
            dpToPx(PILL_HEIGHT_DP),
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = -dpToPx(PILL_BEZEL_OFFSET_DP)
            y = standbyDockY
        }
        createOverlayView()
    }
    private var dragAccumulatorX: Float = 0f
    private var dragAccumulatorY: Float = 0f
    private var isActivelyDragging: Boolean = false

    private fun createOverlayView() {
        val composeView = ComposeView(this).apply {
            setViewTreeSavedStateRegistryOwner(this@FloatingOverlayService)
            setViewTreeLifecycleOwner(this@FloatingOverlayService)

            setContent {
                val currentConfig = configState.value
                RECTheme {
                    FloatingPillView(
                        config = currentConfig,
                        isDockedOnLeft = isDockedOnLeftState.value,
                        isDockedOnRight = isDockedOnRightState.value,
                        onDrag = { dx, dy ->
                            windowAnimator?.cancel()
                            val params = this@FloatingOverlayService.layoutParams
                            val bounds = getDockBoundsCached()

                            if (!isActivelyDragging) {
                                isActivelyDragging = true
                                dragAccumulatorX = params.x.toFloat()
                                dragAccumulatorY = params.y.toFloat()
                            }

                            dragAccumulatorX += dx
                            dragAccumulatorY += dy

                            val minDragX = bounds.minX
                            val maxDragX = bounds.maxX
                            val minDragY = bounds.minY
                            val maxDragY = bounds.maxY

                            params.x = dragAccumulatorX.roundToInt().coerceIn(minDragX, maxDragX)
                            params.y = dragAccumulatorY.roundToInt().coerceIn(minDragY, maxDragY)
                            standbyDockY = params.y
                            try {
                                windowManager?.updateViewLayout(overlayView, params)
                            } catch (e: Exception) {
                                // ignore transient update races
                            }
                        },
                        onDragEnd = {
                            isActivelyDragging = false
                            snapToNearestEdge()
                        },
                        onExpandChanged = { isExpanded ->
                            handleExpandChange(isExpanded)
                        },
                        onCollapseComplete = {},
                        onRecordClick = {
                            startActivity(CapturePermissionActivity.createIntent(this@FloatingOverlayService, this@FloatingOverlayService.config))
                        },
                        onReplayClick = {
                            android.widget.Toast.makeText(this@FloatingOverlayService, "⚡ Instant Replay buffer initializing...", android.widget.Toast.LENGTH_SHORT).show()
                            openMainActivity()
                        },
                        onScreenshotClick = {
                            openMainActivity(startRecord = false)
                        },
                        onVaultClick = {
                            openMainActivity(tab = "VAULT")
                        },
                        onSettingsClick = {
                            openMainActivity(tab = "SETTINGS")
                        },
                        onStopClick = {
                            RecordingService.stopService(this@FloatingOverlayService)
                        },
                        onPauseClick = {
                            RecordingService.pauseService(this@FloatingOverlayService)
                        },
                        onResumeClick = {
                            RecordingService.resumeService(this@FloatingOverlayService)
                        }
                    )
                }
            }
        }

        overlayView = composeView
        try {
            windowManager?.addView(composeView, layoutParams)
        } catch (e: Exception) {
            android.util.Log.e("FloatingOverlayService", "Failed to add overlay view", e)
        }
    }

    private var cachedDockBounds: DockBounds? = null

    private fun getDockBoundsCached(): DockBounds {
        return cachedDockBounds ?: getDockBounds().also { cachedDockBounds = it }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        cachedDockBounds = null // Invalidate cached screen bounds on rotation
        // Screen orientation changed (Portrait <-> Landscape)
        overlayView?.post {
            snapToNearestEdge()
        }
    }

    private var windowAnimator: ValueAnimator? = null
    private var lastSnapIpcTimeMs: Long = 0L

    private fun animateWindowTo(targetX: Int, targetY: Int, onEnd: (() -> Unit)? = null) {
        windowAnimator?.cancel()
        val startX = layoutParams.x
        val startY = layoutParams.y
        lastSnapIpcTimeMs = 0L

        windowAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 240
            interpolator = DecelerateInterpolator(1.2f)
            addUpdateListener { anim ->
                val f = anim.animatedFraction
                val newX = (startX + (targetX - startX) * f).roundToInt()
                val newY = (startY + (targetY - startY) * f).roundToInt()
                val now = android.os.SystemClock.uptimeMillis()

                // Throttle Binder IPC across process boundary to ~60Hz (16ms) during 120Hz snap physics
                if (f >= 1f || now - lastSnapIpcTimeMs >= 16L) {
                    lastSnapIpcTimeMs = now
                    layoutParams.x = newX
                    layoutParams.y = newY
                    try {
                        windowManager?.updateViewLayout(overlayView, layoutParams)
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    layoutParams.x = targetX
                    layoutParams.y = targetY
                    try {
                        windowManager?.updateViewLayout(overlayView, layoutParams)
                    } catch (e: Exception) {
                        // ignore
                    }
                    onEnd?.invoke()
                }
            })
            start()
        }
    }

    private fun snapToNearestEdge() {
        val bounds = getDockBoundsCached()
        val currentX = layoutParams.x
        val viewWidth = dpToPx(PILL_WIDTH_DP)
        val isRec = RecordingService.serviceState.value is pixl.rec.core.model.RecorderState.Recording || RecordingService.serviceState.value is pixl.rec.core.model.RecorderState.Paused
        val snapBehavior = if (isRec) config.recordingHudConfig.snapBehavior else config.standbyHudConfig.snapBehavior

        when (snapBehavior) {
            pixl.rec.core.model.HudSnapBehavior.ALWAYS_SNAP_EDGE -> {
                val midX = bounds.screenWidth / 2
                val isLeft = (currentX + viewWidth / 2 < midX)
                val targetX = if (isLeft) bounds.minX else bounds.maxX
                val targetY = standbyDockY.coerceIn(bounds.minY, bounds.maxY)
                animateWindowTo(targetX, targetY) {
                    finishDocking(isLeft, bounds, targetY)
                }
            }
            pixl.rec.core.model.HudSnapBehavior.PROXIMITY_SNAP -> {
                val edgeThresholdPx = dpToPx(24f)
                val isNearLeft = (currentX - bounds.minX) <= edgeThresholdPx
                val isNearRight = (bounds.maxX - currentX) <= edgeThresholdPx

                if (isNearLeft) {
                    val targetY = standbyDockY.coerceIn(bounds.minY, bounds.maxY)
                    animateWindowTo(bounds.minX, targetY) {
                        finishDocking(true, bounds, targetY)
                    }
                } else if (isNearRight) {
                    val targetY = standbyDockY.coerceIn(bounds.minY, bounds.maxY)
                    animateWindowTo(bounds.maxX, targetY) {
                        finishDocking(false, bounds, targetY)
                    }
                } else {
                    finishFreeFloat(bounds)
                }
            }
            pixl.rec.core.model.HudSnapBehavior.FREE_FLOAT -> {
                finishFreeFloat(bounds)
            }
        }
    }

    private fun finishDocking(isLeft: Boolean, bounds: DockBounds, targetY: Int) {
        isDockedOnLeftState.value = isLeft
        isDockedOnRightState.value = !isLeft
        layoutParams.x = if (isLeft) bounds.minX else bounds.maxX
        layoutParams.y = targetY
        standbyDockY = targetY
        try {
            windowManager?.updateViewLayout(overlayView, layoutParams)
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun finishFreeFloat(bounds: DockBounds) {
        isDockedOnLeftState.value = false
        isDockedOnRightState.value = false
        standbyDockY = layoutParams.y
        try {
            windowManager?.updateViewLayout(overlayView, layoutParams)
        } catch (e: Exception) {
            // ignore
        }
    }

    private var menuOverlayView: View? = null

    private fun handleExpandChange(isExpanded: Boolean) {
        if (isExpanded) {
            if (menuOverlayView != null) return
            openRadialMenuOverlay()
        } else {
            removeMenuOverlay()
        }
    }

    private fun openRadialMenuOverlay() {
        val bounds = getDockBoundsCached()
        val isLeft = isDockedOnLeftState.value
        val isRight = isDockedOnRightState.value

        val menuW: Int
        val menuH: Int
        val menuX: Int
        val menuY: Int

        if (isLeft) {
            menuW = dpToPx(186f)
            menuH = dpToPx(240f)
            menuX = -dpToPx(46f)
            menuY = (layoutParams.y - dpToPx(84f)).coerceIn(bounds.minY, bounds.maxY)
        } else if (isRight) {
            menuW = dpToPx(186f)
            menuH = dpToPx(240f)
            menuX = (bounds.screenWidth - dpToPx(140f)).coerceAtLeast(0)
            menuY = (layoutParams.y - dpToPx(84f)).coerceIn(bounds.minY, bounds.maxY)
        } else {
            val pillCenterX = layoutParams.x + dpToPx(PILL_WIDTH_DP / 2f)
            val pillCenterY = layoutParams.y + dpToPx(PILL_HEIGHT_DP / 2f)
            menuW = dpToPx(164f)
            menuH = dpToPx(188f)
            menuX = pillCenterX - dpToPx(82f)
            menuY = pillCenterY - dpToPx(94f)
        }

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val menuParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        val isMenuExpandedState = mutableStateOf(false)

        val menuView = ComposeView(this).apply {
            setViewTreeSavedStateRegistryOwner(this@FloatingOverlayService)
            setViewTreeLifecycleOwner(this@FloatingOverlayService)

            setContent {
                val serviceState by RecordingService.serviceState.collectAsState()
                val isRecordingActive = serviceState is RecorderState.Recording || serviceState is RecorderState.Paused
                val isPaused = serviceState is RecorderState.Paused
                val currentDuration = when (val s = serviceState) {
                    is RecorderState.Recording -> s.durationMs
                    is RecorderState.Paused -> s.durationMs
                    else -> 0L
                }

                val currentConfig = configState.value
                RECTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                isMenuExpandedState.value = false
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .offset { IntOffset(menuX, menuY) }
                                .size(
                                    width = with(LocalDensity.current) { menuW.toDp() },
                                    height = with(LocalDensity.current) { menuH.toDp() }
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    // Absorbs clicks within radial menu boundary to prevent dismissal
                                }
                        ) {
                            FloatingRadialMenuView(
                                isExpanded = isMenuExpandedState.value,
                                isDockedOnLeft = isLeft,
                                isDockedOnRight = isRight,
                                isRecordingActive = isRecordingActive,
                                isPaused = isPaused,
                                durationMs = currentDuration,
                                hudConfig = if (isRecordingActive) currentConfig.recordingHudConfig else currentConfig.standbyHudConfig,
                                onToggleExpand = { expanded ->
                                    if (!expanded) {
                                        isMenuExpandedState.value = false
                                    }
                                },
                                onCollapseComplete = {
                                    removeMenuOverlay()
                                },
                                onDrag = { _, _ -> },
                                onDragEnd = {},
                                onRecordClick = {
                                    removeMenuOverlay()
                                    startActivity(CapturePermissionActivity.createIntent(this@FloatingOverlayService, this@FloatingOverlayService.config))
                                },
                                onPauseClick = {
                                    RecordingService.pauseService(this@FloatingOverlayService)
                                },
                                onResumeClick = {
                                    RecordingService.resumeService(this@FloatingOverlayService)
                                },
                                onStopClick = {
                                    removeMenuOverlay()
                                    RecordingService.stopService(this@FloatingOverlayService)
                                },
                                onGhostClick = {
                                    setTemporarilyHidden(true)
                                    removeMenuOverlay()
                                },
                                onReplayClick = {
                                    removeMenuOverlay()
                                    android.widget.Toast.makeText(this@FloatingOverlayService, "⚡ Instant Replay buffer initializing...", android.widget.Toast.LENGTH_SHORT).show()
                                    openMainActivity()
                                },
                                onScreenshotClick = {
                                    removeMenuOverlay()
                                    openMainActivity(startRecord = false)
                                },
                                onVaultClick = {
                                    removeMenuOverlay()
                                    openMainActivity(tab = "VAULT")
                                },
                                onSettingsClick = {
                                    removeMenuOverlay()
                                    openMainActivity(tab = "SETTINGS")
                                }
                            )
                        }
                    }
                }
            }
        }

        menuOverlayView = menuView
        try {
            windowManager?.addView(menuView, menuParams)
            overlayView?.visibility = View.INVISIBLE
            menuView.post {
                isMenuExpandedState.value = true
            }
        } catch (e: Exception) {
            menuOverlayView = null
            if (!_isTemporarilyHidden.value) {
                overlayView?.visibility = View.VISIBLE
            }
        }
    }

    private fun removeMenuOverlay() {
        val menuView = menuOverlayView ?: return
        menuOverlayView = null
        try {
            windowManager?.removeView(menuView)
        } catch (e: Exception) {
            // ignore
        }
        if (!_isTemporarilyHidden.value) {
            overlayView?.visibility = View.VISIBLE
        }
    }

    private fun openMainActivity(tab: String? = null, startRecord: Boolean = false) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (tab != null) {
                putExtra(MainActivity.EXTRA_TARGET_TAB, tab)
            }
            if (startRecord) {
                putExtra(MainActivity.EXTRA_START_RECORD, true)
            }
        }
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        removeMenuOverlay()
        if (overlayView != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (e: Exception) {
                android.util.Log.w("FloatingOverlayService", "Error removing overlay view", e)
            }
            overlayView = null
        }
        serviceScope.cancel()
    }

    companion object {
        const val EXTRA_CONFIG = "extra_config"

        const val PILL_WIDTH_DP = 116f
        const val PILL_HEIGHT_DP = 88f
        const val PILL_BEZEL_OFFSET_DP = 46f
        const val PILL_ONSCREEN_TOUCH_DP = 70f

        private val _isTemporarilyHidden = kotlinx.coroutines.flow.MutableStateFlow(false)
        val isTemporarilyHidden: kotlinx.coroutines.flow.StateFlow<Boolean> = _isTemporarilyHidden

        fun setTemporarilyHidden(hidden: Boolean) {
            _isTemporarilyHidden.value = hidden
        }

        fun start(context: Context, config: RecordingConfig = RecordingConfig()) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)) {
                _isTemporarilyHidden.value = false
                val intent = Intent(context, FloatingOverlayService::class.java).apply {
                    putExtra(EXTRA_CONFIG, config)
                }
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingOverlayService::class.java)
            context.stopService(intent)
        }
    }
}
