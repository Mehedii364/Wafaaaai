package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val WafaDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = Color(0xFF001F29),
    primaryContainer = Color(0xFF083344),
    onPrimaryContainer = Color(0xFFA5F3FC),
    secondary = ElectricBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF172554),
    onSecondaryContainer = Color(0xFFBFDBFE),
    tertiary = VioletPulse,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF2E1065),
    onTertiaryContainer = Color(0xFFDDD6FE),
    background = VoidObsidian,
    onBackground = TextPrimaryDark,
    surface = DeepNavySurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ElevatedNavy,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = VoidObsidian,
    surfaceContainerLow = DeepNavySurface,
    surfaceContainer = ElevatedNavy,
    surfaceContainerHigh = GlassNavy,
    outline = OutlineDark,
    outlineVariant = OutlineCyan,
    error = CrimsonAlert,
    onError = Color.White
)

private val WafaLightColorScheme = lightColorScheme(
    primary = DeepCyanLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0C4A6E),
    secondary = DeepBlueLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDBEAFE),
    onSecondaryContainer = Color(0xFF1E3A8A),
    tertiary = DeepVioletLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEDE9FE),
    onTertiaryContainer = Color(0xFF4C1D95),
    background = DaylightBackground,
    onBackground = TextPrimaryLight,
    surface = DaylightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = DaylightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = OutlineLight,
    error = CrimsonAlert,
    onError = Color.White
)

val WafaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun WafaAiTheme(
    themeMode: String = "dark",
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        "light" -> false
        "system" -> isSystemInDarkTheme()
        else -> true
    }
    val colorScheme = if (useDark) WafaDarkColorScheme else WafaLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = WafaTypography,
        shapes = WafaShapes,
        content = content
    )
}
