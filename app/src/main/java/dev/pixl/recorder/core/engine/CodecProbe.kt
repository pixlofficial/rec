package dev.pixl.recorder.core.engine

import android.content.Context
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import dev.pixl.recorder.core.model.CodecCapabilityInfo
import dev.pixl.recorder.core.model.DeviceCapabilities
import dev.pixl.recorder.core.model.DisplayProfile
import dev.pixl.recorder.core.model.RecordingConfig
import dev.pixl.recorder.core.model.VideoCodec
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pre-flight hardware capabilities scanner.
 * Inspects SoC hardware encoders and display refresh rates to ensure zero-crash encoder configuration.
 */
object CodecProbe {

    private val STANDARD_FPS_TIERS = listOf(30, 60, 90, 120, 144)

    /**
     * Probes all available hardware encoders and display profiles on this device.
     */
    fun probeDevice(context: Context): DeviceCapabilities {
        val displayProfile = probeDisplay(context)
        val codecMap = mutableMapOf<VideoCodec, CodecCapabilityInfo>()

        for (codec in VideoCodec.entries) {
            val capability = probeCodec(codec)
            codecMap[codec] = capability
        }

        // Determine maximum hardware-supported framerate across hardware encoders
        val maxHardwareFps = codecMap.values
            .filter { it.isHardwareAccelerated }
            .flatMap { it.supportedFramerates }
            .maxOrNull() ?: 60

        // Select recommended codec (prefer HEVC hardware, fallback to AVC)
        val recommendedCodec = when {
            codecMap[VideoCodec.HEVC]?.isHardwareAccelerated == true -> VideoCodec.HEVC
            codecMap[VideoCodec.AVC]?.isHardwareAccelerated == true -> VideoCodec.AVC
            else -> VideoCodec.AVC
        }

        // Match recommended framerate to active display refresh rate, capped at hardware encoder limit
        val displayRefresh = displayProfile.currentRefreshRate.roundToInt()
        val recommendedFramerate = min(displayRefresh, maxHardwareFps).coerceAtLeast(30)

        return DeviceCapabilities(
            display = displayProfile,
            codecs = codecMap,
            hasInternalAudioCapture = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
            maxHardwareFps = maxHardwareFps,
            recommendedCodec = recommendedCodec,
            recommendedFramerate = recommendedFramerate,
            recommendedWidth = displayProfile.physicalWidth,
            recommendedHeight = displayProfile.physicalHeight
        )
    }

