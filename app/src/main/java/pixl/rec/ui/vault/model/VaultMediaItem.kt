package pixl.rec.ui.vault.model

import android.graphics.Bitmap
import android.net.Uri
import pixl.rec.core.storage.StorageCalculator
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Media-neutral representation of a file in the PixL REC Media Vault.
 * Unifies full-length recordings, broadcast stream archives, instant replay clips,
 * and high-resolution screenshots.
 */
data class VaultMediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val mediaType: VaultMediaType,
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val dateAddedSec: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val mimeType: String = "video/mp4",
    val thumbnail: Bitmap? = null
) {
    val uniqueKey: String
        get() = uri.toString()

    val isVideo: Boolean
        get() = mediaType.isVideo

    val isImage: Boolean
        get() = mediaType.isImage

    val formattedDuration: String
        get() = if (durationMs > 0) StorageCalculator.formatDuration(durationMs) else ""

    val formattedSize: String
        get() = StorageCalculator.formatBytes(sizeBytes)

    val formattedDate: String
        get() = dateFormatter.format(Instant.ofEpochSecond(dateAddedSec))

    val formattedResolution: String
        get() = if (width > 0 && height > 0) "${width}x${height}" else ""

    companion object {
        private val dateFormatter = DateTimeFormatter
            .ofPattern("MMM dd, yyyy • HH:mm", Locale.US)
            .withZone(ZoneId.systemDefault())

        /**
         * Backward compatibility factory from legacy [RecordingItem].
         */
        fun fromRecordingItem(item: RecordingItem): VaultMediaItem {
            val type = VaultMediaClassifier.classify(item.displayName, "video/mp4")
            return VaultMediaItem(
                id = item.id,
                uri = item.uri,
                displayName = item.displayName,
                mediaType = type,
                durationMs = item.durationMs,
                sizeBytes = item.sizeBytes,
                dateAddedSec = item.dateAddedSec,
                width = item.width,
                height = item.height,
                mimeType = "video/mp4",
                thumbnail = item.thumbnail
            )
        }
    }
}
