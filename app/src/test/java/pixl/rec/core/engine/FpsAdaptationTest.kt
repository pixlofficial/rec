package pixl.rec.core.engine

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import pixl.rec.core.model.VideoCodec

class FpsAdaptationTest {

    @Before
    fun setUp() {
        CodecProbe.simulateSiliconForTesting = true
    }

    @After
    fun tearDown() {
        CodecProbe.simulateSiliconForTesting = false
    }

    @Test
    fun testFindOptimalResolutionForFps_60fps() {
        // Motorola G57 (1080x2400, max 120Hz display)
        // 1080p caps at ~30 FPS on mid-range hardware
        // 60 FPS should adapt to Smooth (900p)
        val optimalTier = CodecProbe.findOptimalResolutionForFps(
            targetFps = 60,
            codec = VideoCodec.AVC,
            physicalWidth = 1080,
            physicalHeight = 2400,
            isLandscape = false,
            maxDisplayHz = 120f
        )

        assertNotNull("Optimal resolution for 60 FPS should not be null", optimalTier)
        assertEquals("900p", optimalTier?.tag)
        assertEquals("Smooth", optimalTier?.label)
    }

    @Test
    fun testFindOptimalResolutionForFps_90fps() {
        // 90 FPS requires Performance tier (720p)
        val optimalTier = CodecProbe.findOptimalResolutionForFps(
            targetFps = 90,
            codec = VideoCodec.AVC,
            physicalWidth = 1080,
            physicalHeight = 2400,
            isLandscape = false,
            maxDisplayHz = 120f
        )

        assertNotNull("Optimal resolution for 90 FPS should not be null", optimalTier)
        assertEquals("720p", optimalTier?.tag)
        assertEquals("Performance", optimalTier?.label)
    }

    @Test
    fun testFindOptimalResolutionForFps_30fps() {
        // 30 FPS is already supported by native 1080p, so findOptimalResolutionForFps returns null (no adaptation needed)
        val optimalTier = CodecProbe.findOptimalResolutionForFps(
            targetFps = 30,
            codec = VideoCodec.AVC,
            physicalWidth = 1080,
            physicalHeight = 2400,
            isLandscape = false,
            maxDisplayHz = 120f
        )

        assertNull("30 FPS is supported at Native tier, no adaptation needed", optimalTier)
    }
}
