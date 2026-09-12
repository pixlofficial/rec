package pixl.rec.ui.vault.model

/**
 * Pure, deterministic classifier for REC-owned media items in Scoped Storage.
 */
object VaultMediaClassifier {

    /**
     * Classifies a media file based on its filename prefix and MIME type.
     *
     * Rules:
     * 1. If filename starts with "STREAM_", it is a [VaultMediaType.STREAM].
     * 2. If filename starts with "CLIP_", it is a [VaultMediaType.REPLAY].
     * 3. If filename starts with "SHOT_" or MIME type starts with "image/", it is a [VaultMediaType.SCREENSHOT].
     * 4. If filename starts with "REC_", it is a [VaultMediaType.RECORDING].
     * 5. Fallback: Any other unrecognized video item owned by REC is classified as [VaultMediaType.RECORDING]
     *    to guarantee no media is hidden or lost.
     */
    fun classify(displayName: String, mimeType: String? = null): VaultMediaType {
        val upperName = displayName.uppercase()

        if (upperName.startsWith("STREAM_")) {
            return VaultMediaType.STREAM
        }
        if (upperName.startsWith("CLIP_")) {
            return VaultMediaType.REPLAY
        }
        if (upperName.startsWith("SHOT_") || mimeType?.startsWith("image/", ignoreCase = true) == true) {
            return VaultMediaType.SCREENSHOT
        }
        if (upperName.startsWith("REC_")) {
            return VaultMediaType.RECORDING
        }

        // Safe fallback: images remain screenshots, videos fallback to recording
        return if (mimeType?.startsWith("image/", ignoreCase = true) == true) {
            VaultMediaType.SCREENSHOT
        } else {
            VaultMediaType.RECORDING
        }
    }
}