    /**
     * Inspects active display resolution, DPI density, and all supported refresh rates.
     */
    fun probeDisplay(context: Context): DisplayProfile {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager

        val display: Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                context.display
            } catch (e: Exception) {
                displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
            }
        } else {
            @Suppress("DEPRECATION")
            windowManager?.defaultDisplay
        }

        val width: Int
        val height: Int
        val densityDpi: Int

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && windowManager != null) {
            val metrics = windowManager.currentWindowMetrics
            val bounds: Rect = metrics.bounds
            width = bounds.width()
            height = bounds.height()
            densityDpi = context.resources.configuration.densityDpi
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            display?.getRealMetrics(metrics)
            width = metrics.widthPixels
            height = metrics.heightPixels
            densityDpi = metrics.densityDpi
        }

        val currentRefreshRate = display?.refreshRate ?: 60.0f
        val supportedRefreshRates = mutableListOf<Float>()

        display?.supportedModes?.forEach { mode ->
            val rate = (mode.refreshRate * 10).roundToInt() / 10.0f
            if (!supportedRefreshRates.contains(rate)) {
                supportedRefreshRates.add(rate)
            }
        }

        if (supportedRefreshRates.isEmpty()) {
            supportedRefreshRates.add(currentRefreshRate)
        }
        supportedRefreshRates.sort()

        return DisplayProfile(
            physicalWidth = width,
            physicalHeight = height,
            densityDpi = densityDpi,
            currentRefreshRate = currentRefreshRate,
            supportedRefreshRates = supportedRefreshRates
        )
    }

    /**
     * Probes capabilities of a specific video codec on the SoC.
     */
    fun probeCodec(codec: VideoCodec): CodecCapabilityInfo {
        val codecInfo = findHardwareEncoder(codec.mimeType) ?: findEncoder(codec.mimeType)

        if (codecInfo == null) {
            return CodecCapabilityInfo(
                codec = codec,
                codecName = "Unavailable",
                isHardwareAccelerated = false,
                isSoftwareOnly = true,
                maxWidth = 0,
                maxHeight = 0,
                maxBitrate = 0,
                supportedFramerates = emptyList(),
                isSupported = false
            )
        }

        val isHardware = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            codecInfo.isHardwareAccelerated
        } else {
            !codecInfo.name.startsWith("OMX.google.", ignoreCase = true) &&
                    !codecInfo.name.startsWith("c2.android.", ignoreCase = true)
        }

        val isSoftware = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            codecInfo.isSoftwareOnly
        } else {
            !isHardware
        }

        return try {
            val capabilities = codecInfo.getCapabilitiesForType(codec.mimeType)
            val videoCaps = capabilities.videoCapabilities

            val maxWidth = videoCaps?.supportedWidths?.upper ?: 1920
            val maxHeight = videoCaps?.supportedHeights?.upper ?: 1080
            val maxBitrate = videoCaps?.bitrateRange?.upper ?: 50_000_000

            val supportedFps = mutableListOf<Int>()
            for (fps in STANDARD_FPS_TIERS) {
                val isFpsSupported = videoCaps?.supportedFrameRates?.contains(fps) == true ||
                        videoCaps?.areSizeAndRateSupported(min(1080, maxWidth), min(1920, maxHeight), fps.toDouble()) == true
                if (isFpsSupported) {
                    supportedFps.add(fps)
                }
            }

            if (supportedFps.isEmpty()) {
                supportedFps.add(60)
            }

            CodecCapabilityInfo(
                codec = codec,
                codecName = codecInfo.name,
                isHardwareAccelerated = isHardware,
                isSoftwareOnly = isSoftware,
                maxWidth = maxWidth,
                maxHeight = maxHeight,
                maxBitrate = maxBitrate,
                supportedFramerates = supportedFps,
                isSupported = true
            )
        } catch (e: Exception) {
            CodecCapabilityInfo(
                codec = codec,
                codecName = codecInfo.name,
                isHardwareAccelerated = isHardware,
                isSoftwareOnly = isSoftware,
                maxWidth = 1920,
                maxHeight = 1080,
                maxBitrate = 50_000_000,
                supportedFramerates = listOf(30, 60),
                isSupported = true
            )
        }
    }

    /**
     * Locates a dedicated hardware-accelerated video encoder.
     */
    fun findHardwareEncoder(mimeType: String): MediaCodecInfo? {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        for (info in codecList.codecInfos) {
            if (!info.isEncoder) continue
            val types = info.supportedTypes
            val supportsType = types.any { it.equals(mimeType, ignoreCase = true) }
            if (!supportsType) continue

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (info.isHardwareAccelerated && !info.isSoftwareOnly) {
                    return info
                }
            } else {
                val name = info.name.lowercase()
                if (!name.startsWith("omx.google.") && !name.startsWith("c2.android.")) {
                    return info
                }
            }
        }
        return null
    }

    /**
     * Fallback lookup for any encoder supporting the mime type.
     */
    private fun findEncoder(mimeType: String): MediaCodecInfo? {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        return codecList.codecInfos.firstOrNull { info ->
            info.isEncoder && info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }
        }
    }

    /**
     * Clamps user configuration to hardware limits (even dimensions and max supported fps).
     */
    fun sanitizeConfig(config: RecordingConfig, capabilities: DeviceCapabilities): RecordingConfig {
        var selectedCodec = config.videoCodec
        val codecInfo = capabilities.codecs[selectedCodec]

        // If requested codec is unsupported or AV1 without hardware ASIC, fallback to HEVC or AVC
        if (codecInfo == null || !codecInfo.isSupported || (selectedCodec == VideoCodec.AV1 && !capabilities.isAv1HardwareSupported)) {
            selectedCodec = capabilities.recommendedCodec
        }

        // Align dimensions to 2
        val alignedWidth = (config.width + 1) and 1.inv()
        val alignedHeight = (config.height + 1) and 1.inv()

        // Clamp framerate to hardware limits
        val maxFps = capabilities.maxHardwareFps
        val clampedFps = min(config.framerate, maxFps).coerceAtLeast(30)

        return config.copy(
            width = alignedWidth,
            height = alignedHeight,
            framerate = clampedFps,
            videoCodec = selectedCodec
        )
    }
}
