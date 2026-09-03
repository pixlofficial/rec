package pixl.rec.ui

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
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import pixl.rec.R
import pixl.rec.service.FloatingOverlayService
import pixl.rec.ui.dashboard.DashboardViewModel
import pixl.rec.ui.main.MainScreen
import pixl.rec.ui.navigation.NavigationTab
import pixl.rec.ui.theme.RECTheme

/**
 * Main entry activity handling Compose dashboard initialization, overlay auto-start,
 * and Android single-use MediaProjection consent negotiations.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: DashboardViewModel by viewModels()
    private var currentNavTab by mutableStateOf(NavigationTab.DASHBOARD)

    // 1. MediaProjection Screen Capture Permission Contract
    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            // Start Foreground Recording Service (which handles Overlay, Sensors & MediaCodec)
            viewModel.startRecording(result.resultCode, result.data!!)
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
            Toast.makeText(this, "Microphone permission denied, only internal audio will record", Toast.LENGTH_SHORT).show()
        }
        // Launch MediaProjection screen capture prompt
        requestScreenCapturePermission()
    }

    // 3. Overlay Settings Launcher
    private var hasPromptedOverlay = false
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        hasPromptedOverlay = true
        val config = viewModel.uiState.value.config
        if (config.alwaysOnFloatingPill && (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this))) {
            FloatingOverlayService.start(this, config)
        }
        // If recording flow triggered this, continue to audio permissions
        if (isStartingRecordFlow) {
            isStartingRecordFlow = false
            checkAudioAndRecordPermissions()
        }
    }

    private var isStartingRecordFlow = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Transition from native 0ms splash window to app theme
        setTheme(R.style.Theme_REC)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        handleIntent(intent)

        setContent {
            RECTheme {
                MainScreen(
                    dashboardViewModel = viewModel,
                    initialTab = currentNavTab,
                    onRequestRecordPermission = {
                        checkAndRequestPermissions()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val config = viewModel.uiState.value.config
        val isRecording = viewModel.isRecordingActive.value
        if (config.alwaysOnFloatingPill || (config.showFloatingPill && isRecording)) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
                FloatingOverlayService.start(this, config)
            } else if (!hasPromptedOverlay) {
                hasPromptedOverlay = true
                requestOverlayPermission(forRecording = false)
            }
        } else if (!isRecording) {
            FloatingOverlayService.stop(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val tabExtra = intent?.getStringExtra(EXTRA_TARGET_TAB)
        if (tabExtra != null) {
            when (tabExtra) {
                "VAULT" -> currentNavTab = NavigationTab.VAULT
                "SETTINGS" -> currentNavTab = NavigationTab.SETTINGS
                "MORE" -> currentNavTab = NavigationTab.MORE
                else -> currentNavTab = NavigationTab.DASHBOARD
            }
        }
        if (intent?.getBooleanExtra(EXTRA_START_RECORD, false) == true) {
            checkAndRequestPermissions()
        }
    }

    private fun requestOverlayPermission(forRecording: Boolean) {
        isStartingRecordFlow = forRecording
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        }
    }

    private fun checkAndRequestPermissions() {
        val config = viewModel.uiState.value.config

        // If Floating Pill enabled and permission not granted, prompt once then proceed
        if (config.showFloatingPill && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this) && !hasPromptedOverlay) {
            requestOverlayPermission(forRecording = true)
            return
        }

        checkAudioAndRecordPermissions()
    }

    private fun checkAudioAndRecordPermissions() {
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

    companion object {
        const val EXTRA_TARGET_TAB = "EXTRA_TARGET_TAB"
        const val EXTRA_START_RECORD = "EXTRA_START_RECORD"
    }
}
