package com.example

import android.app.Application
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
}

/**
 * The user's persisted theme choices. [seedColor] is the single colour the entire Material 3
 * palette is generated from (see `ui/theme/Theme.kt`) - leaving it at [DefaultThemeColor] on
 * Android 12+ hands theming over to the system wallpaper palette instead.
 */
data class ThemeState(
    val seedColor: Color = DefaultThemeColor,
    val pureBlack: Boolean = false,
    val darkTheme: Boolean = true,
    /** Re-seed the palette from the current track's album art while something is playing. */
    val dynamicAlbumColor: Boolean = false,
) {
    val isUsingDefaultSeed: Boolean get() = seedColor == DefaultThemeColor
}

private class ThemeRepository(private val context: Context) {
    val themeState: Flow<ThemeState> = context.themeDataStore.data.map { prefs ->
        ThemeState(
            seedColor = prefs[ThemePreferenceKeys.SEED_COLOR]?.let { Color(it) } ?: DefaultThemeColor,
            pureBlack = prefs[ThemePreferenceKeys.PURE_BLACK] ?: false,
            darkTheme = prefs[ThemePreferenceKeys.DARK_THEME] ?: true,
            dynamicAlbumColor = prefs[ThemePreferenceKeys.DYNAMIC_ALBUM_COLOR] ?: false,
        )
    }

    suspend fun setSeedColor(color: Color) {
        context.themeDataStore.edit { it[ThemePreferenceKeys.SEED_COLOR] = color.toArgbInt() }
    }

    suspend fun setPureBlack(enabled: Boolean) {
        context.themeDataStore.edit { it[ThemePreferenceKeys.PURE_BLACK] = enabled }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.themeDataStore.edit { it[ThemePreferenceKeys.DARK_THEME] = enabled }
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
    fun setPureBlack(enabled: Boolean) = viewModelScope.launch { repository.setPureBlack(enabled) }
    fun setDarkTheme(enabled: Boolean) = viewModelScope.launch { repository.setDarkTheme(enabled) }
    fun setDynamicAlbumColor(enabled: Boolean) =
        viewModelScope.launch { repository.setDynamicAlbumColor(enabled) }

    fun resetToDefaultSeed() = viewModelScope.launch { repository.setSeedColor(DefaultThemeColor) }
}
