package ru.vkusdetstva.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object BrandColors {
    val Terracotta = Color(0xFFA74731)
    val Amber = Color(0xFF9A5E16)
    val Coral = Color(0xFFB64F43)
    val Cream = Color(0xFFFFF8EA)
    val Paper = Color(0xFFFBF5E9)
    val Ink = Color(0xFF312921)
    val Forest = Color(0xFF365847)
    val Muted = Color(0xFF776E64)
    val DarkBackground = Color(0xFF211D1A)
    val DarkSurface = Color(0xFF302923)
    val DarkInk = Color(0xFFF5EBDD)
    val DarkPrimary = Color(0xFFF0AA91)
    val DarkAccent = Color(0xFFBDD7BF)
}

private val LightScheme = lightColorScheme(
    primary = BrandColors.Terracotta, onPrimary = Color.White,
    primaryContainer = Color(0xFFF5DCD1), onPrimaryContainer = Color(0xFF572514),
    secondary = BrandColors.Forest, onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8E9D9), onSecondaryContainer = Color(0xFF203E2F),
    background = BrandColors.Paper, onBackground = BrandColors.Ink,
    surface = BrandColors.Cream, onSurface = BrandColors.Ink,
    surfaceVariant = Color(0xFFF0E8DC), onSurfaceVariant = BrandColors.Muted,
    outline = Color(0xFF877D72), error = Color(0xFFB3261E), onError = Color.White
)

private val DarkScheme = darkColorScheme(
    primary = BrandColors.DarkPrimary, onPrimary = Color(0xFF512416),
    primaryContainer = Color(0xFF793B28), onPrimaryContainer = Color(0xFFFFDCCE),
    secondary = BrandColors.DarkAccent, onSecondary = Color(0xFF203C2A),
    secondaryContainer = Color(0xFF355542), onSecondaryContainer = Color(0xFFD8E9D9),
    background = BrandColors.DarkBackground, onBackground = BrandColors.DarkInk,
    surface = BrandColors.DarkSurface, onSurface = BrandColors.DarkInk,
    surfaceVariant = Color(0xFF473D35), onSurfaceVariant = Color(0xFFD5C7B8),
    outline = Color(0xFFAA9A8A), error = Color(0xFFFFB4AB), onError = Color(0xFF690005)
)

object BrandDimens {
    val grid = 8.dp
    val pagePadding = 16.dp
    val cardRadius = 12.dp
    val buttonRadius = 8.dp
    val sheetRadius = 24.dp
    val buttonHeight = 48.dp
    val fieldHeight = 56.dp
    val navigationHeight = 64.dp
}

private fun type(size: TextUnit, line: TextUnit, weight: FontWeight, serif: Boolean = false,
                 tracking: TextUnit = 0.sp) = TextStyle(
    fontFamily = if (serif) FontFamily.Serif else FontFamily.SansSerif,
    fontWeight = weight, fontSize = size, lineHeight = line, letterSpacing = tracking
)

val BrandTypography = Typography(
    displayLarge = type(57.sp, 64.sp, FontWeight.Normal, serif = true, tracking = (-0.25).sp),
    displayMedium = type(45.sp, 52.sp, FontWeight.Normal, serif = true),
    displaySmall = type(36.sp, 44.sp, FontWeight.Normal, serif = true),
    headlineLarge = type(32.sp, 40.sp, FontWeight.Normal, serif = true),
    headlineMedium = type(28.sp, 36.sp, FontWeight.Normal, serif = true),
    headlineSmall = type(24.sp, 32.sp, FontWeight.Normal, serif = true),
    titleLarge = type(22.sp, 28.sp, FontWeight.Medium, serif = true),
    titleMedium = type(16.sp, 24.sp, FontWeight.SemiBold, tracking = 0.15.sp),
    titleSmall = type(14.sp, 20.sp, FontWeight.SemiBold, tracking = 0.1.sp),
    bodyLarge = type(16.sp, 24.sp, FontWeight.Normal, tracking = 0.5.sp),
    bodyMedium = type(14.sp, 20.sp, FontWeight.Normal, tracking = 0.25.sp),
    bodySmall = type(12.sp, 16.sp, FontWeight.Normal, tracking = 0.4.sp),
    labelLarge = type(14.sp, 20.sp, FontWeight.Medium, tracking = 0.1.sp),
    labelMedium = type(12.sp, 16.sp, FontWeight.Medium, tracking = 0.5.sp),
    labelSmall = type(11.sp, 16.sp, FontWeight.Medium, tracking = 0.5.sp)
)

@Composable
fun VkusTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = BrandTypography, content = content)
}
