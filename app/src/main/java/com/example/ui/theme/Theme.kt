package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = GitBluePrimary,
    onPrimary = Color(0xFF041326),
    primaryContainer = GitBlueContainer,
    onPrimaryContainer = Color.White,
    secondary = GitGreenSecondary,
    onSecondary = Color(0xFF04210B),
    secondaryContainer = GitGreenContainer,
    onSecondaryContainer = Color.White,
    tertiary = GitVioletTertiary,
    onTertiary = Color(0xFF1A0933),
    tertiaryContainer = GitVioletContainer,
    onTertiaryContainer = Color(0xFFEDE2FF),
    background = GitCanvasDark,
    onBackground = GitTextPrimaryDark,
    surface = GitSurfaceDark,
    onSurface = GitTextPrimaryDark,
    surfaceVariant = GitSurfaceElevatedDark,
    onSurfaceVariant = GitTextSecondaryDark,
    outline = GitBorderDark,
    error = GitErrorRed
)

private val LightColorScheme = lightColorScheme(
    primary = GitBluePrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDF4FF),
    onPrimaryContainer = Color(0xFF052D61),
    secondary = GitGreenSecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDAFBE1),
    onSecondaryContainer = Color(0xFF052E14),
    tertiary = GitVioletTertiaryLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFBEFFF),
    onTertiaryContainer = Color(0xFF2E0B59),
    background = GitCanvasLight,
    onBackground = GitTextPrimaryLight,
    surface = GitSurfaceLight,
    onSurface = GitTextPrimaryLight,
    surfaceVariant = GitSurfaceElevatedLight,
    onSurfaceVariant = GitTextSecondaryLight,
    outline = Color(0xFFD0D7DE),
    error = Color(0xFFCF222E)
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun GitPulseTheme(
    darkTheme: Boolean = true, // Default to developer dark aesthetic
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
