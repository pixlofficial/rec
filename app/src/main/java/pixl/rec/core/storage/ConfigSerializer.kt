package pixl.rec.core.storage

import org.json.JSONObject
import pixl.rec.BuildConfig
import pixl.rec.core.model.AudioSource
import pixl.rec.core.model.BitrateMode
import pixl.rec.core.model.CaptureTarget
import pixl.rec.core.model.ColorRange
import pixl.rec.core.model.DeviceCapabilities
import pixl.rec.core.model.HudAnimation
import pixl.rec.core.model.HudShape
import pixl.rec.core.model.HudSnapBehavior
import pixl.rec.core.model.HudStyleConfig
import pixl.rec.core.model.LaserSweepInterval
import pixl.rec.core.model.PillRecallGesture
import pixl.rec.core.model.QuickPreset
import pixl.rec.core.model.RecordingConfig
import pixl.rec.core.model.RecordingOrientation
import pixl.rec.core.model.StreamHudConfig
import pixl.rec.core.model.StrokeStyle
import pixl.rec.core.model.VideoCodec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Robust JSON serializer and deserializer for REC recording configurations.
 * Handles backward/forward compatibility, field sanitization, and fallback clamping.
 */
object ConfigSerializer {

    const val SIGNATURE_REC = "REC"
    const val SIGNATURE_PIXL_REC = "PixL-REC"
    const val CREATOR_PIXL = "PixL"
    const val SCHEMA_VERSION = 1

    /**
     * Serializes a [RecordingConfig] into a structured, human-readable JSON string.
     */
    fun exportToJson(
        config: RecordingConfig,
        versionName: String = BuildConfig.VERSION_NAME,
        versionCode: Int = BuildConfig.VERSION_CODE
    ): String {
        val root = JSONObject()

        // 1. Metadata Header
        val metadata = JSONObject().apply {
            put("app", SIGNATURE_REC)
            put("creator", CREATOR_PIXL)
            put("schema_version", SCHEMA_VERSION)
            put("version_name", versionName)
            put("version_code", versionCode)
            put("exported_at", getIsoTimestamp())
        }
        root.put("metadata", metadata)

        // 2. Video Settings
        val video = JSONObject().apply {
            put("width", config.width)
            put("height", config.height)
            put("dpi", config.dpi)
            put("framerate", config.framerate)
            put("video_bitrate", config.videoBitrate)
            put("video_codec", config.videoCodec.name)
            put("bitrate_mode", config.bitrateMode.name)
            put("iframe_interval", config.iFrameIntervalSeconds.toDouble())
            put("recording_orientation", config.recordingOrientation.name)
            put("color_range", config.colorRange.name)
            put("enable_intra_refresh", config.enableIntraRefresh)
            put("allow_experimental_fps", config.allowExperimentalFps)
            put("active_preset", config.activePreset.name)
        }
        root.put("video", video)

        // 3. Audio Settings
        val audio = JSONObject().apply {
            put("audio_source", config.audioSource.name)
            put("audio_bitrate", config.audioBitrate)
            put("audio_sample_rate", config.audioSampleRate)
            put("audio_channel_count", config.audioChannelCount)
            put("mic_gain", config.micGain.toDouble())
            put("internal_audio_gain", config.internalAudioGain.toDouble())
            put("audio_sync_offset_ms", config.audioSyncOffsetMs)
        }
        root.put("audio", audio)

        // 4. Controls & System Automation
        val controls = JSONObject().apply {
            put("show_floating_pill", config.showFloatingPill)
            put("always_on_floating_pill", config.alwaysOnFloatingPill)
            put("hide_pill_during_recording", config.hidePillDuringRecording)
            put("auto_hide_pill", config.autoHidePill)
            put("pill_recall_gesture", config.pillRecallGesture.name)
            put("shake_to_stop", config.shakeToStop)
            put("stop_on_screen_off", config.stopOnScreenOff)
            put("capture_target", config.captureTarget.name)
            put("countdown_seconds", config.countdownSeconds)
            put("smart_game_optimization", config.smartGameOptimization)
            put("standby_notification", config.standbyNotification)
            put("recording_notification", config.recordingNotification)
        }
        root.put("controls", controls)

        // 5. HUD Styling & Snapping
        val hud = JSONObject().apply {
            put("hud_snap_behavior", config.hudSnapBehavior.name)
            put("standby", serializeHudStyle(config.standbyHudConfig))
            put("recording", serializeHudStyle(config.recordingHudConfig))
            put("stream_hud", JSONObject().apply {
                put("laser_sweep_interval", config.streamHudConfig.laserSweepInterval.name)
                put("laser_glow_intensity", config.streamHudConfig.laserGlowIntensity.toDouble())
                put("enable_uplink_health_aura", config.streamHudConfig.enableUplinkHealthAura)
                put("live_pulse_rhythm", config.streamHudConfig.livePulseRhythm.name)
                put("standby", serializeHudStyle(config.streamHudConfig.standbyHud))
                put("active", serializeHudStyle(config.streamHudConfig.activeHud))
            })
        }
        root.put("hud", hud)

        // 6. Instant Replay Buffer
        val replayBuffer = JSONObject().apply {
            put("enabled", config.enableReplayBuffer)
            put("duration_seconds", config.replayBufferDurationSeconds)
        }
        root.put("replay_buffer", replayBuffer)

        return root.toString(2)
    }

