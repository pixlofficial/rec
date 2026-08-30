package dev.pixl.recorder.ui.navigation

import androidx.annotation.DrawableRes
import dev.pixl.recorder.R

enum class NavigationTab(
    val label: String,
    @DrawableRes val iconRes: Int
) {
    DASHBOARD("DASH", R.drawable.ic_pixel_home),
    VAULT("VAULT", R.drawable.ic_pixel_vault),
    SETTINGS("CONFIG", R.drawable.ic_pixel_settings),
    MORE("SYSTEM", R.drawable.ic_pixel_system)
}
