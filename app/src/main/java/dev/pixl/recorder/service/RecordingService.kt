package dev.pixl.recorder.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import dev.pixl.recorder.PixLApp
import dev.pixl.recorder.R
import dev.pixl.recorder.core.engine.ScreenRecorderEngine
import dev.pixl.recorder.core.model.RecorderState
import dev.pixl.recorder.core.model.RecordingConfig
import dev.pixl.recorder.core.storage.StorageCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Android 14/15/16 Foreground Service hosting the zero-copy [ScreenRecorderEngine].
 * Implements [FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION] and [FOREGROUND_SERVICE_TYPE_MICROPHONE].
 */
class RecordingService : Service() {

    private val tag = "RecordingService"
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var stateCollectionJob: Job? = null

    private var mediaProjection: MediaProjection? = null
    private var engine: ScreenRecorderEngine? = null

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): RecordingService = this@RecordingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                val config = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getSerializableExtra(EXTRA_CONFIG, RecordingConfig::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getSerializableExtra(EXTRA_CONFIG) as? RecordingConfig
                } ?: RecordingConfig()

                if (resultCode != 0 && resultData != null) {
                    startRecordingSession(resultCode, resultData, config)
                } else {
                    Log.e(tag, "Missing MediaProjection result token")
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
        // 1. Enter foreground immediately with required Android 14/15 FGS types
        val initialNotification = buildNotification("Initializing recording...", isPaused = false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                if (config.audioSource.hasMic) {
                    types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                }
                types
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            }
            startForeground(PixLApp.NOTIFICATION_ID_RECORDING, initialNotification, fgsType)
        } else {
            startForeground(PixLApp.NOTIFICATION_ID_RECORDING, initialNotification)
        }

        // 2. Consume single-use MediaProjection token immediately
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = projectionManager.getMediaProjection(resultCode, resultData)

        if (projection == null) {
            Log.e(tag, "Failed to obtain MediaProjection token")
            stopSelf()
            return
        }

        mediaProjection = projection

        // Register MediaProjection stop callback
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.w(tag, "MediaProjection revoked or stopped by system")
                stopRecordingSession()
            }
        }, null)

        // 3. Initialize and start master recording engine
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
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                    is RecorderState.Error -> {
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
        engine?.stop()
    }

    private fun updateNotification(timerText: String, isPaused: Boolean) {
        val notification = buildNotification(timerText, isPaused)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(PixLApp.NOTIFICATION_ID_RECORDING, notification)
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

        return NotificationCompat.Builder(this, PixLApp.CHANNEL_ID_RECORDING)
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
        stateCollectionJob?.cancel()
        serviceScope.cancel()

        engine?.release()
        engine = null

        try {
            mediaProjection?.stop()
        } catch (e: Exception) {
            Log.w(tag, "Error stopping MediaProjection", e)
        }
        mediaProjection = null

        _serviceState.value = RecorderState.Idle
        Log.i(tag, "RecordingService destroyed")
    }

    companion object {
        const val ACTION_START = "dev.pixl.recorder.action.START"
        const val ACTION_STOP = "dev.pixl.recorder.action.STOP"
        const val ACTION_PAUSE = "dev.pixl.recorder.action.PAUSE"
        const val ACTION_RESUME = "dev.pixl.recorder.action.RESUME"

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