    /**
     * Parses a JSON string and maps it safely to a verified [RecordingConfig].
     * Any missing or invalid properties gracefully fall back to defaults.
     */
    fun importFromJson(
        jsonString: String,
        capabilities: DeviceCapabilities? = null
    ): Result<RecordingConfig> = runCatching {
        val root = JSONObject(jsonString)

        // Validate App Signature if metadata block is present
        if (root.has("metadata")) {
            val metadata = root.getJSONObject("metadata")
            val appSig = metadata.optString("app", "")
            if (appSig.isNotBlank() && !appSig.equals(SIGNATURE_REC, ignoreCase = true) && !appSig.equals(SIGNATURE_PIXL_REC, ignoreCase = true)) {
                throw IllegalArgumentException("Unsupported configuration file signature: '$appSig'")
            }
        }

        val defaultConf = RecordingConfig()

        // 1. Video Section
        val videoObj = root.optJSONObject("video") ?: root
        val rawWidth = videoObj.optInt("width", defaultConf.width)
        val rawHeight = videoObj.optInt("height", defaultConf.height)
        val rawDpi = videoObj.optInt("dpi", defaultConf.dpi).coerceIn(120, 960)
        val rawFps = videoObj.optInt("framerate", defaultConf.framerate).coerceIn(15, 240)
        val rawVideoBitrate = videoObj.optInt("video_bitrate", defaultConf.videoBitrate).coerceIn(1_000_000, 200_000_000)
        val videoCodec = runCatching { VideoCodec.valueOf(videoObj.optString("video_codec", defaultConf.videoCodec.name)) }
            .getOrDefault(defaultConf.videoCodec)
        val bitrateMode = runCatching { BitrateMode.valueOf(videoObj.optString("bitrate_mode", defaultConf.bitrateMode.name)) }
            .getOrDefault(defaultConf.bitrateMode)
        val iFrameInterval = videoObj.optDouble("iframe_interval", defaultConf.iFrameIntervalSeconds.toDouble()).toFloat().coerceIn(0.1f, 10.0f)
        val orientation = runCatching { RecordingOrientation.valueOf(videoObj.optString("recording_orientation", defaultConf.recordingOrientation.name)) }
            .getOrDefault(defaultConf.recordingOrientation)
        val colorRange = runCatching { ColorRange.valueOf(videoObj.optString("color_range", defaultConf.colorRange.name)) }
            .getOrDefault(defaultConf.colorRange)
        val enableIntraRefresh = videoObj.optBoolean("enable_intra_refresh", defaultConf.enableIntraRefresh)
        val allowExperimentalFps = videoObj.optBoolean("allow_experimental_fps", defaultConf.allowExperimentalFps)
        val activePreset = runCatching { QuickPreset.valueOf(videoObj.optString("active_preset", defaultConf.activePreset.name)) }
            .getOrDefault(defaultConf.activePreset)

        // 2. Audio Section
        val audioObj = root.optJSONObject("audio") ?: root
        val audioSource = runCatching { AudioSource.valueOf(audioObj.optString("audio_source", defaultConf.audioSource.name)) }
            .getOrDefault(defaultConf.audioSource)
        val audioBitrate = audioObj.optInt("audio_bitrate", defaultConf.audioBitrate).coerceIn(64_000, 512_000)
        val audioSampleRate = audioObj.optInt("audio_sample_rate", defaultConf.audioSampleRate).coerceIn(16_000, 96_000)
        val audioChannelCount = audioObj.optInt("audio_channel_count", defaultConf.audioChannelCount).coerceIn(1, 2)
        val micGain = audioObj.optDouble("mic_gain", defaultConf.micGain.toDouble()).toFloat().coerceIn(0.0f, 4.0f)
        val internalGain = audioObj.optDouble("internal_audio_gain", defaultConf.internalAudioGain.toDouble()).toFloat().coerceIn(0.0f, 4.0f)
        val audioSyncOffsetMs = audioObj.optInt("audio_sync_offset_ms", defaultConf.audioSyncOffsetMs).coerceIn(-200, 200)

        // 3. Controls Section
        val controlsObj = root.optJSONObject("controls") ?: root
        val showFloatingPill = controlsObj.optBoolean("show_floating_pill", defaultConf.showFloatingPill)
        val alwaysOnFloatingPill = controlsObj.optBoolean("always_on_floating_pill", defaultConf.alwaysOnFloatingPill)
        val hidePillDuringRecording = controlsObj.optBoolean("hide_pill_during_recording", defaultConf.hidePillDuringRecording)
        val autoHidePill = controlsObj.optBoolean("auto_hide_pill", defaultConf.autoHidePill)
        val pillRecallGesture = runCatching { PillRecallGesture.valueOf(controlsObj.optString("pill_recall_gesture", defaultConf.pillRecallGesture.name)) }
            .getOrDefault(defaultConf.pillRecallGesture)
        val shakeToStop = controlsObj.optBoolean("shake_to_stop", defaultConf.shakeToStop)
        val stopOnScreenOff = controlsObj.optBoolean("stop_on_screen_off", defaultConf.stopOnScreenOff)
        val captureTarget = runCatching { CaptureTarget.valueOf(controlsObj.optString("capture_target", defaultConf.captureTarget.name)) }
            .getOrDefault(defaultConf.captureTarget)
        val countdownSeconds = controlsObj.optInt("countdown_seconds", defaultConf.countdownSeconds).coerceIn(0, 10)
        val smartGameOptimization = controlsObj.optBoolean("smart_game_optimization", defaultConf.smartGameOptimization)
        val standbyNotification = controlsObj.optBoolean("standby_notification", defaultConf.standbyNotification)
        val recordingNotification = controlsObj.optBoolean("recording_notification", defaultConf.recordingNotification)

        // 4. HUD Section
        val hudObj = root.optJSONObject("hud") ?: root
        val hudSnapBehavior = runCatching { HudSnapBehavior.valueOf(hudObj.optString("hud_snap_behavior", defaultConf.hudSnapBehavior.name)) }
            .getOrDefault(defaultConf.hudSnapBehavior)

        val standbyHud = hudObj.optJSONObject("standby")?.let { deserializeHudStyle(it, defaultConf.standbyHudConfig) }
            ?: defaultConf.standbyHudConfig
        val recordingHud = hudObj.optJSONObject("recording")?.let { deserializeHudStyle(it, defaultConf.recordingHudConfig) }
            ?: defaultConf.recordingHudConfig

        val streamHudObj = hudObj.optJSONObject("stream_hud")
        val streamHud = if (streamHudObj != null) {
            val laserInterval = runCatching { LaserSweepInterval.valueOf(streamHudObj.optString("laser_sweep_interval", defaultConf.streamHudConfig.laserSweepInterval.name)) }
                .getOrDefault(defaultConf.streamHudConfig.laserSweepInterval)
            val laserGlow = streamHudObj.optDouble("laser_glow_intensity", defaultConf.streamHudConfig.laserGlowIntensity.toDouble()).toFloat().coerceIn(0.1f, 1.0f)
            val uplinkAura = streamHudObj.optBoolean("enable_uplink_health_aura", defaultConf.streamHudConfig.enableUplinkHealthAura)
            val livePulse = runCatching { HudAnimation.valueOf(streamHudObj.optString("live_pulse_rhythm", defaultConf.streamHudConfig.livePulseRhythm.name)) }
                .getOrDefault(defaultConf.streamHudConfig.livePulseRhythm)
            val streamStandby = streamHudObj.optJSONObject("standby")?.let { deserializeHudStyle(it, defaultConf.streamHudConfig.standbyHud) }
                ?: defaultConf.streamHudConfig.standbyHud
            val streamActive = streamHudObj.optJSONObject("active")?.let { deserializeHudStyle(it, defaultConf.streamHudConfig.activeHud) }
                ?: defaultConf.streamHudConfig.activeHud
            StreamHudConfig(
                laserSweepInterval = laserInterval,
                laserGlowIntensity = laserGlow,
                enableUplinkHealthAura = uplinkAura,
                livePulseRhythm = livePulse,
                standbyHud = streamStandby,
                activeHud = streamActive
            )
        } else {
            defaultConf.streamHudConfig
        }

        // Ensure resolution macroblock alignment and non-zero dimensions
        val safeWidth = if (rawWidth > 0) ((rawWidth + 15) / 16) * 16 else defaultConf.width
        val safeHeight = if (rawHeight > 0) ((rawHeight + 15) / 16) * 16 else defaultConf.height

        // 6. Instant Replay Buffer
        val replayObj = root.optJSONObject("replay_buffer")
        val enableReplay = replayObj?.optBoolean("enabled", defaultConf.enableReplayBuffer) ?: defaultConf.enableReplayBuffer
        val replayDuration = replayObj?.optInt("duration_seconds", defaultConf.replayBufferDurationSeconds)?.coerceIn(15, 120)
            ?: defaultConf.replayBufferDurationSeconds

        RecordingConfig(
            width = safeWidth,
            height = safeHeight,
            dpi = rawDpi,
            framerate = rawFps,
            videoBitrate = rawVideoBitrate,
            videoCodec = videoCodec,
            bitrateMode = bitrateMode,
            iFrameIntervalSeconds = iFrameInterval,
            audioSource = audioSource,
            audioBitrate = audioBitrate,
            audioSampleRate = audioSampleRate,
            audioChannelCount = audioChannelCount,
            micGain = micGain,
            internalAudioGain = internalGain,
            recordingOrientation = orientation,
            activePreset = activePreset,
            allowExperimentalFps = allowExperimentalFps,
            colorRange = colorRange,
            enableIntraRefresh = enableIntraRefresh,
            showFloatingPill = showFloatingPill,
            alwaysOnFloatingPill = alwaysOnFloatingPill,
            hidePillDuringRecording = hidePillDuringRecording,
            autoHidePill = autoHidePill,
            pillRecallGesture = pillRecallGesture,
            shakeToStop = shakeToStop,
            stopOnScreenOff = stopOnScreenOff,
            captureTarget = captureTarget,
            countdownSeconds = countdownSeconds,
            smartGameOptimization = smartGameOptimization,
            standbyNotification = standbyNotification,
            recordingNotification = recordingNotification,
            standbyHudConfig = standbyHud,
            recordingHudConfig = recordingHud,
            streamHudConfig = streamHud,
            hudSnapBehavior = hudSnapBehavior,
            enableReplayBuffer = enableReplay,
            replayBufferDurationSeconds = replayDuration,
            audioSyncOffsetMs = audioSyncOffsetMs
        )
    }

