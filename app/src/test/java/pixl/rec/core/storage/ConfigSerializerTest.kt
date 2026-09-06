package pixl.rec.core.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pixl.rec.core.model.AudioSource
import pixl.rec.core.model.BitrateMode
import pixl.rec.core.model.ColorRange
import pixl.rec.core.model.HudAnimation
import pixl.rec.core.model.HudShape
import pixl.rec.core.model.HudSnapBehavior
import pixl.rec.core.model.HudStyleConfig
import pixl.rec.core.model.PillRecallGesture
import pixl.rec.core.model.QuickPreset
import pixl.rec.core.model.RecordingConfig
import pixl.rec.core.model.RecordingOrientation
import pixl.rec.core.model.StrokeStyle
import pixl.rec.core.model.VideoCodec

class ConfigSerializerTest {

    @Test
    fun testExportAndImportRoundTripIntegrity() {
        val original = RecordingConfig(
            width = 1440,
            height = 3120,
            dpi = 560,
            framerate = 90,
            videoBitrate = 40_000_000,
            videoCodec = VideoCodec.AVC,
            bitrateMode = BitrateMode.CBR,
            iFrameIntervalSeconds = 2.0f,
            audioSource = AudioSource.INTERNAL_ONLY,
            audioBitrate = 320_000,
            audioSampleRate = 44_100,
            audioChannelCount = 1,
            micGain = 1.5f,
            internalAudioGain = 0.8f,
            recordingOrientation = RecordingOrientation.LANDSCAPE,
            activePreset = QuickPreset.GAMING,
            allowExperimentalFps = true,
            colorRange = ColorRange.LIMITED,
            enableIntraRefresh = true,
            showFloatingPill = true,
            alwaysOnFloatingPill = false,
            hidePillDuringRecording = true,
            autoHidePill = true,
            pillRecallGesture = PillRecallGesture.DOUBLE_TAP,
            shakeToStop = false,
            stopOnScreenOff = false,
            countdownSeconds = 3,
            smartGameOptimization = true,
            standbyNotification = false,
            recordingNotification = false,
            standbyHudConfig = HudStyleConfig(
                iconSizeDp = 32,
                iconOpacity = 0.8f,
                animation = HudAnimation.PULSE,
                hasBackground = true,
                shape = HudShape.CIRCLE,
                nodeSizeDp = 48,
                backgroundOpacity = 0.9f,
                backgroundColorHex = 0xFF112233L,
                hasStroke = true,
                strokeColorHex = 0xFFAABBCCL,
                strokeWidthDp = 1.5f,
                strokeStyle = StrokeStyle.DASHED,
                strokeOpacity = 0.7f,
                snapBehavior = HudSnapBehavior.ALWAYS_SNAP_EDGE
            ),
            recordingHudConfig = HudStyleConfig(
                iconSizeDp = 40,
                iconOpacity = 1.0f,
                animation = HudAnimation.HEARTBEAT,
                hasBackground = false,
                shape = HudShape.HEXAGON,
                nodeSizeDp = 44,
                backgroundOpacity = 0.5f,
                backgroundColorHex = 0xFF000000L,
                hasStroke = false,
                strokeColorHex = 0xFFFF0000L,
                strokeWidthDp = 2.0f,
                strokeStyle = StrokeStyle.SOLID,
                strokeOpacity = 1.0f,
                snapBehavior = HudSnapBehavior.FREE_FLOAT
            ),
            hudSnapBehavior = HudSnapBehavior.ALWAYS_SNAP_EDGE
        )

        val json = ConfigSerializer.exportToJson(original, versionName = "0.5.0", versionCode = 8)
        assertTrue(json.contains("\"app\": \"REC\""))
        assertTrue(json.contains("\"version_name\": \"0.5.0\""))

        val importResult = ConfigSerializer.importFromJson(json)
        assertTrue("Import should succeed", importResult.isSuccess)

        val restored = importResult.getOrThrow()
        assertEquals(original.width, restored.width)
        assertEquals(original.height, restored.height)
        assertEquals(original.dpi, restored.dpi)
        assertEquals(original.framerate, restored.framerate)
        assertEquals(original.videoBitrate, restored.videoBitrate)
        assertEquals(original.videoCodec, restored.videoCodec)
        assertEquals(original.bitrateMode, restored.bitrateMode)
        assertEquals(original.iFrameIntervalSeconds, restored.iFrameIntervalSeconds, 0.01f)
        assertEquals(original.audioSource, restored.audioSource)
        assertEquals(original.audioBitrate, restored.audioBitrate)
        assertEquals(original.audioSampleRate, restored.audioSampleRate)
        assertEquals(original.audioChannelCount, restored.audioChannelCount)
        assertEquals(original.micGain, restored.micGain, 0.01f)
        assertEquals(original.internalAudioGain, restored.internalAudioGain, 0.01f)
        assertEquals(original.recordingOrientation, restored.recordingOrientation)
        assertEquals(original.colorRange, restored.colorRange)
        assertEquals(original.enableIntraRefresh, restored.enableIntraRefresh)
        assertEquals(original.showFloatingPill, restored.showFloatingPill)
        assertEquals(original.alwaysOnFloatingPill, restored.alwaysOnFloatingPill)
        assertEquals(original.hidePillDuringRecording, restored.hidePillDuringRecording)
        assertEquals(original.autoHidePill, restored.autoHidePill)
        assertEquals(original.pillRecallGesture, restored.pillRecallGesture)
        assertEquals(original.shakeToStop, restored.shakeToStop)
        assertEquals(original.stopOnScreenOff, restored.stopOnScreenOff)
        assertEquals(original.countdownSeconds, restored.countdownSeconds)
        assertEquals(original.smartGameOptimization, restored.smartGameOptimization)
        assertEquals(original.standbyNotification, restored.standbyNotification)
        assertEquals(original.recordingNotification, restored.recordingNotification)
        assertEquals(original.hudSnapBehavior, restored.hudSnapBehavior)

        // Verify HUD nested configurations
        assertEquals(original.standbyHudConfig.iconSizeDp, restored.standbyHudConfig.iconSizeDp)
        assertEquals(original.standbyHudConfig.shape, restored.standbyHudConfig.shape)
        assertEquals(original.standbyHudConfig.animation, restored.standbyHudConfig.animation)
        assertEquals(original.standbyHudConfig.hasBackground, restored.standbyHudConfig.hasBackground)
        assertEquals(original.standbyHudConfig.hasStroke, restored.standbyHudConfig.hasStroke)
        assertEquals(original.recordingHudConfig.animation, restored.recordingHudConfig.animation)
        assertEquals(original.recordingHudConfig.shape, restored.recordingHudConfig.shape)
    }

