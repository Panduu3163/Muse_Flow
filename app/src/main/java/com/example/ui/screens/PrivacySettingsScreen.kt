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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.AppSettingsViewModel
import com.example.PlaybackHistoryRepository
import com.example.SearchHistoryRepository
import com.example.ui.component.ActionPreference
import com.example.ui.component.PreferenceGroup
import com.example.ui.component.SwitchPreference

/** Screenshot blocking and clearing listening/search history. */
@Composable
fun PrivacySettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settingsViewModel: AppSettingsViewModel = viewModel()
    val settings by settingsViewModel.state.collectAsState()

    val context = LocalContext.current
    var confirmingClearHistory by remember { mutableStateOf(false) }
    var confirmingClearSearchHistory by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .testTag("privacy_settings_screen"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("privacy_back")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = "Privacy",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
        }

        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 200.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PreferenceGroup(title = "Privacy") {
                SwitchPreference(
                    title = "Disable screenshots",
                    subtitle = "Blocks screenshots and screen recording, and hides MuseFlow's " +
                        "preview in the recent-apps switcher.",
                    icon = Icons.Default.VisibilityOff,
                    checked = settings.disableScreenshots,
                    onCheckedChange = settingsViewModel::setDisableScreenshots,
                )
                ActionPreference(
                    title = "Clear listening history",
                    subtitle = "Removes every track from your play history. Liked songs, " +
                        "downloads and playlists aren't affected.",
                    icon = Icons.Default.History,
                    destructive = true,
                    onClick = { confirmingClearHistory = true },
                )
                ActionPreference(
                    title = "Clear search history",
                    subtitle = "Removes your recent search queries.",
                    icon = Icons.Default.Search,
                    destructive = true,
                    onClick = { confirmingClearSearchHistory = true },
                )
            }
        }
    }

    if (confirmingClearHistory) {
        AlertDialog(
            onDismissRequest = { confirmingClearHistory = false },
            title = { Text("Clear listening history?") },
            text = {
                Text(
                    "Every track is removed from your play history - Home's \"Recently " +
                        "played\" and Library's \"Top 50\" go with it. Liked songs, downloads " +
                        "and playlists aren't affected."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        PlaybackHistoryRepository.getInstance(context).clear()
                        confirmingClearHistory = false
                    },
                    modifier = Modifier.testTag("settings_clear_history_confirm"),
                ) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingClearHistory = false }) { Text("Cancel") }
            },
        )
    }

    if (confirmingClearSearchHistory) {
        AlertDialog(
            onDismissRequest = { confirmingClearSearchHistory = false },
            title = { Text("Clear search history?") },
            text = { Text("Removes your recent search queries from Search.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        SearchHistoryRepository.getInstance(context).clearAll()
                        confirmingClearSearchHistory = false
                    },
                    modifier = Modifier.testTag("settings_clear_search_history_confirm"),
                ) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingClearSearchHistory = false }) { Text("Cancel") }
            },
        )
    }
}
