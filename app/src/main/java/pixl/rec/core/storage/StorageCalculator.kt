package pixl.rec.core.storage

import android.os.Environment
import android.os.StatFs
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Storage estimation formulas and disk space monitoring for high-bitrate screen recording sessions.
 */
object StorageCalculator {

    const val CRITICAL_LOW_STORAGE_THRESHOLD_BYTES = 2_147_483_648L // 2.0 GB

    /**
     * Computes the data write rate in Megabytes per Minute:
     * Data Rate (MB/min) = ((Video Bitrate + Audio Bitrate) / 8 / (1024 * 1024)) * 60
     */
    fun calculateMbPerMinute(videoBitrateBps: Int, audioBitrateBps: Int): Double {
        val totalBitrateBps = videoBitrateBps.toDouble() + audioBitrateBps.toDouble()
        val bytesPerSecond = totalBitrateBps / 8.0
        val mbPerSecond = bytesPerSecond / (1024.0 * 1024.0)
        return mbPerSecond * 60.0
    }

    /**
     * Queries available free disk space on the primary storage partition.
     */
    fun getAvailableStorageBytes(storageDirectory: File = Environment.getExternalStorageDirectory()): Long {
        return try {
            val stat = StatFs(storageDirectory.path)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Estimates maximum recording time remaining (in minutes) before storage exhaustion.
     */
    fun estimateRemainingMinutes(
        availableBytes: Long,
        videoBitrateBps: Int,
        audioBitrateBps: Int
    ): Double {
        val totalBytesPerSecond = (videoBitrateBps.toDouble() + audioBitrateBps.toDouble()) / 8.0
        if (totalBytesPerSecond <= 0.0) return 0.0

        val remainingSeconds = availableBytes / totalBytesPerSecond
        return remainingSeconds / 60.0
    }

    /**
     * Checks if remaining disk space is below safety threshold (2 GB).
     */
    fun isStorageLow(availableBytes: Long, thresholdBytes: Long = CRITICAL_LOW_STORAGE_THRESHOLD_BYTES): Boolean {
        return availableBytes < thresholdBytes
    }

    /**
     * Formats bytes into human-readable string (e.g., "12.4 MB", "1.82 GB").
     */
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (kotlin.math.log10(bytes.toDouble()) / kotlin.math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
        return String.format(Locale.US, "%.1f %s", value, units[digitGroups])
    }

    /**
     * Formats duration milliseconds into "HH:MM:SS" or "MM:SS".
     */
    fun formatDuration(durationMs: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(durationMs)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60

        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }
}
