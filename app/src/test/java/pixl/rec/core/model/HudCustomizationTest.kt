package pixl.rec.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HudCustomizationTest {

    @Test
    fun testDefaultHudConfig() {
        val config = RecordingConfig()
        assertNotNull(config.standbyHudConfig)
        assertNotNull(config.recordingHudConfig)

        assertEquals(HudShape.OCTAGON, config.standbyHudConfig.shape)
        assertEquals(44, config.standbyHudConfig.iconSizeDp)
        assertEquals(44, config.standbyHudConfig.nodeSizeDp)
        assertEquals(44, config.standbyHudConfig.sizeDp)
        assertEquals(StrokeStyle.SOLID, config.standbyHudConfig.strokeStyle)
        assertEquals(HudAnimation.NONE, config.standbyHudConfig.animation)
        assertEquals(HudAnimation.BREATHE, config.recordingHudConfig.animation)
        assertFalse(config.standbyHudConfig.hasBackground)
        assertFalse(config.standbyHudConfig.hasStroke)
        assertEquals(HudSnapBehavior.PROXIMITY_SNAP, config.hudSnapBehavior)
    }

    @Test
    fun testCustomHudShapesAndStrokes() {
        val customHud = HudStyleConfig(
            iconSizeDp = 32,
            hasBackground = true,
            shape = HudShape.HEXAGON,
            nodeSizeDp = 48,
            hasStroke = true,
            strokeColorHex = 0xFFFF2A4DL,
            strokeStyle = StrokeStyle.DOTTED,
            animation = HudAnimation.PULSE
        )

        assertEquals(32, customHud.iconSizeDp)
        assertEquals(true, customHud.hasBackground)
        assertEquals(HudShape.HEXAGON, customHud.shape)
        assertEquals(48, customHud.nodeSizeDp)
        assertEquals(48, customHud.sizeDp)
        assertEquals(StrokeStyle.DOTTED, customHud.strokeStyle)
        assertEquals(true, customHud.hasStroke)
        assertEquals(HudAnimation.PULSE, customHud.animation)
    }
}
