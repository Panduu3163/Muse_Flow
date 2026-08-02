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
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.GraphicEq
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
import com.example.ui.component.NavigationPreference
import com.example.ui.component.PreferenceGroup
import com.example.ui.component.SliderPreference
import com.example.ui.component.SwitchPreference

/** Equalizer entry point plus DSP toggles - skip silence, normalization, crossfade, bass boost,
 * crossfeed. */
@Composable
fun AudioSettingsScreen(
    onBack: () -> Unit,
    onOpenEqualizer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settingsViewModel: AppSettingsViewModel = viewModel()
    val settings by settingsViewModel.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .testTag("audio_settings_screen"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("audio_back")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = "Audio",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
        }

        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 200.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PreferenceGroup(title = "Audio") {
                NavigationPreference(
                    title = "Equalizer",
                    subtitle = "Adjust frequency bands and presets.",
                    icon = Icons.Default.GraphicEq,
                    onClick = onOpenEqualizer,
                )
                SwitchPreference(
                    title = "Skip silence",
                    subtitle = "Trims silent passages between and inside tracks.",
                    icon = Icons.Default.FastForward,
                    checked = settings.skipSilence,
                    onCheckedChange = settingsViewModel::setSkipSilence,
                )
                SwitchPreference(
                    title = "Normalize volume",
                    subtitle = "Smooths loudness differences between tracks.",
                    icon = Icons.Default.GraphicEq,
                    checked = settings.audioNormalizationEnabled,
                    onCheckedChange = settingsViewModel::setAudioNormalizationEnabled,
                )
                SwitchPreference(
                    title = "Crossfade",
                    subtitle = "Fades between tracks instead of a hard cut.",
                    icon = Icons.Default.GraphicEq,
                    checked = settings.crossfadeEnabled,
                    onCheckedChange = settingsViewModel::setCrossfadeEnabled,
                )
                SliderPreference(
                    title = "Crossfade length",
                    value = settings.crossfadeDurationMs / 1000,
                    range = 1..12,
                    enabled = settings.crossfadeEnabled,
                    valueLabel = { "${it}s" },
                    onValueChange = { settingsViewModel.setCrossfadeDurationMs(it * 1000) },
                )
                SwitchPreference(
                    title = "Bass boost",
                    subtitle = "A low-end lift, separate from the equalizer's own bands.",
                    icon = Icons.Default.GraphicEq,
                    checked = settings.bassBoostEnabled,
                    onCheckedChange = settingsViewModel::setBassBoostEnabled,
                )
                SliderPreference(
                    title = "Bass boost intensity",
                    value = settings.bassBoostIntensity,
                    range = 0..100,
                    enabled = settings.bassBoostEnabled,
                    valueLabel = { "$it%" },
                    onValueChange = settingsViewModel::setBassBoostIntensity,
                )
                SwitchPreference(
                    title = "Headphone crossfeed",
                    subtitle = "Softens hard stereo panning on headphones.",
                    icon = Icons.Default.GraphicEq,
                    checked = settings.crossfeedEnabled,
                    onCheckedChange = settingsViewModel::setCrossfeedEnabled,
                )
                SliderPreference(
                    title = "Crossfeed intensity",
                    value = settings.crossfeedIntensity,
                    range = 0..100,
                    enabled = settings.crossfeedEnabled,
                    valueLabel = { "$it%" },
                    onValueChange = settingsViewModel::setCrossfeedIntensity,
                )
            }
        }
    }
}
