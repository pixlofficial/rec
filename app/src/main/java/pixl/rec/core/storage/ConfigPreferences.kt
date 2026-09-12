package pixl.rec.core.storage

import android.content.Context
import android.content.SharedPreferences
import pixl.rec.core.model.AudioSource
import pixl.rec.core.model.BitrateMode
import pixl.rec.core.model.CaptureTarget
import pixl.rec.core.model.ColorRange
import pixl.rec.core.model.HudAnimation
import pixl.rec.core.model.PillRecallGesture
import pixl.rec.core.model.RecordingConfig
import pixl.rec.core.model.RecordingOrientation
import pixl.rec.core.model.VideoCodec
import pixl.rec.core.model.HudShape
import pixl.rec.core.model.StrokeStyle
import pixl.rec.core.model.HudSnapBehavior
import pixl.rec.core.model.HudStyleConfig
import pixl.rec.core.model.LaserSweepInterval
import pixl.rec.core.model.StreamHudConfig
import pixl.rec.core.model.StreamConfig
import pixl.rec.core.model.StreamDestination
import pixl.rec.core.model.StreamPlatform

/**
 * Operating mode of the PixL REC Studio (Offline recording vs. live broadcasting).
 */
enum class StudioMode(val displayName: String) {
    RECORD("RECORD"),
    STREAM("STREAM")
}

/**
 * SharedPreferences persistence manager for user configuration profiles.
 */
object ConfigPreferences {
    private const val PREFS_NAME = "rec_config_prefs"

    private const val KEY_WIDTH = "width"
    private const val KEY_HEIGHT = "height"
    private const val KEY_DPI = "dpi"
    private const val KEY_FRAMERATE = "framerate"
    private const val KEY_VIDEO_BITRATE = "video_bitrate"
    private const val KEY_VIDEO_CODEC = "video_codec"
    private const val KEY_BITRATE_MODE = "bitrate_mode"
    private const val KEY_IFRAME_INTERVAL = "iframe_interval"
    private const val KEY_AUDIO_SOURCE = "audio_source"
    private const val KEY_AUDIO_BITRATE = "audio_bitrate"
    private const val KEY_AUDIO_SAMPLE_RATE = "audio_sample_rate"
    private const val KEY_AUDIO_CHANNELS = "audio_channels"
    private const val KEY_MIC_GAIN = "mic_gain"
    private const val KEY_INTERNAL_GAIN = "internal_gain"
    private const val KEY_ORIENTATION = "recording_orientation"
    private const val KEY_SHOW_FLOATING_PILL = "show_floating_pill"
    private const val KEY_ALWAYS_ON_FLOATING_PILL = "always_on_floating_pill"
    private const val KEY_HIDE_PILL_DURING_REC = "hide_pill_during_rec"
    private const val KEY_AUTO_HIDE_PILL = "auto_hide_pill"
    private const val KEY_PILL_RECALL_GESTURE = "pill_recall_gesture"
    private const val KEY_SHAKE_TO_STOP = "shake_to_stop"
    private const val KEY_STOP_ON_SCREEN_OFF = "stop_on_screen_off"
    private const val KEY_CAPTURE_TARGET = "capture_target"
    private const val KEY_COUNTDOWN_SECONDS = "rec_pref_countdown_seconds"
    private const val KEY_ACTIVE_PRESET = "active_preset"
    private const val KEY_ALLOW_EXPERIMENTAL_FPS = "allow_experimental_fps"
    private const val KEY_COLOR_RANGE = "color_range"
    private const val KEY_ENABLE_INTRA_REFRESH = "enable_intra_refresh"
    private const val KEY_SMART_GAME_OPTIMIZATION = "smart_game_optimization"
    private const val KEY_STANDBY_NOTIFICATION = "standby_notification"
    private const val KEY_RECORDING_NOTIFICATION = "recording_notification"
    private const val KEY_DISMISS_GAMING_PRESET_PROMPT = "dismiss_gaming_preset_prompt"
    private const val KEY_DISMISS_AUTOTUNE_BITRATE = "dismiss_autotune_bitrate"
    private const val KEY_HAS_SEEN_WELCOME = "has_seen_welcome"
    private const val KEY_LAST_SEEN_VERSION_CODE = "last_seen_version_code"
    private const val KEY_PILL_DOCKED_ON_LEFT = "pill_docked_on_left"
    private const val KEY_PILL_DOCKED_ON_RIGHT = "pill_docked_on_right"
    private const val KEY_PILL_DOCK_Y_RATIO = "pill_dock_y_ratio"

