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
    val dashboardUrl: String,
    val protocolTag: String = "RTMP"
) {
    YOUTUBE(
        displayName = "YouTube Live",
        defaultEndpoint = "rtmps://a.rtmp.youtube.com/live2",
        defaultVideoBitrate = 9_000_000, // 9 Mbps for 1440p/1080p60
        recommendedFps = 60,
        supportsHevc = true, // YouTube supports Enhanced RTMP HEVC
        dashboardUrl = "https://studio.youtube.com/channel/UC/livestreaming",
        protocolTag = "RTMPS"
    ),
    TWITCH(
        displayName = "Twitch",
        defaultEndpoint = "rtmp://live.twitch.tv/app/",
        defaultVideoBitrate = 6_000_000, // Twitch standard 6 Mbps ingest limit
        recommendedFps = 60,
        supportsHevc = false, // AVC required
        dashboardUrl = "https://dashboard.twitch.tv/stream-manager",
        protocolTag = "RTMP"
    ),
    KICK(
        displayName = "Kick",
        defaultEndpoint = "rtmps://fa723fc1b171.global-contribute.live-video.net/app/",
        defaultVideoBitrate = 8_000_000, // 8 Mbps
        recommendedFps = 60,
        supportsHevc = false,
        dashboardUrl = "https://kick.com/dashboard/stream",
        protocolTag = "RTMPS"
    ),
    FACEBOOK(
        displayName = "Facebook Live",
        defaultEndpoint = "rtmps://live-api-s.facebook.com:443/rtmp/",
        defaultVideoBitrate = 6_000_000,
        recommendedFps = 60,
        supportsHevc = false,
        dashboardUrl = "https://www.facebook.com/live/producer",
        protocolTag = "RTMPS"
    ),
    LOCO(
        displayName = "Loco",
        defaultEndpoint = "rtmp://live.loco.gg/app/",
        defaultVideoBitrate = 6_000_000,
        recommendedFps = 60,
        supportsHevc = false,
        dashboardUrl = "https://loco.gg/streamer",
        protocolTag = "RTMP"
    ),
    TIKTOK(
        displayName = "TikTok Live",
        defaultEndpoint = "rtmp://",
        defaultVideoBitrate = 6_000_000,
        recommendedFps = 60,
        supportsHevc = false,
        dashboardUrl = "",
        protocolTag = "RTMP"
    ),
    TWITTER(
        displayName = "X (Twitter)",
        defaultEndpoint = "rtmps://prod-fastly-us-east-1.video.pscp.tv:443/x/",
        defaultVideoBitrate = 6_000_000,
        recommendedFps = 60,
        supportsHevc = false,
        dashboardUrl = "https://studio.x.com",
        protocolTag = "RTMPS"
    ),
    RESTREAM(
        displayName = "Restream.io",
        defaultEndpoint = "rtmp://live.restream.io/live",
        defaultVideoBitrate = 8_000_000,
        recommendedFps = 60,
        supportsHevc = false,
        dashboardUrl = "https://app.restream.io",
        protocolTag = "MULTI"
    ),
    CUSTOM(
        displayName = "Custom RTMP",
        defaultEndpoint = "rtmp://",
        defaultVideoBitrate = 6_000_000,
        recommendedFps = 60,
        supportsHevc = true,
        dashboardUrl = "",
        protocolTag = "CUSTOM"
    );

    val isPrimary: Boolean
        get() = this == YOUTUBE || this == TWITCH || this == KICK

    val isCustomEndpoint: Boolean
        get() = this == CUSTOM || this == TIKTOK

    companion object {
        val PRIMARY_PLATFORMS = listOf(YOUTUBE, TWITCH, KICK)
        val EXTENDED_PLATFORMS = listOf(FACEBOOK, LOCO, TIKTOK, TWITTER, RESTREAM, CUSTOM)
    }
}

/**
 * Individual live streaming broadcast destination.
 */
@Parcelize
data class StreamDestination(
    val id: String = java.util.UUID.randomUUID().toString(),
    val platform: StreamPlatform = StreamPlatform.YOUTUBE,
    val customEndpointUrl: String = "",
    val streamKey: String = "",
    val enabled: Boolean = true
) : Parcelable {

    val activeEndpointUrl: String
        get() = if (platform.isCustomEndpoint && customEndpointUrl.isNotBlank()) {
            customEndpointUrl.trim()
        } else {
            platform.defaultEndpoint
        }

    val isConfigured: Boolean
        get() = enabled && streamKey.isNotBlank() && activeEndpointUrl.isNotBlank()
}

/**
 * Configuration profile for Live Streaming sessions with single or multi-destination broadcasting.
 */
@Parcelize
data class StreamConfig(
    val platform: StreamPlatform = StreamPlatform.YOUTUBE,
    val customEndpointUrl: String = "",
    val streamKey: String = "",
    val destinations: List<StreamDestination> = emptyList(),
    val videoBitrate: Int = 9_000_000,
    val enableAbr: Boolean = true,
    val minBitrate: Int = 2_000_000,
    val maxBitrate: Int = 14_000_000,
    val saveLocalMasterArchive: Boolean = true,
    val useEnhancedHevc: Boolean = true
) : Parcelable {

    val activeEndpointUrl: String
        get() = if (platform.isCustomEndpoint && customEndpointUrl.isNotBlank()) {
            customEndpointUrl.trim()
        } else {
            platform.defaultEndpoint
        }

    /**
     * Active configured broadcast destinations.
     * Falls back cleanly to single primary destination if [destinations] is empty.
     */
    val activeDestinations: List<StreamDestination>
        get() = if (destinations.isNotEmpty()) {
            destinations.filter { it.isConfigured }
        } else if (streamKey.isNotBlank() && activeEndpointUrl.isNotBlank()) {
            listOf(
                StreamDestination(
                    platform = platform,
                    customEndpointUrl = customEndpointUrl,
                    streamKey = streamKey,
                    enabled = true
                )
            )
        } else {
            emptyList()
        }

    val isConfigured: Boolean
        get() = activeDestinations.isNotEmpty()

    /**
     * True if all active destinations support HEVC (e.g. YouTube or Custom) AND Enhanced HEVC is enabled.
     * If ANY destination (such as Twitch or Kick) mandates AVC, this evaluates to false so the
     * hardware encoder produces universally ingestible H.264 video.
     */
    val effectiveSupportsHevc: Boolean
        get() = activeDestinations.isNotEmpty() &&
            activeDestinations.all { it.platform.supportsHevc } &&
            useEnhancedHevc

    /**
     * Combined required uplink bandwidth (video + 256kbps AAC audio per active stream).
     */
    val totalRequiredBitrateBps: Long
        get() = activeDestinations.sumOf { dest ->
            videoBitrate.toLong() + 256_000L
        }.coerceAtLeast(videoBitrate.toLong() + 256_000L)
}

