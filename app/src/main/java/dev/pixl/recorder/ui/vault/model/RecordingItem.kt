package dev.pixl.recorder.ui.vault.model

import android.graphics.Bitmap
import android.net.Uri
import dev.pixl.recorder.core.storage.StorageCalculator
import java.text.SimpleDateFormat
import java.util.Date
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
        get() = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.US).format(Date(dateAddedSec * 1000))
}