    // Live Streaming & Studio Mode Keys
    const val KEY_STUDIO_MODE = "studio_mode"
    private const val KEY_ENABLE_LIVE_STREAMING = "enable_live_streaming"
    private const val KEY_STREAM_PLATFORM = "stream_platform"
    private const val KEY_STREAM_CUSTOM_ENDPOINT = "stream_custom_endpoint"
    private const val KEY_STREAM_VIDEO_BITRATE = "stream_video_bitrate"
    private const val KEY_STREAM_ENABLE_ABR = "stream_enable_abr"
    private const val KEY_STREAM_MIN_BITRATE = "stream_min_bitrate"
    private const val KEY_STREAM_MAX_BITRATE = "stream_max_bitrate"
    private const val KEY_STREAM_SAVE_LOCAL_ARCHIVE = "stream_save_local_archive"
    private const val KEY_STREAM_USE_ENHANCED_HEVC = "stream_use_enhanced_hevc"

    // Standby HUD Keys
    private const val KEY_STANDBY_ICON_SIZE_DP = "standby_hud_icon_size_dp"
    private const val KEY_STANDBY_ICON_OPACITY = "standby_hud_icon_opacity"
    private const val KEY_STANDBY_ANIMATION = "standby_hud_animation"
    private const val KEY_STANDBY_HAS_BG = "standby_hud_has_bg"
    private const val KEY_STANDBY_SHAPE = "standby_hud_shape"
    private const val KEY_STANDBY_NODE_SIZE_DP = "standby_hud_node_size_dp"
    private const val KEY_STANDBY_BG_OPACITY = "standby_hud_bg_opacity"
    private const val KEY_STANDBY_HAS_STROKE = "standby_hud_has_stroke"
    private const val KEY_STANDBY_STROKE_WIDTH = "standby_hud_stroke_width"
    private const val KEY_STANDBY_STROKE_STYLE = "standby_hud_stroke_style"
    private const val KEY_STANDBY_STROKE_OPACITY = "standby_hud_stroke_opacity"
    private const val KEY_STANDBY_SNAP_BEHAVIOR = "standby_hud_snap"

    // Recording HUD Keys
    private const val KEY_REC_ICON_SIZE_DP = "rec_hud_icon_size_dp"
    private const val KEY_REC_ICON_OPACITY = "rec_hud_icon_opacity"
    private const val KEY_REC_ANIMATION = "rec_hud_animation"
    private const val KEY_REC_HAS_BG = "rec_hud_has_bg"
    private const val KEY_REC_SHAPE = "rec_hud_shape"
    private const val KEY_REC_NODE_SIZE_DP = "rec_hud_node_size_dp"
    private const val KEY_REC_BG_OPACITY = "rec_hud_bg_opacity"
    private const val KEY_REC_HAS_STROKE = "rec_hud_has_stroke"
    private const val KEY_REC_STROKE_WIDTH = "rec_hud_stroke_width"
    private const val KEY_REC_STROKE_STYLE = "rec_hud_stroke_style"
    private const val KEY_REC_STROKE_OPACITY = "rec_hud_stroke_opacity"
    private const val KEY_REC_SNAP_BEHAVIOR = "rec_hud_snap"

