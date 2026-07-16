package com.example

import android.app.Application
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.datastore.preferences.preferencesDataStore
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

private class UserProfileRepository(private val context: Context) {
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

/**
 * Single source of truth for the user's display name, photo, and onboarding-completion flag.
 * Scoped to the hosting Activity (via `viewModel()`), same pattern as [ThemeViewModel]. Persisted
 * to DataStore Preferences so it survives process death and app restarts - critically, so
 * onboarding is never shown a second time once completed.
 */
class UserProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = UserProfileRepository(application)

    val state: StateFlow<UserProfileState> = repository.state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserProfileState(isLoaded = false)
    )

    fun completeOnboarding(displayName: String, photoUri: String?) {
        viewModelScope.launch { repository.completeOnboarding(displayName, photoUri) }
    }

    fun updateProfile(displayName: String, photoUri: String?) {
        viewModelScope.launch { repository.updateProfile(displayName, photoUri) }
    }
}

/** Circular avatar showing the user's photo if set, otherwise their initials on a gradient. */
@Composable
fun UserAvatar(
    photoUri: String?,
    initials: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    if (photoUri != null) {
        AsyncImage(
            model = photoUri,
            contentDescription = "Profile photo",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(colors = listOf(Color(0xFF381E72), Color(0xFF4F378B)))
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.32f).sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Shared name-field + tappable-avatar UI used by both onboarding's profile step and Settings'
 * edit-profile screen. Opens the Android Photo Picker (no storage permission needed) to choose
 * a picture.
 */
@Composable
fun ProfileEditorContent(
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    photoUri: String?,
    onPhotoUriChange: (String?) -> Unit,
    nameLabel: String,
    modifier: Modifier = Modifier
) {
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) onPhotoUriChange(uri.toString()) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .clickable {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
                .testTag("profile_photo_picker"),
            contentAlignment = Alignment.BottomEnd
        ) {
            UserAvatar(
                photoUri = photoUri,
                initials = computeInitials(displayName),
                size = 96.dp
            )
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = "Add a photo",
                    tint = Color(0xFF0C0C0E),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Add a photo (optional)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(28.dp))

        OutlinedTextField(
            value = displayName,
            onValueChange = onDisplayNameChange,
            label = { Text(nameLabel) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("display_name_field")
        )
    }
}
