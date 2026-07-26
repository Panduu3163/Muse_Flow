package com.example.ui.theme

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import com.materialkolor.score.Score

/*
 * Seed-based theming, adapted from Echo-Music (GPL-3.0)
 * https://github.com/EchoMusicApp/Echo-Music - see `ui/theme/Theme.kt` there.
 * MuseFlow is GPL-3.0 as well, so this reuse keeps the same licence.
 */

/**
 * MuseFlow's default seed - "Aurora Violet". Every Material 3 role (all the surface tiers,
 * containers, outlines and error colours) is *generated* from this one colour rather than
 * hand-written, so a user-picked accent always produces a complete, contrast-correct palette.
 */
val DefaultThemeColor = Color(0xFF7C5CFF)

/**
 * The app-wide Material 3 theme.
 *
 * @param darkTheme dark colours (MuseFlow is dark-first, so this defaults to true).
 * @param pureBlack collapses surface/background to true black for AMOLED screens.
 * @param themeColor the seed colour the whole scheme is generated from. When it's left at
 *   [DefaultThemeColor] on Android 12+, the system wallpaper palette is used instead, so the app
 *   matches the device theme unless the user has deliberately picked their own accent.
 */
@Composable
fun MuseFlowTheme(
    darkTheme: Boolean = true,
    pureBlack: Boolean = false,
    themeColor: Color = DefaultThemeColor,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current

    val useSystemDynamicColor =
        themeColor == DefaultThemeColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val baseColorScheme = if (useSystemDynamicColor) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        rememberDynamicColorScheme(
            seedColor = themeColor,
            isDark = darkTheme,
            specVersion = ColorSpec.SpecVersion.SPEC_2025,
            // Fidelity, not TonalSpot: TonalSpot normalizes every seed to a fixed, moderate chroma
            // regardless of how saturated the input colour actually was, so the custom colour
            // picker's saturation/value square could move its own selection ring correctly and
            // still produce a visually identical theme - hue was the only thing that ever came
            // through. Fidelity keeps the generated palette's chroma tied to the seed's own, so a
            // more saturated pick actually reads as more vivid and a desaturated one actually reads
            // as more muted/grey, matching what the square is for.
            style = PaletteStyle.Fidelity
        )
    }

    val colorScheme = remember(baseColorScheme, pureBlack, darkTheme) {
        if (darkTheme && pureBlack) baseColorScheme.pureBlack(true) else baseColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

/** Collapses the two "paper" roles to true black, so an AMOLED panel can switch those pixels off
 * entirely. Deliberately leaves the container tiers alone - flattening those too would erase the
 * elevation cues that separate cards, sheets and bars from the background. */
fun ColorScheme.pureBlack(apply: Boolean): ColorScheme =
    if (apply) copy(surface = Color.Black, background = Color.Black) else this

/** The single most representative colour of a piece of album art, scored the way Material's own
 * quantizer does, for use as a theme seed. */
fun Bitmap.extractThemeColor(): Color {
    val colorsToPopulation = Palette.from(this)
        .maximumColorCount(8)
        .generate()
        .swatches
        .associate { it.rgb to it.population }
    val rankedColors = Score.score(colorsToPopulation)
    return Color(rankedColors.first())
}

/** Two ordered colours from album art, bright-to-dark, for a Now Playing gradient backdrop.
 * Falls back to a neutral dark pair when the art is too flat to score two distinct colours. */
fun Bitmap.extractGradientColors(): List<Color> {
    val extractedColors = Palette.from(this)
        .maximumColorCount(64)
        .generate()
        .swatches
        .associate { it.rgb to it.population }

    val orderedColors = Score.score(extractedColors, 2, 0xFF4285F4.toInt(), true)
        .sortedByDescending { Color(it).luminance() }

    return if (orderedColors.size >= 2) {
        listOf(Color(orderedColors[0]), Color(orderedColors[1]))
    } else {
        listOf(Color(0xFF595959), Color(0xFF0D0D0D))
    }
}

/** Lets a user-picked accent [Color] survive process death in `rememberSaveable`. */
val ColorSaver: Saver<Color, Int> = object : Saver<Color, Int> {
    override fun restore(value: Int): Color = Color(value)
    override fun SaverScope.save(value: Color): Int = value.toArgb()
}
