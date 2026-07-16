package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = MusePrimary,
    secondary = MuseSecondary,
    tertiary = MuseTertiary,
    background = MuseBackground,
    surface = MuseSurface,
    onBackground = MuseOnBackground,
    onSurface = MuseOnSurface,
    surfaceVariant = MuseSurfaceVariant,
    onSurfaceVariant = MuseOnSurfaceVariant,
    primaryContainer = MusePrimaryContainer,
    onPrimaryContainer = MuseOnPrimaryContainer
)

private val LightColorScheme = lightColorScheme(
    primary = MusePrimary,
    secondary = MuseSecondary,
    tertiary = MuseTertiary,
    background = MuseBackground, // Force dark background for a "dark-theme-first" experience
    surface = MuseSurface,
    onBackground = MuseOnBackground,
    onSurface = MuseOnSurface,
    surfaceVariant = MuseSurfaceVariant,
    onSurfaceVariant = MuseOnSurfaceVariant,
    primaryContainer = MusePrimaryContainer,
    onPrimaryContainer = MuseOnPrimaryContainer
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force dark theme by default for dark-theme-first design
    dynamicColor: Boolean = false, // Set to false to preserve the customized MuseFlow visual identity
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