    // Stream HUD Keys
    private const val KEY_STREAM_HUD_LASER_INTERVAL = "stream_hud_laser_interval"
    private const val KEY_STREAM_HUD_LASER_GLOW = "stream_hud_laser_glow"
    private const val KEY_STREAM_HUD_ENABLE_UPLINK_AURA = "stream_hud_enable_uplink_aura"
    private const val KEY_STREAM_HUD_LIVE_PULSE = "stream_hud_live_pulse"

    // Replay Buffer Keys
    private const val KEY_ENABLE_REPLAY_BUFFER = "enable_replay_buffer"
    private const val KEY_REPLAY_BUFFER_DURATION = "replay_buffer_duration"


    fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun loadConfig(context: Context, defaultConfig: RecordingConfig): RecordingConfig {
        val prefs = getPrefs(context)
        if (!prefs.contains(KEY_FRAMERATE)) {
            return defaultConfig
        }

        val standbyHud = loadHudStyle(prefs, "standby_hud", defaultConfig.standbyHudConfig)
        val recordingHud = loadHudStyle(prefs, "rec_hud", defaultConfig.recordingHudConfig)

        val streamHud = StreamHudConfig(
            laserSweepInterval = runCatching { LaserSweepInterval.valueOf(prefs.getString(KEY_STREAM_HUD_LASER_INTERVAL, defaultConfig.streamHudConfig.laserSweepInterval.name) ?: defaultConfig.streamHudConfig.laserSweepInterval.name) }.getOrDefault(defaultConfig.streamHudConfig.laserSweepInterval),
            laserGlowIntensity = prefs.getFloat(KEY_STREAM_HUD_LASER_GLOW, defaultConfig.streamHudConfig.laserGlowIntensity),
            enableUplinkHealthAura = prefs.getBoolean(KEY_STREAM_HUD_ENABLE_UPLINK_AURA, defaultConfig.streamHudConfig.enableUplinkHealthAura),
            livePulseRhythm = runCatching { HudAnimation.valueOf(prefs.getString(KEY_STREAM_HUD_LIVE_PULSE, defaultConfig.streamHudConfig.livePulseRhythm.name) ?: defaultConfig.streamHudConfig.livePulseRhythm.name) }.getOrDefault(defaultConfig.streamHudConfig.livePulseRhythm),
            standbyHud = loadHudStyle(prefs, "stream_hud_standby", defaultConfig.streamHudConfig.standbyHud),
            activeHud = loadHudStyle(prefs, "stream_hud_active", defaultConfig.streamHudConfig.activeHud)
        )

        return RecordingConfig(
            width = prefs.getInt(KEY_WIDTH, defaultConfig.width),
            height = prefs.getInt(KEY_HEIGHT, defaultConfig.height),
            dpi = prefs.getInt(KEY_DPI, defaultConfig.dpi),
            framerate = prefs.getInt(KEY_FRAMERATE, defaultConfig.framerate),
            videoBitrate = prefs.getInt(KEY_VIDEO_BITRATE, defaultConfig.videoBitrate),
            videoCodec = runCatching { VideoCodec.valueOf(prefs.getString(KEY_VIDEO_CODEC, defaultConfig.videoCodec.name) ?: defaultConfig.videoCodec.name) }.getOrDefault(defaultConfig.videoCodec),
            bitrateMode = runCatching { BitrateMode.valueOf(prefs.getString(KEY_BITRATE_MODE, defaultConfig.bitrateMode.name) ?: defaultConfig.bitrateMode.name) }.getOrDefault(defaultConfig.bitrateMode),
            iFrameIntervalSeconds = try {
                prefs.getFloat(KEY_IFRAME_INTERVAL, defaultConfig.iFrameIntervalSeconds)
            } catch (_: ClassCastException) {
                try {
                    prefs.getInt(KEY_IFRAME_INTERVAL, defaultConfig.iFrameIntervalSeconds.toInt()).toFloat()
                } catch (_: Exception) {
                    defaultConfig.iFrameIntervalSeconds
                }
            },
            audioSource = runCatching { AudioSource.valueOf(prefs.getString(KEY_AUDIO_SOURCE, defaultConfig.audioSource.name) ?: defaultConfig.audioSource.name) }.getOrDefault(defaultConfig.audioSource),
            audioBitrate = prefs.getInt(KEY_AUDIO_BITRATE, defaultConfig.audioBitrate),
            audioSampleRate = prefs.getInt(KEY_AUDIO_SAMPLE_RATE, defaultConfig.audioSampleRate),
            audioChannelCount = prefs.getInt(KEY_AUDIO_CHANNELS, defaultConfig.audioChannelCount),
            micGain = prefs.getFloat(KEY_MIC_GAIN, defaultConfig.micGain),
            internalAudioGain = prefs.getFloat(KEY_INTERNAL_GAIN, defaultConfig.internalAudioGain),
            recordingOrientation = runCatching { RecordingOrientation.valueOf(prefs.getString(KEY_ORIENTATION, defaultConfig.recordingOrientation.name) ?: defaultConfig.recordingOrientation.name) }.getOrDefault(defaultConfig.recordingOrientation),
            activePreset = runCatching { pixl.rec.core.model.QuickPreset.valueOf(prefs.getString(KEY_ACTIVE_PRESET, defaultConfig.activePreset.name) ?: defaultConfig.activePreset.name) }.getOrDefault(defaultConfig.activePreset),
            allowExperimentalFps = prefs.getBoolean(KEY_ALLOW_EXPERIMENTAL_FPS, defaultConfig.allowExperimentalFps),
            colorRange = runCatching { ColorRange.valueOf(prefs.getString(KEY_COLOR_RANGE, defaultConfig.colorRange.name) ?: defaultConfig.colorRange.name) }.getOrDefault(defaultConfig.colorRange),
            enableIntraRefresh = prefs.getBoolean(KEY_ENABLE_INTRA_REFRESH, defaultConfig.enableIntraRefresh),
            showFloatingPill = prefs.getBoolean(KEY_SHOW_FLOATING_PILL, defaultConfig.showFloatingPill),
            alwaysOnFloatingPill = prefs.getBoolean(KEY_ALWAYS_ON_FLOATING_PILL, defaultConfig.alwaysOnFloatingPill),
            hidePillDuringRecording = prefs.getBoolean(KEY_HIDE_PILL_DURING_REC, defaultConfig.hidePillDuringRecording),
            autoHidePill = prefs.getBoolean(KEY_AUTO_HIDE_PILL, defaultConfig.autoHidePill),
            pillRecallGesture = runCatching { PillRecallGesture.valueOf(prefs.getString(KEY_PILL_RECALL_GESTURE, defaultConfig.pillRecallGesture.name) ?: defaultConfig.pillRecallGesture.name) }.getOrDefault(defaultConfig.pillRecallGesture),
            shakeToStop = prefs.getBoolean(KEY_SHAKE_TO_STOP, defaultConfig.shakeToStop),
            stopOnScreenOff = prefs.getBoolean(KEY_STOP_ON_SCREEN_OFF, defaultConfig.stopOnScreenOff),
            captureTarget = runCatching { CaptureTarget.valueOf(prefs.getString(KEY_CAPTURE_TARGET, defaultConfig.captureTarget.name) ?: defaultConfig.captureTarget.name) }.getOrDefault(defaultConfig.captureTarget),
            countdownSeconds = prefs.getInt(KEY_COUNTDOWN_SECONDS, defaultConfig.countdownSeconds).let {
                if (it in listOf(0, 3, 5)) it else 0
            },
            smartGameOptimization = prefs.getBoolean(KEY_SMART_GAME_OPTIMIZATION, defaultConfig.smartGameOptimization),
            standbyNotification = prefs.getBoolean(KEY_STANDBY_NOTIFICATION, defaultConfig.standbyNotification),
            recordingNotification = prefs.getBoolean(KEY_RECORDING_NOTIFICATION, defaultConfig.recordingNotification),
            standbyHudConfig = standbyHud,
            recordingHudConfig = recordingHud,
            streamHudConfig = streamHud,
            enableReplayBuffer = prefs.getBoolean(KEY_ENABLE_REPLAY_BUFFER, defaultConfig.enableReplayBuffer),
            replayBufferDurationSeconds = prefs.getInt(KEY_REPLAY_BUFFER_DURATION, defaultConfig.replayBufferDurationSeconds)
        )
    }

