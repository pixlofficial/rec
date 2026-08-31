package pixl.rec.core.storage

import android.content.Context
import android.content.SharedPreferences
import pixl.rec.core.model.AudioSource
import pixl.rec.core.model.BitrateMode
import pixl.rec.core.model.CaptureTarget
import pixl.rec.core.model.PillRecallGesture
import pixl.rec.core.model.RecordingConfig
import pixl.rec.core.model.RecordingOrientation
import pixl.rec.core.model.VideoCodec

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

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun loadConfig(context: Context, defaultConfig: RecordingConfig): RecordingConfig {
        val prefs = getPrefs(context)
        if (!prefs.contains(KEY_FRAMERATE)) {
            // First time run, return hardware-probed defaults
            return defaultConfig
        }

        return RecordingConfig(
            width = prefs.getInt(KEY_WIDTH, defaultConfig.width),
            height = prefs.getInt(KEY_HEIGHT, defaultConfig.height),
            dpi = prefs.getInt(KEY_DPI, defaultConfig.dpi),
            framerate = prefs.getInt(KEY_FRAMERATE, defaultConfig.framerate),
            videoBitrate = prefs.getInt(KEY_VIDEO_BITRATE, defaultConfig.videoBitrate),
            videoCodec = runCatching { VideoCodec.valueOf(prefs.getString(KEY_VIDEO_CODEC, defaultConfig.videoCodec.name) ?: defaultConfig.videoCodec.name) }.getOrDefault(defaultConfig.videoCodec),
            bitrateMode = runCatching { BitrateMode.valueOf(prefs.getString(KEY_BITRATE_MODE, defaultConfig.bitrateMode.name) ?: defaultConfig.bitrateMode.name) }.getOrDefault(defaultConfig.bitrateMode),
            iFrameIntervalSeconds = prefs.getInt(KEY_IFRAME_INTERVAL, defaultConfig.iFrameIntervalSeconds),
            audioSource = runCatching { AudioSource.valueOf(prefs.getString(KEY_AUDIO_SOURCE, defaultConfig.audioSource.name) ?: defaultConfig.audioSource.name) }.getOrDefault(defaultConfig.audioSource),
            audioBitrate = prefs.getInt(KEY_AUDIO_BITRATE, defaultConfig.audioBitrate),
            audioSampleRate = prefs.getInt(KEY_AUDIO_SAMPLE_RATE, defaultConfig.audioSampleRate),
            audioChannelCount = prefs.getInt(KEY_AUDIO_CHANNELS, defaultConfig.audioChannelCount),
            micGain = prefs.getFloat(KEY_MIC_GAIN, defaultConfig.micGain),
            internalAudioGain = prefs.getFloat(KEY_INTERNAL_GAIN, defaultConfig.internalAudioGain),
            recordingOrientation = runCatching { RecordingOrientation.valueOf(prefs.getString(KEY_ORIENTATION, defaultConfig.recordingOrientation.name) ?: defaultConfig.recordingOrientation.name) }.getOrDefault(defaultConfig.recordingOrientation),
            showFloatingPill = prefs.getBoolean(KEY_SHOW_FLOATING_PILL, defaultConfig.showFloatingPill),
            alwaysOnFloatingPill = prefs.getBoolean(KEY_ALWAYS_ON_FLOATING_PILL, defaultConfig.alwaysOnFloatingPill),
            hidePillDuringRecording = prefs.getBoolean(KEY_HIDE_PILL_DURING_REC, defaultConfig.hidePillDuringRecording),
            autoHidePill = prefs.getBoolean(KEY_AUTO_HIDE_PILL, defaultConfig.autoHidePill),
            pillRecallGesture = runCatching { PillRecallGesture.valueOf(prefs.getString(KEY_PILL_RECALL_GESTURE, defaultConfig.pillRecallGesture.name) ?: defaultConfig.pillRecallGesture.name) }.getOrDefault(defaultConfig.pillRecallGesture),
            shakeToStop = prefs.getBoolean(KEY_SHAKE_TO_STOP, defaultConfig.shakeToStop),
            stopOnScreenOff = prefs.getBoolean(KEY_STOP_ON_SCREEN_OFF, defaultConfig.stopOnScreenOff),
            captureTarget = runCatching { CaptureTarget.valueOf(prefs.getString(KEY_CAPTURE_TARGET, defaultConfig.captureTarget.name) ?: defaultConfig.captureTarget.name) }.getOrDefault(defaultConfig.captureTarget)
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
            .putInt(KEY_IFRAME_INTERVAL, config.iFrameIntervalSeconds)
            .putString(KEY_AUDIO_SOURCE, config.audioSource.name)
            .putInt(KEY_AUDIO_BITRATE, config.audioBitrate)
            .putInt(KEY_AUDIO_SAMPLE_RATE, config.audioSampleRate)
            .putInt(KEY_AUDIO_CHANNELS, config.audioChannelCount)
            .putFloat(KEY_MIC_GAIN, config.micGain)
            .putFloat(KEY_INTERNAL_GAIN, config.internalAudioGain)
            .putString(KEY_ORIENTATION, config.recordingOrientation.name)
            .putBoolean(KEY_SHOW_FLOATING_PILL, config.showFloatingPill)
            .putBoolean(KEY_ALWAYS_ON_FLOATING_PILL, config.alwaysOnFloatingPill)
            .putBoolean(KEY_HIDE_PILL_DURING_REC, config.hidePillDuringRecording)
            .putBoolean(KEY_AUTO_HIDE_PILL, config.autoHidePill)
            .putString(KEY_PILL_RECALL_GESTURE, config.pillRecallGesture.name)
            .putBoolean(KEY_SHAKE_TO_STOP, config.shakeToStop)
            .putBoolean(KEY_STOP_ON_SCREEN_OFF, config.stopOnScreenOff)
            .putString(KEY_CAPTURE_TARGET, config.captureTarget.name)
            .apply()
    }
}
