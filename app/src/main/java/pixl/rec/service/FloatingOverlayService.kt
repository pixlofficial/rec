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
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import pixl.rec.core.model.RecordingConfig
import pixl.rec.ui.MainActivity
import pixl.rec.ui.overlay.FloatingPillView
import pixl.rec.ui.theme.RECTheme
import kotlin.math.roundToInt

/**
 * Window Overlay Service hosting the edge-snapping Standby Bubble & Cyberpunk Radial Menu,
 * and the live recording telemetry pill.
 *
 * Full multi-orientation (Portrait & Landscape) support with unified physical screen coordinates
 * and strict system navigation bar (3-button / gesture bar) safety clearance margins.
 */
class FloatingOverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var config: RecordingConfig = RecordingConfig()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val passedConfig = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra(EXTRA_CONFIG, RecordingConfig::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getSerializableExtra(EXTRA_CONFIG) as? RecordingConfig
            }
            if (passedConfig != null) {
                config = passedConfig
            }
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
        val viewHeight = overlayView?.height ?: dpToPx(44f)

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

        // Left edge: if navigation bar is on left in landscape, stay outside it; otherwise dock to physical glass edge
        val minX = if (navLeft > 0) {
            navLeft + dpToPx(8f)
        } else {
            -dpToPx(31f)
        }

        // Right edge: if navigation bar is on right in landscape, stay outside it; otherwise dock to physical glass edge
        val maxX = if (navRight > 0) {
            dm.widthPixels - navRight - dpToPx(13f) - dpToPx(16f)
        } else {
            dm.widthPixels - dpToPx(13f)
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

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

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

        // Using FLAG_LAYOUT_IN_SCREEN to map (x, y) directly to absolute screen pixels
        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = -dpToPx(31f)
            y = dpToPx(300f)
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingOverlayService)
            setViewTreeSavedStateRegistryOwner(this@FloatingOverlayService)
            setContent {
                RECTheme {
                    FloatingPillView(
                        config = config,
                        onDrag = { dx, dy ->
                            val params = this@FloatingOverlayService.layoutParams
                            val bounds = getDockBounds()

                            params.x = (params.x + dx.toInt()).coerceIn(bounds.minX, bounds.maxX)
                            params.y = (params.y + dy.toInt()).coerceIn(bounds.minY, bounds.maxY)
                            try {
                                windowManager?.updateViewLayout(overlayView, params)
                            } catch (e: Exception) {
                                // ignore transient update races
                            }
                        },
                        onDragEnd = {
                            snapToNearestEdge()
                        },
                        onExpandChanged = { isExpanded ->
                            handleExpandChange(isExpanded)
                        },
                        onRecordClick = {
                            openMainActivity(startRecord = true)
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Screen orientation changed (Portrait <-> Landscape)
        overlayView?.post {
            snapToNearestEdge()
        }
    }

    private fun snapToNearestEdge() {
        val bounds = getDockBounds()
        val currentX = layoutParams.x
        val currentY = layoutParams.y
        val viewWidth = overlayView?.width ?: dpToPx(44f)

        layoutParams.y = currentY.coerceIn(bounds.minY, bounds.maxY)

        val midX = bounds.screenWidth / 2
        val targetX = if (currentX + viewWidth / 2 < midX) {
            bounds.minX
        } else {
            bounds.maxX
        }

        val animator = ValueAnimator.ofInt(currentX, targetX).apply {
            duration = 240
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                layoutParams.x = anim.animatedValue as Int
                try {
                    windowManager?.updateViewLayout(overlayView, layoutParams)
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
        animator.start()
    }

    private fun handleExpandChange(isExpanded: Boolean) {
        val bounds = getDockBounds()
        if (isExpanded) {
            val menuWidthPx = dpToPx(220f)
            val menuHeightPx = dpToPx(220f)
            val currentX = layoutParams.x
            val currentY = layoutParams.y

            val minX = (bounds.minX + dpToPx(35f)).coerceAtLeast(dpToPx(16f))
            val maxX = (bounds.maxX - menuWidthPx - dpToPx(8f)).coerceAtLeast(minX)

            val targetX = if (currentX + menuWidthPx / 2 < bounds.screenWidth / 2) {
                minX
            } else {
                maxX
            }

            val minY = bounds.minY
            val maxY = (bounds.maxY + dpToPx(44f) - menuHeightPx).coerceAtLeast(minY)
            val targetY = currentY.coerceIn(minY, maxY)

            layoutParams.x = targetX
            layoutParams.y = targetY
            try {
                windowManager?.updateViewLayout(overlayView, layoutParams)
            } catch (e: Exception) {
                // ignore
            }
        } else {
            snapToNearestEdge()
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

        if (overlayView != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (e: Exception) {
                android.util.Log.w("FloatingOverlayService", "Error removing overlay view", e)
            }
            overlayView = null
        }
    }

    companion object {
        const val EXTRA_CONFIG = "extra_config"

        fun start(context: Context, config: RecordingConfig = RecordingConfig()) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)) {
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
