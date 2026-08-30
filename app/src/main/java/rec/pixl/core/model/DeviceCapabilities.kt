package rec.pixl.core.model

/**
 * Detailed capability metadata for a single video codec encoder on this SoC.
 */
data class CodecCapabilityInfo(
    val codec: VideoCodec,
    val codecName: String,
    val isHardwareAccelerated: Boolean,
    val isSoftwareOnly: Boolean,
    val maxWidth: Int,
    val maxHeight: Int,
    val maxBitrate: Int,
    val supportedFramerates: List<Int>,
    val isSupported: Boolean
)

/**
 * Active display physical dimensions, density, and hardware refresh rate limits.
 */
data class DisplayProfile(
    val physicalWidth: Int,
    val physicalHeight: Int,
    val densityDpi: Int,
    val currentRefreshRate: Float,
    val supportedRefreshRates: List<Float>
)

/**
 * Aggregated hardware capabilities discovered during pre-flight probe.
 */
data class DeviceCapabilities(
    val display: DisplayProfile,
    val codecs: Map<VideoCodec, CodecCapabilityInfo>,
    val hasInternalAudioCapture: Boolean,
    val maxHardwareFps: Int,
    val recommendedCodec: VideoCodec,
    val recommendedFramerate: Int,
    val recommendedWidth: Int,
    val recommendedHeight: Int
) {
    val isHevcHardwareSupported: Boolean
        get() = codecs[VideoCodec.HEVC]?.isHardwareAccelerated == true

    val isAv1HardwareSupported: Boolean
        get() = codecs[VideoCodec.AV1]?.isHardwareAccelerated == true &&
                codecs[VideoCodec.AV1]?.isSoftwareOnly == false

    val is120FpsSupported: Boolean
        get() = maxHardwareFps >= 120

    val is144FpsSupported: Boolean
        get() = maxHardwareFps >= 144
}
