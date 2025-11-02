package com.orotrain.oro.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = AccentCyan,
    onPrimary = Charcoal,
    background = Charcoal,
    onBackground = ColorPalette.onBackgroundDark,
    surface = SurfaceBackdrop,
    onSurface = ColorPalette.onSurfaceDark,
    secondary = AccentCyanDark
)

private val LightColorScheme = lightColorScheme(
    primary = AccentCyan,
    onPrimary = Charcoal,
    background = ColorPalette.backgroundLight,
    onBackground = ColorPalette.onBackgroundLight,
    surface = ColorPalette.surfaceLight,
    onSurface = ColorPalette.onSurfaceLight,
    secondary = AccentCyanDark
)

private object ColorPalette {
    val backgroundLight = Color(0xFFF1F2F5)
    val surfaceLight = Color(0xFFFFFFFF)
    val onSurfaceLight = Color(0xFF1A1F2E)
    val onBackgroundLight = Color(0xFF1A1F2E)
    val onSurfaceDark = Color(0xFFE8EDF5)
    val onBackgroundDark = Color(0xFFE8EDF5)
}

@Composable
fun OroTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorScheme: ColorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