    fun saveConfig(context: Context, config: RecordingConfig) {
        val editor = getPrefs(context).edit()
        editor.putInt(KEY_WIDTH, config.width)
            .putInt(KEY_HEIGHT, config.height)
            .putInt(KEY_DPI, config.dpi)
            .putInt(KEY_FRAMERATE, config.framerate)
            .putInt(KEY_VIDEO_BITRATE, config.videoBitrate)
            .putString(KEY_VIDEO_CODEC, config.videoCodec.name)
            .putString(KEY_BITRATE_MODE, config.bitrateMode.name)
            .putFloat(KEY_IFRAME_INTERVAL, config.iFrameIntervalSeconds)
            .putString(KEY_AUDIO_SOURCE, config.audioSource.name)
            .putInt(KEY_AUDIO_BITRATE, config.audioBitrate)
            .putInt(KEY_AUDIO_SAMPLE_RATE, config.audioSampleRate)
            .putInt(KEY_AUDIO_CHANNELS, config.audioChannelCount)
            .putFloat(KEY_MIC_GAIN, config.micGain)
            .putFloat(KEY_INTERNAL_GAIN, config.internalAudioGain)
            .putString(KEY_ORIENTATION, config.recordingOrientation.name)
            .putString(KEY_ACTIVE_PRESET, config.activePreset.name)
            .putBoolean(KEY_ALLOW_EXPERIMENTAL_FPS, config.allowExperimentalFps)
            .putString(KEY_COLOR_RANGE, config.colorRange.name)
            .putBoolean(KEY_ENABLE_INTRA_REFRESH, config.enableIntraRefresh)
            .putBoolean(KEY_SHOW_FLOATING_PILL, config.showFloatingPill)
            .putBoolean(KEY_ALWAYS_ON_FLOATING_PILL, config.alwaysOnFloatingPill)
            .putBoolean(KEY_HIDE_PILL_DURING_REC, config.hidePillDuringRecording)
            .putBoolean(KEY_AUTO_HIDE_PILL, config.autoHidePill)
            .putString(KEY_PILL_RECALL_GESTURE, config.pillRecallGesture.name)
            .putBoolean(KEY_SHAKE_TO_STOP, config.shakeToStop)
            .putBoolean(KEY_STOP_ON_SCREEN_OFF, config.stopOnScreenOff)
            .putBoolean(KEY_SMART_GAME_OPTIMIZATION, config.smartGameOptimization)
            .putBoolean(KEY_STANDBY_NOTIFICATION, config.standbyNotification)
            .putBoolean(KEY_RECORDING_NOTIFICATION, config.recordingNotification)
            .putString(KEY_CAPTURE_TARGET, config.captureTarget.name)
            .putInt(KEY_COUNTDOWN_SECONDS, config.countdownSeconds)
            .putBoolean(KEY_ENABLE_REPLAY_BUFFER, config.enableReplayBuffer)
            .putInt(KEY_REPLAY_BUFFER_DURATION, config.replayBufferDurationSeconds)
            // Stream HUD Customization
            .putString(KEY_STREAM_HUD_LASER_INTERVAL, config.streamHudConfig.laserSweepInterval.name)
            .putFloat(KEY_STREAM_HUD_LASER_GLOW, config.streamHudConfig.laserGlowIntensity)
            .putBoolean(KEY_STREAM_HUD_ENABLE_UPLINK_AURA, config.streamHudConfig.enableUplinkHealthAura)
            .putString(KEY_STREAM_HUD_LIVE_PULSE, config.streamHudConfig.livePulseRhythm.name)

        saveHudStyle(editor, "standby_hud", config.standbyHudConfig)
        saveHudStyle(editor, "rec_hud", config.recordingHudConfig)
        saveHudStyle(editor, "stream_hud_standby", config.streamHudConfig.standbyHud)
        saveHudStyle(editor, "stream_hud_active", config.streamHudConfig.activeHud)

        editor.apply()
    }

