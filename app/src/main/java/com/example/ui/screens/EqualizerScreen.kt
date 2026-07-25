package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.EqualizerBand
import com.example.EqualizerViewModel
import com.example.ui.component.PreferenceGroup
import com.example.ui.component.SwitchPreference
import kotlin.math.roundToInt

/**
 * The device equalizer.
 *
 * Writes land in `EqualizerRepository`; `PlaybackService` already collects that Flow and re-applies
 * it to the live audio effect, so moving a slider changes what you're hearing immediately without
 * this screen touching the player.
 */
@Composable
fun EqualizerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: EqualizerViewModel = viewModel()
    val settings by viewModel.settings.collectAsState()
    val caps = viewModel.capabilities

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .testTag("equalizer_screen"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("equalizer_back")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = "Equalizer",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        if (!caps.supported) {
            // Honest dead end rather than sliders that silently do nothing: not every OEM audio
            // stack implements the effect.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "This device's audio system doesn't support an equalizer.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            return@Column
        }

        Column(modifier = Modifier.padding(16.dp)) {
            PreferenceGroup(title = "Equalizer") {
                SwitchPreference(
                    title = "Enable equalizer",
                    subtitle = "Applies to everything MuseFlow plays.",
                    checked = settings.enabled,
                    onCheckedChange = viewModel::setEnabled,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            caps.bands.forEach { band ->
                BandSlider(
                    band = band,
                    levelMillibel = settings.bandLevelsMillibel.getOrElse(band.index) { 0 },
                    minMillibel = caps.minLevelMillibel,
                    maxMillibel = caps.maxLevelMillibel,
                    enabled = settings.enabled,
                    onLevelChange = { viewModel.setBandLevel(band.index, it) },
                )
            }
        }

        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                onClick = viewModel::resetToFlat,
                enabled = settings.enabled,
                modifier = Modifier.testTag("equalizer_reset"),
            ) { Text("Reset to flat") }
        }

        if (caps.presets.isNotEmpty()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = "PRESETS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    caps.presets.forEachIndexed { index, preset ->
                        AssistChip(
                            onClick = { viewModel.applyPreset(index) },
                            label = { Text(preset) },
                            modifier = Modifier.testTag("eq_preset_${preset.lowercase()}"),
                        )
                    }
                }
                Text(
                    text = "Applying a preset copies its levels onto the sliders, so you can keep tweaking from there.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 24.dp),
                )
            }
        }
    }
}

/**
 * One vertical band slider.
 *
 * Compose has no vertical Slider, so a horizontal one is rotated. The value is committed on
 * release rather than per drag frame - each write hits DataStore *and* re-applies the live audio
 * effect, which isn't something to do on every pixel.
 */
@Composable
private fun BandSlider(
    band: EqualizerBand,
    levelMillibel: Int,
    minMillibel: Int,
    maxMillibel: Int,
    enabled: Boolean,
    onLevelChange: (Int) -> Unit,
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging ?: levelMillibel.toFloat()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(56.dp),
    ) {
        Text(
            text = "${(shown / 100).roundToInt()} dB",
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )

        Box(
            modifier = Modifier
                .height(200.dp)
                .width(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            Slider(
                value = shown,
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { onLevelChange(it.roundToInt()) }
                    dragging = null
                },
                valueRange = minMillibel.toFloat()..maxMillibel.toFloat(),
                enabled = enabled,
                modifier = Modifier
                    .graphicsLayer { rotationZ = 270f }
                    // Rotation alone doesn't change how the slider is measured, so it's laid out
                    // at the box's height and then constrained back to the column's width.
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(
                            constraints.copy(
                                minWidth = constraints.minHeight,
                                maxWidth = constraints.maxHeight,
                                minHeight = constraints.minWidth,
                                maxHeight = constraints.maxWidth,
                            )
                        )
                        layout(placeable.height, placeable.width) {
                            placeable.place(
                                -(placeable.width / 2 - placeable.height / 2),
                                -(placeable.height / 2 - placeable.width / 2),
                            )
                        }
                    },
            )
        }

        Text(
            text = band.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
