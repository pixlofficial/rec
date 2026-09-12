package pixl.rec.core.screenshot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Orchestrates tactical screenshot captures in both active recording and standby modes.
 */
object ScreenshotCaptureManager {

    private const val TAG = "ScreenshotCaptureManager"

    /**
     * Decodes an active in-session video keyframe and writes it directly to Scoped Storage.
     */
    suspend fun captureInSession(
        context: Context,
        videoFormat: MediaFormat,
        keyframeData: ByteArray,
        ptsUs: Long
    ): Uri? = withContext(Dispatchers.IO) {
        val bitmap = HardwareFrameDecoder.decodeKeyframeToBitmap(videoFormat, keyframeData, ptsUs)
        if (bitmap == null) {
            Log.w(TAG, "HardwareFrameDecoder failed to produce bitmap from keyframe")
            return@withContext null
        }
        ScreenshotWriter.saveScreenshot(context, bitmap)
    }

    /**
     * Standby one-shot screenshot capture using a temporary [ImageReader] and [VirtualDisplay].
     */
    suspend fun captureFromMediaProjection(
        context: Context,
        mediaProjection: MediaProjection,
        width: Int,
        height: Int,
        dpi: Int
    ): Uri? = withContext(Dispatchers.IO) {
        var imageReader: ImageReader? = null
        var virtualDisplay: VirtualDisplay? = null
        val bitmapDeferred = CompletableDeferred<Bitmap?>()

        try {
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            val handler = Handler(Looper.getMainLooper())

            imageReader.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                if (image != null && !bitmapDeferred.isCompleted) {
                    try {
                        val planes = image.planes
                        val buffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride = planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * width

                        val bitmap = Bitmap.createBitmap(
                            width + rowPadding / pixelStride,
                            height,
                            Bitmap.Config.ARGB_8888
                        )
                        bitmap.copyPixelsFromBuffer(buffer)

                        // Crop padding if necessary
                        val cropped = if (rowPadding > 0) {
                            Bitmap.createBitmap(bitmap, 0, 0, width, height)
                        } else {
                            bitmap
                        }
                        bitmapDeferred.complete(cropped)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to copy image to bitmap", e)
                        bitmapDeferred.complete(null)
                    } finally {
                        image.close()
                    }
                }
            }, handler)

            virtualDisplay = mediaProjection.createVirtualDisplay(
                "PixL-Screenshot",
                width,
                height,
                dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface,
                null,
                handler
            )

            val bitmap = withTimeoutOrNull(2500L) {
                bitmapDeferred.await()
            }

            if (bitmap == null) {
                Log.w(TAG, "Timed out capturing frame from MediaProjection")
                return@withContext null
            }

            ScreenshotWriter.saveScreenshot(context, bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture screenshot from MediaProjection", e)
            null
        } finally {
            try {
                virtualDisplay?.release()
                imageReader?.close()
            } catch (ignored: Exception) {}
        }
    }
}