    private fun loadHudStyle(prefs: SharedPreferences, prefix: String, fallback: HudStyleConfig): HudStyleConfig {
        return HudStyleConfig(
            iconSizeDp = prefs.getInt("${prefix}_icon_size_dp", fallback.iconSizeDp),
            iconOpacity = prefs.getFloat("${prefix}_icon_opacity", fallback.iconOpacity),
            animation = runCatching { HudAnimation.valueOf(prefs.getString("${prefix}_animation", fallback.animation.name) ?: fallback.animation.name) }.getOrDefault(fallback.animation),
            hasBackground = prefs.getBoolean("${prefix}_has_bg", fallback.hasBackground),
            shape = runCatching { HudShape.valueOf(prefs.getString("${prefix}_shape", fallback.shape.name) ?: fallback.shape.name) }.getOrDefault(fallback.shape),
            nodeSizeDp = prefs.getInt("${prefix}_node_size_dp", fallback.nodeSizeDp),
            backgroundOpacity = prefs.getFloat("${prefix}_bg_opacity", fallback.backgroundOpacity),
            backgroundColorHex = prefs.getLong("${prefix}_bg_color_hex", fallback.backgroundColorHex),
            hasStroke = prefs.getBoolean("${prefix}_has_stroke", fallback.hasStroke),
            strokeColorHex = prefs.getLong("${prefix}_stroke_color_hex", fallback.strokeColorHex),
            strokeWidthDp = prefs.getFloat("${prefix}_stroke_width", fallback.strokeWidthDp),
            strokeStyle = runCatching { StrokeStyle.valueOf(prefs.getString("${prefix}_stroke_style", fallback.strokeStyle.name) ?: fallback.strokeStyle.name) }.getOrDefault(fallback.strokeStyle),
            strokeOpacity = prefs.getFloat("${prefix}_stroke_opacity", fallback.strokeOpacity),
            snapBehavior = runCatching { HudSnapBehavior.valueOf(prefs.getString("${prefix}_snap", fallback.snapBehavior.name) ?: fallback.snapBehavior.name) }.getOrDefault(fallback.snapBehavior)
        )
    }

