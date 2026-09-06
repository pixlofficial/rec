package pixl.rec.core.notification

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import pixl.rec.R
import pixl.rec.RecApp
import pixl.rec.core.model.RecorderState
import pixl.rec.core.model.RecordingConfig
import pixl.rec.receiver.NotificationActionReceiver
import pixl.rec.service.FloatingOverlayService
import pixl.rec.service.RecordingService
import pixl.rec.ui.CapturePermissionActivity
import pixl.rec.ui.MainActivity

/**
 * Manages the persistent Standby Notification with custom pixel icon controls in Android's notification shade.
 */
object StandbyNotificationManager {

    fun show(context: Context, config: RecordingConfig) {
        if (!config.standbyNotification) {
            cancel(context)
            return
        }

        if (RecordingService.serviceState.value !is RecorderState.Idle) {
            cancel(context)
            return
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val notification = buildNotification(context, config)
        notificationManager.notify(RecApp.NOTIFICATION_ID_STANDBY, notification)
    }

    fun cancel(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        notificationManager.cancel(RecApp.NOTIFICATION_ID_STANDBY)
    }

    private fun buildNotification(context: Context, config: RecordingConfig): Notification {
        // Content Intent: Open MainActivity (navigating to CONFIG tab)
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_TARGET_TAB, "SETTINGS")
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context, 10, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 1: START RECORDING (Launches invisible CapturePermissionActivity directly)
        val startRecIntent = CapturePermissionActivity.createIntent(context, config)
        val startRecPendingIntent = PendingIntent.getActivity(
            context, 11, startRecIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 2: TOGGLE FLOATING PILL
        val togglePillIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_TOGGLE_PILL
        }
        val togglePillPendingIntent = PendingIntent.getBroadcast(
            context, 12, togglePillIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 2 (Alternative): OPEN VAULT (When alwaysOnFloatingPill is false)
        val openVaultIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_TARGET_TAB, "VAULT")
        }
        val openVaultPendingIntent = PendingIntent.getActivity(
            context, 15, openVaultIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 3: OPEN CONFIG / SETTINGS
        val openConfigIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_TARGET_TAB, "SETTINGS")
        }
        val openConfigPendingIntent = PendingIntent.getActivity(
            context, 14, openConfigIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action 4: DISMISS STANDBY NOTIFICATION
        val dismissIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_DISMISS_STANDBY
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context, 13, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Dynamic subtitle with current resolution, FPS, and preset info
        val presetName = config.activePreset.displayName
        val resText = "${config.width}×${config.height}"
        val fpsText = "${config.framerate} FPS"
        val subtitle = "Ready • $resText $fpsText ($presetName)"

        // Dynamic Action 2 Slot: Pill Toggle (if always-on pill enabled) vs Vault (if always-on pill disabled)
        val isPillMode = config.alwaysOnFloatingPill
        val action2Icon: Int
        val action2Desc: String
        val action2PendingIntent: PendingIntent

        if (isPillMode) {
            val isPillShowing = FloatingOverlayService.isPillVisible
            action2Icon = if (isPillShowing) R.drawable.ic_pixel_eye_off else R.drawable.ic_pixel_eye
            action2Desc = if (isPillShowing) "Hide Pill" else "Show Pill"
            action2PendingIntent = togglePillPendingIntent
        } else {
            action2Icon = R.drawable.ic_pixel_vault
            action2Desc = "Open Vault"
            action2PendingIntent = openVaultPendingIntent
        }

        // Collapsed RemoteViews (now contains full 4-button toolbar as well)
        val collapsedView = RemoteViews(context.packageName, R.layout.notification_standby_collapsed).apply {
            setTextViewText(R.id.notif_title, "REC // STANDBY")
            setTextViewText(R.id.notif_subtitle, subtitle)
            setImageViewResource(R.id.btn_pill, action2Icon)
            setContentDescription(R.id.btn_pill, action2Desc)
            setOnClickPendingIntent(R.id.btn_record, startRecPendingIntent)
            setOnClickPendingIntent(R.id.btn_pill, action2PendingIntent)
            setOnClickPendingIntent(R.id.btn_config, openConfigPendingIntent)
            setOnClickPendingIntent(R.id.btn_dismiss, dismissPendingIntent)
        }

        // Expanded RemoteViews (Option 1: Clean Horizontal Icon Toolbar with Record, Pill/Vault, Config, Dismiss)
        val expandedView = RemoteViews(context.packageName, R.layout.notification_standby_expanded).apply {
            setTextViewText(R.id.notif_title, "REC // STANDBY")
            setTextViewText(R.id.notif_subtitle, subtitle)
            setImageViewResource(R.id.btn_pill, action2Icon)
            setContentDescription(R.id.btn_pill, action2Desc)
            setOnClickPendingIntent(R.id.btn_record, startRecPendingIntent)
            setOnClickPendingIntent(R.id.btn_pill, action2PendingIntent)
            setOnClickPendingIntent(R.id.btn_config, openConfigPendingIntent)
            setOnClickPendingIntent(R.id.btn_dismiss, dismissPendingIntent)
        }

        return NotificationCompat.Builder(context, RecApp.CHANNEL_ID_STANDBY)
            .setSmallIcon(R.drawable.ic_pixel_record)
            .setColor(0xFFFF0033.toInt()) // HyperCrimson
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(collapsedView)
            .setCustomBigContentView(expandedView)
            .build()
    }
}
