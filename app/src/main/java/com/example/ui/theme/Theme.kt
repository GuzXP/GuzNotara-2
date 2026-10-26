package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = AcousticAmber,
    onPrimary = ObsidianBg,
    primaryContainer = AcousticAmberDark,
    onPrimaryContainer = TextPrimaryDark,
    secondary = ElectricSage,
    onSecondary = ObsidianBg,
    secondaryContainer = ElectricSageDark,
    onSecondaryContainer = TextPrimaryDark,
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = TextSecondaryDark,
    outline = ObsidianBorder,
    error = CrimsonAlert,
    onError = TextPrimaryDark
)

private val LightColorScheme = lightColorScheme(
    primary = AcousticAmberDark,
    onPrimary = LightStudioSurface,
    primaryContainer = AcousticAmber,
    onPrimaryContainer = ObsidianBg,
    secondary = ElectricSageDark,
    onSecondary = LightStudioSurface,
    secondaryContainer = ElectricSage,
    onSecondaryContainer = ObsidianBg,
    background = LightStudioBg,
    onBackground = TextPrimaryLight,
    surface = LightStudioSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightStudioSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = LightStudioBorder,
    error = CrimsonAlert,
    onError = LightStudioSurface
)

@Composable
fun GuzNotaraTheme(
    darkTheme: Boolean = true, // Default to deep editorial obsidian theme
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Backwards compatibility alias
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    GuzNotaraTheme(darkTheme = darkTheme, content = content)
}
