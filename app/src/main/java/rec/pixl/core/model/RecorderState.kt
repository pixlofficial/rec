package rec.pixl.core.model

import android.net.Uri

/**
 * Immutable state representations emitted by [ScreenRecorderEngine] and [RecordingService].
 */
sealed interface RecorderState {

    /**
     * Engine is idle, waiting for user trigger.
     */
    data object Idle : RecorderState

    /**
     * Initializing MediaCodec, Surface, AudioRecord, and MediaMuxer.
     */
    data class Preparing(val status: String = "Initializing hardware pipeline...") : RecorderState

    /**
     * Actively capturing frames and audio.
     */
    data class Recording(
        val durationMs: Long = 0L,
        val bytesWritten: Long = 0L,
        val currentFps: Float = 0f,
        val gameAudioDb: Float = -60f,
        val micAudioDb: Float = -60f,
        val isPaused: Boolean = false
    ) : RecorderState

    /**
     * Capturing paused.
     */
    data class Paused(
        val durationMs: Long,
        val bytesWritten: Long
    ) : RecorderState

    /**
     * Finalizing MP4 file in MediaStore and releasing codec resources.
     */
    data object Stopping : RecorderState

    /**
     * Recording successfully completed and committed to MediaStore.
     */
    data class Finished(
        val uri: Uri?,
        val durationMs: Long,
        val totalBytes: Long,
        val formattedSize: String
    ) : RecorderState

    /**
     * Error encountered during hardware setup or active streaming.
     */
    data class Error(
        val message: String,
        val throwable: Throwable? = null
    ) : RecorderState
}
