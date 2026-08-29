package dev.pixl.recorder.core.model

import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Parcelable
import java.io.Serializable

/**
 * Supported hardware video codec standard representations.
 */
enum class VideoCodec(val mimeType: String, val displayName: String) {
    HEVC(MediaFormat.MIMETYPE_VIDEO_HEVC, "HEVC (H.265)"),
    AVC(MediaFormat.MIMETYPE_VIDEO_AVC, "AVC (H.264)"),
    AV1(MediaFormat.MIMETYPE_VIDEO_AV1, "AV1");

    companion object {
        fun fromMime(mime: String): VideoCodec = entries.find { it.mimeType.equals(mime, ignoreCase = true) } ?: HEVC
    }
}

/**
 * Audio capture routing modes.
 */
enum class AudioSource(val displayName: String) {
    INTERNAL_AND_MIC("Game Audio + Microphone"),
    INTERNAL_ONLY("Game Audio Only (Internal)"),
    MIC_ONLY("Microphone Only"),
    MUTE("Muted (No Audio)");

    val hasInternal: Boolean get() = this == INTERNAL_AND_MIC || this == INTERNAL_ONLY
    val hasMic: Boolean get() = this == INTERNAL_AND_MIC || this == MIC_ONLY
    val hasAudio: Boolean get() = this != MUTE
}

/**
 * Rate control strategy for video encoding.
 */
enum class BitrateMode(val androidMode: Int, val displayName: String) {
    VBR(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR, "Variable Bitrate (VBR)"),
    CBR(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR, "Constant Bitrate (CBR)"),
    CQ(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ, "Constant Quality (CQ)")
}

/**
 * Master configuration profile for zero-copy recording session.
 */
data class RecordingConfig(
    val width: Int = 1080,
    val height: Int = 2400,
    val dpi: Int = 420,
    val framerate: Int = 60,
    val videoBitrate: Int = 50_000_000, // 50 Mbps default for 1080p high quality
    val videoCodec: VideoCodec = VideoCodec.HEVC,
    val bitrateMode: BitrateMode = BitrateMode.VBR,
    val iFrameIntervalSeconds: Int = 1,
    val audioSource: AudioSource = AudioSource.INTERNAL_AND_MIC,
    val audioBitrate: Int = 256_000, // 256 kbps AAC
    val audioSampleRate: Int = 48_000, // 48 kHz standard studio rate
    val audioChannelCount: Int = 2, // Stereo
    val micGain: Float = 1.0f,
    val internalAudioGain: Float = 1.0f
) : Serializable {

    val aspectRatio: Float
        get() = if (height != 0) width.toFloat() / height.toFloat() else 1.0f

    val totalBitrateMbps: Float
        get() = (videoBitrate + if (audioSource.hasAudio) audioBitrate else 0) / 1_000_000f

    /**
     * Estimated disk space consumption in Megabytes per Minute.
     */
    val estimatedMbPerMinute: Double
        get() = ((videoBitrate + if (audioSource.hasAudio) audioBitrate else 0) / 8.0 / (1024.0 * 1024.0)) * 60.0

    /**
     * Returns a copy clamped and aligned to H.264/H.265 macroblock requirements (multiples of 16 or 2).
     */
    fun withMacroblockAlignment(): RecordingConfig {
        val alignedWidth = (width + 1) and 1.inv() // Even number
        val alignedHeight = (height + 1) and 1.inv() // Even number
        return copy(width = alignedWidth, height = alignedHeight)
    }
}
