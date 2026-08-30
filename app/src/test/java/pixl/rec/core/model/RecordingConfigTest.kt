package pixl.rec.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingConfigTest {

    @Test
    fun testDefaultConfigValues() {
        val config = RecordingConfig()
        assertEquals(1080, config.width)
        assertEquals(2400, config.height)
        assertEquals(60, config.framerate)
        assertEquals(16_000_000, config.videoBitrate)
        assertEquals(VideoCodec.HEVC, config.videoCodec)
        assertEquals(AudioSource.INTERNAL_AND_MIC, config.audioSource)
        assertTrue(config.audioSource.hasInternal)
        assertTrue(config.audioSource.hasMic)
        assertTrue(config.audioSource.hasAudio)
    }

    @Test
    fun testMacroblockAlignment() {
        // Odd numbers should be rounded up to even numbers
        val unaligned = RecordingConfig(width = 1079, height = 2399)
        val aligned = unaligned.withMacroblockAlignment()
        assertEquals(1080, aligned.width)
        assertEquals(2400, aligned.height)
    }

    @Test
    fun testAspectRatio() {
        val config = RecordingConfig(width = 1080, height = 1920)
        assertEquals(1080f / 1920f, config.aspectRatio, 0.001f)
    }

    @Test
    fun testTotalBitrateMbps() {
        val config = RecordingConfig(videoBitrate = 50_000_000, audioBitrate = 256_000)
        assertEquals(50.256f, config.totalBitrateMbps, 0.001f)

        val mutedConfig = config.copy(audioSource = AudioSource.MUTE)
        assertEquals(50.0f, mutedConfig.totalBitrateMbps, 0.001f)
    }
}
