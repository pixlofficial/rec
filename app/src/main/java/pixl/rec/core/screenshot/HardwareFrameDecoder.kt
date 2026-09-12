package pixl.rec.core.screenshot

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Asynchronous hardware video decoder that decodes a single encoded video keyframe
 * (H.264/AVC or H.265/HEVC) into an uncompressed [Bitmap] on background threads.
 *
 * Guarantees 0ms stall on the live zero-copy recording pipeline.
 */
object HardwareFrameDecoder {

    private const val TAG = "HardwareFrameDecoder"

    suspend fun decodeKeyframeToBitmap(
        videoFormat: MediaFormat,
        keyframeData: ByteArray,
        ptsUs: Long
    ): Bitmap? = withContext(Dispatchers.IO) {
        val mime = videoFormat.getString(MediaFormat.KEY_MIME) ?: MediaFormat.MIMETYPE_VIDEO_AVC
        val width = videoFormat.getInteger(MediaFormat.KEY_WIDTH)
        val height = videoFormat.getInteger(MediaFormat.KEY_HEIGHT)

        var decoder: MediaCodec? = null
        try {
            decoder = MediaCodec.createDecoderByType(mime)
            val decodeFormat = MediaFormat.createVideoFormat(mime, width, height).apply {
                if (videoFormat.containsKey("csd-0")) {
                    setByteBuffer("csd-0", videoFormat.getByteBuffer("csd-0"))
                }
                if (videoFormat.containsKey("csd-1")) {
                    setByteBuffer("csd-1", videoFormat.getByteBuffer("csd-1"))
                }
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
                )
            }

            decoder.configure(decodeFormat, null, null, 0)
            decoder.start()

            // Feed keyframe
            val inIndex = decoder.dequeueInputBuffer(150_000L)
            if (inIndex >= 0) {
                val inputBuffer = decoder.getInputBuffer(inIndex)
                inputBuffer?.clear()
                inputBuffer?.put(keyframeData)
                decoder.queueInputBuffer(inIndex, 0, keyframeData.size, ptsUs, MediaCodec.BUFFER_FLAG_KEY_FRAME)
            } else {
                Log.w(TAG, "Timed out waiting for decoder input buffer")
                return@withContext null
            }

            // Read output
            val bufferInfo = MediaCodec.BufferInfo()
            var outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 250_000L)

            var attempts = 0
            while (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                attempts++
                if (attempts > 5) break
                outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 100_000L)
            }

            if (outputIndex >= 0) {
                val image = decoder.getOutputImage(outputIndex)
                val bitmap = if (image != null) {
                    yuv420ImageToBitmap(image)
                } else {
                    null
                }
                image?.close()
                decoder.releaseOutputBuffer(outputIndex, false)
                bitmap
            } else {
                Log.w(TAG, "Failed to dequeue output buffer from decoder (index $outputIndex)")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "HardwareFrameDecoder error", e)
            null
        } finally {
            try {
                decoder?.stop()
                decoder?.release()
            } catch (ignored: Exception) {}
        }
    }

    private fun yuv420ImageToBitmap(image: Image): Bitmap? {
        val nv21 = yuv420ToNv21(image) ?: return null
        val yuvImage = YuvImage(
            nv21,
            ImageFormat.NV21,
            image.width,
            image.height,
            null
        )
        val out = ByteArrayOutputStream()
        val success = yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
        if (!success) return null
        val jpegBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
    }

    private fun yuv420ToNv21(image: Image): ByteArray? {
        val width = image.width
        val height = image.height
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val nv21 = ByteArray(width * height * 3 / 2)
        var pos = 0

        // Copy Y plane
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        for (row in 0 until height) {
            yBuffer.position(row * yRowStride)
            if (yPixelStride == 1) {
                yBuffer.get(nv21, pos, width)
                pos += width
            } else {
                for (col in 0 until width) {
                    nv21[pos++] = yBuffer.get()
                    if (col < width - 1) {
                        yBuffer.position(yBuffer.position() + yPixelStride - 1)
                    }
                }
            }
        }

        // Interleave V and U planes (NV21 expects V first, then U)
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride
        val uvHeight = height / 2
        val uvWidth = width / 2

        for (row in 0 until uvHeight) {
            for (col in 0 until uvWidth) {
                val vPos = row * uvRowStride + col * uvPixelStride
                val uPos = row * uPlane.rowStride + col * uPlane.pixelStride
                nv21[pos++] = vBuffer.get(vPos)
                nv21[pos++] = uBuffer.get(uPos)
            }
        }

        return nv21
    }
}
