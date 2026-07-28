package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HideImage
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.RoundedCorner
import androidx.compose.material3.Icon
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
import com.example.BackgroundStyle
import com.example.DefaultTab
import com.example.DisplayDensity
import com.example.GridCellSize
import com.example.LyricsTextPosition
import com.example.PlayerButtonColorOption
import com.example.PlayerSliderStyle
import com.example.WordAnimationStyle
import com.example.ThemeViewModel
import com.example.ui.component.ColorPickerDialog
import com.example.ui.component.ListPreference
import com.example.ui.component.NavigationPreference
import com.example.ui.component.PreferenceGroup
import com.example.ui.component.SliderPreference
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

/**
 * Settings, built on the preference DSL in `ui/component/Preference.kt`.
 *
 * **Every option here drives real behaviour.** Preferences whose backing feature isn't built yet
 * (lyrics, equalizer, canvas, haptics...) exist in [com.example.AppSettingsState] and persist, but
 * are deliberately not surfaced until they do something - a settings screen full of inert toggles
 * is worse than a short one.
 */
@Composable
fun SettingsScreen(
    onOpenEqualizer: () -> Unit = {},
    onOpenBackup: () -> Unit = {},
    onOpenCrashLogs: () -> Unit = {},
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

        item {
            PreferenceGroup(title = "Mini player") {
                ListPreference(
                    title = "Background style",
                    subtitle = "Gradient, blur and glow are built from the current artwork.",
                    icon = Icons.Default.Gradient,
                    selected = settings.miniPlayerBackgroundStyle,
                    options = BackgroundStyle.entries.toList(),
                    label = { it.label },
                    onSelect = settingsViewModel::setMiniPlayerBackgroundStyle,
                )
            }
        }

        item {
            PreferenceGroup(title = "Player") {
                SwitchPreference(
                    title = "Hide artwork",
                    subtitle = "Show a compact player without the large cover image.",
                    icon = Icons.Default.HideImage,
                    checked = settings.hidePlayerThumbnail,
                    onCheckedChange = settingsViewModel::setHidePlayerThumbnail,
                )
                SwitchPreference(
                    title = "Crop artwork to square",
                    subtitle = "Off keeps the original aspect ratio inside the frame.",
                    icon = Icons.Default.Crop,
                    checked = settings.cropAlbumArt,
                    onCheckedChange = settingsViewModel::setCropAlbumArt,
                    enabled = !settings.hidePlayerThumbnail,
                )
                ListPreference(
                    title = "Background style",
                    subtitle = "Gradient and blur are built from the current artwork.",
                    icon = Icons.Default.Gradient,
                    selected = settings.playerBackgroundStyle,
                    options = BackgroundStyle.entries.toList(),
                    label = { it.label },
                    onSelect = settingsViewModel::setPlayerBackgroundStyle,
                )
                ListPreference(
                    title = "Button colour",
                    subtitle = "Which accent role drives the play button.",
                    icon = Icons.Default.Circle,
                    selected = settings.playerButtonColor,
                    options = PlayerButtonColorOption.entries.toList(),
                    label = { it.label },
                    onSelect = settingsViewModel::setPlayerButtonColor,
                )
                ListPreference(
                    title = "Progress bar style",
                    subtitle = "Wavy animates while audio is playing.",
                    icon = Icons.Default.Waves,
                    selected = settings.playerSliderStyle,
                    options = PlayerSliderStyle.entries.toList(),
                    label = { it.label },
                    onSelect = settingsViewModel::setPlayerSliderStyle,
                )
                SliderPreference(
                    title = "Artwork corner radius",
                    value = settings.thumbnailCornerRadius,
                    range = 0..48,
                    onValueChange = settingsViewModel::setThumbnailCornerRadius,
                    enabled = !settings.hidePlayerThumbnail,
                    valueLabel = { "$it dp" },
                )
            }
        }

        item {
            PreferenceGroup(title = "Lyrics") {
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
            }
        }

        item {
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

        item {
            PreferenceGroup(title = "Playback") {
                SwitchPreference(
                    title = "Preload next track",
                    subtitle = "Resolves the next song early so skipping doesn't pause.",
                    icon = Icons.Default.Bolt,
                    checked = settings.preloadNextTrack,
                    onCheckedChange = settingsViewModel::setPreloadNextTrack,
                )
                SwitchPreference(
                    title = "Remember queue",
                    subtitle = "Restores what you were playing after the app is closed.",
                    icon = Icons.Default.Restore,
                    checked = settings.persistentQueue,
                    onCheckedChange = settingsViewModel::setPersistentQueue,
                )
            }
        }

        item {
            PreferenceGroup(title = "General") {
                ListPreference(
                    title = "Default tab",
                    subtitle = "Which tab opens when MuseFlow starts.",
                    icon = Icons.Default.Home,
                    selected = settings.defaultOpenTab,
                    options = DefaultTab.entries.toList(),
                    label = { it.label },
                    onSelect = settingsViewModel::setDefaultOpenTab,
                )
                NavigationPreference(
                    title = "Backup & restore",
                    subtitle = "Export liked songs and playlists, or restore them.",
                    icon = Icons.Default.Backup,
                    onClick = onOpenBackup,
                )
                NavigationPreference(
                    title = "Crash logs",
                    subtitle = "View or share a report from a previous crash.",
                    icon = Icons.Default.BugReport,
                    onClick = onOpenCrashLogs,
                )
            }
        }

        item {
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
                    title = "Recently played",
                    checked = settings.showCachedPlaylist,
                    onCheckedChange = settingsViewModel::setShowCachedPlaylist,
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
