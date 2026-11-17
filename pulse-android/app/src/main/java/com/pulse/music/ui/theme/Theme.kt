package com.pulse.music.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = PulseRed,
    secondary = PulseRed,
    tertiary = PulseRed,
    background = DarkBackground,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onPrimary = TextPrimary,
    onSecondary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed
)

private val LightColorScheme = lightColorScheme(
    primary = PulseRed,
    secondary = PulseRed,
    tertiary = PulseRed,
    background = Color(0xFFF8F9FA), // Off-white
    surface = Color.White,
    surfaceVariant = Color(0xFFE9ECEF),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF212529), // Dark text
    onSurface = Color(0xFF212529),
    onSurfaceVariant = Color(0xFF495057),
    error = ErrorRed
)

@Composable
fun PulseMusicTheme(
    isDarkTheme: Boolean = true, // Set to true for Dark Theme (default)
    content: @Composable () -> Unit
) {
    val colorScheme = if (isDarkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDarkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !isDarkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
