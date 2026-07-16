package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel

/** How the app paints its background across every screen. */
enum class BackgroundMode { Amoled, Gradient }

/** A selectable preset gradient used when [BackgroundMode.Gradient] is active. */
data class GradientPalette(
    val id: String,
    val label: String,
    val colors: List<Color>
)

object ThemePalettes {
    val PurpleBlue = GradientPalette(
        id = "purple_blue",
        label = "Purple / Blue",
        colors = listOf(Color(0xFF1A0B2E), Color(0xFF2D1B4E), Color(0xFF16213E))
    )
    val TealGreen = GradientPalette(
        id = "teal_green",
        label = "Teal / Green",
        colors = listOf(Color(0xFF032B26), Color(0xFF0B4F44), Color(0xFF14532D))
    )
    val SunsetOrangePink = GradientPalette(
        id = "sunset_pink",
        label = "Sunset Orange / Pink",
        colors = listOf(Color(0xFF3B0764), Color(0xFF7C2D12), Color(0xFFBE185D))
    )
    val DeepRedMaroon = GradientPalette(
        id = "deep_red_maroon",
        label = "Deep Red / Maroon",
        colors = listOf(Color(0xFF1A0000), Color(0xFF4A0404), Color(0xFF6E1423))
    )
    val OceanBlueCyan = GradientPalette(
        id = "ocean_blue_cyan",
        label = "Ocean Blue / Cyan",
        colors = listOf(Color(0xFF021024), Color(0xFF0A2647), Color(0xFF205295))
    )
    val MidnightIndigo = GradientPalette(
        id = "midnight_indigo",
        label = "Midnight Indigo",
        colors = listOf(Color(0xFF0D0221), Color(0xFF190933), Color(0xFF2E1760))
    )

    val All = listOf(PurpleBlue, TealGreen, SunsetOrangePink, DeepRedMaroon, OceanBlueCyan, MidnightIndigo)
    val Default = PurpleBlue
}

/** The user's persisted theme selection, resolved to a concrete [GradientPalette]. */
data class ThemeState(
    val backgroundMode: BackgroundMode = BackgroundMode.Amoled,
    val paletteId: String = ThemePalettes.Default.id
) {
    val palette: GradientPalette
        get() = ThemePalettes.All.find { it.id == paletteId } ?: ThemePalettes.Default
}

/**
 * Wraps screen content with the app-wide themed background (flat AMOLED black, or the
 * selected gradient palette). Reads the shared [ThemeViewModel], which is scoped to the
 * hosting Activity, so every screen using this composable observes the same theme state
 * and recomposes together when the user changes it in Settings.
 */
@Composable
fun ThemedBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val themeViewModel: ThemeViewModel = viewModel()
    val themeState by themeViewModel.themeState.collectAsState()

    val backgroundModifier = when (themeState.backgroundMode) {
        BackgroundMode.Amoled -> Modifier.background(Color.Black)
        BackgroundMode.Gradient -> Modifier.background(Brush.verticalGradient(themeState.palette.colors))
    }

    Box(modifier = modifier.fillMaxSize().then(backgroundModifier)) {
        content()
    }
}
