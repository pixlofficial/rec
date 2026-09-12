package pixl.rec.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pixl.rec.core.model.RecordingConfig
import pixl.rec.core.screenshot.ScreenshotCaptureManager
import pixl.rec.service.RecordingService

/**
 * Lightweight, 100% Invisible Trampoline Activity.
 *
 * Prompts the Android MediaProjection consent dialog directly on top of whatever
 * app or game the user is currently playing, without pulling REC into the foreground.
 * Supports both full recording sessions and one-shot standby screenshots.
 */
class CapturePermissionActivity : ComponentActivity() {

    private var config: RecordingConfig = RecordingConfig()
    private var isScreenshotOnly: Boolean = false

    // 1. MediaProjection Screen Capture Permission Contract
    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            if (isScreenshotOnly) {
                val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val projection = projectionManager.getMediaProjection(result.resultCode, result.data!!)
                if (projection == null) {
                    Toast.makeText(this, "⚠️ Failed to obtain MediaProjection", Toast.LENGTH_SHORT).show()
                    finishWithNoAnimation()
                    return@registerForActivityResult
                }
                val metrics = resources.displayMetrics
                val appCtx = applicationContext

                // Capture one-shot screenshot asynchronously in background
                CoroutineScope(Dispatchers.IO).launch {
                    val uri = ScreenshotCaptureManager.captureFromMediaProjection(
                        context = appCtx,
                        mediaProjection = projection,
                        width = metrics.widthPixels,
                        height = metrics.heightPixels,
                        dpi = metrics.densityDpi
                    )
                    withContext(Dispatchers.Main) {
                        if (uri != null) {
                            Toast.makeText(appCtx, "📸 SCREENSHOT SAVED: ${uri.lastPathSegment}", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(appCtx, "⚠️ Failed to capture screenshot", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                RecordingService.startService(
                    context = this,
                    resultCode = result.resultCode,
                    resultData = result.data!!,
                    config = config
                )
            }
        } else {
            val msg = if (isScreenshotOnly) "Screenshot permission cancelled" else "Screen recording permission was cancelled"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
        finishWithNoAnimation()
    }

    // 2. Microphone & Notification Runtime Permission Contract
    private val runtimePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val micGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (!micGranted && config.audioSource.hasMic) {
            Toast.makeText(this, "Microphone permission denied, recording without mic", Toast.LENGTH_SHORT).show()
        }
        launchScreenCapturePrompt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isScreenshotOnly = intent?.getBooleanExtra(EXTRA_IS_SCREENSHOT_ONLY, false) == true
        config = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_CONFIG, RecordingConfig::class.java) ?: RecordingConfig()
        } else {
            @Suppress("DEPRECATION")
            (intent?.getParcelableExtra(EXTRA_CONFIG) as? RecordingConfig) ?: RecordingConfig()
        }

        if (isScreenshotOnly) {
            launchScreenCapturePrompt()
        } else {
            checkAndRequestAudioPermissions()
        }
    }

    private fun checkAndRequestAudioPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (config.audioSource.hasMic &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissionsToRequest.isNotEmpty()) {
            runtimePermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            launchScreenCapturePrompt()
        }
    }

    private fun launchScreenCapturePrompt() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        try {
            projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to launch screen capture: ${e.message}", Toast.LENGTH_SHORT).show()
            finishWithNoAnimation()
        }
    }

    private fun finishWithNoAnimation() {
        finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    companion object {
        const val EXTRA_CONFIG = "EXTRA_CONFIG"
        const val EXTRA_IS_SCREENSHOT_ONLY = "EXTRA_IS_SCREENSHOT_ONLY"

        fun createIntent(context: Context, config: RecordingConfig): Intent {
            return Intent(context, CapturePermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                putExtra(EXTRA_CONFIG, config)
                putExtra(EXTRA_IS_SCREENSHOT_ONLY, false)
            }
        }

        fun createScreenshotIntent(context: Context): Intent {
            return Intent(context, CapturePermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                putExtra(EXTRA_IS_SCREENSHOT_ONLY, true)
            }
        }
    }
}
