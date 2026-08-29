package dev.pixl.recorder.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = CyberYellow,
    onPrimary = TextInverse,
    primaryContainer = SurfaceElevated,
    onPrimaryContainer = TextPrimary,
    secondary = ToxicLime,
    onSecondary = TextInverse,
    secondaryContainer = SurfaceRaised,
    onSecondaryContainer = ToxicLime,
    tertiary = HyperCyan,
    onTertiary = TextInverse,
    error = HyperCrimson,
    onError = TextPrimary,
    background = ObsidianCanvas,
    onBackground = TextPrimary,
    surface = BrutalistSurface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = BorderStark
)

@Composable
fun RECTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = ObsidianCanvas.toArgb()
                window.navigationBarColor = ObsidianCanvas.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = BrutalistTypography,
        content = content
    )
}
