package com.example.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/*
 * The settings DSL.
 *
 * Everything in Settings is built from these four primitives, so adding an option costs a few
 * declarative lines instead of a hand-assembled Row each time. This matters at the scale MuseFlow
 * is heading for (~120 preferences): without it, every option is bespoke and the effort compounds.
 */

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
            shape = RoundedCornerShape(20.dp),
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
        trailing = {
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        },
    )
}

/**
 * Single-choice preference rendered as a dropdown.
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
    var expanded by remember { mutableStateOf(false) }

    Box {
        PreferenceRow(
            title = title,
            subtitle = subtitle ?: label(selected),
            icon = icon,
            enabled = enabled,
            onClick = { expanded = true },
            trailing = {
                Text(
                    text = label(selected),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
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

    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
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

/** Shared row shape, so every preference type lines up on the same grid. */
@Composable
private fun PreferenceRow(
    title: String,
    subtitle: String?,
    icon: ImageVector?,
    enabled: Boolean,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.4f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("pref_${title.lowercase().replace(" ", "_")}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
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
