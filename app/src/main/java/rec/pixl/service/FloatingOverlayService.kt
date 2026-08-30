package rec.pixl.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import rec.pixl.core.model.RecordingConfig
import rec.pixl.ui.overlay.FloatingPillView
import rec.pixl.ui.theme.RECTheme

/**
 * Foreground Window Overlay Service hosting the draggable [FloatingPillView] Compose view.
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
        return START_NOT_STICKY
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

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 250
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
                            params.x = (params.x + dx.toInt()).coerceAtLeast(0)
                            params.y = (params.y + dy.toInt()).coerceAtLeast(0)
                            windowManager?.updateViewLayout(overlayView, params)
                        },
                        onStopClick = {
                            RecordingService.stopService(this@FloatingOverlayService)
                            stopSelf()
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
