package rec.pixl.core.storage

import android.content.ContentValues
import android.content.Context
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import rec.pixl.core.model.RecordingConfig
import java.io.File
import java.io.FileDescriptor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Scoped Storage writer for streaming MP4 recordings directly into [MediaStore.Video.Media]
 * with zero raw file leaks.
 */
class MediaStoreWriter(
    private val context: Context,
    private val config: RecordingConfig
) {
    private val tag = "MediaStoreWriter"
    var currentUri: Uri? = null
        private set

    private var pfd: ParcelFileDescriptor? = null
    var mediaMuxer: MediaMuxer? = null
        private set

    private var filename: String = ""

    /**
     * Creates a new pending MediaStore record and initializes the [MediaMuxer].
     */
    fun open(): MediaMuxer {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        filename = "REC_$timestamp.mp4"

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, filename)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis())
            put(MediaStore.Video.Media.WIDTH, config.width)
            put(MediaStore.Video.Media.HEIGHT, config.height)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/PixL-REC")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val uri = context.contentResolver.insert(collectionUri, values)
            ?: throw IllegalStateException("Failed to create MediaStore record for $filename")

        currentUri = uri
        val parcelFd = context.contentResolver.openFileDescriptor(uri, "rw")
            ?: throw IllegalStateException("Failed to open file descriptor for $uri")

        pfd = parcelFd
        val muxer = MediaMuxer(parcelFd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        mediaMuxer = muxer
        Log.i(tag, "Opened MediaStoreWriter: $filename -> $uri")
        return muxer
    }

    /**
     * Commits the pending video record into MediaStore, publishing it to system gallery.
     */
    fun finish(durationMs: Long, totalBytesWritten: Long) {
        val uri = currentUri ?: return
        try {
            pfd?.close()
        } catch (e: Exception) {
            Log.w(tag, "Error closing ParcelFileDescriptor", e)
        }
        pfd = null

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DURATION, durationMs)
            put(MediaStore.Video.Media.SIZE, totalBytesWritten)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            }
        }

        try {
            context.contentResolver.update(uri, values, null, null)
            Log.i(tag, "Committed video to MediaStore: $uri (${durationMs / 1000}s, $totalBytesWritten bytes)")
        } catch (e: Exception) {
            Log.e(tag, "Failed to commit MediaStore record $uri", e)
        }
    }

    /**
     * Deletes the pending record if recording failed or was discarded.
     */
    fun cancel() {
        val uri = currentUri ?: return
        try {
            pfd?.close()
        } catch (e: Exception) {
            Log.w(tag, "Error closing PFD on cancel", e)
        }
        pfd = null

        try {
            context.contentResolver.delete(uri, null, null)
            Log.i(tag, "Cancelled and deleted pending record $uri")
        } catch (e: Exception) {
            Log.w(tag, "Error deleting cancelled URI $uri", e)
        }
        currentUri = null
    }
}
