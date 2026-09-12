package pixl.rec.core.replay

import android.content.Context
import android.media.MediaCodec
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pixl.rec.core.model.RecordingConfig
import pixl.rec.core.storage.MediaStoreWriter
import java.nio.ByteBuffer

/**
 * High-speed, non-blocking MediaMuxer worker that drains a [ReplaySnapshot] and writes
 * a standalone MP4 clip directly into Scoped Storage on [Dispatchers.IO].
 */
object ReplayClipMuxer {
    private const val TAG = "ReplayClipMuxer"

    suspend fun saveClip(
        context: Context,
        config: RecordingConfig,
        snapshot: ReplaySnapshot
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val writer = MediaStoreWriter(
                context = context,
                config = config,
                customPrefix = "CLIP_"
            )
            val muxer = writer.open()

            var videoTrack = -1
            var audioTrack = -1

            try {
                videoTrack = muxer.addTrack(snapshot.videoFormat)

                val audioFmt = snapshot.audioFormat
                if (audioFmt != null) {
                    audioTrack = muxer.addTrack(audioFmt)
                }

                muxer.start()

                var writtenBytes = 0L
                val bufferInfo = MediaCodec.BufferInfo()

                for (sample in snapshot.samples) {
                    val track = if (sample.trackIndex == 0) videoTrack else audioTrack
                    if (track < 0) continue

                    val byteBuffer = ByteBuffer.wrap(sample.data)
                    bufferInfo.set(
                        0,
                        sample.sizeBytes,
                        sample.presentationTimeUs,
                        sample.flags
                    )
                    muxer.writeSampleData(track, byteBuffer, bufferInfo)
                    writtenBytes += sample.sizeBytes
                }

                muxer.stop()
                muxer.release()

                writer.finish(snapshot.durationMs, writtenBytes)

                val uri = writer.currentUri ?: throw IllegalStateException("Replay clip URI is null after commit")
                Log.i(TAG, "Replay clip successfully saved: $uri (${snapshot.durationMs}ms, ${writtenBytes / 1024} KB)")
                uri
            } catch (e: Exception) {
                Log.e(TAG, "Failed to write replay clip", e)
                try {
                    muxer.release()
                } catch (re: Exception) {
                    Log.w(TAG, "Error releasing muxer after failure", re)
                }
                writer.cancel()
                throw e
            }
        }
    }
}
