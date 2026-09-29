package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.HideImage
import androidx.compose.material.icons.filled.SwipeLeft
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.AppSettingsViewModel
import com.example.BackgroundStyle
import com.example.PlayerButtonColorOption
import com.example.PlayerSliderStyle
import com.example.PlayerTransportStyle
import com.example.ui.component.ListPreference
import com.example.ui.component.ActionPreference
import com.example.ui.component.PlayerLivePreview
import com.example.ui.component.PreferenceGroup
import com.example.ui.component.SliderPreference
import com.example.ui.component.SquigglySlider
import com.example.ui.component.WavySeekBar
import com.example.ui.component.SlimSeekBar
import com.example.ui.component.SwitchPreference
import com.example.ui.component.settingsFocusTarget

/** Now Playing's artwork, background, buttons and progress bar. */
@Composable
fun PlayerSettingsScreen(
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
            .testTag("player_settings_screen"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("player_back")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = "Player",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
        }

        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 200.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PreferenceGroup(title = "Player background") {
                ListPreference(
                    title = "Background style",
                    icon = Icons.Default.Gradient,
                    selected = settings.playerBackgroundStyle,
                    options = BackgroundStyle.entries.toList(),
                    label = { it.label },
                    onSelect = settingsViewModel::setPlayerBackgroundStyle,
                )
            }
            PlayerLivePreview(settings)
            PreferenceGroup(title = "Artwork") {
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
                SwitchPreference(
                    title = "Rotating artwork",
                    subtitle = "Slowly rotate the cover in a circular frame while music plays.",
                    icon = Icons.Default.Autorenew,
                    checked = settings.rotatingThumbnailAnimation,
                    onCheckedChange = settingsViewModel::setRotatingThumbnailAnimation,
                    enabled = !settings.hidePlayerThumbnail,
                )
                SliderPreference(
                    title = "Artwork corner radius",
                    value = settings.thumbnailCornerRadius,
                    range = 0..48,
                    onValueChange = settingsViewModel::setThumbnailCornerRadius,
                    enabled = !settings.hidePlayerThumbnail && !settings.rotatingThumbnailAnimation,
                    valueLabel = { "$it dp" },
                )
            }
            PreferenceGroup(title = "Progress and controls") {
                SliderStylePreference(
                    selected = settings.playerSliderStyle,
                    onSelect = settingsViewModel::setPlayerSliderStyle,
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
                    title = "Transport button style",
                    subtitle = "Control shape and motion",
                    icon = Icons.Default.SkipNext,
                    selected = settings.playerTransportStyle,
                    options = PlayerTransportStyle.entries.toList(),
                    label = { it.label },
                    onSelect = settingsViewModel::setPlayerTransportStyle,
                )
                SwitchPreference(
                    title = "Swipe to change song",
                    subtitle = "Swipe the artwork left/right on Now Playing to skip tracks.",
                    icon = Icons.Default.SwipeLeft,
                    checked = settings.swipeToChangeSongEnabled,
                    onCheckedChange = settingsViewModel::setSwipeToChangeSong,
                )
                SwitchPreference(
                    title = "Show codec info",
                    subtitle = "Displays the audio format/bitrate on Now Playing.",
                    icon = Icons.Default.Code,
                    checked = settings.showCodecInfo,
                    onCheckedChange = settingsViewModel::setShowCodecInfo,
                )
            }
            PreferenceGroup(title = "Reset") {
                ActionPreference(
                    title = "Reset player appearance",
                    subtitle = "Restore artwork, background, progress and button defaults.",
                    onClick = settingsViewModel::resetPlayer,
                )
            }
        }
    }
}

/**
 * The "Progress bar style" row - opens a bottom sheet with all four [PlayerSliderStyle]s laid out
 * in a 2x2 grid, each cell showing a small live-animated instance of that actual style (not a
 * static screenshot), so the choice is made by seeing the real motion rather than reading a name.
 */
@Composable
private fun SliderStylePreference(
    selected: PlayerSliderStyle,
    onSelect: (PlayerSliderStyle) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .settingsFocusTarget("Progress bar style")
            .clickable { showPicker = true }
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .testTag("player_slider_style_row"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant,
            RoundedCornerShape(com.example.ui.theme.MuseFlowShapes.control)),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Waves, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Progress bar style", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(
                text = selected.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (showPicker) {
        SliderStylePickerSheet(
            selected = selected,
            onSelect = {
                onSelect(it)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SliderStylePickerSheet(
    selected: PlayerSliderStyle,
    onSelect: (PlayerSliderStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = com.example.ui.theme.MuseFlowShapes.sheet,
            topEnd = com.example.ui.theme.MuseFlowShapes.sheet),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
            Text(
                text = "Progress bar style",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp).padding(top = 4.dp, bottom = 16.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth().height(280.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(PlayerSliderStyle.entries.toList()) { style ->
                    SliderStylePreviewCell(
                        style = style,
                        isSelected = style == selected,
                        onClick = { onSelect(style) },
                    )
                }
            }
        }
    }
}

/** One 2x2-grid cell: the style's name plus a small, genuinely animated instance of it (a fake
 * ~40%-progress, always-"playing" preview) so the motion itself sells the choice. */
@Composable
private fun SliderStylePreviewCell(
    style: PlayerSliderStyle,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clickable(onClick = onClick)
            .testTag("slider_style_${style.name.lowercase()}"),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = style.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.height(18.dp),
                    )
                }
            }
            // interactive = false / enabled = false throughout: these are static previews, not
            // real controls - a real interactive slider nested inside this cell's own clickable
            // would silently swallow any tap that lands on the wave/track itself (Compose
            // dispatches gesture recognition child-first), leaving only taps on the label text
            // actually reaching the cell's onClick. Disabled Slider's default colours are also
            // heavily dimmed, so those two branches override them back to the normal look.
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                when (style) {
                    PlayerSliderStyle.Squiggly -> SquigglySlider(
                        progress = 0.4f,
                        onSeek = {},
                        playing = true,
                        activeColor = MaterialTheme.colorScheme.primary,
                        inactiveColor = MaterialTheme.colorScheme.outlineVariant,
                        visibleCycles = 4f,
                        phaseDurationMs = 3200,
                        interactive = false,
                        pillThumb = true,
                    )
                    PlayerSliderStyle.Wavy -> WavySeekBar(
                        progress = 0.4f,
                        onSeek = {},
                        playing = true,
                        activeColor = MaterialTheme.colorScheme.primary,
                        inactiveColor = MaterialTheme.colorScheme.outlineVariant,
                        interactive = false,
                    )
                    PlayerSliderStyle.Slim -> SlimSeekBar(
                        progress = .4f, onSeek = {}, interactive = false,
                        activeColor = MaterialTheme.colorScheme.primary,
                        inactiveColor = MaterialTheme.colorScheme.outlineVariant,
                    )
                    PlayerSliderStyle.Default -> Slider(
                        value = 0.4f,
                        onValueChange = {},
                        enabled = false,
                        colors = SliderDefaults.colors(
                            disabledThumbColor = MaterialTheme.colorScheme.primary,
                            disabledActiveTrackColor = MaterialTheme.colorScheme.primary,
                            disabledInactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
