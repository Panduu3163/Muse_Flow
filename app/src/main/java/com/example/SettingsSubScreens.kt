package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/** Shared header row (back arrow + title) used by every Settings sub-screen. */
@Composable
private fun SubScreenHeader(title: String, onBack: () -> Unit, backTag: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 8.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.testTag(backTag)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back to Settings",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
fun AccountScreen(
    onEditProfile: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val userProfileViewModel: UserProfileViewModel = viewModel()
    val profile by userProfileViewModel.state.collectAsState()

    ThemedBackground(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SubScreenHeader(title = "Account", onBack = onBack, backTag = "account_back_button")
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 90.dp) // Spacer for compact player
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                        .clickable { onEditProfile() }
                        .testTag("settings_profile_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserAvatar(
                            photoUri = profile.photoUri,
                            initials = profile.initials,
                            size = 64.dp
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = profile.displayName.ifBlank { "Set your name" },
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Tap to edit your name or photo",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Edit profile",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val userProfileViewModel: UserProfileViewModel = viewModel()
    val profile by userProfileViewModel.state.collectAsState()

    var displayName by remember(profile.displayName) { mutableStateOf(profile.displayName) }
    var photoUri by remember(profile.photoUri) { mutableStateOf(profile.photoUri) }

    ThemedBackground(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SubScreenHeader(title = "Edit Profile", onBack = onBack, backTag = "edit_profile_back_button")
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 90.dp) // Spacer for compact player
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                ProfileEditorContent(
                    displayName = displayName,
                    onDisplayNameChange = { displayName = it },
                    photoUri = photoUri,
                    onPhotoUriChange = { photoUri = it },
                    nameLabel = "Your name",
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = {
                        userProfileViewModel.updateProfile(displayName.trim(), photoUri)
                        onBack()
                    },
                    enabled = displayName.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("save_profile_button")
                ) {
                    Text("Save", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                }
            }
        }
    }
}

@Composable
fun AppearanceSettingsScreen(
    themeState: ThemeState,
    onOpenTheme: () -> Unit,
    appSettings: AppSettingsState,
    appSettingsViewModel: AppSettingsViewModel,
    hideVideoContent: Boolean,
    onHideVideoContentChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    ThemedBackground(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SubScreenHeader(title = "Appearance", onBack = onBack, backTag = "appearance_back_button")
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 90.dp) // Spacer for compact player
            ) {
                SettingsGroupHeader(title = "Theme")
                SettingsActionRow(
                    icon = Icons.Default.Palette,
                    title = "Theme",
                    subtitle = when (themeState.backgroundMode) {
                        BackgroundMode.Amoled -> "AMOLED Black"
                        BackgroundMode.Gradient -> "Gradient · ${themeState.palette.label}"
                    },
                    onClick = onOpenTheme,
                    tag = "action_theme"
                )
                SettingsToggleRow(
                    icon = Icons.Default.VideocamOff,
                    title = "Hide video content",
                    subtitle = "Disable video canvases or background loops entirely",
                    checked = hideVideoContent,
                    onCheckedChange = onHideVideoContentChange,
                    tag = "toggle_hide_video"
                )

                Spacer(modifier = Modifier.height(16.dp))
                AppearanceMiniPlayerSection(state = appSettings, viewModel = appSettingsViewModel)

                Spacer(modifier = Modifier.height(16.dp))
                AppearancePlayerSection(state = appSettings, viewModel = appSettingsViewModel)

                Spacer(modifier = Modifier.height(16.dp))
                AppearanceLayoutSection(state = appSettings, viewModel = appSettingsViewModel)
            }
        }
    }
}

