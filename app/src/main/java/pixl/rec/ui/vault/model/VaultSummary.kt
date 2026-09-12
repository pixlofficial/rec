package pixl.rec.ui.vault.model

import pixl.rec.core.storage.StorageCalculator

/**
 * Pre-computed catalog metrics for the Media Vault.
 * Computed once on background threads to keep UI rendering 100% jank-free.
 */
data class VaultSummary(
    val totalCount: Int = 0,
    val totalBytes: Long = 0L,
    val recordingCount: Int = 0,
    val recordingBytes: Long = 0L,
    val streamCount: Int = 0,
    val streamBytes: Long = 0L,
    val replayCount: Int = 0,
    val replayBytes: Long = 0L,
    val screenshotCount: Int = 0,
    val screenshotBytes: Long = 0L
) {
    fun getCount(type: VaultMediaType): Int = when (type) {
        VaultMediaType.RECORDING -> recordingCount
        VaultMediaType.STREAM -> streamCount
        VaultMediaType.REPLAY -> replayCount
        VaultMediaType.SCREENSHOT -> screenshotCount
    }

    fun getBytes(type: VaultMediaType): Long = when (type) {
        VaultMediaType.RECORDING -> recordingBytes
        VaultMediaType.STREAM -> streamBytes
        VaultMediaType.REPLAY -> replayBytes
        VaultMediaType.SCREENSHOT -> screenshotBytes
    }

    fun getFormattedBytes(type: VaultMediaType): String =
        StorageCalculator.formatBytes(getBytes(type))

    val formattedTotalBytes: String
        get() = StorageCalculator.formatBytes(totalBytes)

    companion object {
        val EMPTY = VaultSummary()

        fun fromItems(items: List<VaultMediaItem>): VaultSummary {
            var totalBytes = 0L
            var recCount = 0
            var recBytes = 0L
            var streamCount = 0
            var streamBytes = 0L
            var replayCount = 0
            var replayBytes = 0L
            var shotCount = 0
            var shotBytes = 0L

            for (item in items) {
                totalBytes += item.sizeBytes
                when (item.mediaType) {
                    VaultMediaType.RECORDING -> {
                        recCount++
                        recBytes += item.sizeBytes
                    }
                    VaultMediaType.STREAM -> {
                        streamCount++
                        streamBytes += item.sizeBytes
                    }
                    VaultMediaType.REPLAY -> {
                        replayCount++
                        replayBytes += item.sizeBytes
                    }
                    VaultMediaType.SCREENSHOT -> {
                        shotCount++
                        shotBytes += item.sizeBytes
                    }
                }
            }

            return VaultSummary(
                totalCount = items.size,
                totalBytes = totalBytes,
                recordingCount = recCount,
                recordingBytes = recBytes,
                streamCount = streamCount,
                streamBytes = streamBytes,
                replayCount = replayCount,
                replayBytes = replayBytes,
                screenshotCount = shotCount,
                screenshotBytes = shotBytes
            )
        }
    }
}

/**
 * Represents the complete catalog state loaded from MediaStore.
 */
data class VaultCatalog(
    val items: List<VaultMediaItem> = emptyList(),
    val summary: VaultSummary = VaultSummary.EMPTY,
    val isLoading: Boolean = false,
    val lastError: String? = null
)
