package pixl.rec.ui.navigation

import androidx.annotation.DrawableRes
import pixl.rec.R

enum class NavigationTab(
    val label: String,
    @DrawableRes val iconRes: Int
) {
    DASHBOARD("DASH", R.drawable.ic_pixel_dashboard),
    VAULT("VAULT", R.drawable.ic_pixel_vault),
    SETTINGS("CONFIG", R.drawable.ic_pixel_settings),
    MORE("SYSTEM", R.drawable.ic_pixel_system)
}
