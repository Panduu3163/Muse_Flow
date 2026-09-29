package com.example.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import com.example.ui.theme.MuseFlowShapes
import com.example.ui.theme.MuseFlowSpacing
import com.example.SettingsSearchIndex

/*
 * The settings DSL.
 *
 * Everything in Settings is built from these four primitives, so adding an option costs a few
 * declarative lines instead of a hand-assembled Row each time. This matters at the scale MuseFlow
 * is heading for (~120 preferences): without it, every option is bespoke and the effort compounds.
 */

/** The control title requested by Settings search on the current destination. */
val LocalSettingsFocus = staticCompositionLocalOf { "" }
val LocalSettingsRoute = staticCompositionLocalOf { "" }

/** Bring a searched control into view after its destination has finished entering. */
@Composable
fun Modifier.settingsFocusTarget(title: String): Modifier {
    val target = LocalSettingsFocus.current == SettingsSearchIndex.idFor(LocalSettingsRoute.current, title)
    val requester = remember { BringIntoViewRequester() }
    var positioned by remember { mutableStateOf(false) }
    LaunchedEffect(target, positioned) {
        if (target && positioned) {
            delay(250)
            requester.bringIntoView()
        }
    }
    return this
        .bringIntoViewRequester(requester)
        .onGloballyPositioned { positioned = true }
        .background(
            if (target) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent,
            RoundedCornerShape(MuseFlowShapes.control),
        )
}

/** A titled card grouping related preferences. */
@Composable
fun PreferenceGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(MuseFlowShapes.card),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(modifier = Modifier.padding(vertical = 6.dp)) { content() }
        }
    }
}

/** On/off preference. */
@Composable
fun SwitchPreference(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    PreferenceRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        enabled = enabled,
        onClick = { onCheckedChange(!checked) },
        role = Role.Switch,
        stateLabel = if (checked) "On" else "Off",
        trailing = {
            Switch(checked = checked, onCheckedChange = null, enabled = enabled,
                modifier = Modifier.clearAndSetSemantics { })
        },
    )
}

/**
 * Single-choice preference. Tapping the row opens a full-screen-style bottom sheet listing every
 * option with the current selection checked - closing on tap - rather than an inline dropdown, so
 * options with long labels or many entries (accent style, grid size...) get room to breathe.
 *
 * [label] converts a value to its display name, so callers can pass enums directly without every
 * enum having to know about the UI.
 */
@Composable
fun <T> ListPreference(
    title: String,
    subtitle: String? = null,
    selected: T,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    var showPicker by remember { mutableStateOf(false) }

    PreferenceRow(
        title = title,
        subtitle = if (subtitle == null || subtitle == label(selected)) label(selected)
            else "${label(selected)} · $subtitle",
        icon = icon,
        enabled = enabled,
        onClick = { showPicker = true },
        trailing = {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
    )

    if (showPicker) {
        ListPreferencePicker(
            title = title,
            selected = selected,
            options = options,
            label = label,
            onSelect = {
                onSelect(it)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

/** The full-screen picker [ListPreference] opens - a bottom sheet rather than a true separate
 * screen/route, matching the house pattern already used by [StatsScreen]'s listening-summary
 * sheet, so a value pick doesn't cost a back-stack entry. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ListPreferencePicker(
    title: String,
    selected: T,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = MuseFlowShapes.sheet, topEnd = MuseFlowShapes.sheet),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp).padding(top = 4.dp, bottom = 12.dp),
            )
            options.forEach { option ->
                val isSelected = option == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable { onSelect(option) }
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                        .testTag("list_pref_option_${label(option).lowercase().replace(" ", "_")}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = label(option),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(text = "Cancel")
                }
            }
        }
    }
}

/**
 * Numeric preference with a slider.
 *
 * The value is committed on release rather than on every drag frame, so a preference backed by
 * DataStore doesn't issue a disk write per pixel of movement.
 */
@Composable
fun SliderPreference(
    title: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    enabled: Boolean = true,
    valueLabel: (Int) -> String = { it.toString() },
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging?.roundToInt() ?: value

    Column(modifier = Modifier.settingsFocusTarget(title).padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.4f),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueLabel(shown),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = shown.toFloat(),
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onValueChange(it.roundToInt()) }
                dragging = null
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            enabled = enabled,
            modifier = Modifier.heightIn(min = 56.dp).semantics {
                contentDescription = title
                stateDescription = valueLabel(shown)
            },
        )
    }
}

/** Navigates somewhere else (a sub-screen, an external page). */
@Composable
fun NavigationPreference(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    PreferenceRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        enabled = enabled,
        onClick = onClick,
        trailing = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

/**
 * A one-shot action with no persisted state and nowhere to navigate - "Clear cache", "Clear
 * listening history". [destructive] tints the title (and, unless overridden, the icon) with the
 * error colour, so a row that deletes something reads as different from a row that just opens
 * something - callers are still expected to gate the actual deletion behind a confirmation dialog
 * themselves; this only supplies the row's look.
 */
@Composable
fun ActionPreference(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    destructive: Boolean = false,
) {
    val titleColor = if (destructive) MaterialTheme.colorScheme.error else null
    PreferenceRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        enabled = enabled,
        onClick = onClick,
        titleColor = titleColor,
        iconTint = titleColor,
        trailing = {},
    )
}

/** Shared row shape, so every preference type lines up on the same grid. */
@Composable
private fun PreferenceRow(
    title: String,
    subtitle: String?,
    icon: ImageVector?,
    enabled: Boolean,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
    titleColor: Color? = null,
    iconTint: Color? = null,
    role: Role? = null,
    stateLabel: String? = null,
) {
    val alpha = if (enabled) 1f else 0.4f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .settingsFocusTarget(title)
            .semantics(mergeDescendants = true) {
                if (role != null) this.role = role
                if (stateLabel != null) stateDescription = stateLabel
            }
            .clickable(enabled = enabled, role = role, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .testTag("pref_${title.lowercase().replace(" ", "_")}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(MuseFlowShapes.control))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = (iconTint ?: MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = alpha),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = (titleColor ?: MaterialTheme.colorScheme.onSurface).copy(alpha = alpha),
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                )
            }
        }
        trailing()
    }
}
