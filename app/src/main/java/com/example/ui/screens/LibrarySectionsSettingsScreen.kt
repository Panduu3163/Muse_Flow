package com.example.ui.screens

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.AppSettingsViewModel
import com.example.ui.component.PreferenceGroup
import com.example.ui.component.SwitchPreference

/** Which shelves show up on the Library screen. */
@Composable
fun LibrarySectionsSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settingsViewModel: AppSettingsViewModel = viewModel()
    val settings by settingsViewModel.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .testTag("library_sections_settings_screen"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("library_sections_back")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = "Library sections",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
        }

        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 200.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PreferenceGroup(title = "Library sections") {
                SwitchPreference(
                    title = "Liked",
                    checked = settings.showLikedPlaylist,
                    onCheckedChange = settingsViewModel::setShowLikedPlaylist,
                )
                SwitchPreference(
                    title = "Downloads",
                    checked = settings.showDownloadedPlaylist,
                    onCheckedChange = settingsViewModel::setShowDownloadedPlaylist,
                )
                SwitchPreference(
                    title = "Top 50",
                    checked = settings.showTopPlaylist,
                    onCheckedChange = settingsViewModel::setShowTopPlaylist,
                )
                SwitchPreference(
                    title = "Recently played shortcut",
                    subtitle = "The history icon next to search/stats.",
                    checked = settings.showRecentlyPlayedShortcut,
                    onCheckedChange = settingsViewModel::setShowRecentlyPlayedShortcut,
                )
            }
        }
    }
}
