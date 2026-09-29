package com.example

import android.app.Application
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ui.theme.DefaultThemeColor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val Context.themeDataStore by preferencesDataStore(name = "theme_prefs")

private object ThemePreferenceKeys {
    val SEED_COLOR = intPreferencesKey("seed_color")
    val PURE_BLACK = booleanPreferencesKey("pure_black")
    val DARK_THEME = booleanPreferencesKey("dark_theme")
    val DYNAMIC_ALBUM_COLOR = booleanPreferencesKey("dynamic_album_color")
    val THEME_MODE = stringPreferencesKey("theme_mode")
}

enum class ThemeMode(val label: String) {
    System("Follow system"),
    Light("Light"),
    Dark("Dark"),
    Amoled("AMOLED"),
}

internal fun resolveThemeMode(saved: String?, legacyDark: Boolean?, legacyPureBlack: Boolean?): ThemeMode =
    saved?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: when {
        legacyPureBlack == true -> ThemeMode.Amoled
        legacyDark == false -> ThemeMode.Light
        else -> ThemeMode.Dark
    }

fun ThemeMode.isDark(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.System -> systemDark
    ThemeMode.Light -> false
    ThemeMode.Dark, ThemeMode.Amoled -> true
}

internal fun themeSeedColor(savedSeed: Color, albumAccent: Color?, fromAlbum: Boolean): Color =
    if (fromAlbum) albumAccent ?: savedSeed else savedSeed

/**
 * The user's persisted theme choices. [seedColor] is the single colour the entire Material 3
 * palette is generated from (see `ui/theme/Theme.kt`). Album-art colour temporarily supplies
 * another seed while a track with artwork is playing; the saved colour remains available.
 */
data class ThemeState(
    val seedColor: Color = DefaultThemeColor,
    val mode: ThemeMode = ThemeMode.Dark,
    /** Re-seed the palette from the current track's album art while something is playing. */
    val dynamicAlbumColor: Boolean = false,
    /** False only for the single frame before DataStore's first real read completes - lets the
     * root composable hold a blank screen for that one frame instead of briefly painting the
     * *real* default seed colour (which looks identical to "not loaded yet" and was getting
     * mistaken for it - see [MuseFlowApp]'s doc comment) and then recomposing into whatever the
     * user actually chose. */
    val isLoaded: Boolean = false,
) {
    val isUsingDefaultSeed: Boolean get() = seedColor == DefaultThemeColor
}

private class ThemeRepository(private val context: Context) {
    val themeState: Flow<ThemeState> = context.themeDataStore.data.map { prefs ->
        ThemeState(
            seedColor = prefs[ThemePreferenceKeys.SEED_COLOR]?.let { Color(it) } ?: DefaultThemeColor,
            mode = resolveThemeMode(prefs[ThemePreferenceKeys.THEME_MODE],
                prefs[ThemePreferenceKeys.DARK_THEME], prefs[ThemePreferenceKeys.PURE_BLACK]),
            dynamicAlbumColor = prefs[ThemePreferenceKeys.DYNAMIC_ALBUM_COLOR] ?: false,
            isLoaded = true,
        )
    }

    suspend fun setSeedColor(color: Color) {
        context.themeDataStore.edit { it[ThemePreferenceKeys.SEED_COLOR] = color.toArgbInt() }
    }

    suspend fun setMode(mode: ThemeMode) {
        context.themeDataStore.edit { it[ThemePreferenceKeys.THEME_MODE] = mode.name }
    }

    suspend fun setDynamicAlbumColor(enabled: Boolean) {
        context.themeDataStore.edit { it[ThemePreferenceKeys.DYNAMIC_ALBUM_COLOR] = enabled }
    }
}

/** `Color.toArgb()` lives in the UI layer; this keeps the repository free of that import. */
private fun Color.toArgbInt(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt()
)

/**
 * Single source of truth for theming. Scoped to the hosting Activity, so every screen that calls
 * `viewModel()` shares one instance and recomposes together when the user changes an option.
 * Persisted via DataStore so choices survive process death.
 */
class ThemeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ThemeRepository(application)

    val themeState: StateFlow<ThemeState> = repository.themeState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ThemeState()
    )

    fun setSeedColor(color: Color) = viewModelScope.launch { repository.setSeedColor(color) }
    fun setMode(mode: ThemeMode) = viewModelScope.launch { repository.setMode(mode) }
    fun setDynamicAlbumColor(enabled: Boolean) =
        viewModelScope.launch { repository.setDynamicAlbumColor(enabled) }

    fun resetToDefaultSeed() = viewModelScope.launch { repository.setSeedColor(DefaultThemeColor) }
}