    private fun saveHudStyle(editor: SharedPreferences.Editor, prefix: String, style: HudStyleConfig) {
        editor.putInt("${prefix}_icon_size_dp", style.iconSizeDp)
            .putFloat("${prefix}_icon_opacity", style.iconOpacity)
            .putString("${prefix}_animation", style.animation.name)
            .putBoolean("${prefix}_has_bg", style.hasBackground)
            .putString("${prefix}_shape", style.shape.name)
            .putInt("${prefix}_node_size_dp", style.nodeSizeDp)
            .putFloat("${prefix}_bg_opacity", style.backgroundOpacity)
            .putLong("${prefix}_bg_color_hex", style.backgroundColorHex)
            .putBoolean("${prefix}_has_stroke", style.hasStroke)
            .putLong("${prefix}_stroke_color_hex", style.strokeColorHex)
            .putFloat("${prefix}_stroke_width", style.strokeWidthDp)
            .putString("${prefix}_stroke_style", style.strokeStyle.name)
            .putFloat("${prefix}_stroke_opacity", style.strokeOpacity)
            .putString("${prefix}_snap", style.snapBehavior.name)
    }

    fun isAutoTuneBitrateDismissed(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_DISMISS_AUTOTUNE_BITRATE, false)
    }

    fun setAutoTuneBitrateDismissed(context: Context, dismissed: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DISMISS_AUTOTUNE_BITRATE, dismissed).apply()
    }

    fun isGamingPresetPromptDismissed(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_DISMISS_GAMING_PRESET_PROMPT, false)
    }

    fun setGamingPresetPromptDismissed(context: Context, dismissed: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DISMISS_GAMING_PRESET_PROMPT, dismissed).apply()
    }

    fun getStandbyNotification(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_STANDBY_NOTIFICATION, true)
    }

    fun setStandbyNotification(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_STANDBY_NOTIFICATION, enabled).apply()
    }

    fun getRecordingNotification(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_RECORDING_NOTIFICATION, true)
    }

    fun hasSeenWelcome(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_HAS_SEEN_WELCOME, false)
    }

    fun setHasSeenWelcome(context: Context, seen: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_HAS_SEEN_WELCOME, seen).apply()
    }

    fun getLastSeenVersionCode(context: Context): Int {
        return getPrefs(context).getInt(KEY_LAST_SEEN_VERSION_CODE, 0)
    }

    fun setLastSeenVersionCode(context: Context, versionCode: Int) {
        getPrefs(context).edit().putInt(KEY_LAST_SEEN_VERSION_CODE, versionCode).apply()
    }

    fun savePillDockState(context: Context, isLeft: Boolean, isRight: Boolean, yRatio: Float) {
        getPrefs(context).edit()
            .putBoolean(KEY_PILL_DOCKED_ON_LEFT, isLeft)
            .putBoolean(KEY_PILL_DOCKED_ON_RIGHT, isRight)
            .putFloat(KEY_PILL_DOCK_Y_RATIO, yRatio)
            .apply()
    }

    fun loadPillDockState(context: Context): Triple<Boolean, Boolean, Float> {
        val prefs = getPrefs(context)
        val isLeft = prefs.getBoolean(KEY_PILL_DOCKED_ON_LEFT, true)
        val isRight = prefs.getBoolean(KEY_PILL_DOCKED_ON_RIGHT, false)
        val yRatio = prefs.getFloat(KEY_PILL_DOCK_Y_RATIO, 0.35f)
        return Triple(isLeft, isRight, yRatio)
    }

    fun getStudioMode(context: Context): StudioMode {
        val name = getPrefs(context).getString(KEY_STUDIO_MODE, StudioMode.RECORD.name) ?: StudioMode.RECORD.name
        return runCatching { StudioMode.valueOf(name) }.getOrDefault(StudioMode.RECORD)
    }

    fun setStudioMode(context: Context, mode: StudioMode) {
        getPrefs(context).edit().putString(KEY_STUDIO_MODE, mode.name).apply()
    }

    fun isLiveStreamingEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ENABLE_LIVE_STREAMING, true)
    }

    fun setLiveStreamingEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ENABLE_LIVE_STREAMING, enabled).apply()
    }

    const val KEY_LAST_OTHER_PLATFORM = "key_last_other_platform"

    fun getLastOtherPlatform(context: Context): StreamPlatform {
        val name = getPrefs(context).getString(KEY_LAST_OTHER_PLATFORM, StreamPlatform.FACEBOOK.name)
        return runCatching { StreamPlatform.valueOf(name ?: StreamPlatform.FACEBOOK.name) }
            .getOrDefault(StreamPlatform.FACEBOOK)
            .let { if (it.isPrimary) StreamPlatform.FACEBOOK else it }
    }

    fun saveLastOtherPlatform(context: Context, platform: StreamPlatform) {
        if (!platform.isPrimary) {
            getPrefs(context).edit().putString(KEY_LAST_OTHER_PLATFORM, platform.name).apply()
        }
    }

    fun loadStreamConfig(context: Context): StreamConfig {
        val prefs = getPrefs(context)
        val platformStr = prefs.getString(KEY_STREAM_PLATFORM, StreamPlatform.YOUTUBE.name) ?: StreamPlatform.YOUTUBE.name
        val platform = runCatching { StreamPlatform.valueOf(platformStr) }.getOrDefault(StreamPlatform.YOUTUBE)
        val legacyStreamKey = SecureStreamPreferences.getStreamKey(context)
        val customUrl = prefs.getString(KEY_STREAM_CUSTOM_ENDPOINT, "") ?: ""
        val activePlatformKey = SecureStreamPreferences.getPlatformStreamKey(context, platform).ifBlank {
            if (platform == StreamPlatform.YOUTUBE) legacyStreamKey else ""
        }

        val destinations = StreamPlatform.entries.map { p ->
            val isEnabled = prefs.getBoolean("stream_dest_enabled_${p.name.lowercase()}", p == platform)
            val key = SecureStreamPreferences.getPlatformStreamKey(context, p).ifBlank {
                if (p == platform) activePlatformKey else ""
            }
            StreamDestination(
                platform = p,
                customEndpointUrl = if (p.isCustomEndpoint) customUrl else "",
                streamKey = key,
                enabled = isEnabled
            )
        }

        return StreamConfig(
            platform = platform,
            customEndpointUrl = customUrl,
            streamKey = activePlatformKey,
            destinations = destinations,
            videoBitrate = prefs.getInt(KEY_STREAM_VIDEO_BITRATE, platform.defaultVideoBitrate),
            enableAbr = prefs.getBoolean(KEY_STREAM_ENABLE_ABR, true),
            minBitrate = prefs.getInt(KEY_STREAM_MIN_BITRATE, 2_000_000),
            maxBitrate = prefs.getInt(KEY_STREAM_MAX_BITRATE, 14_000_000),
            saveLocalMasterArchive = prefs.getBoolean(KEY_STREAM_SAVE_LOCAL_ARCHIVE, true),
            useEnhancedHevc = prefs.getBoolean(KEY_STREAM_USE_ENHANCED_HEVC, true)
        )
    }

    fun saveStreamConfig(context: Context, config: StreamConfig) {
        // Save secret keys per destination in hardware-backed Keystore
        config.destinations.forEach { dest ->
            SecureStreamPreferences.savePlatformStreamKey(context, dest.platform, dest.streamKey)
        }
        if (config.streamKey.isNotBlank()) {
            SecureStreamPreferences.savePlatformStreamKey(context, config.platform, config.streamKey)
            if (config.platform == StreamPlatform.YOUTUBE) {
                SecureStreamPreferences.saveStreamKey(context, config.streamKey)
            }
        }
        if (!config.platform.isPrimary) {
            saveLastOtherPlatform(context, config.platform)
        }

        // Save non-sensitive parameters
        val editor = getPrefs(context).edit()
            .putString(KEY_STREAM_PLATFORM, config.platform.name)
            .putString(KEY_STREAM_CUSTOM_ENDPOINT, config.customEndpointUrl)
            .putInt(KEY_STREAM_VIDEO_BITRATE, config.videoBitrate)
            .putBoolean(KEY_STREAM_ENABLE_ABR, config.enableAbr)
            .putInt(KEY_STREAM_MIN_BITRATE, config.minBitrate)
            .putInt(KEY_STREAM_MAX_BITRATE, config.maxBitrate)
            .putBoolean(KEY_STREAM_SAVE_LOCAL_ARCHIVE, config.saveLocalMasterArchive)
            .putBoolean(KEY_STREAM_USE_ENHANCED_HEVC, config.useEnhancedHevc)

        config.destinations.forEach { dest ->
            editor.putBoolean("stream_dest_enabled_${dest.platform.name.lowercase()}", dest.enabled)
            if (dest.platform.isCustomEndpoint && dest.customEndpointUrl.isNotBlank()) {
                editor.putString(KEY_STREAM_CUSTOM_ENDPOINT, dest.customEndpointUrl)
            }
        }

        editor.apply()
    }
}
