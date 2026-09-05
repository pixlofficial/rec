package pixl.rec.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingConfigTest {

    @Test
    fun testDefaultProFeatures() {
        val config = RecordingConfig(
            width = 1088,
            height = 2400,
            dpi = 400,
            framerate = 30,
            videoCodec = VideoCodec.AVC,
            videoBitrate = 16_000_000
        )

        assertFalse("allowExperimentalFps should default to false", config.allowExperimentalFps)
        assertEquals("colorRange should default to FULL", ColorRange.FULL, config.colorRange)
        assertFalse("enableIntraRefresh should default to false", config.enableIntraRefresh)
        assertEquals("iFrameIntervalSeconds should default to 1.0f", 1.0f, config.iFrameIntervalSeconds)
        assertEquals("bitrateMode should default to VBR", BitrateMode.VBR, config.bitrateMode)
        assertEquals("countdownSeconds should default to 0", 0, config.countdownSeconds)
    }

    @Test
    fun testOverclockConfig() {
        val config = RecordingConfig(
            width = 1088,
            height = 2400,
            dpi = 400,
            framerate = 60,
            videoCodec = VideoCodec.AVC,
            videoBitrate = 35_000_000,
            allowExperimentalFps = true,
            colorRange = ColorRange.FULL,
            enableIntraRefresh = true,
            iFrameIntervalSeconds = 0.5f,
            bitrateMode = BitrateMode.CBR
        )

        assertTrue(config.allowExperimentalFps)
        assertEquals(ColorRange.FULL, config.colorRange)
        assertTrue(config.enableIntraRefresh)
        assertEquals(0.5f, config.iFrameIntervalSeconds)
        assertEquals(BitrateMode.CBR, config.bitrateMode)
        assertEquals(35_000_000, config.videoBitrate)
    }

    @Test
    fun testMacroblockAlignment() {
        val unaligned = RecordingConfig(width = 1079, height = 2399, dpi = 400)
        val aligned = unaligned.withMacroblockAlignment()
        assertEquals(1088, aligned.width)
        assertEquals(2400, aligned.height)

        val exact = RecordingConfig(width = 1920, height = 1088, dpi = 400)
        val alignedExact = exact.withMacroblockAlignment()
        assertEquals(1920, alignedExact.width)
        assertEquals(1088, alignedExact.height)
    }

    @Test
    fun testAspectRatio() {
        val config = RecordingConfig(width = 1080, height = 1920, dpi = 400)
        assertEquals(1080f / 1920f, config.aspectRatio, 0.001f)
    }

    @Test
    fun testTotalBitrateMbps() {
        val config = RecordingConfig(videoBitrate = 50_000_000, audioBitrate = 256_000, dpi = 400)
        assertEquals(50.256f, config.totalBitrateMbps, 0.001f)

        val mutedConfig = config.copy(audioSource = AudioSource.MUTE)
        assertEquals(50.0f, mutedConfig.totalBitrateMbps, 0.001f)
    }

    @Test
    fun test2KResolutionMacroblockAlignment() {
        val config2K = RecordingConfig(width = 1440, height = 3120, dpi = 560)
        val aligned2K = config2K.withMacroblockAlignment()
        assertEquals(1440, aligned2K.width)
        assertEquals(3120, aligned2K.height)
    }
}