    @Test
    fun testTolerantImportWithPixLRecSignature() {
        val json = """
        {
          "metadata": {
            "app": "PixL-REC",
            "version_name": "0.4.0"
          },
          "video": {
            "width": 1080,
            "height": 2400,
            "framerate": 60
          }
        }
        """.trimIndent()

        val result = ConfigSerializer.importFromJson(json)
        assertTrue("Should accept PixL-REC signature", result.isSuccess)
        val config = result.getOrThrow()
        assertEquals(1088, config.width) // macroblock aligned (1080 -> 1088)
        assertEquals(2400, config.height)
        assertEquals(60, config.framerate)
    }

    @Test
    fun testRejectForeignAppSignature() {
        val json = """
        {
          "metadata": {
            "app": "SomeOtherScreenRecorder"
          }
        }
        """.trimIndent()

        val result = ConfigSerializer.importFromJson(json)
        assertTrue("Should fail on invalid app signature", result.isFailure)
    }

    @Test
    fun testGracefulDefaultsOnMinimalJson() {
        val json = "{}"
        val result = ConfigSerializer.importFromJson(json)
        assertTrue("Should succeed on empty JSON with defaults", result.isSuccess)

        val defaultConf = RecordingConfig()
        val parsed = result.getOrThrow()
        assertEquals(defaultConf.videoBitrate, parsed.videoBitrate)
        assertEquals(defaultConf.audioSource, parsed.audioSource)
        assertEquals(defaultConf.standbyHudConfig.shape, parsed.standbyHudConfig.shape)
    }

    @Test
    fun testValueClampingSanitization() {
        val json = """
        {
          "video": {
            "framerate": 9999,
            "video_bitrate": 999999999,
            "dpi": 50
          },
          "audio": {
            "mic_gain": 25.0
          },
          "controls": {
            "countdown_seconds": 120
          }
        }
        """.trimIndent()

        val result = ConfigSerializer.importFromJson(json)
        assertTrue(result.isSuccess)
        val config = result.getOrThrow()

        // Framerate should be clamped to 240
        assertEquals(240, config.framerate)
        // Bitrate should be clamped to 200 Mbps
        assertEquals(200_000_000, config.videoBitrate)
        // DPI clamped to min 120
        assertEquals(120, config.dpi)
        // Mic gain clamped to 4.0f
        assertEquals(4.0f, config.micGain, 0.01f)
        // Countdown clamped to 10s
        assertEquals(10, config.countdownSeconds)
    }

    @Test
    fun testInvalidJsonRejection() {
        val malformedJson = "{ not valid json ... "
        val result = ConfigSerializer.importFromJson(malformedJson)
        assertTrue(result.isFailure)
    }
}
