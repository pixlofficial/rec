package dev.pixl.recorder.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.pixl.recorder.R

// Display Font for Headings, Brand & Primary Action Buttons
val LexendTera = FontFamily(
    Font(R.font.lexend_tera, FontWeight.Normal),
    Font(R.font.lexend_tera, FontWeight.Medium),
    Font(R.font.lexend_tera, FontWeight.SemiBold),
    Font(R.font.lexend_tera, FontWeight.Bold),
    Font(R.font.lexend_tera, FontWeight.Black)
)

// Cyberpunk Dot-Matrix HUD Font for Timers, Telemetry, Settings & Body Text
val Handjet = FontFamily(
    Font(R.font.handjet, FontWeight.Normal),
    Font(R.font.handjet, FontWeight.Medium),
    Font(R.font.handjet, FontWeight.SemiBold),
    Font(R.font.handjet, FontWeight.Bold),
    Font(R.font.handjet, FontWeight.Black)
)

val BrutalistTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Handjet,
        fontWeight = FontWeight.Bold,
        fontSize = 42.sp,
        lineHeight = 44.sp,
        letterSpacing = 1.sp,
        color = TextPrimary
    ),
    displayMedium = TextStyle(
        fontFamily = LexendTera,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
        color = TextPrimary
    ),
    headlineLarge = TextStyle(
        fontFamily = LexendTera,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.5.sp,
        color = TextPrimary
    ),
    headlineMedium = TextStyle(
        fontFamily = LexendTera,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.5.sp,
        color = TextPrimary
    ),
    titleLarge = TextStyle(
        fontFamily = LexendTera,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.5.sp,
        color = TextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = Handjet,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.5.sp,
        color = TextPrimary
    ),
    bodyLarge = TextStyle(
        fontFamily = Handjet,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.5.sp,
        color = TextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = Handjet,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.5.sp,
        color = TextSecondary
    ),
    labelLarge = TextStyle(
        fontFamily = Handjet,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 17.sp,
        letterSpacing = 1.sp,
        color = TextPrimary
    ),
    labelMedium = TextStyle(
        fontFamily = Handjet,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
        color = TextSecondary
    ),
    labelSmall = TextStyle(
        fontFamily = Handjet,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp,
        color = TextMuted
    )
)
