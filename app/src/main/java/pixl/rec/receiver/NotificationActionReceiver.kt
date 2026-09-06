package pixl.rec.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import pixl.rec.core.model.RecordingConfig
import pixl.rec.core.notification.StandbyNotificationManager
import pixl.rec.core.storage.ConfigPreferences
import pixl.rec.service.FloatingOverlayService

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_TOGGLE_PILL -> {
                val config = ConfigPreferences.loadConfig(context, RecordingConfig())
                FloatingOverlayService.toggle(context, config)
            }
            ACTION_DISMISS_STANDBY -> {
                StandbyNotificationManager.cancel(context)
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE_PILL = "pixl.rec.action.TOGGLE_PILL"
        const val ACTION_DISMISS_STANDBY = "pixl.rec.action.DISMISS_STANDBY"
    }
}
