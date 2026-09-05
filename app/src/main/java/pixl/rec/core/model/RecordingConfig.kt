package pixl.rec.core.model

import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

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
    INTERNAL_AND_MIC("Internal Audio + Microphone"),
    INTERNAL_ONLY("Internal Audio Only"),
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
 * Color dynamic range quantization.
 */
enum class ColorRange(val androidRange: Int, val displayName: String) {
    FULL(MediaFormat.COLOR_RANGE_FULL, "Full (0–255)"),
    LIMITED(MediaFormat.COLOR_RANGE_LIMITED, "Limited (16–235)");
}

/**
 * Gestures used to recall the floating pill when invisible/auto-hidden during recording.
 */
enum class PillRecallGesture(val displayName: String, val description: String) {
    EDGE_SWIPE("Edge Swipe", "Swipe inward from screen edge"),
    EDGE_TAP("Edge Tap", "Tap subtle edge trigger zone"),
    DOUBLE_TAP("Double Tap", "Double-tap the edge trigger");
}

/**
 * Target display or application capture scope.
 */
enum class CaptureTarget(val displayName: String) {
    ENTIRE_SCREEN("Entire Screen"),
    SINGLE_APP("Single App (Android 14+)");
}

/**
 * Canvas orientation locking modes for screen recording.
 */
enum class RecordingOrientation(val displayName: String) {
    AUTO("Auto (match device)"),       // Current behavior: use orientation at record start
    LANDSCAPE("Landscape"),            // Always max×min — gamers' pick
    PORTRAIT("Portrait");              // Always min×max — vertical content creators
}

/**
 * Universal quick configuration presets.
 */
enum class QuickPreset(val displayName: String, val description: String) {
    BEST_QUALITY("Best Quality", "Full native display clarity"),
    GAMING("Gaming (60 FPS)", "High motion stability for games"),
    MAX_FPS("Max FPS", "Highest framerate supported"),
    SMALL_SIZE("Small Size", "Lightweight file for fast sharing"),
    CUSTOM("Custom", "Custom user-configured profile");
}

/**
 * Master configuration profile for zero-copy recording session.
 */
@Parcelize
data class RecordingConfig(
    val width: Int = 1080,
    val height: Int = 2400,
    val dpi: Int = 420,
    val framerate: Int = 60,
    val videoBitrate: Int = 16_000_000, // 16 Mbps standard balanced default
    val videoCodec: VideoCodec = VideoCodec.HEVC,
    val bitrateMode: BitrateMode = BitrateMode.VBR,
    val iFrameIntervalSeconds: Float = 1.0f,
    val audioSource: AudioSource = AudioSource.INTERNAL_AND_MIC,
    val audioBitrate: Int = 256_000, // 256 kbps AAC
    val audioSampleRate: Int = 48_000, // 48 kHz standard studio rate
    val audioChannelCount: Int = 2, // Stereo
    val micGain: Float = 1.0f,
    val internalAudioGain: Float = 1.0f,
    val recordingOrientation: RecordingOrientation = RecordingOrientation.AUTO,
    val activePreset: QuickPreset = QuickPreset.BEST_QUALITY,

    // Advanced Studio & Silicon Controls
    val allowExperimentalFps: Boolean = false,
    val colorRange: ColorRange = ColorRange.FULL,
    val enableIntraRefresh: Boolean = false,

    // Overlay & Clean Canvas Controls
    val showFloatingPill: Boolean = true,
    val alwaysOnFloatingPill: Boolean = true,
    val hidePillDuringRecording: Boolean = false,
    val autoHidePill: Boolean = false,
    val pillRecallGesture: PillRecallGesture = PillRecallGesture.EDGE_SWIPE,
    val shakeToStop: Boolean = true,
    val stopOnScreenOff: Boolean = true,
    val captureTarget: CaptureTarget = CaptureTarget.ENTIRE_SCREEN,
    val countdownSeconds: Int = 0,

    // HUD Customization Configuration (Separate Standby & Recording Configs + Global Snap)
    val standbyHudConfig: HudStyleConfig = HudStyleConfig(animation = HudAnimation.NONE),
    val recordingHudConfig: HudStyleConfig = HudStyleConfig(animation = HudAnimation.BREATHE),
    val hudSnapBehavior: HudSnapBehavior = HudSnapBehavior.PROXIMITY_SNAP
) : Parcelable {

    val hudConfig: HudStyleConfig
        get() = standbyHudConfig

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
     * Returns a copy clamped and aligned to H.264/H.265 16-pixel macroblock requirements.
     */
    fun withMacroblockAlignment(): RecordingConfig {
        val alignedWidth = ((width + 15) / 16) * 16
        val alignedHeight = ((height + 15) / 16) * 16
        return copy(width = alignedWidth, height = alignedHeight)
    }
}
