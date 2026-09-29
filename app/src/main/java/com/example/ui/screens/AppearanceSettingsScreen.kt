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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.AppSettingsViewModel
import com.example.ThemeMode
import com.example.ThemeViewModel
import com.example.ui.component.ActionPreference
import com.example.ui.component.AppearanceLivePreview
import com.example.ui.component.ColorPickerDialog
import com.example.ui.component.NavigationPreference
import com.example.ui.component.PreferenceGroup
import com.example.ui.theme.DefaultThemeColor

@Composable
fun AppearanceSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val themeViewModel: ThemeViewModel = viewModel()
    val theme by themeViewModel.themeState.collectAsState()
    val settingsViewModel: AppSettingsViewModel = viewModel()
    val settings by settingsViewModel.state.collectAsState()

    var showColorPicker by remember { mutableStateOf(false) }
    var showFontPicker by remember { mutableStateOf(false) }
    var showDensityPicker by remember { mutableStateOf(false) }
    var showCardSizePicker by remember { mutableStateOf(false) }

    if (showColorPicker) ColorPickerDialog(
        initialColor = theme.seedColor,
        onConfirm = { themeViewModel.setSeedColor(it); showColorPicker = false },
        onDismiss = { showColorPicker = false },
    )
    if (showFontPicker) FontStyleDialog(settings.fontStyle, settingsViewModel::setFontStyle) { showFontPicker = false }
    if (showDensityPicker) DisplayDensityDialog(settings.displayDensity, settingsViewModel::setDisplayDensity) { showDensityPicker = false }
    if (showCardSizePicker) CardSizeDialog(settings.gridCellSize, settingsViewModel::setGridCellSize) { showCardSizePicker = false }

    Column(
        modifier = modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
            .testTag("appearance_settings_screen"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("appearance_back")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Appearance", style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground)
        }
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 200.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ThemeModeCards(theme.mode, themeViewModel::setMode)
            Text("COLOUR PALETTE", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp))
            ColorPaletteControls(
                seed = theme.seedColor,
                fromAlbum = theme.dynamicAlbumColor,
                onFromAlbumChange = themeViewModel::setDynamicAlbumColor,
                onSeedChange = themeViewModel::setSeedColor,
                onFineTune = { showColorPicker = true },
            )
            AppearanceLivePreview(settings)
            PreferenceGroup(title = "Personalize layout") {
                NavigationPreference("Font style", settings.fontStyle.label, { showFontPicker = true })
                NavigationPreference("Display density", settings.displayDensity.label, { showDensityPicker = true })
                NavigationPreference("Card size", settings.gridCellSize.label, { showCardSizePicker = true })
            }
            PreferenceGroup(title = "Reset") {
                ActionPreference(
                    title = "Reset appearance",
                    subtitle = "Restore theme, colour, type, spacing and card size defaults.",
                    onClick = {
                        themeViewModel.setSeedColor(DefaultThemeColor)
                        themeViewModel.setMode(ThemeMode.Dark)
                        themeViewModel.setDynamicAlbumColor(false)
                        settingsViewModel.resetAppearance()
                    },
                )
            }
        }
    }
}
