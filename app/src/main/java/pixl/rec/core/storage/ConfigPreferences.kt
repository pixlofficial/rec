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
    private const val KEY_DISMISS_AUTOTUNE_BITRATE = "dismiss_autotune_bitrate"

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

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun loadConfig(context: Context, defaultConfig: RecordingConfig): RecordingConfig {
        val prefs = getPrefs(context)
        if (!prefs.contains(KEY_FRAMERATE)) {
            return defaultConfig
        }

        val standbyHud = HudStyleConfig(
            iconSizeDp = prefs.getInt(KEY_STANDBY_ICON_SIZE_DP, defaultConfig.standbyHudConfig.iconSizeDp),
            iconOpacity = prefs.getFloat(KEY_STANDBY_ICON_OPACITY, defaultConfig.standbyHudConfig.iconOpacity),
            animation = runCatching { HudAnimation.valueOf(prefs.getString(KEY_STANDBY_ANIMATION, defaultConfig.standbyHudConfig.animation.name) ?: defaultConfig.standbyHudConfig.animation.name) }.getOrDefault(defaultConfig.standbyHudConfig.animation),
            hasBackground = prefs.getBoolean(KEY_STANDBY_HAS_BG, defaultConfig.standbyHudConfig.hasBackground),
            shape = runCatching { HudShape.valueOf(prefs.getString(KEY_STANDBY_SHAPE, defaultConfig.standbyHudConfig.shape.name) ?: defaultConfig.standbyHudConfig.shape.name) }.getOrDefault(defaultConfig.standbyHudConfig.shape),
            nodeSizeDp = prefs.getInt(KEY_STANDBY_NODE_SIZE_DP, defaultConfig.standbyHudConfig.nodeSizeDp),
            backgroundOpacity = prefs.getFloat(KEY_STANDBY_BG_OPACITY, defaultConfig.standbyHudConfig.backgroundOpacity),
            hasStroke = prefs.getBoolean(KEY_STANDBY_HAS_STROKE, defaultConfig.standbyHudConfig.hasStroke),
            strokeWidthDp = prefs.getFloat(KEY_STANDBY_STROKE_WIDTH, defaultConfig.standbyHudConfig.strokeWidthDp),
            strokeStyle = runCatching { StrokeStyle.valueOf(prefs.getString(KEY_STANDBY_STROKE_STYLE, defaultConfig.standbyHudConfig.strokeStyle.name) ?: defaultConfig.standbyHudConfig.strokeStyle.name) }.getOrDefault(defaultConfig.standbyHudConfig.strokeStyle),
            strokeOpacity = prefs.getFloat(KEY_STANDBY_STROKE_OPACITY, defaultConfig.standbyHudConfig.strokeOpacity),
            snapBehavior = runCatching { HudSnapBehavior.valueOf(prefs.getString(KEY_STANDBY_SNAP_BEHAVIOR, defaultConfig.standbyHudConfig.snapBehavior.name) ?: defaultConfig.standbyHudConfig.snapBehavior.name) }.getOrDefault(defaultConfig.standbyHudConfig.snapBehavior)
        )

        val recordingHud = HudStyleConfig(
            iconSizeDp = prefs.getInt(KEY_REC_ICON_SIZE_DP, defaultConfig.recordingHudConfig.iconSizeDp),
            iconOpacity = prefs.getFloat(KEY_REC_ICON_OPACITY, defaultConfig.recordingHudConfig.iconOpacity),
            animation = runCatching { HudAnimation.valueOf(prefs.getString(KEY_REC_ANIMATION, defaultConfig.recordingHudConfig.animation.name) ?: defaultConfig.recordingHudConfig.animation.name) }.getOrDefault(defaultConfig.recordingHudConfig.animation),
            hasBackground = prefs.getBoolean(KEY_REC_HAS_BG, defaultConfig.recordingHudConfig.hasBackground),
            shape = runCatching { HudShape.valueOf(prefs.getString(KEY_REC_SHAPE, defaultConfig.recordingHudConfig.shape.name) ?: defaultConfig.recordingHudConfig.shape.name) }.getOrDefault(defaultConfig.recordingHudConfig.shape),
            nodeSizeDp = prefs.getInt(KEY_REC_NODE_SIZE_DP, defaultConfig.recordingHudConfig.nodeSizeDp),
            backgroundOpacity = prefs.getFloat(KEY_REC_BG_OPACITY, defaultConfig.recordingHudConfig.backgroundOpacity),
            hasStroke = prefs.getBoolean(KEY_REC_HAS_STROKE, defaultConfig.recordingHudConfig.hasStroke),
            strokeWidthDp = prefs.getFloat(KEY_REC_STROKE_WIDTH, defaultConfig.recordingHudConfig.strokeWidthDp),
            strokeStyle = runCatching { StrokeStyle.valueOf(prefs.getString(KEY_REC_STROKE_STYLE, defaultConfig.recordingHudConfig.strokeStyle.name) ?: defaultConfig.recordingHudConfig.strokeStyle.name) }.getOrDefault(defaultConfig.recordingHudConfig.strokeStyle),
            strokeOpacity = prefs.getFloat(KEY_REC_STROKE_OPACITY, defaultConfig.recordingHudConfig.strokeOpacity),
            snapBehavior = runCatching { HudSnapBehavior.valueOf(prefs.getString(KEY_REC_SNAP_BEHAVIOR, defaultConfig.recordingHudConfig.snapBehavior.name) ?: defaultConfig.recordingHudConfig.snapBehavior.name) }.getOrDefault(defaultConfig.recordingHudConfig.snapBehavior)
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
            standbyHudConfig = standbyHud,
            recordingHudConfig = recordingHud
        )
    }

    fun saveConfig(context: Context, config: RecordingConfig) {
        getPrefs(context).edit()
            .putInt(KEY_WIDTH, config.width)
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
            .putString(KEY_CAPTURE_TARGET, config.captureTarget.name)
            .putInt(KEY_COUNTDOWN_SECONDS, config.countdownSeconds)
            // Standby HUD Customization
            .putInt(KEY_STANDBY_ICON_SIZE_DP, config.standbyHudConfig.iconSizeDp)
            .putFloat(KEY_STANDBY_ICON_OPACITY, config.standbyHudConfig.iconOpacity)
            .putString(KEY_STANDBY_ANIMATION, config.standbyHudConfig.animation.name)
            .putBoolean(KEY_STANDBY_HAS_BG, config.standbyHudConfig.hasBackground)
            .putString(KEY_STANDBY_SHAPE, config.standbyHudConfig.shape.name)
            .putInt(KEY_STANDBY_NODE_SIZE_DP, config.standbyHudConfig.nodeSizeDp)
            .putFloat(KEY_STANDBY_BG_OPACITY, config.standbyHudConfig.backgroundOpacity)
            .putBoolean(KEY_STANDBY_HAS_STROKE, config.standbyHudConfig.hasStroke)
            .putFloat(KEY_STANDBY_STROKE_WIDTH, config.standbyHudConfig.strokeWidthDp)
            .putString(KEY_STANDBY_STROKE_STYLE, config.standbyHudConfig.strokeStyle.name)
            .putFloat(KEY_STANDBY_STROKE_OPACITY, config.standbyHudConfig.strokeOpacity)
            .putString(KEY_STANDBY_SNAP_BEHAVIOR, config.standbyHudConfig.snapBehavior.name)
            // Recording HUD Customization
            .putInt(KEY_REC_ICON_SIZE_DP, config.recordingHudConfig.iconSizeDp)
            .putFloat(KEY_REC_ICON_OPACITY, config.recordingHudConfig.iconOpacity)
            .putString(KEY_REC_ANIMATION, config.recordingHudConfig.animation.name)
            .putBoolean(KEY_REC_HAS_BG, config.recordingHudConfig.hasBackground)
            .putString(KEY_REC_SHAPE, config.recordingHudConfig.shape.name)
            .putInt(KEY_REC_NODE_SIZE_DP, config.recordingHudConfig.nodeSizeDp)
            .putFloat(KEY_REC_BG_OPACITY, config.recordingHudConfig.backgroundOpacity)
            .putBoolean(KEY_REC_HAS_STROKE, config.recordingHudConfig.hasStroke)
            .putFloat(KEY_REC_STROKE_WIDTH, config.recordingHudConfig.strokeWidthDp)
            .putString(KEY_REC_STROKE_STYLE, config.recordingHudConfig.strokeStyle.name)
            .putFloat(KEY_REC_STROKE_OPACITY, config.recordingHudConfig.strokeOpacity)
            .putString(KEY_REC_SNAP_BEHAVIOR, config.recordingHudConfig.snapBehavior.name)
            .apply()
    }

    fun isAutoTuneBitrateDismissed(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_DISMISS_AUTOTUNE_BITRATE, false)
    }

    fun setAutoTuneBitrateDismissed(context: Context, dismissed: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DISMISS_AUTOTUNE_BITRATE, dismissed).apply()
    }
}
