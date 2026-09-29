package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.painterResource
import com.example.AppFontStyle
import com.example.DisplayDensity
import com.example.GridCellSize
import com.example.R
import com.example.ThemeMode
import com.example.ui.component.SwitchPreference
import com.example.ui.component.settingsFocusTarget
import com.example.ui.theme.MuseFlowShapes
import com.example.ui.theme.MuseFlowSpacing
import com.example.ui.theme.fontFamilyFor

@Composable
internal fun ThemeModeCards(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MuseFlowSpacing.small)) {
        Text("THEME MODE", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp))
        listOf(ThemeMode.System to ThemeMode.Light, ThemeMode.Dark to ThemeMode.Amoled).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MuseFlowSpacing.small)) {
                listOf(pair.first, pair.second).forEach { mode ->
                    ThemeModeCard(mode, selected == mode, { onSelect(mode) }, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ThemeModeCard(mode: ThemeMode, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val icon: ImageVector = when (mode) {
        ThemeMode.System -> Icons.Default.BrightnessAuto
        ThemeMode.Light -> Icons.Default.LightMode
        ThemeMode.Dark -> Icons.Default.DarkMode
        ThemeMode.Amoled -> Icons.Default.Contrast
    }
    val sample = when (mode) {
        ThemeMode.System -> Brush.horizontalGradient(listOf(Color(0xFFF3F0F8), Color(0xFF242128)))
        ThemeMode.Light -> Brush.horizontalGradient(listOf(Color(0xFFF8F5FC), Color(0xFFE4DBF2)))
        ThemeMode.Dark -> Brush.horizontalGradient(listOf(Color(0xFF3B3545), Color(0xFF17141C)))
        ThemeMode.Amoled -> Brush.horizontalGradient(listOf(Color.Black, Color.Black))
    }
    Surface(
        modifier = modifier.settingsFocusTarget(mode.label).border(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            RoundedCornerShape(MuseFlowShapes.card),
        ).clickable(onClick = onClick).semantics {
            role = Role.RadioButton
            this.selected = selected
        }.testTag("theme_mode_${mode.name.lowercase()}"),
        shape = RoundedCornerShape(MuseFlowShapes.card),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().height(50.dp).background(sample, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null,
                    tint = if (mode == ThemeMode.Light) Color(0xFF423650) else Color.White)
            }
            Text(mode.label, style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
internal fun ColorPaletteControls(
    seed: Color,
    fromAlbum: Boolean,
    onFromAlbumChange: (Boolean) -> Unit,
    onSeedChange: (Color) -> Unit,
    onFineTune: () -> Unit,
) {
    Surface(modifier = Modifier.settingsFocusTarget("Accent colour"),
        shape = RoundedCornerShape(MuseFlowShapes.card), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            SwitchPreference(
                title = "Colour from album art",
                subtitle = "Follow the current cover's best colour; use your saved hue when no art is available.",
                checked = fromAlbum,
                onCheckedChange = onFromAlbumChange,
            )
            if (fromAlbum) {
                Text("The app palette follows the song playing now.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            } else {
                val hsv = remember(seed) { FloatArray(3).also { android.graphics.Color.colorToHSV(seed.toArgb(), it) } }
                var draftHue by remember(seed) { mutableFloatStateOf(-1f) }
                val shownHue = if (draftHue >= 0f) draftHue else hsv[0]
                val shown = Color.hsv(shownHue, hsv[1].coerceAtLeast(.55f), hsv[2].coerceAtLeast(.7f))
                Column(Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth().height(52.dp).background(shown, RoundedCornerShape(16.dp)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Selected hue", color = if (shown.red * .299f + shown.green * .587f + shown.blue * .114f > .55f) Color.Black else Color.White,
                            style = MaterialTheme.typography.labelLarge)
                    }
                    Text("Hue ${shownHue.toInt()}°", style = MaterialTheme.typography.labelMedium)
                    Box(contentAlignment = Alignment.Center) {
                        Box(Modifier.fillMaxWidth().height(20.dp).background(
                            Brush.horizontalGradient((0..12).map { Color.hsv(it * 30f, .85f, .95f) }),
                            RoundedCornerShape(50)))
                        Slider(
                            value = shownHue,
                            onValueChange = { draftHue = it },
                            onValueChangeFinished = {
                                if (draftHue >= 0f) onSeedChange(Color.hsv(draftHue, hsv[1].coerceAtLeast(.55f), hsv[2].coerceAtLeast(.7f)))
                                draftHue = -1f
                            },
                            valueRange = 0f..360f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color.Transparent,
                                inactiveTrackColor = Color.Transparent,
                            ),
                            modifier = Modifier.semantics { contentDescription = "Accent hue" }
                                .testTag("appearance_hue_slider"),
                        )
                    }
                    TextButton(onClick = onFineTune) { Text("Fine tune colour") }
                }
            }
        }
    }
}

@Composable
internal fun FontStyleDialog(selected: AppFontStyle, onSelect: (AppFontStyle) -> Unit, onDismiss: () -> Unit) {
    AppearanceChoiceDialog("Font style", onDismiss) {
        AppFontStyle.entries.forEach { style ->
            ChoiceCard(style.label, style == selected, { onSelect(style); onDismiss() },
                titleFontFamily = fontFamilyFor(style),
                titleFontWeight = if (style == AppFontStyle.DotMatrix) FontWeight.Normal else FontWeight.Bold) {
                Text("MuseFlow", fontFamily = fontFamilyFor(style),
                    fontWeight = if (style == AppFontStyle.DotMatrix) FontWeight.Normal else FontWeight.Bold,
                    fontSize = if (style == AppFontStyle.DotMatrix) 24.sp else 22.sp)
                Text("Midnight Drive · Artist · 3:42", fontFamily = fontFamilyFor(style),
                    fontWeight = if (style == AppFontStyle.DotMatrix) FontWeight.Normal else null,
                    style = MaterialTheme.typography.bodyMedium)
                if (style == AppFontStyle.DotMatrix) Text("Doto · Nothing-inspired display style", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun DisplayDensityDialog(current: DisplayDensity, onSelect: (DisplayDensity) -> Unit, onDismiss: () -> Unit) {
    val baseDensity = LocalDensity.current
    AppearanceChoiceDialog("Display density", onDismiss) {
        DisplayDensity.entries.forEach { option ->
            ChoiceCard(option.label, option == current, { onSelect(option); onDismiss() }) {
                CompositionLocalProvider(LocalDensity provides Density(baseDensity.density * option.scale / current.scale, baseDensity.fontScale)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(52.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(126.dp, 10.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .55f), RoundedCornerShape(50)))
                            Box(Modifier.size(86.dp, 8.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .3f), RoundedCornerShape(50)))
                        }
                    }
                }
                Text("Spacing shown at ${((option.scale) * 100).toInt()}%", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun CardSizeDialog(selected: GridCellSize, onSelect: (GridCellSize) -> Unit, onDismiss: () -> Unit) {
    AppearanceChoiceDialog("Home card size", onDismiss) {
        GridCellSize.entries.forEach { option ->
            ChoiceCard(option.label, option == selected, { onSelect(option); onDismiss() }) {
                Image(painter = painterResource(R.drawable.preview_album_art), contentDescription = null,
                    modifier = Modifier.size(option.homeSizeDp.dp))
                Text("Sample album", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun AppearanceChoiceDialog(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(.9f).widthIn(max = 520.dp).heightIn(max = 620.dp),
            shape = RoundedCornerShape(MuseFlowShapes.sheet),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(MuseFlowSpacing.medium),
                verticalArrangement = Arrangement.spacedBy(MuseFlowSpacing.small)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close") }
                }
                content()
            }
        }
    }
}

@Composable
private fun ChoiceCard(title: String, selected: Boolean, onClick: () -> Unit,
    titleFontFamily: androidx.compose.ui.text.font.FontFamily? = null,
    titleFontWeight: FontWeight? = null,
    content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().border(if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            RoundedCornerShape(MuseFlowShapes.card))
            .clickable(onClick = onClick).semantics {
                role = Role.RadioButton
                this.selected = selected
            }.padding(MuseFlowSpacing.medium),
        verticalArrangement = Arrangement.spacedBy(MuseFlowSpacing.small),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium,
            fontFamily = titleFontFamily,
            fontWeight = titleFontWeight,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        content()
    }
}
