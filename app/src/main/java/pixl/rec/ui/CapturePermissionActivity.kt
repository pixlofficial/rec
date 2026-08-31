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
import pixl.rec.core.model.RecordingConfig
import pixl.rec.service.RecordingService

/**
 * Lightweight, 100% Invisible Trampoline Activity.
 *
 * Prompts the Android MediaProjection consent dialog directly on top of whatever
 * app or game the user is currently playing, without pulling REC into the foreground.
 */
class CapturePermissionActivity : ComponentActivity() {

    private var config: RecordingConfig = RecordingConfig()

    // 1. MediaProjection Screen Capture Permission Contract
    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            RecordingService.startService(
                context = this,
                resultCode = result.resultCode,
                resultData = result.data!!,
                config = config
            )
        } else {
            Toast.makeText(this, "Screen recording permission was cancelled", Toast.LENGTH_SHORT).show()
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
        config = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_CONFIG, RecordingConfig::class.java) ?: RecordingConfig()
        } else {
            @Suppress("DEPRECATION")
            (intent?.getParcelableExtra(EXTRA_CONFIG) as? RecordingConfig) ?: RecordingConfig()
        }

        checkAndRequestAudioPermissions()
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

        fun createIntent(context: Context, config: RecordingConfig): Intent {
            return Intent(context, CapturePermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                putExtra(EXTRA_CONFIG, config)
            }
        }
    }
}