@Composable
fun PlayerAudioSettingsScreen(
    offlineMode: Boolean,
    onOfflineModeChange: (Boolean) -> Unit,
    crossfade: Boolean,
    onCrossfadeChange: (Boolean) -> Unit,
    gaplessPlayback: Boolean,
    onGaplessPlaybackChange: (Boolean) -> Unit,
    pauseOnMute: Boolean,
    onPauseOnMuteChange: (Boolean) -> Unit,
    resumeOnBluetooth: Boolean,
    onResumeOnBluetoothChange: (Boolean) -> Unit,
    appSettings: AppSettingsState,
    appSettingsViewModel: AppSettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    ThemedBackground(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SubScreenHeader(title = "Player & Audio", onBack = onBack, backTag = "player_audio_back_button")
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 90.dp) // Spacer for compact player
            ) {
                SettingsGroupHeader(title = "Playback")
                SettingsToggleRow(
                    icon = Icons.Default.SignalWifiOff,
                    title = "Offline mode",
                    subtitle = "Only play downloaded music tracks",
                    checked = offlineMode,
                    onCheckedChange = onOfflineModeChange,
                    tag = "toggle_offline_mode"
                )
                SettingsToggleRow(
                    icon = Icons.Default.Transform,
                    title = "Crossfade",
                    subtitle = "Enable smooth transitions between songs",
                    checked = crossfade,
                    onCheckedChange = onCrossfadeChange,
                    tag = "toggle_crossfade"
                )
                SettingsToggleRow(
                    icon = Icons.Default.Hearing,
                    title = "Gapless playback",
                    subtitle = "Play continuous music albums seamlessly",
                    checked = gaplessPlayback,
                    onCheckedChange = onGaplessPlaybackChange,
                    tag = "toggle_gapless_playback"
                )
                SettingsToggleRow(
                    icon = Icons.Default.VolumeMute,
                    title = "Pause on mute",
                    subtitle = "Automatically pause when device volume drops to zero",
                    checked = pauseOnMute,
                    onCheckedChange = onPauseOnMuteChange,
                    tag = "toggle_pause_on_mute"
                )
                SettingsToggleRow(
                    icon = Icons.Default.BluetoothConnected,
                    title = "Resume on Bluetooth reconnect",
                    subtitle = "Auto play when headphones or speakers re-pair",
                    checked = resumeOnBluetooth,
                    onCheckedChange = onResumeOnBluetoothChange,
                    tag = "toggle_resume_bluetooth"
                )

                Spacer(modifier = Modifier.height(16.dp))
                PlayerAudioNowPlayingSection(state = appSettings, viewModel = appSettingsViewModel)

                Spacer(modifier = Modifier.height(16.dp))
                PlayerAudioMiniPlayerSection(state = appSettings, viewModel = appSettingsViewModel)
            }
        }
    }
}

@Composable
fun LyricsSettingsScreen(
    appSettings: AppSettingsState,
    appSettingsViewModel: AppSettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    ThemedBackground(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SubScreenHeader(title = "Lyrics", onBack = onBack, backTag = "lyrics_back_button")
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 90.dp) // Spacer for compact player
            ) {
                LyricsSection(state = appSettings, viewModel = appSettingsViewModel)
            }
        }
    }
}

@Composable
fun LibraryPlaylistsSettingsScreen(
    appSettings: AppSettingsState,
    appSettingsViewModel: AppSettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    ThemedBackground(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SubScreenHeader(title = "Library & Playlists", onBack = onBack, backTag = "library_playlists_back_button")
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 90.dp) // Spacer for compact player
            ) {
                LibraryPlaylistsBrowseSection(state = appSettings, viewModel = appSettingsViewModel)

                Spacer(modifier = Modifier.height(16.dp))
                LibraryPlaylistsInteractionsSection(state = appSettings, viewModel = appSettingsViewModel)

                Spacer(modifier = Modifier.height(16.dp))
                AutoPlaylistsSection(state = appSettings, viewModel = appSettingsViewModel)
            }
        }
    }
}

@Composable
fun ListenTogetherScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    ThemedBackground(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SubScreenHeader(title = "Listen Together", onBack = onBack, backTag = "listen_together_back_button")
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 90.dp) // Spacer for compact player
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("listen_together_placeholder_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Listen Together is coming soon",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Synced group listening sessions with friends aren't implemented yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StorageScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    ThemedBackground(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SubScreenHeader(title = "Storage", onBack = onBack, backTag = "storage_back_button")
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 90.dp) // Spacer for compact player
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("storage_placeholder_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Storage management is coming soon",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Cache size, downloaded tracks, and clear-storage controls aren't implemented yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
