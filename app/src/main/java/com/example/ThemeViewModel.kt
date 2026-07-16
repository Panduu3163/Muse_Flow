package com.example

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val Context.themeDataStore by preferencesDataStore(name = "theme_prefs")

private object ThemePreferenceKeys {
    val BACKGROUND_MODE = stringPreferencesKey("background_mode")
    val PALETTE_ID = stringPreferencesKey("palette_id")
}

private class ThemeRepository(private val context: Context) {
    val themeState: Flow<ThemeState> = context.themeDataStore.data.map { prefs ->
        val mode = prefs[ThemePreferenceKeys.BACKGROUND_MODE]
            ?.let { saved -> runCatching { BackgroundMode.valueOf(saved) }.getOrNull() }
            ?: BackgroundMode.Amoled
        val paletteId = prefs[ThemePreferenceKeys.PALETTE_ID] ?: ThemePalettes.Default.id
        ThemeState(backgroundMode = mode, paletteId = paletteId)
    }

    suspend fun setBackgroundMode(mode: BackgroundMode) {
        context.themeDataStore.edit { it[ThemePreferenceKeys.BACKGROUND_MODE] = mode.name }
    }

    suspend fun setPalette(paletteId: String) {
        context.themeDataStore.edit { it[ThemePreferenceKeys.PALETTE_ID] = paletteId }
    }
}

/**
 * Single source of truth for the app's background theme. Scoped to the hosting Activity,
 * so every screen that requests this ViewModel (via `viewModel()`) shares the same instance
 * and StateFlow, keeping the whole app in sync without prop-drilling or a CompositionLocal.
 * Selections are persisted to DataStore Preferences so they survive process death/restarts.
 */
class ThemeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ThemeRepository(application)

    val themeState: StateFlow<ThemeState> = repository.themeState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ThemeState()
    )

    fun setBackgroundMode(mode: BackgroundMode) {
        viewModelScope.launch { repository.setBackgroundMode(mode) }
    }

    fun setPalette(paletteId: String) {
        viewModelScope.launch { repository.setPalette(paletteId) }
    }
}
