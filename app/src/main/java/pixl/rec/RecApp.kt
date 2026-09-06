package pixl.rec

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class RecApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java) ?: return

            // 1. Live Recording Active Channel
            val recordingChannel = NotificationChannel(
                CHANNEL_ID_RECORDING,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
            }
            notificationManager.createNotificationChannel(recordingChannel)

            // 2. Silent Recording Channel (FGS Compliance when notification is disabled)
            val silentChannel = NotificationChannel(
                CHANNEL_ID_RECORDING_SILENT,
                getString(R.string.notification_channel_silent_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_silent_desc)
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
            }
            notificationManager.createNotificationChannel(silentChannel)

            // 3. Standby Quick Controls Channel (Persistent in main shade, auto-expanded, silent)
            val standbyChannel = NotificationChannel(
                CHANNEL_ID_STANDBY,
                getString(R.string.notification_channel_standby_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.notification_channel_standby_desc)
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(standbyChannel)
        }
    }

    companion object {
        const val CHANNEL_ID_RECORDING = "rec_recording_channel"
        const val CHANNEL_ID_RECORDING_SILENT = "rec_recording_silent_channel"
        const val CHANNEL_ID_STANDBY = "rec_standby_channel_v2"

        const val NOTIFICATION_ID_STANDBY = 1000
        const val NOTIFICATION_ID_RECORDING = 1001
    }
}
