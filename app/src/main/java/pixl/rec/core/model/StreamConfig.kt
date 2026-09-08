package pixl.rec.core.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Supported broadcasting platforms with optimized presets.
 */
enum class StreamPlatform(
    val displayName: String,
    val defaultEndpoint: String,
    val defaultVideoBitrate: Int,
    val recommendedFps: Int,
    val supportsHevc: Boolean,
    val dashboardUrl: String
) {
    YOUTUBE(
        displayName = "YouTube Live",
        defaultEndpoint = "rtmps://a.rtmp.youtube.com/live2",
        defaultVideoBitrate = 9_000_000, // 9 Mbps for 1440p/1080p60
        recommendedFps = 60,
        supportsHevc = true, // YouTube supports Enhanced RTMP HEVC
        dashboardUrl = "https://studio.youtube.com/channel/UC/livestreaming"
    ),
    TWITCH(
        displayName = "Twitch",
        defaultEndpoint = "rtmp://live.twitch.tv/app/",
        defaultVideoBitrate = 6_000_000, // Twitch standard 6 Mbps ingest limit
        recommendedFps = 60,
        supportsHevc = false, // AVC required
        dashboardUrl = "https://dashboard.twitch.tv/stream-manager"
    ),
    KICK(
        displayName = "Kick",
        defaultEndpoint = "rtmps://fa723fc1b171.global-contribute.live-video.net/app/",
        defaultVideoBitrate = 8_000_000, // 8 Mbps
        recommendedFps = 60,
        supportsHevc = false,
        dashboardUrl = "https://kick.com/dashboard/stream"
    ),
    CUSTOM(
        displayName = "Custom RTMP",
        defaultEndpoint = "rtmp://",
        defaultVideoBitrate = 6_000_000,
        recommendedFps = 60,
        supportsHevc = true,
        dashboardUrl = ""
    )
}

/**
 * Configuration profile for Live Streaming sessions.
 */
@Parcelize
data class StreamConfig(
    val platform: StreamPlatform = StreamPlatform.YOUTUBE,
    val customEndpointUrl: String = "",
    val streamKey: String = "",
    val videoBitrate: Int = 9_000_000,
    val enableAbr: Boolean = true,
    val minBitrate: Int = 2_000_000,
    val maxBitrate: Int = 14_000_000,
    val saveLocalMasterArchive: Boolean = true,
    val useEnhancedHevc: Boolean = true
) : Parcelable {

    val activeEndpointUrl: String
        get() = if (platform == StreamPlatform.CUSTOM && customEndpointUrl.isNotBlank()) {
            customEndpointUrl.trim()
        } else {
            platform.defaultEndpoint
        }

    val isConfigured: Boolean
        get() = streamKey.isNotBlank() && activeEndpointUrl.isNotBlank()
}
