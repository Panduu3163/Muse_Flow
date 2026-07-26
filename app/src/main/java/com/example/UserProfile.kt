package com.example

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** First letter of the first and last word of [name], e.g. "Mynul Kabir Nayem" -> "MN". */
fun computeInitials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
    }
}

data class UserProfileState(
    val hasSeenOnboarding: Boolean = false,
    val displayName: String = "",
    val photoUri: String? = null,
    /** False only until the first real read from DataStore completes. */
    val isLoaded: Boolean = false
) {
    val initials: String get() = computeInitials(displayName)
}

private val Context.userProfileDataStore by preferencesDataStore(name = "user_profile_prefs")

private object UserProfileKeys {
    val HAS_SEEN_ONBOARDING = booleanPreferencesKey("has_seen_onboarding")
    val DISPLAY_NAME = stringPreferencesKey("display_name")
    val PHOTO_URI = stringPreferencesKey("photo_uri")
}

/**
 * Persistence for the user's display name, photo, and onboarding-completion flag, backed by
 * DataStore Preferences so it survives process death and app restarts - critically, so
 * onboarding is never shown a second time once completed.
 */
class UserProfileRepository(private val context: Context) {
    val state: Flow<UserProfileState> = context.userProfileDataStore.data.map { prefs ->
        UserProfileState(
            hasSeenOnboarding = prefs[UserProfileKeys.HAS_SEEN_ONBOARDING] ?: false,
            displayName = prefs[UserProfileKeys.DISPLAY_NAME] ?: "",
            photoUri = prefs[UserProfileKeys.PHOTO_URI],
            isLoaded = true
        )
    }

    suspend fun completeOnboarding(displayName: String, photoUri: String?) {
        context.userProfileDataStore.edit { prefs ->
            prefs[UserProfileKeys.HAS_SEEN_ONBOARDING] = true
            prefs[UserProfileKeys.DISPLAY_NAME] = displayName
            if (photoUri != null) prefs[UserProfileKeys.PHOTO_URI] = photoUri else prefs.remove(UserProfileKeys.PHOTO_URI)
        }
    }

    suspend fun updateProfile(displayName: String, photoUri: String?) {
        context.userProfileDataStore.edit { prefs ->
            prefs[UserProfileKeys.DISPLAY_NAME] = displayName
            if (photoUri != null) prefs[UserProfileKeys.PHOTO_URI] = photoUri else prefs.remove(UserProfileKeys.PHOTO_URI)
        }
    }
}
