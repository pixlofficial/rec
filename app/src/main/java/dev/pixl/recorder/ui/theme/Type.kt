package dev.pixl.recorder.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.pixl.recorder.R

// Bitcount Prop Single: Proportional Dot-Matrix Marquee for Headings, Brand, Badges & Buttons
val BitcountPropSingle = FontFamily(
    Font(R.font.bitcount_prop_single, FontWeight.Normal),
    Font(R.font.bitcount_prop_single, FontWeight.Medium),
    Font(R.font.bitcount_prop_single, FontWeight.SemiBold),
    Font(R.font.bitcount_prop_single, FontWeight.Bold),
    Font(R.font.bitcount_prop_single, FontWeight.Black)
)

val BitcountSingle = FontFamily(
    Font(R.font.bitcount_single, FontWeight.Normal),
    Font(R.font.bitcount_single, FontWeight.Medium),
    Font(R.font.bitcount_single, FontWeight.SemiBold),
    Font(R.font.bitcount_single, FontWeight.Bold),
    Font(R.font.bitcount_single, FontWeight.Black)
)

// Space Mono: Crisp Developer / Technical Font for Settings, Switches & Descriptions
val SpaceMono = FontFamily(
    Font(R.font.space_mono_regular, FontWeight.Normal),
    Font(R.font.space_mono_bold, FontWeight.Bold)
)

val Handjet = FontFamily(
    Font(R.font.handjet, FontWeight.Normal),
    Font(R.font.handjet, FontWeight.Medium),
    Font(R.font.handjet, FontWeight.SemiBold),
    Font(R.font.handjet, FontWeight.Bold),
    Font(R.font.handjet, FontWeight.Black)
)

val LexendTera = FontFamily(
    Font(R.font.lexend_tera, FontWeight.Normal),
    Font(R.font.lexend_tera, FontWeight.Medium),
    Font(R.font.lexend_tera, FontWeight.SemiBold),
    Font(R.font.lexend_tera, FontWeight.Bold),
    Font(R.font.lexend_tera, FontWeight.Black)
)

val BrutalistTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = BitcountPropSingle,
        fontWeight = FontWeight.Bold,
        fontSize = 42.sp,
        lineHeight = 44.sp,
        letterSpacing = 1.sp,
        color = TextPrimary
    ),
    displayMedium = TextStyle(
        fontFamily = BitcountPropSingle,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.5.sp,
        color = TextPrimary
    ),
    headlineLarge = TextStyle(
        fontFamily = BitcountPropSingle,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.5.sp,
        color = TextPrimary
    ),
    headlineMedium = TextStyle(
        fontFamily = BitcountPropSingle,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.5.sp,
        color = TextPrimary
    ),
    titleLarge = TextStyle(
        fontFamily = BitcountPropSingle,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.5.sp,
        color = TextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.sp,
        color = TextPrimary
    ),
    bodyLarge = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.sp,
        color = TextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.sp,
        color = TextSecondary
    ),
    labelLarge = TextStyle(
        fontFamily = BitcountPropSingle,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.5.sp,
        color = TextPrimary
    ),
    labelMedium = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.sp,
        color = TextSecondary
    ),
    labelSmall = TextStyle(
        fontFamily = SpaceMono,
        fontWeight = FontWeight.Normal,
        fontSize = 9.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.sp,
        color = TextMuted
    )
)
