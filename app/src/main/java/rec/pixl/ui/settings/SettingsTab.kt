package rec.pixl.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.ui.graphics.vector.ImageVector

enum class SettingsTab(
    val title: String,
    val icon: ImageVector
) {
    VIDEO("VIDEO", Icons.Default.Videocam),
    AUDIO("AUDIO", Icons.Default.Audiotrack),
    CONTROLS("CONTROLS", Icons.Default.Gesture),
    STORAGE("STORAGE", Icons.Default.SdCard)
}
