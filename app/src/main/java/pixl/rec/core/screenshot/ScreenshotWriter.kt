package pixl.rec.core.screenshot

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Scoped Storage writer for in-game screenshots and still captures.
 * Writes high-resolution lossless PNG/JPEG images to Pictures/PixL-REC.
 */
object ScreenshotWriter {

    private const val TAG = "ScreenshotWriter"

    suspend fun saveScreenshot(
        context: Context,
        bitmap: Bitmap,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
        quality: Int = 100
    ): Uri? = withContext(Dispatchers.IO) {
        val extension = if (format == Bitmap.CompressFormat.JPEG) "jpg" else "png"
        val mimeType = if (format == Bitmap.CompressFormat.JPEG) "image/jpeg" else "image/png"
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val displayName = "SHOT_$timestamp.$extension"

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            put(MediaStore.Images.Media.WIDTH, bitmap.width)
            put(MediaStore.Images.Media.HEIGHT, bitmap.height)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/PixL-REC")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val resolver = context.contentResolver
        var uri: Uri? = null
        try {
            uri = resolver.insert(collectionUri, contentValues) ?: return@withContext null
            resolver.openOutputStream(uri)?.use { outputStream: OutputStream ->
                val success = bitmap.compress(format, quality, outputStream)
                outputStream.flush()
                if (!success) {
                    throw IllegalStateException("Bitmap compression returned false")
                }
            } ?: throw IllegalStateException("Failed to open output stream for screenshot $uri")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }

            Log.i(TAG, "Successfully saved screenshot to Scoped Storage: $displayName ($uri)")
            uri
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save screenshot", e)
            if (uri != null) {
                try {
                    resolver.delete(uri, null, null)
                } catch (delEx: Exception) {
                    Log.w(TAG, "Failed to cleanup pending screenshot URI", delEx)
                }
            }
            null
        }
    }
}
