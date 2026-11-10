package com.dahl404.penny937.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dahl404.penny937.R

val Inter = FontFamily(
    Font(R.font.inter_400, FontWeight.Normal),
    Font(R.font.inter_500, FontWeight.Medium),
    Font(R.font.inter_600, FontWeight.SemiBold),
    Font(R.font.inter_700, FontWeight.Bold)
)

@Immutable
data class PennyPalette(
    val isDark: Boolean,
    val background: Color,
    val backgroundRaised: Color,
    val surface: Color,
    val surfaceSunken: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val hairline: Color,
    val hairlineStrong: Color,
    val accent: Color,
    val accentSoft: Color,
    val danger: Color,
    val shadow: Color,
    val paperShadow: Color
)

private val LightPalette = PennyPalette(
    isDark = false,
    background = Color(0xFFF2F2F7),
    backgroundRaised = Color(0xFFF7F7FA),
    surface = Color(0xFFFFFFFF),
    surfaceSunken = Color(0xFFEAEAEF),
    textPrimary = Color(0xFF1D1D1F),
    textSecondary = Color(0xFF6E6E73),
    textTertiary = Color(0xFFA1A1A6),
    hairline = Color(0x14000000),
    hairlineStrong = Color(0x22000000),
    accent = Color(0xFF0071E3),
    accentSoft = Color(0xFFE7F1FD),
    danger = Color(0xFFFF3B30),
    shadow = Color(0x1A000000),
    paperShadow = Color(0x26000000)
)

private val DarkPalette = PennyPalette(
    isDark = true,
    background = Color(0xFF000000),
    backgroundRaised = Color(0xFF0C0C0E),
    surface = Color(0xFF1C1C1E),
    surfaceSunken = Color(0xFF141416),
    textPrimary = Color(0xFFF5F5F7),
    textSecondary = Color(0xFF9A9AA0),
    textTertiary = Color(0xFF6E6E73),
    hairline = Color(0x22FFFFFF),
    hairlineStrong = Color(0x33FFFFFF),
    accent = Color(0xFF0A84FF),
    accentSoft = Color(0xFF102A45),
    danger = Color(0xFFFF453A),
    shadow = Color(0x66000000),
    paperShadow = Color(0x99000000)
)

@Immutable
data class PennyType(
    val hero: TextStyle,
    val title: TextStyle,
    val headline: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val mono: TextStyle
)

val PennyTypography = PennyType(
    hero = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Bold,
        fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-1.1).sp
    ),
    title = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp, lineHeight = 26.sp, letterSpacing = (-0.5).sp
    ),
    headline = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 21.sp, letterSpacing = (-0.2).sp
    ),
    body = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = (-0.1).sp
    ),
    bodyStrong = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Medium,
        fontSize = 15.sp, lineHeight = 21.sp, letterSpacing = (-0.1).sp
    ),
    label = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Medium,
        fontSize = 13.sp, lineHeight = 17.sp, letterSpacing = 0.sp
    ),
    caption = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.2.sp
    ),
    mono = TextStyle(
        fontFamily = Inter, fontWeight = FontWeight.Medium,
        fontSize = 13.sp, lineHeight = 17.sp, letterSpacing = 0.2.sp
    )
)

val LocalPenny = staticCompositionLocalOf { LightPalette }
val LocalPennyType = staticCompositionLocalOf { PennyTypography }

object PennyDimens {
    val screenPadding = 16.dp
    val cardRadius = 24.dp
    val sheetRadius = 30.dp
    val hairline = 0.6.dp
}

@Composable
fun PennyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            error = palette.danger
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            error = palette.danger
        )
    }
    CompositionLocalProvider(
        LocalPenny provides palette,
        LocalPennyType provides PennyTypography
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
