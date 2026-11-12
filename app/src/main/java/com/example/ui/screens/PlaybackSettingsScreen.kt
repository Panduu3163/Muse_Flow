package com.example.ui.screens

import com.example.ui.utils.MicroIconButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.AppSettingsViewModel
import com.example.AudioQualityMode
import com.example.ui.component.ListPreference
import com.example.ui.component.PreferenceGroup
import com.example.ui.component.SwitchPreference

/** Streaming quality, preloading and queue persistence. */
@Composable
fun PlaybackSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settingsViewModel: AppSettingsViewModel = viewModel()
    val settings by settingsViewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .testTag("playback_settings_screen"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MicroIconButton(onClick = onBack, modifier = Modifier.testTag("playback_back")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = "Playback",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
        }

        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 200.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PreferenceGroup(title = "Playback") {
                ListPreference(
                    title = "Streaming quality",
                    subtitle = "Data saver prefers audio up to 96 kbps when available. Local files keep their original quality; the Player shows the actual codec and bitrate.",
                    selected = settings.audioQualityMode,
                    options = AudioQualityMode.entries,
                    label = { it.label },
                    icon = Icons.Default.Tune,
                    onSelect = settingsViewModel::setAudioQualityMode,
                )
                SwitchPreference(
                    title = "Preload next track",
                    subtitle = "Prepares the next song's audio ahead of time to reduce waiting when you skip.",
                    icon = Icons.Default.Bolt,
                    checked = settings.preloadNextTrack,
                    onCheckedChange = settingsViewModel::setPreloadNextTrack,
                )
                SwitchPreference(
                    title = "Remember queue",
                    subtitle = "Restores the paused queue, position, shuffle and repeat choices. Press Play to resume.",
                    icon = Icons.Default.Restore,
                    checked = settings.persistentQueue,
                    onCheckedChange = settingsViewModel::setPersistentQueue,
                )
            }
        }
    }
}
