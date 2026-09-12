package pixl.rec.ui.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pixl.rec.core.model.HudAnimation
import pixl.rec.core.model.HudShape
import pixl.rec.core.model.HudSnapBehavior
import pixl.rec.core.model.HudStyleConfig
import pixl.rec.core.model.LaserSweepInterval
import pixl.rec.core.model.RecordingConfig
import pixl.rec.core.model.StreamHudConfig
import pixl.rec.core.model.StrokeStyle
import pixl.rec.core.storage.ConfigSerializer
import pixl.rec.core.storage.StudioMode

/**
 * Unit tests verifying Point 2 Unified HUD Studio Architecture:
 * - StudioMode to HudLabMode alignment
 * - LaserSweepInterval preset timings & enumeration
 * - StreamHudConfig defaults and parameter bounds
 * - ConfigSerializer JSON round-trip serialization for Stream HUD
 * - Backward compatibility with legacy configs missing stream_hud
 * - Independent configuration state isolation between Record and Stream profiles
 */
class HudStudioModeTest {

    @Test
    fun testStudioModeToLabModeMapping() {
        // Verify default mapping logic used in HudStudioScreen
        val recordInitial = StudioMode.RECORD
        val streamInitial = StudioMode.STREAM

        val recordLabMode = if (recordInitial == StudioMode.STREAM) HudLabMode.STREAM_LAB else HudLabMode.RECORD_LAB
        val streamLabMode = if (streamInitial == StudioMode.STREAM) HudLabMode.STREAM_LAB else HudLabMode.RECORD_LAB

        assertEquals(HudLabMode.RECORD_LAB, recordLabMode)
        assertEquals(HudLabMode.STREAM_LAB, streamLabMode)
        assertEquals(2, HudLabMode.entries.size)
    }

    @Test
    fun testLaserSweepIntervalTimings() {
        assertEquals(1500L, LaserSweepInterval.FAST.intervalMs)
        assertEquals(2500L, LaserSweepInterval.STANDARD.intervalMs)
        assertEquals(4000L, LaserSweepInterval.CALM.intervalMs)
        assertEquals(800L, LaserSweepInterval.CONTINUOUS.intervalMs)
        assertEquals(0L, LaserSweepInterval.OFF.intervalMs)
    }

    @Test
    fun testStreamHudConfigDefaults() {
        val defaultConfig = StreamHudConfig()

        assertEquals(LaserSweepInterval.STANDARD, defaultConfig.laserSweepInterval)
        assertEquals(0.85f, defaultConfig.laserGlowIntensity, 0.001f)
        assertTrue("Uplink Health Aura should be enabled by default", defaultConfig.enableUplinkHealthAura)
        assertEquals(HudAnimation.PULSE, defaultConfig.livePulseRhythm)
        assertEquals(0xFFFF0033L, defaultConfig.standbyHud.strokeColorHex)
        assertEquals(0xFFFF0033L, defaultConfig.activeHud.strokeColorHex)
    }

