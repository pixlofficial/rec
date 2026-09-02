package pixl.rec.core.engine

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
import pixl.rec.core.model.CodecCapabilityInfo
import pixl.rec.core.model.DeviceCapabilities
import pixl.rec.core.model.DisplayProfile
import pixl.rec.core.model.RecordingConfig
import pixl.rec.core.model.VideoCodec
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
            val capability = probeCodec(codec, displayProfile.physicalWidth, displayProfile.physicalHeight)
            codecMap[codec] = capability
        }

        // Determine maximum hardware-supported framerate across hardware encoders for this display
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

        // Match recommended framerate to active display refresh rate, capped strictly at hardware encoder limit
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
    fun probeCodec(codec: VideoCodec, targetWidth: Int = 1080, targetHeight: Int = 1920): CodecCapabilityInfo {
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

            val testW = min(targetWidth, maxWidth).coerceAtLeast(16)
            val testH = min(targetHeight, maxHeight).coerceAtLeast(16)

            val supportedFps = mutableListOf<Int>()
            for (fps in STANDARD_FPS_TIERS) {
                val isFpsSupported = try {
                    videoCaps?.areSizeAndRateSupported(testW, testH, fps.toDouble()) == true
                } catch (e: Exception) {
                    fps <= 60
                }
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
    fun findEncoder(mimeType: String): MediaCodecInfo? {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        return codecList.codecInfos.firstOrNull { info ->
            info.isEncoder && info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }
        }
    }

    /**
     * Selects optimal Profile and Level (e.g. AVC Level 5.1/5.2 or HEVC Main Level 5.1) for 2K/4K high-resolution encoding.
     */
    fun selectProfileAndLevel(codecInfo: MediaCodecInfo, mimeType: String, width: Int, height: Int, fps: Int): Pair<Int, Int>? {
        val caps = try { codecInfo.getCapabilitiesForType(mimeType) } catch (e: Exception) { return null }
        val profileLevels = caps.profileLevels ?: return null

        return if (mimeType.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true)) {
            // For AVC (H.264): Prioritize AVCProfileHigh with highest level (e.g. Level 5.1 / 5.2 for 2K/4K)
            val highProfiles = profileLevels.filter {
                it.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileHigh ||
                it.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileMain ||
                it.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline
            }
            val best = highProfiles.maxByOrNull { it.level } ?: profileLevels.maxByOrNull { it.level }
            if (best != null) best.profile to best.level else null
        } else if (mimeType.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, ignoreCase = true)) {
            // For HEVC (H.265): Prioritize HEVCProfileMain / Main10 with highest level
            val mainProfiles = profileLevels.filter {
                it.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain ||
                it.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10
            }
            val best = mainProfiles.maxByOrNull { it.level } ?: profileLevels.maxByOrNull { it.level }
            if (best != null) best.profile to best.level else null
        } else {
            null
        }
    }

    /**
     * Verifies if dimensions are natively supported by the encoder's VPU registers.
     * If dimensions exceed physical registers, scales down proportionally preserving exact aspect ratio.
     */
    fun validateAndClampDimensions(
        codecInfo: MediaCodecInfo,
        mimeType: String,
        requestedWidth: Int,
        requestedHeight: Int
    ): Pair<Int, Int> {
        val caps = try { codecInfo.getCapabilitiesForType(mimeType) } catch (e: Exception) { return requestedWidth to requestedHeight }
        val videoCaps = caps.videoCapabilities ?: return requestedWidth to requestedHeight

        // Check if exact requested dimensions are directly supported
        if (videoCaps.isSizeSupported(requestedWidth, requestedHeight)) {
            return requestedWidth to requestedHeight
        }

        val maxWidth = videoCaps.supportedWidths.upper
        val maxHeight = videoCaps.supportedHeights.upper

        var w = requestedWidth
        var h = requestedHeight

        // If width or height exceeds maximum hardware registers, scale down preserving aspect ratio
        if (w > maxWidth || h > maxHeight) {
            val widthScale = maxWidth.toFloat() / w.toFloat()
            val heightScale = maxHeight.toFloat() / h.toFloat()
            val scale = min(widthScale, heightScale)
            w = (w * scale).toInt()
            h = (h * scale).toInt()
        }

        // Ensure 16-pixel macroblock alignment
        w = ((w + 15) / 16) * 16
        h = ((h + 15) / 16) * 16

        // Clamp to supported range
        w = w.coerceIn(videoCaps.supportedWidths.lower, maxWidth)
        h = h.coerceIn(videoCaps.supportedHeights.lower, maxHeight)

        // Final verification
        if (!videoCaps.isSizeSupported(w, h)) {
            val safeW = min(w, 1088)
            val safeH = ((safeW * (requestedHeight.toDouble() / requestedWidth.toDouble())).toInt() / 16) * 16
            return safeW to safeH
        }

        return w to h
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

        // Align dimensions to 16-pixel macroblock boundary
        val alignedWidth = ((config.width + 15) / 16) * 16
        val alignedHeight = ((config.height + 15) / 16) * 16

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
