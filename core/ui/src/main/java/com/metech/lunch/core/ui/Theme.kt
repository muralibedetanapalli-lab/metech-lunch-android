package com.metech.lunch.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Metech Lunch, "warm & appetising": deep leaf green and saffron on a warm cream canvas.
 * Headings use the system serif for a friendly, menu-like feel; body text stays sans-serif.
 */

object Palette {
    val Green = Color(0xFF1F5F3F)
    val GreenDeep = Color(0xFF123D28)
    val GreenSoft = Color(0xFFDCEEE2)
    val Saffron = Color(0xFFF2994A)
    val SaffronDeep = Color(0xFFD9752B)
    val SaffronSoft = Color(0xFFFDE7D2)
    val Cream = Color(0xFFFFF8EE)
    val CreamDeep = Color(0xFFFBEFDD)
    val Ink = Color(0xFF2A2118)
    val InkMuted = Color(0xFF7A6A58)
    val Line = Color(0xFFEADBC6)
    val Veg = Color(0xFF2E8B57)
    val NonVeg = Color(0xFFB5452F)
    val Danger = Color(0xFFB3261E)
    val DangerSoft = Color(0xFFFCE4E1)
    val Amber = Color(0xFFB26A00)
    val AmberSoft = Color(0xFFFFF0D6)
}

private val LightColors = lightColorScheme(
    primary = Palette.Green,
    onPrimary = Color.White,
    primaryContainer = Palette.GreenSoft,
    onPrimaryContainer = Palette.GreenDeep,
    secondary = Palette.Saffron,
    onSecondary = Color.White,
    secondaryContainer = Palette.SaffronSoft,
    onSecondaryContainer = Color(0xFF5A2E06),
    tertiary = Palette.SaffronDeep,
    background = Palette.Cream,
    onBackground = Palette.Ink,
    surface = Color.White,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.CreamDeep,
    onSurfaceVariant = Palette.InkMuted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFCF6),
    surfaceContainer = Palette.CreamDeep,
    surfaceContainerHigh = Color(0xFFF7E8D3),
    surfaceContainerHighest = Color(0xFFF2E1C9),
    outline = Color(0xFFD8C5AC),
    outlineVariant = Palette.Line,
    error = Palette.Danger,
    errorContainer = Palette.DangerSoft,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD0A9),
    onPrimary = Color(0xFF0B2E1C),
    primaryContainer = Color(0xFF1F4A33),
    onPrimaryContainer = Color(0xFFCDEBD8),
    secondary = Color(0xFFF6B27A),
    onSecondary = Color(0xFF4A2504),
    secondaryContainer = Color(0xFF5C3414),
    onSecondaryContainer = Color(0xFFFFDCC2),
    tertiary = Color(0xFFF6B27A),
    background = Color(0xFF1B1712),
    onBackground = Color(0xFFF2E7DA),
    surface = Color(0xFF241F19),
    onSurface = Color(0xFFF2E7DA),
    surfaceVariant = Color(0xFF2F2820),
    onSurfaceVariant = Color(0xFFCDBDA9),
    surfaceContainerLowest = Color(0xFF16120E),
    surfaceContainerLow = Color(0xFF211C16),
    surfaceContainer = Color(0xFF2A241D),
    surfaceContainerHigh = Color(0xFF342D25),
    surfaceContainerHighest = Color(0xFF3F372E),
    outline = Color(0xFF6E6152),
    outlineVariant = Color(0xFF453B30),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF5C1A14),
)

/** Colours Material 3 has no slot for. */
@Immutable
data class LunchExtraColors(
    val veg: Color,
    val nonVeg: Color,
    val success: Color,
    val successSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    val saffron: Color,
    val saffronSoft: Color,
    val heroStart: Color,
    val heroEnd: Color,
)

private val LightExtras = LunchExtraColors(
    veg = Palette.Veg, nonVeg = Palette.NonVeg,
    success = Palette.Green, successSoft = Palette.GreenSoft,
    warning = Palette.Amber, warningSoft = Palette.AmberSoft,
    danger = Palette.Danger, dangerSoft = Palette.DangerSoft,
    saffron = Palette.Saffron, saffronSoft = Palette.SaffronSoft,
    heroStart = Palette.Green, heroEnd = Palette.GreenDeep,
)

private val DarkExtras = LunchExtraColors(
    veg = Color(0xFF6CCB92), nonVeg = Color(0xFFF08A70),
    success = Color(0xFF8FD0A9), successSoft = Color(0xFF1F4A33),
    warning = Color(0xFFFFC56B), warningSoft = Color(0xFF4A3510),
    danger = Color(0xFFFFB4AB), dangerSoft = Color(0xFF5C1A14),
    saffron = Color(0xFFF6B27A), saffronSoft = Color(0xFF5C3414),
    heroStart = Color(0xFF1F4A33), heroEnd = Color(0xFF0F2A1C),
)

private val LocalExtras = staticCompositionLocalOf { LightExtras }

object LunchTheme {
    val extras: LunchExtraColors
        @Composable @ReadOnlyComposable get() = LocalExtras.current
}

private val Display = FontFamily.Serif
private val Body = FontFamily.SansSerif

private val LunchTypography = Typography(
    displaySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 40.sp),
    headlineLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Body, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Body, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Body, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.3.sp),
)

private val LunchShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun LunchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalExtras provides if (darkTheme) DarkExtras else LightExtras) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = LunchTypography,
            shapes = LunchShapes,
            content = content,
        )
    }
}