    private fun serializeHudStyle(style: HudStyleConfig): JSONObject = JSONObject().apply {
        put("icon_size_dp", style.iconSizeDp)
        put("icon_opacity", style.iconOpacity.toDouble())
        put("animation", style.animation.name)
        put("has_background", style.hasBackground)
        put("shape", style.shape.name)
        put("node_size_dp", style.nodeSizeDp)
        put("background_opacity", style.backgroundOpacity.toDouble())
        put("background_color_hex", style.backgroundColorHex)
        put("has_stroke", style.hasStroke)
        put("stroke_color_hex", style.strokeColorHex)
        put("stroke_width_dp", style.strokeWidthDp.toDouble())
        put("stroke_style", style.strokeStyle.name)
        put("stroke_opacity", style.strokeOpacity.toDouble())
        put("snap_behavior", style.snapBehavior.name)
    }

    private fun deserializeHudStyle(obj: JSONObject, fallback: HudStyleConfig): HudStyleConfig {
        return HudStyleConfig(
            iconSizeDp = obj.optInt("icon_size_dp", fallback.iconSizeDp).coerceIn(15, 44),
            iconOpacity = obj.optDouble("icon_opacity", fallback.iconOpacity.toDouble()).toFloat().coerceIn(0.1f, 1.0f),
            animation = runCatching { HudAnimation.valueOf(obj.optString("animation", fallback.animation.name)) }.getOrDefault(fallback.animation),
            hasBackground = obj.optBoolean("has_background", fallback.hasBackground),
            shape = runCatching { HudShape.valueOf(obj.optString("shape", fallback.shape.name)) }.getOrDefault(fallback.shape),
            nodeSizeDp = obj.optInt("node_size_dp", fallback.nodeSizeDp).coerceIn(36, 56),
            backgroundOpacity = obj.optDouble("background_opacity", fallback.backgroundOpacity.toDouble()).toFloat().coerceIn(0.1f, 1.0f),
            backgroundColorHex = obj.optLong("background_color_hex", fallback.backgroundColorHex),
            hasStroke = obj.optBoolean("has_stroke", fallback.hasStroke),
            strokeColorHex = obj.optLong("stroke_color_hex", fallback.strokeColorHex),
            strokeWidthDp = obj.optDouble("stroke_width_dp", fallback.strokeWidthDp.toDouble()).toFloat().coerceIn(0.5f, 5.0f),
            strokeStyle = runCatching { StrokeStyle.valueOf(obj.optString("stroke_style", fallback.strokeStyle.name)) }.getOrDefault(fallback.strokeStyle),
            strokeOpacity = obj.optDouble("stroke_opacity", fallback.strokeOpacity.toDouble()).toFloat().coerceIn(0.1f, 1.0f),
            snapBehavior = runCatching { HudSnapBehavior.valueOf(obj.optString("snap_behavior", fallback.snapBehavior.name)) }.getOrDefault(fallback.snapBehavior)
        )
    }

    private fun getIsoTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return sdf.format(Date())
    }

    /**
     * Generates a timestamped default filename for export, e.g. "rec_config_20260906_143000.json".
     */
    fun generateDefaultExportFilename(): String {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        return "rec_config_${sdf.format(Date())}.json"
    }
}
