package pixl.rec.ui.vault.model

import android.graphics.Bitmap
import android.net.Uri
import pixl.rec.core.storage.StorageCalculator
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class RecordingItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateAddedSec: Long,
    val width: Int,
    val height: Int,
    val thumbnail: Bitmap? = null
) {
    val formattedDuration: String
        get() = StorageCalculator.formatDuration(durationMs)

    val formattedSize: String
        get() = StorageCalculator.formatBytes(sizeBytes)

    val formattedDate: String
        get() = dateFormatter.format(Instant.ofEpochSecond(dateAddedSec))

    companion object {
        private val dateFormatter = DateTimeFormatter
            .ofPattern("MMM dd, yyyy • HH:mm", Locale.US)
            .withZone(ZoneId.systemDefault())
    }
}
