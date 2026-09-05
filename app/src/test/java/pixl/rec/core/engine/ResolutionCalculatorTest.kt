package pixl.rec.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolutionCalculatorTest {

    @Test
    fun testAlign16() {
        assertEquals(16, ResolutionCalculator.align16(1))
        assertEquals(16, ResolutionCalculator.align16(16))
        assertEquals(32, ResolutionCalculator.align16(17))
        assertEquals(1088, ResolutionCalculator.align16(1080))
        assertEquals(2400, ResolutionCalculator.align16(2400))
        assertEquals(1920, ResolutionCalculator.align16(1920))
        assertEquals(720, ResolutionCalculator.align16(720))
    }

    @Test
    fun testPresetsFor1080x2400Device() {
        // Motorola G57 Power / Pixel 7: 1080 x 2400 (20:9)
        val portraitPresets = ResolutionCalculator.getPresetsForDevice(1080, 2400, isLandscape = false)
        assertEquals(4, portraitPresets.size)

        // Native
        assertEquals("Native", portraitPresets[0].label)
        assertEquals(1088, portraitPresets[0].width)
        assertEquals(2400, portraitPresets[0].height)
        assertEquals("1088 × 2400", portraitPresets[0].displayDimensionString)

        // Smooth 900p
        assertEquals("Smooth", portraitPresets[1].label)
        assertTrue(portraitPresets[1].width % 16 == 0)
        assertTrue(portraitPresets[1].height % 16 == 0)
        assertEquals(912, portraitPresets[1].width)
        assertEquals(2000, portraitPresets[1].height)
        assertEquals("912 × 2000", portraitPresets[1].displayDimensionString)

        // Performance 720p
        assertEquals("Performance", portraitPresets[2].label)
        assertTrue(portraitPresets[2].width % 16 == 0)
        assertTrue(portraitPresets[2].height % 16 == 0)
        assertEquals(720, portraitPresets[2].width)
        assertEquals(1600, portraitPresets[2].height)
        assertEquals("720 × 1600", portraitPresets[2].displayDimensionString)

        // Lite 540p
        assertEquals("Lite", portraitPresets[3].label)
        assertTrue(portraitPresets[3].width % 16 == 0)
        assertTrue(portraitPresets[3].height % 16 == 0)
        assertEquals(544, portraitPresets[3].width)
        assertEquals(1200, portraitPresets[3].height)
        assertEquals("544 × 1200", portraitPresets[3].displayDimensionString)

        // Landscape mode
        val landscapePresets = ResolutionCalculator.getPresetsForDevice(1080, 2400, isLandscape = true)
        assertEquals(4, landscapePresets.size)
        assertEquals(2400, landscapePresets[0].width)
        assertEquals(1088, landscapePresets[0].height)
        assertEquals("2400 × 1088", landscapePresets[0].displayDimensionString)
        assertEquals(2000, landscapePresets[1].width)
        assertEquals(912, landscapePresets[1].height)
        assertEquals(1600, landscapePresets[2].width)
        assertEquals(720, landscapePresets[2].height)
        assertEquals(1200, landscapePresets[3].width)
        assertEquals(544, landscapePresets[3].height)
        assertEquals("1200 × 544", landscapePresets[3].displayDimensionString)
    }

    @Test
    fun testAspectRatioLabel() {
        assertEquals("20:9 • PORTRAIT", ResolutionCalculator.getAspectRatioLabel(1088, 2400))
        assertEquals("20:9 • LANDSCAPE", ResolutionCalculator.getAspectRatioLabel(2400, 1088))
        assertEquals("16:9 • LANDSCAPE", ResolutionCalculator.getAspectRatioLabel(1920, 1088))
        assertEquals("16:9 • PORTRAIT", ResolutionCalculator.getAspectRatioLabel(1088, 1920))
        assertEquals("1:1 • SQUARE", ResolutionCalculator.getAspectRatioLabel(1088, 1088))
    }

    @Test
    fun testFlipDimensions() {
        val flipped = ResolutionCalculator.flipDimensions(1088, 2400)
        assertEquals(2400, flipped.first)
        assertEquals(1088, flipped.second)

        val flippedBack = ResolutionCalculator.flipDimensions(flipped.first, flipped.second)
        assertEquals(1088, flippedBack.first)
        assertEquals(2400, flippedBack.second)
    }

    @Test
    fun testRecommendedBitrates() {
        assertEquals(50, ResolutionCalculator.getRecommendedBitrateMbps(1440))
        assertEquals(28, ResolutionCalculator.getRecommendedBitrateMbps(1080))
        assertEquals(16, ResolutionCalculator.getRecommendedBitrateMbps(900))
        assertEquals(16, ResolutionCalculator.getRecommendedBitrateMbps(720))
        assertEquals(8, ResolutionCalculator.getRecommendedBitrateMbps(540))
        assertEquals(8, ResolutionCalculator.getRecommendedBitrateMbps(480))
    }
}
