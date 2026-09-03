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

        // Determine maximum hardware-supported framerate across hardware encoders
        val maxHardwareFps = codecMap.values
            .filter { it.isHardwareAccelerated }
            .mapNotNull {
                val codecInfo = findHardwareEncoder(it.codec.mimeType)
                try {
                    codecInfo?.getCapabilitiesForType(it.codec.mimeType)?.videoCapabilities?.supportedFrameRates?.upper?.toInt()
                } catch (_: Exception) { null }
            }
            .maxOrNull() ?: 60

        // Select recommended codec (prefer HEVC hardware, fallback to AVC)
        val recommendedCodec = when {
            codecMap[VideoCodec.HEVC]?.isHardwareAccelerated == true -> VideoCodec.HEVC
            codecMap[VideoCodec.AVC]?.isHardwareAccelerated == true -> VideoCodec.AVC
            else -> VideoCodec.AVC
        }

        // Match recommended framerate to active display refresh rate, capped strictly at hardware encoder limit for native display
        val displayRefresh = displayProfile.currentRefreshRate.roundToInt()
        val nativeSupportedFps = getSupportedFrameratesFor(
            codec = recommendedCodec,
            width = displayProfile.physicalWidth,
            height = displayProfile.physicalHeight,
            maxDisplayHz = displayProfile.supportedRefreshRates.maxOrNull() ?: displayProfile.currentRefreshRate
        )
        val recommendedFramerate = nativeSupportedFps.filter { it <= displayRefresh }.maxOrNull() ?: 30

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
            val minDim = min(testW, testH)
            val maxDim = max(testW, testH)

            val supportedFps = mutableListOf<Int>()
            for (fps in STANDARD_FPS_TIERS) {
                val isFpsSupported = if (videoCaps != null) {
                    isFpsSupportedDirect(videoCaps, testW, testH, minDim, maxDim, fps)
                } else {
                    fps <= 60
                }
                if (isFpsSupported) {
                    supportedFps.add(fps)
                }
            }

            if (supportedFps.isEmpty()) {
                supportedFps.add(30)
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
     * Cache for dynamic resolution/codec/framerate queries: (codec, alignedW, alignedH) -> supportedFpsList
     */
    private val framerateQueryCache = mutableMapOf<Triple<VideoCodec, Int, Int>, List<Int>>()

    /**
     * Probes all supported framerate tiers for a specific codec at the given dimensions.
     * Evaluates against hardware encoder capabilities, respecting orientation swapping and display Hz.
     */
    fun getSupportedFrameratesFor(
        codec: VideoCodec,
        width: Int,
        height: Int,
        maxDisplayHz: Float = 120f
    ): List<Int> {
        val alignedW = ((width + 15) / 16) * 16
        val alignedH = ((height + 15) / 16) * 16
        val cacheKey = Triple(codec, alignedW, alignedH)

        val cached = framerateQueryCache[cacheKey]
        val supportedTiers = if (cached != null) {
            cached
        } else {
            val codecInfo = findHardwareEncoder(codec.mimeType) ?: findEncoder(codec.mimeType)
            val vCaps = try {
                codecInfo?.getCapabilitiesForType(codec.mimeType)?.videoCapabilities
            } catch (_: Exception) {
                null
            }

            if (vCaps == null) {
                listOf(30)
            } else {
                val minDim = min(alignedW, alignedH)
                val maxDim = max(alignedW, alignedH)

                val result = mutableListOf<Int>()
                for (fps in STANDARD_FPS_TIERS) {
                    val supported = isFpsSupportedDirect(vCaps, alignedW, alignedH, minDim, maxDim, fps)
                    if (supported) {
                        result.add(fps)
                    }
                }
                if (!result.contains(30)) {
                    result.add(0, 30)
                }
                result.sort()
                framerateQueryCache[cacheKey] = result
                result
            }
        }

        // Filter against display refresh rate ceiling (with 1 FPS tolerance for 59.94 / 119.8 Hz displays)
        return if (maxDisplayHz > 0) {
            supportedTiers.filter { fps -> fps == 30 || (fps - 1f) <= maxDisplayHz }
        } else {
            supportedTiers
        }
    }

    /**
     * Checks if a specific framerate is supported by VideoCapabilities, testing both orientations.
     */
    private fun isFpsSupportedDirect(
        vCaps: MediaCodecInfo.VideoCapabilities,
        w: Int,
        h: Int,
        minDim: Int,
        maxDim: Int,
        fps: Int
    ): Boolean {
        // Test 1: Direct native query
        val directMatch = try {
            vCaps.areSizeAndRateSupported(w, h, fps.toDouble())
        } catch (_: Exception) {
            false
        }
        if (directMatch) return true

        // Test 2: Inverted orientation (portrait <-> landscape swap)
        val swappedMatch = try {
            vCaps.areSizeAndRateSupported(h, w, fps.toDouble())
        } catch (_: Exception) {
            false
        }
        if (swappedMatch) return true

        // Test 3: Standard landscape bounds (maxDim x minDim)
        val landscapeMatch = try {
            vCaps.areSizeAndRateSupported(maxDim, minDim, fps.toDouble())
        } catch (_: Exception) {
            false
        }
        if (landscapeMatch) return true

        // Test 4: Query supported frame rate range for dimensions
        val maxFromRange = try {
            val r1 = runCatching { vCaps.getSupportedFrameRatesFor(w, h).upper.toDouble() }.getOrNull()
            val r2 = runCatching { vCaps.getSupportedFrameRatesFor(h, w).upper.toDouble() }.getOrNull()
            val r3 = runCatching { vCaps.getSupportedFrameRatesFor(maxDim, minDim).upper.toDouble() }.getOrNull()
            listOfNotNull(r1, r2, r3).maxOrNull() ?: 0.0
        } catch (_: Exception) {
            0.0
        }

        return maxFromRange >= (fps - 0.5)
    }

    /**
     * Returns the maximum hardware framerate achievable at the given dimensions for the codec.
     */
    fun getMaxFramerateFor(codec: VideoCodec, width: Int, height: Int): Int {
        val codecInfo = findHardwareEncoder(codec.mimeType) ?: findEncoder(codec.mimeType) ?: return 30
        return try {
            val vCaps = codecInfo.getCapabilitiesForType(codec.mimeType)?.videoCapabilities ?: return 30
            val alignedW = ((width + 15) / 16) * 16
            val alignedH = ((height + 15) / 16) * 16
            val minDim = min(alignedW, alignedH)
            val maxDim = max(alignedW, alignedH)

            val r1 = runCatching { vCaps.getSupportedFrameRatesFor(alignedW, alignedH).upper.toInt() }.getOrNull()
            val r2 = runCatching { vCaps.getSupportedFrameRatesFor(alignedH, alignedW).upper.toInt() }.getOrNull()
            val r3 = runCatching { vCaps.getSupportedFrameRatesFor(maxDim, minDim).upper.toInt() }.getOrNull()
            val rate = listOfNotNull(r1, r2, r3).maxOrNull()

            if (rate != null && rate > 0) {
                rate
            } else {
                listOf(144, 120, 90, 60, 30).firstOrNull { fps ->
                    isFpsSupportedDirect(vCaps, alignedW, alignedH, minDim, maxDim, fps)
                } ?: 30
            }
        } catch (_: Exception) {
            30
        }
    }

    /**
     * Checks whether the specified dimensions are within the codec's supported size envelope.
     */
    fun isSizeSupportedFor(codec: VideoCodec, width: Int, height: Int): Boolean {
        val codecInfo = findHardwareEncoder(codec.mimeType) ?: findEncoder(codec.mimeType) ?: return false
        return try {
            val vCaps = codecInfo.getCapabilitiesForType(codec.mimeType)?.videoCapabilities ?: return false
            val alignedW = ((width + 15) / 16) * 16
            val alignedH = ((height + 15) / 16) * 16
            val minDim = min(alignedW, alignedH)
            val maxDim = max(alignedW, alignedH)

            vCaps.isSizeSupported(alignedW, alignedH) ||
            vCaps.isSizeSupported(alignedH, alignedW) ||
            vCaps.isSizeSupported(maxDim, minDim)
        } catch (_: Exception) {
            true
        }
    }

    /**
     * Returns the maximum supported bitrate in bits per second for the specified codec.
     */
    fun getMaxBitrateFor(codec: VideoCodec): Int {
        val codecInfo = findHardwareEncoder(codec.mimeType) ?: findEncoder(codec.mimeType) ?: return 50_000_000
        return try {
            codecInfo.getCapabilitiesForType(codec.mimeType)?.videoCapabilities?.bitrateRange?.upper ?: 50_000_000
        } catch (_: Exception) {
            50_000_000
        }
    }

    /**
     * Locates a dedicated hardware-accelerated video encoder.
     */
    fun findHardwareEncoder(mimeType: String): MediaCodecInfo? {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        for (info in codecList.codecInfos) {
            if (!info.isEncoder) continue
            try {
                val caps = info.getCapabilitiesForType(mimeType) ?: continue
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    if (info.isHardwareAccelerated && !info.isSoftwareOnly) {
                        return info
                    }
                } else {
                    val name = info.name.lowercase()
                    if (!name.startsWith("omx.google.") &&
                        !name.startsWith("c2.android.") &&
                        !name.contains("sw")
                    ) {
                        return info
                    }
                }
            } catch (e: Exception) {
                continue
            }
        }
        return null
    }

    /**
     * Locates any valid encoder for the given MIME type (hardware or software).
     */
    fun findEncoder(mimeType: String): MediaCodecInfo? {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        for (info in codecList.codecInfos) {
            if (!info.isEncoder) continue
            try {
                info.getCapabilitiesForType(mimeType) ?: continue
                return info
            } catch (e: Exception) {
                continue
            }
        }
        return null
    }

    /**
     * Inspects device encoder profile and level support to maximize encoding efficiency.
     */
    fun selectProfileAndLevel(codecInfo: MediaCodecInfo, mimeType: String, width: Int, height: Int, fps: Int): Pair<Int, Int>? {
        val caps = try { codecInfo.getCapabilitiesForType(mimeType) } catch (e: Exception) { return null }
        val profileLevels = caps.profileLevels ?: return null

        return if (mimeType.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true)) {
            // For AVC (H.264): Prioritize AVCProfileHigh, then Main, then Baseline
            val highProfiles = profileLevels.filter { it.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileHigh }
            val mainProfiles = profileLevels.filter { it.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileMain }
            val baselineProfiles = profileLevels.filter { it.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline }
            val best = highProfiles.maxByOrNull { it.level }
                ?: mainProfiles.maxByOrNull { it.level }
                ?: baselineProfiles.maxByOrNull { it.level }
            if (best != null) best.profile to best.level else null
        } else if (mimeType.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, ignoreCase = true)) {
            // For HEVC (H.265): Strictly prioritize HEVCProfileMain (8-bit SDR) for screen surface capture.
            // Exclude HEVCProfileMain10 to prevent 10-bit HDR configuration failures on SDR surfaces.
            val mainProfiles = profileLevels.filter {
                it.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain
            }
            val best = mainProfiles.maxByOrNull { it.level }
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
