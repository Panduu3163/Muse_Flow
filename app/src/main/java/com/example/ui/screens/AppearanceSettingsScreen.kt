package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.AppSettingsViewModel
import com.example.DisplayDensity
import com.example.GridCellSize
import com.example.ThemeViewModel
import com.example.ui.component.ColorPickerDialog
import com.example.ui.component.ListPreference
import com.example.ui.component.PreferenceGroup
import com.example.ui.component.SwitchPreference
import com.example.ui.theme.DefaultThemeColor

/** Accent seeds. Each is only a *seed* - the full Material 3 palette is generated from it, so
 * every entry yields a complete, contrast-correct theme rather than just a highlight colour. */
private val AccentSeeds: List<Pair<String, Color>> = listOf(
    "Aurora Violet" to DefaultThemeColor,
    "Indigo" to Color(0xFF5B6CFF),
    "Cyan" to Color(0xFF00B5C8),
    "Emerald" to Color(0xFF19A974),
    "Amber" to Color(0xFFE8A317),
    "Sunset" to Color(0xFFFF6B4A),
    "Rose" to Color(0xFFF2557F),
    "Magenta" to Color(0xFFC64BD6),
)

/** Accent colour, theme, display density and card size - everything that changes how MuseFlow
 * looks rather than how it plays. */
@Composable
fun AppearanceSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val themeViewModel: ThemeViewModel = viewModel()
    val theme by themeViewModel.themeState.collectAsState()

    val settingsViewModel: AppSettingsViewModel = viewModel()
    val settings by settingsViewModel.state.collectAsState()

    var showColorPicker by remember { mutableStateOf(false) }

    if (showColorPicker) {
        ColorPickerDialog(
            initialColor = theme.seedColor,
            onConfirm = {
                themeViewModel.setSeedColor(it)
                showColorPicker = false
            },
            onDismiss = { showColorPicker = false },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .testTag("appearance_settings_screen"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("appearance_back")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = "Appearance",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
        }

        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 200.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PreferenceGroup(title = "Appearance") {
                Text(
                    text = "Accent colour",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 20.dp, top = 6.dp, bottom = 10.dp),
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                ) {
                    items(AccentSeeds, key = { it.first }) { (name, color) ->
                        AccentSwatch(
                            name = name,
                            color = color,
                            selected = theme.seedColor == color,
                            onClick = { themeViewModel.setSeedColor(color) },
                        )
                    }
                    item(key = "__custom__") {
                        // Marked selected whenever the seed isn't one of the presets, so a custom
                        // colour doesn't leave the row looking like nothing is chosen.
                        CustomAccentSwatch(
                            color = theme.seedColor,
                            selected = AccentSeeds.none { it.second == theme.seedColor },
                            onClick = { showColorPicker = true },
                        )
                    }
                }
                Text(
                    text = if (theme.isUsingDefaultSeed) {
                        "Using the system wallpaper palette on Android 12+, Aurora Violet elsewhere."
                    } else {
                        "The whole palette is generated from this one colour."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
                )

                SwitchPreference(
                    title = "Dark theme",
                    subtitle = "MuseFlow is designed dark-first.",
                    icon = Icons.Default.DarkMode,
                    checked = theme.darkTheme,
                    onCheckedChange = themeViewModel::setDarkTheme,
                )
                SwitchPreference(
                    title = "Pure black (AMOLED)",
                    subtitle = "True black backgrounds so OLED pixels switch off.",
                    icon = Icons.Default.Contrast,
                    checked = theme.pureBlack,
                    onCheckedChange = themeViewModel::setPureBlack,
                    enabled = theme.darkTheme,
                )
                ListPreference(
                    title = "Display density",
                    subtitle = "Scales spacing across the whole app.",
                    icon = Icons.Default.Compress,
                    selected = settings.displayDensity,
                    options = DisplayDensity.entries.toList(),
                    label = { it.label },
                    onSelect = settingsViewModel::setDisplayDensity,
                )
                ListPreference(
                    title = "Card size",
                    subtitle = "How large Home's shelf artwork is drawn.",
                    icon = Icons.Default.GridView,
                    selected = settings.gridCellSize,
                    options = GridCellSize.entries.toList(),
                    label = { it.label },
                    onSelect = settingsViewModel::setGridCellSize,
                )
                SwitchPreference(
                    title = "Colour from album art",
                    subtitle = "Re-seeds the palette from the artwork of whatever is playing.",
                    icon = Icons.Default.Palette,
                    checked = theme.dynamicAlbumColor,
                    onCheckedChange = themeViewModel::setDynamicAlbumColor,
                )
            }
        }
    }
}

/** Opens the colour picker. Shows the current seed when custom, so the swatch previews what you'd
 * be editing rather than being a blank button. */
@Composable
private fun CustomAccentSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (selected) {
                        SolidColor(color)
                    } else {
                        // A hue sweep reads as "pick any colour" without needing a label.
                        Brush.sweepGradient(
                            (0..360 step 45).map { Color.hsv(it.toFloat().coerceAtMost(359f), 0.7f, 0.95f) }
                        )
                    }
                )
                .then(
                    if (selected) {
                        Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                    } else {
                        Modifier
                    }
                )
                .clickable(onClick = onClick)
                .testTag("accent_custom"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (selected) Icons.Default.Check else Icons.Default.Colorize,
                contentDescription = "Custom colour",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = "Custom",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun AccentSwatch(
    name: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color)
                .then(
                    if (selected) {
                        Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                    } else {
                        Modifier
                    }
                )
                .clickable(onClick = onClick)
                .testTag("accent_${name.lowercase().replace(" ", "_")}"),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
