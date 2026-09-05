package pixl.rec.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import pixl.rec.RecApp
import pixl.rec.R
import pixl.rec.core.engine.ScreenRecorderEngine
import pixl.rec.core.model.RecorderState
import pixl.rec.core.model.RecordingConfig
import pixl.rec.core.sensor.ShakeDetector
import pixl.rec.core.storage.StorageCalculator
import pixl.rec.ui.overlay.CountdownOverlayView
import pixl.rec.ui.theme.RECTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "RecordingService"

/**
 * Android 14/15/16 Foreground Service hosting the zero-copy [ScreenRecorderEngine].
 * Implements [FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION] and [FOREGROUND_SERVICE_TYPE_MICROPHONE].
 * Integrates [CountdownOverlayView], [ShakeDetector], Screen-Off, Low-Battery, and Low-Storage safety watchers.
 */
class RecordingService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var stateCollectionJob: Job? = null
    private var storageSafetyJob: Job? = null
    private var countdownJob: Job? = null

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private var mediaProjection: MediaProjection? = null
    private var engine: ScreenRecorderEngine? = null
    private var shakeDetector: ShakeDetector? = null

    private var screenOffReceiver: BroadcastReceiver? = null
    private var batteryLowReceiver: BroadcastReceiver? = null
    private var countdownOverlayView: ComposeView? = null
    private var recordingConfig: RecordingConfig = RecordingConfig()

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): RecordingService = this@RecordingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                val config = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_CONFIG, RecordingConfig::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_CONFIG) as? RecordingConfig
                } ?: RecordingConfig()

                if (resultCode != 0 && resultData != null) {
                    startRecordingSession(resultCode, resultData, config)
                } else {
                    Log.e(TAG, "Missing MediaProjection result token")
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                stopRecordingSession()
            }
            ACTION_PAUSE -> {
                engine?.pause()
            }
            ACTION_RESUME -> {
                engine?.resume()
            }
        }

        return START_NOT_STICKY
    }

    private fun startRecordingSession(resultCode: Int, resultData: Intent, config: RecordingConfig) {
        recordingConfig = config

        // 1. Enter foreground immediately with required Android 14/15/16 FGS types
        val initialNotification = buildNotification(
            if (config.countdownSeconds > 0) "Starting in ${config.countdownSeconds}s..." else "Initializing recording...",
            isPaused = false
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                val hasMicPermission = ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.RECORD_AUDIO
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (config.audioSource.hasMic && hasMicPermission) {
                    types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                }
                types
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            }
            startForeground(RecApp.NOTIFICATION_ID_RECORDING, initialNotification, fgsType)
        } else {
            startForeground(RecApp.NOTIFICATION_ID_RECORDING, initialNotification)
        }

        // 2. Consume single-use MediaProjection token immediately
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = projectionManager.getMediaProjection(resultCode, resultData)

        if (projection == null) {
            Log.e(TAG, "Failed to obtain MediaProjection token")
            stopSelf()
            return
        }

        mediaProjection = projection

        // Register MediaProjection stop callback
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.w(TAG, "MediaProjection revoked or stopped by system")
                stopRecordingSession()
            }
        }, null)

        // 3. Countdown delay & HUD integration
        if (config.countdownSeconds > 0) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || android.provider.Settings.canDrawOverlays(this)) {
                showCountdownOverlay(config.countdownSeconds) {
                    startEngineAndWatchers(projection, config)
                }
            } else {
                // Fallback to notification timer countdown when overlay permission is unavailable
                countdownJob = serviceScope.launch {
                    for (sec in config.countdownSeconds downTo 1) {
                        updateNotification("Starting in ${sec}s...", isPaused = false)
                        delay(1000L)
                    }
                    startEngineAndWatchers(projection, config)
                }
            }
        } else {
            startEngineAndWatchers(projection, config)
        }
    }

    private fun showCountdownOverlay(seconds: Int, onComplete: () -> Unit) {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@RecordingService)
            setViewTreeSavedStateRegistryOwner(this@RecordingService)
            setContent {
                RECTheme {
                    CountdownOverlayView(
                        countdownSeconds = seconds,
                        onCountdownComplete = {
                            removeCountdownOverlay()
                            onComplete()
                        },
                        onCancel = {
                            removeCountdownOverlay()
                            Log.i(TAG, "Countdown cancelled by user tap")
                            stopRecordingSession()
                        }
                    )
                }
            }
        }

        try {
            wm.addView(composeView, params)
            countdownOverlayView = composeView
        } catch (e: Exception) {
            Log.e(TAG, "Failed to attach Countdown overlay, falling back to coroutine countdown", e)
            countdownJob = serviceScope.launch {
                for (sec in seconds downTo 1) {
                    updateNotification("Starting in ${sec}s...", isPaused = false)
                    delay(1000L)
                }
                onComplete()
            }
        }
    }

    private fun removeCountdownOverlay() {
        countdownOverlayView?.let { view ->
            try {
                val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.removeView(view)
            } catch (e: Exception) {
                Log.w(TAG, "Error removing Countdown overlay", e)
            }
            countdownOverlayView = null
        }
    }

    private fun startEngineAndWatchers(projection: MediaProjection, config: RecordingConfig) {
        // 1. Register Shake-to-Stop detector if configured
        if (config.shakeToStop) {
            shakeDetector = ShakeDetector(this) {
                Log.i(TAG, "Shake detected -> Stopping recording")
                stopRecordingSession()
            }.also { it.start() }
        }

        // 2. Register Screen-Off-to-Stop receiver if configured
        if (config.stopOnScreenOff) {
            val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
            screenOffReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                        Log.i(TAG, "Screen turned off -> Finalizing recording")
                        stopRecordingSession()
                    }
                }
            }
            ContextCompat.registerReceiver(
                this,
                screenOffReceiver!!,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }

        // 3. Register Battery-Low receiver (<3%) for auto-finalization
        val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_LOW)
        batteryLowReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_LOW) {
                    Log.w(TAG, "Low battery tripwire (<3%) -> Auto-saving recording to prevent corruption")
                    stopRecordingSession()
                }
            }
        }
        ContextCompat.registerReceiver(
            this,
            batteryLowReceiver!!,
            batteryFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // 4. Low Storage Safety Tripwire (<200MB)
        storageSafetyJob?.cancel()
        storageSafetyJob = serviceScope.launch {
            while (isActive) {
                delay(3000L) // 3-second interval check
                val available = StorageCalculator.getAvailableStorageBytes()
                if (StorageCalculator.isStorageCriticallyLow(available)) {
                    Log.w(TAG, "Storage critically low (<200MB free) -> Auto-saving recording to prevent corruption")
                    stopRecordingSession()
                    break
                }
            }
        }

        // 5. Manage Floating Overlay Pill visibility ONLY if permission is granted
        if (config.showFloatingPill && (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || android.provider.Settings.canDrawOverlays(this))) {
            FloatingOverlayService.start(this, config)
        }

        // 6. Initialize and start master recording engine
        val recEngine = ScreenRecorderEngine(applicationContext, config, projection)
        engine = recEngine

        stateCollectionJob?.cancel()
        stateCollectionJob = serviceScope.launch {
            recEngine.state.collectLatest { state ->
                _serviceState.value = state
                when (state) {
                    is RecorderState.Recording -> {
                        updateNotification(
                            StorageCalculator.formatDuration(state.durationMs),
                            isPaused = false
                        )
                    }
                    is RecorderState.Paused -> {
                        updateNotification(
                            StorageCalculator.formatDuration(state.durationMs),
                            isPaused = true
                        )
                    }
                    is RecorderState.Finished -> {
                        handleOverlayOnRecordingFinished()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                    is RecorderState.Error -> {
                        handleOverlayOnRecordingFinished()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                    else -> Unit
                }
            }
        }

        recEngine.start()
    }

    private fun stopRecordingSession() {
        countdownJob?.cancel()
        countdownJob = null
        removeCountdownOverlay()

        shakeDetector?.stop()
        shakeDetector = null

        if (screenOffReceiver != null) {
            try {
                unregisterReceiver(screenOffReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering screenOffReceiver", e)
            }
            screenOffReceiver = null
        }

        if (batteryLowReceiver != null) {
            try {
                unregisterReceiver(batteryLowReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering batteryLowReceiver", e)
            }
            batteryLowReceiver = null
        }

        storageSafetyJob?.cancel()
        storageSafetyJob = null

        handleOverlayOnRecordingFinished()

        if (engine != null) {
            engine?.stop()
        } else {
            // Cancelled before engine started (e.g. user cancelled during countdown)
            try {
                mediaProjection?.stop()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping MediaProjection on pre-start cancellation", e)
            }
            mediaProjection = null
            _serviceState.value = RecorderState.Idle
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun handleOverlayOnRecordingFinished() {
        if (!recordingConfig.alwaysOnFloatingPill) {
            FloatingOverlayService.stop(this)
        } else {
            FloatingOverlayService.start(this, recordingConfig)
        }
    }

    private fun updateNotification(timerText: String, isPaused: Boolean) {
        val notification = buildNotification(timerText, isPaused)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(RecApp.NOTIFICATION_ID_RECORDING, notification)
    }

    private fun buildNotification(contentText: String, isPaused: Boolean): Notification {
        val stopIntent = Intent(this, RecordingService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeIntent = Intent(this, RecordingService::class.java).apply {
            action = if (isPaused) ACTION_RESUME else ACTION_PAUSE
        }
        val pauseResumePendingIntent = PendingIntent.getService(
            this, 2, pauseResumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isPaused) getString(R.string.notification_paused_title) else getString(R.string.notification_recording_title)
        val pauseResumeActionTitle = if (isPaused) getString(R.string.notification_action_resume) else getString(R.string.notification_action_pause)
        val pauseResumeIcon = if (isPaused) R.drawable.ic_resume else R.drawable.ic_pause

        return NotificationCompat.Builder(this, RecApp.CHANNEL_ID_RECORDING)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(pauseResumeIcon, pauseResumeActionTitle, pauseResumePendingIntent)
            .addAction(R.drawable.ic_stop, getString(R.string.notification_action_stop), stopPendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)

        countdownJob?.cancel()
        countdownJob = null
        removeCountdownOverlay()

        stateCollectionJob?.cancel()
        storageSafetyJob?.cancel()
        serviceScope.cancel()

        shakeDetector?.stop()
        shakeDetector = null

        if (screenOffReceiver != null) {
            try {
                unregisterReceiver(screenOffReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering screenOffReceiver in onDestroy", e)
            }
            screenOffReceiver = null
        }

        if (batteryLowReceiver != null) {
            try {
                unregisterReceiver(batteryLowReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering batteryLowReceiver in onDestroy", e)
            }
            batteryLowReceiver = null
        }

        handleOverlayOnRecordingFinished()

        engine?.release()
        engine = null

        try {
            mediaProjection?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping MediaProjection", e)
        }
        mediaProjection = null

        _serviceState.value = RecorderState.Idle
        Log.i(TAG, "RecordingService destroyed")
    }

    companion object {
        const val ACTION_START = "pixl.rec.action.START"
        const val ACTION_STOP = "pixl.rec.action.STOP"
        const val ACTION_PAUSE = "pixl.rec.action.PAUSE"
        const val ACTION_RESUME = "pixl.rec.action.RESUME"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_CONFIG = "extra_config"

        private val _serviceState = MutableStateFlow<RecorderState>(RecorderState.Idle)
        val serviceState: StateFlow<RecorderState> = _serviceState.asStateFlow()

        fun startService(context: Context, resultCode: Int, resultData: Intent, config: RecordingConfig) {
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, resultData)
                putExtra(EXTRA_CONFIG, config)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun pauseService(context: Context) {
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }

        fun resumeService(context: Context) {
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_RESUME
            }
            context.startService(intent)
        }
    }
}
