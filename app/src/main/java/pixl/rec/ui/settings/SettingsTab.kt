package pixl.rec.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Top-level navigation pillars within the OBS Studio-style Settings deck.
 */
enum class SettingsTab(
    val title: String,
    val icon: ImageVector
) {
    GENERAL("GENERAL", Icons.Default.Settings),
    VIDEO("VIDEO", Icons.Default.Videocam),
    AUDIO("AUDIO", Icons.Default.Audiotrack),
    CONTROLS("CONTROLS", Icons.Default.Gesture),
    STREAM("STREAM", Icons.Default.CellTower)
}
