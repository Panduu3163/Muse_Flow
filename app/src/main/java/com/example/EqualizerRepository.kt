package com.example

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.equalizerDataStore by preferencesDataStore(name = "equalizer_prefs")

private object EqualizerKeys {
    val ENABLED = booleanPreferencesKey("eq_enabled")
    // Comma-joined millibel levels, one per band, in band-index order - band count/valid range
    // are a device/DSP property (read from a live android.media.audiofx.Equalizer, see
    // EqualizerSettingsScreen), not something this app defines, so a flat delimited string is
    // simpler than one fixed-shape preference key per band.
    val BAND_LEVELS = stringPreferencesKey("eq_band_levels")
}

/** Persisted equalizer state. [bandLevelsMillibel] is empty until the user changes any band from
 * its device default (flat/no attenuation). */
data class EqualizerSettings(
    val enabled: Boolean = false,
    val bandLevelsMillibel: List<Int> = emptyList()
)

/**
 * DataStore-backed equalizer settings, readable/writable from both the settings UI
 * ([EqualizerSettingsScreen]) and [PlaybackService] (which applies [settings] to the actual,
 * live [android.media.audiofx.Equalizer] attached to whatever's currently playing) - same
 * decoupled, Flow-driven shape the rest of this app's settings already use (see
 * [AppSettingsViewModel]), so the service doesn't need a direct reference to the settings screen
 * or vice versa.
 */
class EqualizerRepository private constructor(context: Context) {
    private val appContext = context.applicationContext

    val settings: Flow<EqualizerSettings> = appContext.equalizerDataStore.data.map { prefs ->
        EqualizerSettings(
            enabled = prefs[EqualizerKeys.ENABLED] ?: false,
            bandLevelsMillibel = prefs[EqualizerKeys.BAND_LEVELS]
                ?.split(",")
                ?.mapNotNull { it.toIntOrNull() }
                ?: emptyList()
        )
    }

    suspend fun setEnabled(enabled: Boolean) {
        appContext.equalizerDataStore.edit { it[EqualizerKeys.ENABLED] = enabled }
    }

    /** Sets [bandIndex]'s level, growing the stored band list (padded with 0/flat for any
     * lower-indexed band never touched before) as needed. */
    suspend fun setBandLevel(bandIndex: Int, levelMillibel: Int) {
        appContext.equalizerDataStore.edit { prefs ->
            val current = prefs[EqualizerKeys.BAND_LEVELS]
                ?.split(",")
                ?.mapNotNull { it.toIntOrNull() }
                ?.toMutableList()
                ?: mutableListOf()
            while (current.size <= bandIndex) current.add(0)
            current[bandIndex] = levelMillibel
            prefs[EqualizerKeys.BAND_LEVELS] = current.joinToString(",")
        }
    }

    suspend fun resetToFlat() {
        appContext.equalizerDataStore.edit { it.remove(EqualizerKeys.BAND_LEVELS) }
    }

    companion object {
        @Volatile private var instance: EqualizerRepository? = null

        fun getInstance(context: Context): EqualizerRepository =
            instance ?: synchronized(this) {
                instance ?: EqualizerRepository(context.applicationContext).also { instance = it }
            }
    }
}
