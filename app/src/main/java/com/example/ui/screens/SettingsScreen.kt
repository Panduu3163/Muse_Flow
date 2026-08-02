package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.component.NavigationPreference
import com.example.ui.component.PreferenceGroup

/**
 * Settings' top-level screen: a picker for categories rather than a single long list of ~40+
 * controls. Each row here navigates into its own dedicated sub-screen (see
 * `AppearanceSettingsScreen.kt`, `PlayerSettingsScreen.kt`, etc.) which owns the actual controls -
 * this screen only owns navigation. Mirrors the shape of Echo-Music's settings entry point.
 */
@Composable
fun SettingsScreen(
    onOpenAppearance: () -> Unit = {},
    onOpenMiniPlayer: () -> Unit = {},
    onOpenPlayer: () -> Unit = {},
    onOpenLyrics: () -> Unit = {},
    onOpenAudio: () -> Unit = {},
    onOpenPlayback: () -> Unit = {},
    onOpenGeneral: () -> Unit = {},
    onOpenPrivacy: () -> Unit = {},
    onOpenLibrarySections: () -> Unit = {},
    onOpenBackup: () -> Unit = {},
    onOpenStorage: () -> Unit = {},
    onOpenCrashLogs: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("settings_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 200.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        item {
            PreferenceGroup(title = "Look & feel") {
                NavigationPreference(
                    title = "Appearance",
                    subtitle = "Accent colour, theme, density and card size.",
                    icon = Icons.Default.Palette,
                    onClick = onOpenAppearance,
                )
                NavigationPreference(
                    title = "Mini player",
                    subtitle = "The compact player shown above the bottom bar.",
                    icon = Icons.Default.SmartDisplay,
                    onClick = onOpenMiniPlayer,
                )
                NavigationPreference(
                    title = "Player",
                    subtitle = "Now Playing's artwork, buttons and progress bar.",
                    icon = Icons.Default.PlayCircle,
                    onClick = onOpenPlayer,
                )
                NavigationPreference(
                    title = "Lyrics",
                    subtitle = "Text size, alignment, animation and scrolling.",
                    icon = Icons.Default.Lyrics,
                    onClick = onOpenLyrics,
                )
            }
        }

        item {
            PreferenceGroup(title = "Playback") {
                NavigationPreference(
                    title = "Audio",
                    subtitle = "Equalizer, crossfade, bass boost and crossfeed.",
                    icon = Icons.Default.GraphicEq,
                    onClick = onOpenAudio,
                )
                NavigationPreference(
                    title = "Playback",
                    subtitle = "Preloading and queue persistence.",
                    icon = Icons.Default.Tune,
                    onClick = onOpenPlayback,
                )
            }
        }

        item {
            PreferenceGroup(title = "General") {
                NavigationPreference(
                    title = "General",
                    subtitle = "Default tab on launch.",
                    icon = Icons.Default.Tune,
                    onClick = onOpenGeneral,
                )
                NavigationPreference(
                    title = "Library sections",
                    subtitle = "Which shelves show up in Library.",
                    icon = Icons.Default.LibraryMusic,
                    onClick = onOpenLibrarySections,
                )
                NavigationPreference(
                    title = "Privacy",
                    subtitle = "Screenshots and listening/search history.",
                    icon = Icons.Default.VisibilityOff,
                    onClick = onOpenPrivacy,
                )
            }
        }

        item {
            PreferenceGroup(title = "Data") {
                NavigationPreference(
                    title = "Backup & restore",
                    subtitle = "Export liked songs and playlists, or restore them.",
                    icon = Icons.Default.Backup,
                    onClick = onOpenBackup,
                )
                NavigationPreference(
                    title = "Storage",
                    subtitle = "See what downloads and caches take up, and clear them.",
                    icon = Icons.Default.Storage,
                    onClick = onOpenStorage,
                )
                NavigationPreference(
                    title = "Crash logs",
                    subtitle = "View or share a report from a previous crash.",
                    icon = Icons.Default.BugReport,
                    onClick = onOpenCrashLogs,
                )
                NavigationPreference(
                    title = "About",
                    subtitle = "Version and app info.",
                    icon = Icons.Default.Info,
                    onClick = onOpenAbout,
                )
            }
        }
    }
}
