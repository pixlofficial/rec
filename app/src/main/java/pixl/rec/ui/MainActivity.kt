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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import pixl.rec.R
import pixl.rec.core.notification.StandbyNotificationManager
import pixl.rec.core.storage.ConfigPreferences
import pixl.rec.service.FloatingOverlayService
import pixl.rec.ui.dashboard.DashboardViewModel
import pixl.rec.ui.main.MainScreen
import pixl.rec.ui.navigation.NavigationTab
import pixl.rec.ui.setup.SetupModal
import pixl.rec.ui.setup.SetupModalMode
import pixl.rec.ui.theme.RECTheme

/**
 * Main entry activity handling Compose dashboard initialization, overlay auto-start,
 * setup wizard modals, and Android single-use MediaProjection consent negotiations.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: DashboardViewModel by viewModels()
    private var currentNavTab by mutableStateOf(NavigationTab.DASHBOARD)
    private var activeSetupModalMode by mutableStateOf<SetupModalMode?>(null)

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

    // 2. Microphone & Notification Permission Launcher (Legacy / Fallback)
    private val runtimePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val micGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (!micGranted) {
            Toast.makeText(this, "Microphone permission denied, only internal audio will record", Toast.LENGTH_SHORT).show()
        }
        if (isStartingRecordFlow) {
            isStartingRecordFlow = false
            requestScreenCapturePermission()
        }
    }

    // 3. Overlay Settings Launcher
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val config = viewModel.uiState.value.config
        if (config.alwaysOnFloatingPill && (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this))) {
            FloatingOverlayService.start(this, config)
        }
    }

    // 4. Standalone Audio Permission Launcher (for SetupModal)
    private val audioOnlyPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Microphone permission denied, only internal audio will record", Toast.LENGTH_SHORT).show()
        }
        if (isStartingRecordFlow) {
            isStartingRecordFlow = false
            requestScreenCapturePermission()
        }
    }

    // 5. Standalone Notification Permission Launcher (for SetupModal)
    private val notificationOnlyPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val config = viewModel.uiState.value.config
            if (config.standbyNotification) {
                StandbyNotificationManager.show(this, config)
            }
        }
    }

    private var isStartingRecordFlow = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Transition from native 0ms splash window to app theme
        setTheme(R.style.Theme_REC)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        handleIntent(intent)

        // Check if user should see first-launch welcome modal
        if (!ConfigPreferences.hasSeenWelcome(this)) {
            activeSetupModalMode = SetupModalMode.WELCOME
        }

        setContent {
            RECTheme {
                val uiState by viewModel.uiState.collectAsState()

                MainScreen(
                    dashboardViewModel = viewModel,
                    initialTab = currentNavTab,
                    onRequestRecordPermission = {
                        checkAndRequestPermissions()
                    },
                    onOpenSetupGuide = {
                        activeSetupModalMode = SetupModalMode.WELCOME
                    }
                )

                activeSetupModalMode?.let { mode ->
                    SetupModal(
                        mode = mode,
                        config = uiState.config,
                        capabilities = uiState.capabilities,
                        onRequestOverlayPermission = {
                            requestOverlayPermission(forRecording = false)
                        },
                        onRequestAudioPermission = {
                            audioOnlyPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        onRequestNotificationPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationOnlyPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onGetStarted = {
                            ConfigPreferences.setHasSeenWelcome(this@MainActivity, true)
                            val wasFirstRecord = mode == SetupModalMode.FIRST_RECORD
                            activeSetupModalMode = null
                            if (wasFirstRecord) {
                                proceedToRecordFromWizard()
                            }
                        },
                        onSkip = {
                            ConfigPreferences.setHasSeenWelcome(this@MainActivity, true)
                            activeSetupModalMode = null
                        },
                        onDismiss = {
                            if (mode == SetupModalMode.WELCOME) {
                                ConfigPreferences.setHasSeenWelcome(this@MainActivity, true)
                            }
                            activeSetupModalMode = null
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val config = viewModel.uiState.value.config
        val isRecording = viewModel.isRecordingActive.value
        val hasSeenWelcome = ConfigPreferences.hasSeenWelcome(this)

        if (hasSeenWelcome && (config.alwaysOnFloatingPill || (config.showFloatingPill && isRecording))) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
                FloatingOverlayService.start(this, config)
            }
        } else if (!isRecording) {
            FloatingOverlayService.stop(this)
        }

        if (config.standbyNotification && !isRecording) {
            StandbyNotificationManager.show(this, config)
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
        val isAudioGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val isOverlayGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
        val isNotificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(this).areNotificationsEnabled()
        }

        val needsAudio = config.audioSource.hasAudio && !isAudioGranted
        val needsOverlay = config.showFloatingPill && !isOverlayGranted
        val needsNotification = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                (config.standbyNotification || !config.showFloatingPill) &&
                !isNotificationGranted

        if (needsAudio || needsOverlay || needsNotification) {
            // Open First Record Wizard modal to explain missing permissions before recording
            activeSetupModalMode = SetupModalMode.FIRST_RECORD
            return
        }

        // All required permissions granted -> Proceed straight to screen capture consent
        requestScreenCapturePermission()
    }

    private fun proceedToRecordFromWizard() {
        requestScreenCapturePermission()
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
