package pixl.rec.ui.vault.model

import androidx.compose.ui.graphics.Color
import pixl.rec.ui.theme.CyberYellow
import pixl.rec.ui.theme.ElectricPurple
import pixl.rec.ui.theme.HyperCyan
import pixl.rec.ui.theme.ToxicLime

/**
 * Canonical media classification within the PixL REC Media Vault.
 */
enum class VaultMediaType(
    val displayName: String,
    val filterLabel: String,
    val prefix: String,
    val badgeText: String,
    val badgeIconText: String
) {
    RECORDING(
        displayName = "Recording",
        filterLabel = "RECORDINGS",
        prefix = "REC_",
        badgeText = "REC",
        badgeIconText = "🔴"
    ),
    STREAM(
        displayName = "Broadcast Stream",
        filterLabel = "STREAMS",
        prefix = "STREAM_",
        badgeText = "STREAM",
        badgeIconText = "📡"
    ),
    REPLAY(
        displayName = "Instant Replay",
        filterLabel = "REPLAYS",
        prefix = "CLIP_",
        badgeText = "REPLAY",
        badgeIconText = "⚡"
    ),
    SCREENSHOT(
        displayName = "Screenshot",
        filterLabel = "SCREENSHOTS",
        prefix = "SHOT_",
        badgeText = "SHOT",
        badgeIconText = "📸"
    );

    val accentColor: Color
        get() = when (this) {
            RECORDING -> CyberYellow
            STREAM -> HyperCyan
            REPLAY -> ToxicLime
            SCREENSHOT -> ElectricPurple
        }

    val isVideo: Boolean
        get() = this != SCREENSHOT

    val isImage: Boolean
        get() = this == SCREENSHOT
}
