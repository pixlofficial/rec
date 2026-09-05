package pixl.rec.core.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure calculation engine for device-adaptive resolutions, aspect ratios,
 * and 16-pixel macroblock alignment.
 */
object ResolutionCalculator {

    /**
     * Aligns dimension up to the nearest 16-pixel macroblock boundary required
     * by hardware encoders (MediaCodec AVC / HEVC).
     */
    fun align16(dim: Int): Int = ((dim + 15) / 16) * 16

    data class ResolutionTierItem(
        val label: String,
        val tag: String,
        val width: Int,
        val height: Int
    ) {
        val displayDimensionString: String get() = "$width × $height"
    }

    /**
     * Generates deduplicated resolution presets based on the device's physical screen dimensions
     * and current canvas orientation.
     */
    fun getPresetsForDevice(
        physicalWidth: Int,
        physicalHeight: Int,
        isLandscape: Boolean
    ): List<ResolutionTierItem> {
        val minDim = min(physicalWidth, physicalHeight).coerceAtLeast(720)
        val maxDim = max(physicalWidth, physicalHeight).coerceAtLeast(1280)
        val aspectRatio = maxDim.toDouble() / minDim.toDouble()

        fun makePair(shortDim: Int): Pair<Int, Int> {
            val longDim = (shortDim * aspectRatio).roundToInt()
            val alignedShort = align16(shortDim)
            val alignedLong = align16(longDim)
            return if (isLandscape) alignedLong to alignedShort else alignedShort to alignedLong
        }

        val items = mutableListOf<ResolutionTierItem>()

        // 1. Native Display Tier
        val nativePair = if (isLandscape) align16(maxDim) to align16(minDim) else align16(minDim) to align16(maxDim)
        items.add(
            ResolutionTierItem(
                label = "Native",
                tag = "${minDim}p",
                width = nativePair.first,
                height = nativePair.second
            )
        )

        // 2. High Quality Intermediate Tier (e.g. 900p HD+ on 1080p+ devices)
        if (minDim >= 1080) {
            val smoothPair = makePair(900)
            items.add(
                ResolutionTierItem(
                    label = "Smooth",
                    tag = "900p",
                    width = smoothPair.first,
                    height = smoothPair.second
                )
            )
        }

        // 3. Performance / High-Framerate Tier (720p HD)
        if (minDim > 720) {
            val perfPair = makePair(720)
            items.add(
                ResolutionTierItem(
                    label = "Performance",
                    tag = "720p",
                    width = perfPair.first,
                    height = perfPair.second
                )
            )
        }

        // 4. Lite Tier (540p qHD for ultra-compact file sharing)
        val litePair = makePair(540)
        items.add(
            ResolutionTierItem(
                label = "Lite",
                tag = "540p",
                width = litePair.first,
                height = litePair.second
            )
        )

        return items
    }

    /**
     * Computes the human-readable aspect ratio string (e.g. "20:9 • PORTRAIT", "16:9 • LANDSCAPE").
     */
    fun getAspectRatioLabel(width: Int, height: Int): String {
        if (width <= 0 || height <= 0) return "UNKNOWN"

        val isLandscape = width > height
        val isSquare = width == height
        val longDim = max(width, height).toDouble()
        val shortDim = min(width, height).toDouble()
        val ratio = longDim / shortDim

        val ratioString = when {
            isSquare -> "1:1"
            abs(ratio - (20.0 / 9.0)) < 0.05 -> "20:9"
            abs(ratio - (19.5 / 9.0)) < 0.05 -> "19.5:9"
            abs(ratio - (19.0 / 9.0)) < 0.05 -> "19:9"
            abs(ratio - (16.0 / 9.0)) < 0.06 -> "16:9"
            abs(ratio - (4.0 / 3.0)) < 0.05 -> "4:3"
            abs(ratio - (21.0 / 9.0)) < 0.05 -> "21:9"
            else -> String.format(java.util.Locale.US, "%.1f:1", ratio)
        }

        return when {
            isSquare -> "$ratioString • SQUARE"
            isLandscape -> "$ratioString • LANDSCAPE"
            else -> "$ratioString • PORTRAIT"
        }
    }

    /**
     * Swaps width and height while maintaining 16-pixel macroblock alignment.
     */
    fun flipDimensions(width: Int, height: Int): Pair<Int, Int> {
        return align16(height) to align16(width)
    }

    /**
     * Recommends a balanced encoding bitrate (in Mbps) tailored to the given resolution height / short dimension.
     */
    fun getRecommendedBitrateMbps(shortDim: Int): Int = when {
        shortDim >= 1440 -> 50
        shortDim >= 1080 -> 28
        shortDim >= 720 -> 16
        else -> 8
    }
}
