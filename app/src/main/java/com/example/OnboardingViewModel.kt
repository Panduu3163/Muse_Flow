package com.example

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val Context.onboardingDataStore by preferencesDataStore(name = "onboarding_prefs")
private val LAST_SEEN_VERSION_CODE = intPreferencesKey("last_seen_version_code")

/** Which first-launch dialog (if either) is due, from comparing the persisted last-seen version
 * code against [BuildConfig.VERSION_CODE] - a missing value (nothing ever persisted) is a genuine
 * fresh install, distinct from a real update (something lower than the current code was seen
 * before). The two used to be treated identically (both just "haven't seen this version yet"),
 * which meant an update showed the same hobby-project/support-email notice a fresh install needs,
 * instead of an actual changelog of what changed. */
enum class OnboardingKind { None, FreshInstall, Updated }

/**
 * Drives [com.example.ui.screens.OnboardingDialog] (fresh install) and
 * [com.example.ui.screens.ChangelogDialog] (update) - the same persisted-last-seen-version-code
 * pattern Echo Music's own `WelcomeDialog` uses, split into the two cases it actually represents.
 */
class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    val onboardingKind: StateFlow<OnboardingKind> = application.onboardingDataStore.data
        .map { prefs ->
            val lastSeen = prefs[LAST_SEEN_VERSION_CODE] ?: -1
            when {
                lastSeen == -1 -> OnboardingKind.FreshInstall
                lastSeen < BuildConfig.VERSION_CODE -> OnboardingKind.Updated
                else -> OnboardingKind.None
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OnboardingKind.None)

    fun markSeen() {
        viewModelScope.launch {
            getApplication<Application>().onboardingDataStore.edit {
                it[LAST_SEEN_VERSION_CODE] = BuildConfig.VERSION_CODE
            }
        }
    }
}
