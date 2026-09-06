package pixl.rec.core.game

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Process
import android.provider.Settings

/**
 * Lightweight, privacy-first utility for detecting foreground games on Android.
 * Uses native [ApplicationInfo.CATEGORY_GAME] classification and Android's [UsageStatsManager].
 */
object GameDetector {

    /**
     * Checks if the user has granted the PACKAGE_USAGE_STATS permission.
     */
    fun hasUsageAccessPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Launches the system Usage Access settings screen for user authorization.
     */
    fun openUsageAccessSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // Fallback to general settings if specific action fails
            try {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                // Ignore
            }
        }
    }

    /**
     * Checks whether an installed package is registered as a game in Android OS.
     */
    fun isGamePackage(context: Context, packageName: String): Boolean {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appInfo.category == ApplicationInfo.CATEGORY_GAME ||
                    (appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0
            } else {
                (appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Returns the package name of the currently active foreground application,
     * or null if permission is missing or unavailable.
     */
    fun getForegroundPackage(context: Context): String? {
        if (!hasUsageAccessPermission(context)) return null
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null

        val endTime = System.currentTimeMillis()
        val startTime = endTime - 10_000L // Look back 10 seconds
        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        var lastForegroundApp: String? = null

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                lastForegroundApp = event.packageName
            }
        }
        return lastForegroundApp
    }

    /**
     * Convenience method checking whether the current foreground app is a game.
     */
    fun isForegroundAppGame(context: Context): Boolean {
        val fgPkg = getForegroundPackage(context) ?: return false
        if (fgPkg == context.packageName) return false // Ignore REC itself
        return isGamePackage(context, fgPkg)
    }
}
