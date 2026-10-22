package com.dianming.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val BrandBlue = Color(0xFF3B6FF5)
val BrandBlueDark = Color(0xFF2450C8)
val BrandGreen = Color(0xFF17B26A)
val BrandRed = Color(0xFFF04438)
val BrandOrange = Color(0xFFF79009)
val BrandAmber = Color(0xFF2E90FA)

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1EAFF),
    onPrimaryContainer = Color(0xFF0B2E7A),
    secondary = BrandGreen,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8F5E6),
    onSecondaryContainer = Color(0xFF05391F),
    tertiary = BrandOrange,
    onTertiary = Color.White,
    error = BrandRed,
    onError = Color.White,
    errorContainer = Color(0xFFFFE0DC),
    onErrorContainer = Color(0xFF7A1B12),
    background = Color(0xFFF7F8FC),
    onBackground = Color(0xFF16181D),
    surface = Color.White,
    onSurface = Color(0xFF16181D),
    surfaceVariant = Color(0xFFEDF0F7),
    onSurfaceVariant = Color(0xFF454A55),
    outline = Color(0xFFB9C0CF)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DBBFF),
    onPrimary = Color(0xFF002B6B),
    primaryContainer = Color(0xFF1B3E9C),
    onPrimaryContainer = Color(0xFFD9E3FF),
    secondary = Color(0xFF6FD9A2),
    onSecondary = Color(0xFF00391F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE3E5EB),
    surface = Color(0xFF1A1D24),
    onSurface = Color(0xFFE3E5EB),
    surfaceVariant = Color(0xFF2A2E37),
    onSurfaceVariant = Color(0xFFC3C7D2),
    outline = Color(0xFF8D93A1)
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun DianMingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
