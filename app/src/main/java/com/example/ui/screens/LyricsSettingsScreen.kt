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
import androidx.compose.material.icons.filled.Fullscreen
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
import com.example.LyricsTextPosition
import com.example.WordAnimationStyle
import com.example.ui.component.ListPreference
import com.example.ui.component.NavigationPreference
import com.example.ui.component.PreferenceGroup
import com.example.ui.component.SliderPreference
import com.example.ui.component.SwitchPreference
import kotlin.math.roundToInt

/** Lyrics panel text size, alignment, animation and scroll behaviour. */
@Composable
fun LyricsSettingsScreen(
    onBack: () -> Unit,
    onOpenProviderOrder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settingsViewModel: AppSettingsViewModel = viewModel()
    val settings by settingsViewModel.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .testTag("lyrics_settings_screen"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("lyrics_back")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = "Lyrics",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
        }

        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 200.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PreferenceGroup(title = "Lyrics") {
                NavigationPreference(
                    title = "Lyrics provider order",
                    subtitle = "Drag to change which source is tried first.",
                    onClick = onOpenProviderOrder,
                )
                SliderPreference(
                    title = "Text size",
                    value = settings.lyricsTextSize,
                    range = 14..34,
                    onValueChange = settingsViewModel::setLyricsTextSize,
                    valueLabel = { "$it sp" },
                )
                ListPreference(
                    title = "Alignment",
                    selected = settings.lyricsTextPosition,
                    options = LyricsTextPosition.entries.toList(),
                    label = { it.label },
                    onSelect = settingsViewModel::setLyricsTextPosition,
                )
                SwitchPreference(
                    title = "Auto-scroll",
                    subtitle = "Follow the current line while playing.",
                    checked = settings.autoScrollLyrics,
                    onCheckedChange = settingsViewModel::setAutoScrollLyrics,
                )
                SwitchPreference(
                    title = "Tap a line to seek",
                    subtitle = "Jump playback to the line you tap.",
                    checked = settings.changeLyricsOnClick,
                    onCheckedChange = settingsViewModel::setChangeLyricsOnClick,
                )
                SwitchPreference(
                    title = "Blur inactive lines",
                    subtitle = "Softens everything except the line playing now.",
                    checked = settings.blurInactiveLines,
                    onCheckedChange = settingsViewModel::setBlurInactiveLines,
                )
                ListPreference(
                    title = "Word animation",
                    subtitle = "How the word being sung right now is highlighted - only visible " +
                        "on lyrics with word-level timing (not every source has it).",
                    selected = settings.wordAnimationStyle,
                    options = WordAnimationStyle.entries.toList(),
                    label = { it.label },
                    onSelect = settingsViewModel::setWordAnimationStyle,
                )
                SwitchPreference(
                    title = "Glowing effect",
                    subtitle = "Adds a soft light glow around the word being sung.",
                    checked = settings.glowingLyricsEffect,
                    onCheckedChange = settingsViewModel::setGlowingLyricsEffect,
                )
                SliderPreference(
                    title = "Line spacing",
                    // Stored as a Float multiplier (1.0-3.0); the slider itself is Int-only, so
                    // it operates on tenths (10-30) and converts back on the way out.
                    value = (settings.lyricsLineSpacing * 10).roundToInt(),
                    range = 10..30,
                    onValueChange = { settingsViewModel.setLyricsLineSpacing(it / 10f) },
                    valueLabel = { "%.1fx".format(it / 10f) },
                )
                SwitchPreference(
                    title = "Hide status bar in fullscreen lyrics",
                    subtitle = "More room for lyrics while the panel is open.",
                    icon = Icons.Default.Fullscreen,
                    checked = settings.hideStatusBarInFullscreenLyrics,
                    onCheckedChange = settingsViewModel::setHideStatusBarInFullscreenLyrics,
                )
            }
        }
    }
}
