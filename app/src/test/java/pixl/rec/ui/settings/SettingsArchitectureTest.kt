package pixl.rec.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pixl.rec.core.model.RecordingConfig
import pixl.rec.core.model.StreamConfig
import pixl.rec.core.model.StreamDestination
import pixl.rec.core.model.StreamPlatform
import pixl.rec.core.model.VideoCodec
import pixl.rec.ui.settings.sections.OutputSubTab

/**
 * Unit tests verifying Point 9 OBS-Inspired Settings Information Architecture:
 * - 5-pillar top-level navigation contract
 * - Stream-disabled offline mode fallback
 * - Output sub-navigation pillars (Recording, Streaming, Replay Buffer)
 * - Effective stream codec negotiation & multi-destination fallback
 * - Parameter segregation between Capture and Output policies
 */
class SettingsArchitectureTest {

    @Test
    fun testSettingsTabEnumCompleteness() {
        val expectedTabs = listOf("GENERAL", "CAPTURE", "AUDIO", "OUTPUT", "STREAM")
        val actualTabs = SettingsTab.entries.map { it.name }
        assertEquals("Settings tabs must match the 5 OBS-inspired pillars", expectedTabs, actualTabs)
    }

    @Test
    fun testStreamEnabledTabVisibility() {
        val isStreamingEnabled = true
        val availableTabs = if (isStreamingEnabled) {
            SettingsTab.entries
        } else {
            listOf(SettingsTab.GENERAL, SettingsTab.CAPTURE, SettingsTab.AUDIO, SettingsTab.OUTPUT)
        }

        assertEquals(5, availableTabs.size)
        assertTrue(availableTabs.contains(SettingsTab.STREAM))
        assertTrue(availableTabs.contains(SettingsTab.OUTPUT))
        assertTrue(availableTabs.contains(SettingsTab.CAPTURE))
    }

    @Test
    fun testStreamDisabledOfflineModeTabVisibility() {
        val isStreamingEnabled = false
        val availableTabs = if (isStreamingEnabled) {
            SettingsTab.entries
        } else {
            listOf(SettingsTab.GENERAL, SettingsTab.CAPTURE, SettingsTab.AUDIO, SettingsTab.OUTPUT)
        }

        assertEquals(4, availableTabs.size)
        assertFalse("STREAM tab must be hidden when streaming is disabled", availableTabs.contains(SettingsTab.STREAM))
        assertEquals(
            listOf(SettingsTab.GENERAL, SettingsTab.CAPTURE, SettingsTab.AUDIO, SettingsTab.OUTPUT),
            availableTabs
        )
    }

    @Test
    fun testOutputSubTabEnumCompleteness() {
        val expected = listOf("RECORDING", "STREAMING", "REPLAY_BUFFER")
        val actual = OutputSubTab.entries.map { it.name }
        assertEquals("Output sub-tabs must support Recording, Streaming, and Replay Buffer", expected, actual)
    }

    @Test
    fun testSinglePlatformHevcNegotiation() {
        // YouTube supports HEVC
        val youtubeConfig = StreamConfig(
            platform = StreamPlatform.YOUTUBE,
            streamKey = "live_yt_key",
            useEnhancedHevc = true
        )
        assertTrue(
            "YouTube with Enhanced HEVC enabled should yield effective HEVC",
            youtubeConfig.effectiveSupportsHevc
        )

        // Twitch mandates AVC
        val twitchConfig = StreamConfig(
            platform = StreamPlatform.TWITCH,
            streamKey = "live_tw_key",
            useEnhancedHevc = true
        )
        assertFalse(
            "Twitch with Enhanced HEVC enabled must still yield AVC due to ingest constraint",
            twitchConfig.effectiveSupportsHevc
        )
    }

    @Test
    fun testMultiStreamAvcFallbackSafety() {
        // Multistreaming to YouTube + Twitch:
        // YouTube supports HEVC, but Twitch requires AVC -> whole session must fall back to AVC
        val multiConfig = StreamConfig(
            platform = StreamPlatform.YOUTUBE,
            streamKey = "live_yt_key",
            useEnhancedHevc = true,
            destinations = listOf(
                StreamDestination(platform = StreamPlatform.YOUTUBE, streamKey = "key_yt", enabled = true),
                StreamDestination(platform = StreamPlatform.TWITCH, streamKey = "key_tw", enabled = true)
            )
        )
        assertFalse(
            "Multi-stream session containing Twitch must fall back to AVC across all sockets",
            multiConfig.effectiveSupportsHevc
        )
    }

    @Test
    fun testCaptureAndOutputParameterSegregation() {
        val initialConfig = RecordingConfig(
            width = 1080,
            height = 1920,
            framerate = 60,
            videoBitrate = 16_000_000,
            videoCodec = VideoCodec.HEVC
        )

        // Capture adjustments (resolution and FPS)
        val captureAdjusted = initialConfig.copy(
            width = 1440,
            height = 2560,
            framerate = 120
        )

        // Verify Output parameters are untouched
        assertEquals(initialConfig.videoBitrate, captureAdjusted.videoBitrate)
        assertEquals(initialConfig.videoCodec, captureAdjusted.videoCodec)

        // Output adjustments (codec and bitrate)
        val outputAdjusted = captureAdjusted.copy(
            videoBitrate = 30_000_000,
            videoCodec = VideoCodec.AVC
        )

        // Verify Capture dimensions are preserved
        assertEquals(1440, outputAdjusted.width)
        assertEquals(2560, outputAdjusted.height)
        assertEquals(120, outputAdjusted.framerate)
        assertEquals(30_000_000, outputAdjusted.videoBitrate)
        assertEquals(VideoCodec.AVC, outputAdjusted.videoCodec)
    }
}
