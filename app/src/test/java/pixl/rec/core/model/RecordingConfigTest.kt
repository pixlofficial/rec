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
        assertEquals(RecordingOrientation.AUTO, config.recordingOrientation)
        assertTrue(config.audioSource.hasInternal)
        assertTrue(config.audioSource.hasMic)
        assertTrue(config.audioSource.hasAudio)
    }

    @Test
    fun testRecordingOrientationSelection() {
        val autoConfig = RecordingConfig(recordingOrientation = RecordingOrientation.AUTO)
        assertEquals(RecordingOrientation.AUTO, autoConfig.recordingOrientation)

        val landscapeConfig = autoConfig.copy(recordingOrientation = RecordingOrientation.LANDSCAPE)
        assertEquals(RecordingOrientation.LANDSCAPE, landscapeConfig.recordingOrientation)

        val portraitConfig = autoConfig.copy(recordingOrientation = RecordingOrientation.PORTRAIT)
        assertEquals(RecordingOrientation.PORTRAIT, portraitConfig.recordingOrientation)
    }

    @Test
    fun testMacroblockAlignment() {
        // Non-multiples of 16 should be rounded up to the nearest 16-pixel boundary
        val unaligned = RecordingConfig(width = 1079, height = 2399)
        val aligned = unaligned.withMacroblockAlignment()
        assertEquals(1088, aligned.width) // (1079 + 15) / 16 * 16 = 1088
        assertEquals(2400, aligned.height) // (2399 + 15) / 16 * 16 = 2400

        // Dimensions already aligned to 16 should remain unchanged
        val exact = RecordingConfig(width = 1920, height = 1088)
        val alignedExact = exact.withMacroblockAlignment()
        assertEquals(1920, alignedExact.width)
        assertEquals(1088, alignedExact.height)
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
