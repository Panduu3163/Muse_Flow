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

/**
 * Drives [com.example.ui.screens.OnboardingDialog]'s "show once per version" behaviour - the same
 * persisted-last-seen-version-code pattern Echo Music's `WelcomeDialog` uses. A missing value
 * defaults to -1, so a fresh install (nothing ever persisted) shows it exactly like an update to a
 * newer [BuildConfig.VERSION_CODE] does - both are "the user hasn't seen this version's dialog yet."
 */
class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    val shouldShow: StateFlow<Boolean> = application.onboardingDataStore.data
        .map { prefs -> (prefs[LAST_SEEN_VERSION_CODE] ?: -1) < BuildConfig.VERSION_CODE }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun markSeen() {
        viewModelScope.launch {
            getApplication<Application>().onboardingDataStore.edit {
                it[LAST_SEEN_VERSION_CODE] = BuildConfig.VERSION_CODE
            }
        }
    }
}
