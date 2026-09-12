package pixl.rec.core.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Geometric background silhouettes supported by the Floating HUD.
 */
enum class HudShape(val displayName: String) {
    OCTAGON("Octagon"),
    CIRCLE("Circle"),
    HEXAGON("Hexagon")
}

/**
 * Stroke / Border line pattern.
 */
enum class StrokeStyle(val displayName: String) {
    SOLID("Solid"),
    DOTTED("Dotted"),
    DASHED("Dashed")
}

/**
 * Dynamic live animations for the HUD record icon.
 */
enum class HudAnimation(val displayName: String) {
    NONE("None"),
    BREATHE("Breathe"),
    PULSE("Pulse"),
    HEARTBEAT("Heartbeat"),
    BLINK("Blink")
}

/**
 * Screen edge docking and magnetic snapping behaviors.
 */
enum class HudSnapBehavior(val displayName: String, val description: String) {
    PROXIMITY_SNAP("Smart Edge Magnet", "Free-floating; snaps to edge only when released near bezel (<52dp)"),
    ALWAYS_SNAP_EDGE("Always Snap", "Automatically flings to nearest screen edge on release"),
    FREE_FLOAT("Free Floating", "Drops precisely where placed with zero snapping")
}

/**
 * Unified HUD appearance configuration supporting independent icon, background,
 * and stroke sizing, styling, animation, and opacity controls.
 */
@Parcelize
data class HudStyleConfig(
    val iconSizeDp: Int = 44, // Standalone / Inner Icon size in DP (15dp to 44dp)
    val iconOpacity: Float = 1.0f, // 0.20f to 1.0f
    val animation: HudAnimation = HudAnimation.NONE,
    val hasBackground: Boolean = false,
    val shape: HudShape = HudShape.OCTAGON,
    val nodeSizeDp: Int = 44, // Background silhouette container size in DP (36dp to 56dp)
    val backgroundOpacity: Float = 0.95f, // 0.10f to 1.0f
    val backgroundColorHex: Long = 0xFF0D0E15L, // Obsidian Black
    val hasStroke: Boolean = false,
    val strokeColorHex: Long = 0xFFFF2A4DL, // Hyper Crimson
    val strokeWidthDp: Float = 2.0f, // 0.5dp to 3.5dp
    val strokeStyle: StrokeStyle = StrokeStyle.SOLID,
    val strokeOpacity: Float = 1.0f, // 0.10f to 1.0f
    val snapBehavior: HudSnapBehavior = HudSnapBehavior.PROXIMITY_SNAP
) : Parcelable {
    val sizeDp: Int
        get() = if (hasBackground) nodeSizeDp else iconSizeDp

    val idleOpacity: Float
        get() = iconOpacity
}

/**
 * Speed/interval presets for the bi-directional laser sweep animation on the stream standby pill.
 */
enum class LaserSweepInterval(val displayName: String, val intervalMs: Long) {
    FAST("1.5s Fast", 1500L),
    STANDARD("2.5s Standard", 2500L),
    CALM("4.0s Calm", 4000L),
    CONTINUOUS("Beacon", 800L),
    OFF("Off", 0L)
}

/**
 * Dedicated customization settings for the Broadcast / Live Stream Floating HUD pill.
 */
@Parcelize
data class StreamHudConfig(
    val laserSweepInterval: LaserSweepInterval = LaserSweepInterval.STANDARD,
    val laserGlowIntensity: Float = 0.85f, // 0.20f to 1.0f
    val enableUplinkHealthAura: Boolean = true,
    val livePulseRhythm: HudAnimation = HudAnimation.PULSE,
    val standbyHud: HudStyleConfig = HudStyleConfig(
        strokeColorHex = 0xFFFF0033L // HyperCrimson
    ),
    val activeHud: HudStyleConfig = HudStyleConfig(
        strokeColorHex = 0xFFFF0033L
    )
) : Parcelable