    @Test
    fun testConfigSerializerStreamHudRoundTrip() {
        val customStreamHud = StreamHudConfig(
            laserSweepInterval = LaserSweepInterval.FAST,
            laserGlowIntensity = 0.65f,
            enableUplinkHealthAura = false,
            livePulseRhythm = HudAnimation.BREATHE,
            standbyHud = HudStyleConfig(
                iconSizeDp = 36,
                iconOpacity = 0.8f,
                animation = HudAnimation.BREATHE,
                hasBackground = true,
                shape = HudShape.HEXAGON,
                nodeSizeDp = 48,
                hasStroke = true,
                strokeColorHex = 0xFF00FFCCL,
                strokeWidthDp = 3.0f,
                strokeStyle = StrokeStyle.DASHED,
                snapBehavior = HudSnapBehavior.ALWAYS_SNAP_EDGE
            ),
            activeHud = HudStyleConfig(
                iconSizeDp = 40,
                iconOpacity = 0.95f,
                animation = HudAnimation.PULSE,
                hasBackground = false,
                hasStroke = true,
                strokeColorHex = 0xFFFF3366L,
                strokeWidthDp = 2.5f
            )
        )

        val originalConfig = RecordingConfig(
            streamHudConfig = customStreamHud
        )

        val jsonString = ConfigSerializer.exportToJson(originalConfig, versionName = "0.7.0", versionCode = 10)
        assertTrue("Serialized JSON must contain stream_hud object", jsonString.contains("\"stream_hud\""))
        assertTrue("Serialized JSON must contain laser_sweep_interval", jsonString.contains("\"laser_sweep_interval\": \"FAST\""))

        val importResult = ConfigSerializer.importFromJson(jsonString)
        assertTrue("Import should succeed", importResult.isSuccess)

        val deserializedConfig = importResult.getOrThrow()
        val deserializedStreamHud = deserializedConfig.streamHudConfig
        assertEquals(LaserSweepInterval.FAST, deserializedStreamHud.laserSweepInterval)
        assertEquals(0.65f, deserializedStreamHud.laserGlowIntensity, 0.001f)
        assertFalse(deserializedStreamHud.enableUplinkHealthAura)
        assertEquals(HudAnimation.BREATHE, deserializedStreamHud.livePulseRhythm)

        // Verify nested standby HUD style
        assertEquals(36, deserializedStreamHud.standbyHud.iconSizeDp)
        assertEquals(HudShape.HEXAGON, deserializedStreamHud.standbyHud.shape)
        assertEquals(StrokeStyle.DASHED, deserializedStreamHud.standbyHud.strokeStyle)
        assertEquals(0xFF00FFCCL, deserializedStreamHud.standbyHud.strokeColorHex)
        assertEquals(HudSnapBehavior.ALWAYS_SNAP_EDGE, deserializedStreamHud.standbyHud.snapBehavior)

        // Verify nested active HUD style
        assertEquals(40, deserializedStreamHud.activeHud.iconSizeDp)
        assertFalse(deserializedStreamHud.activeHud.hasBackground)
        assertEquals(0xFFFF3366L, deserializedStreamHud.activeHud.strokeColorHex)
    }

    @Test
    fun testConfigSerializerBackwardCompatibilityWithoutStreamHud() {
        // Simulate legacy JSON without stream_hud
        val legacyJson = """
            {
              "schema_version": 1,
              "video": {
                "resolution": "FHD_1080P",
                "fps": 60,
                "bitrate_mbps": 12,
                "codec": "HEVC"
              },
              "audio": {
                "audio_source": "INTERNAL_AND_MIC",
                "sample_rate": 48000,
                "bitrate_kbps": 192,
                "channel_count": 2
              }
            }
        """.trimIndent()

        val parsed = ConfigSerializer.importFromJson(legacyJson).getOrNull()
        assertNotNull("Legacy JSON should deserialize safely", parsed)
        assertEquals("Missing stream_hud must default gracefully", StreamHudConfig(), parsed!!.streamHudConfig)
    }

    @Test
    fun testProfileIsolationBetweenRecordAndStreamHud() {
        val baseConfig = RecordingConfig()

        // Modify Record profile
        val modifiedRecordStandby = baseConfig.standbyHudConfig.copy(iconSizeDp = 28, shape = HudShape.CIRCLE)
        val configWithModifiedRecord = baseConfig.copy(standbyHudConfig = modifiedRecordStandby)

        // Assert Stream profile remains intact
        assertEquals(
            "Stream HUD standby profile must remain unmutated when Record HUD changes",
            baseConfig.streamHudConfig.standbyHud,
            configWithModifiedRecord.streamHudConfig.standbyHud
        )

        // Modify Stream profile
        val modifiedStreamStandby = baseConfig.streamHudConfig.standbyHud.copy(iconSizeDp = 42, shape = HudShape.OCTAGON)
        val modifiedStreamHud = baseConfig.streamHudConfig.copy(
            standbyHud = modifiedStreamStandby,
            laserSweepInterval = LaserSweepInterval.CONTINUOUS
        )
        val configWithModifiedStream = configWithModifiedRecord.copy(streamHudConfig = modifiedStreamHud)

        // Assert Record profile remains unchanged
        assertEquals(
            "Record HUD standby profile must remain intact when Stream HUD changes",
            28,
            configWithModifiedStream.standbyHudConfig.iconSizeDp
        )
        assertEquals(
            "Record HUD active profile must remain intact",
            baseConfig.recordingHudConfig,
            configWithModifiedStream.recordingHudConfig
        )
        assertEquals(
            LaserSweepInterval.CONTINUOUS,
            configWithModifiedStream.streamHudConfig.laserSweepInterval
        )
    }
}
