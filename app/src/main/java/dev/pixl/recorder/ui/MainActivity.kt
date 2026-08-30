package dev.pixl.recorder.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import dev.pixl.recorder.R
import dev.pixl.recorder.ui.dashboard.DashboardScreen
import dev.pixl.recorder.ui.dashboard.DashboardViewModel
import dev.pixl.recorder.ui.theme.RECTheme

/**
 * Main entry activity handling Compose dashboard initialization and Android 14/15
 * single-use MediaProjection consent negotiations.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: DashboardViewModel by viewModels()

    // 1. MediaProjection Screen Capture Permission Contract
    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            // Start Foreground Recording Service (which handles Overlay, Sensors & MediaCodec)
            viewModel.startRecording(result.resultCode, result.data!!)

            // Move to background to reveal game / home screen immediately
            moveTaskToBack(true)
        } else {
            Toast.makeText(this, "Screen recording permission was denied", Toast.LENGTH_SHORT).show()
        }
    }

    // 2. Microphone & Notification Permission Launcher
    private val runtimePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val micGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (!micGranted) {
            Toast.makeText(this, "Microphone permission denied, only internal audio will record", Toast.LENGTH_LONG).show()
        }
        // Launch MediaProjection screen capture prompt
        requestScreenCapturePermission()
    }

    // 3. Overlay Settings Launcher
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkAndRequestPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Transition from native 0ms splash window to app theme
        setTheme(R.style.Theme_REC)
        super.onCreate(savedInstanceState)

        setContent {
            RECTheme {
                DashboardScreen(
                    viewModel = viewModel,
                    onRequestRecordPermission = {
                        checkAndRequestPermissions()
                    }
                )
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val config = viewModel.uiState.value.config

        // 1. Check Floating Overlay Permission only if floating pill is enabled
        if (config.showFloatingPill && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
            return
        }

        // 2. Check Audio & Notification Permissions
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
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
            requestScreenCapturePermission()
        }
    }

    private fun requestScreenCapturePermission() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }
}
