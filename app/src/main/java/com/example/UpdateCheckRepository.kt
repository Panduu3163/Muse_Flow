package com.example

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.updateCheckDataStore by preferencesDataStore(name = "update_check_prefs")

private object UpdateCheckKeys {
    val LAST_NOTIFIED_RELEASE_TAG = stringPreferencesKey("last_notified_release_tag")
}

/**
 * Persists the GitHub release tag [UpdateChecker] most recently notified the user about, so the
 * same release never triggers a second notification across app launches - the tag is written
 * right after a notification decision is made for it (shown or not), never re-derived from the
 * notification itself. Its own DataStore file/key, following the same
 * `stringPreferencesKey`/`preferencesDataStore` shape [AppSettingsViewModel] uses for its
 * preferences.
 *
 * Process-wide singleton (same pattern as [FollowedArtistsRepository]/[BackupRepository]) rather
 * than a ViewModel-owned instance, since it's read and written from
 * [MuseFlowApplication.onCreate], which has no ViewModel to own it.
 */
class UpdateCheckRepository private constructor(context: Context) {
    private val appContext = context.applicationContext

    suspend fun getLastNotifiedTag(): String? =
        appContext.updateCheckDataStore.data.first()[UpdateCheckKeys.LAST_NOTIFIED_RELEASE_TAG]

    suspend fun setLastNotifiedTag(tag: String) {
        appContext.updateCheckDataStore.edit { it[UpdateCheckKeys.LAST_NOTIFIED_RELEASE_TAG] = tag }
    }

    companion object {
        @Volatile private var instance: UpdateCheckRepository? = null

        fun getInstance(context: Context): UpdateCheckRepository =
            instance ?: synchronized(this) {
                instance ?: UpdateCheckRepository(context.applicationContext).also { instance = it }
            }
    }
}
