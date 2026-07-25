package com.example

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.extractorDataStore by preferencesDataStore(name = "extractor_prefs")

/** Which YouTube Music backend the app searches and browses through. */
enum class ExtractorBackend(val label: String, val description: String) {
    /** The ported InnerTube client (`:innertube`) - far broader endpoint coverage. */
    INNERTUBE(
        "InnerTube",
        "Ported YouTube Music API client. Broader coverage; the new default."
    ),

    /** MuseFlow's original hand-rolled provider, kept intact as a fallback. */
    LEGACY(
        "MuseFlow (legacy)",
        "The original built-in provider. Switch here if InnerTube misbehaves."
    );
}

/**
 * Persists which extractor backend is active.
 *
 * Both implementations stay compiled into the app, so switching is a runtime toggle rather than a
 * rebuild - the point being that if InnerTube turns out to be broken in the field, the original
 * provider is one switch away rather than a reinstall away.
 */
object ExtractorPreference {
    private val BACKEND = stringPreferencesKey("extractor_backend")

    /** InnerTube by default; [ExtractorBackend.LEGACY] remains fully functional behind the toggle. */
    val default = ExtractorBackend.INNERTUBE

    fun observe(context: Context): Flow<ExtractorBackend> =
        context.extractorDataStore.data.map { prefs ->
            prefs[BACKEND]?.let { saved ->
                runCatching { ExtractorBackend.valueOf(saved) }.getOrNull()
            } ?: default
        }

    suspend fun set(context: Context, backend: ExtractorBackend) {
        context.extractorDataStore.edit { it[BACKEND] = backend.name }
    }
}
