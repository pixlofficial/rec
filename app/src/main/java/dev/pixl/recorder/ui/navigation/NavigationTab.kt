package dev.pixl.recorder.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class NavigationTab(
    val label: String,
    val icon: ImageVector
) {
    DASHBOARD("DASH", Icons.Default.Home),
    VAULT("VAULT", Icons.Default.Folder),
    SETTINGS("CONFIG", Icons.Default.Settings),
    MORE("SYSTEM", Icons.Default.Info)
}
