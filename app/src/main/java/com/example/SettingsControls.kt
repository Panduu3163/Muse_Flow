package com.example

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Most settings rows in the Appearance/Player & Audio/Lyrics/Library & Playlists sub-screens are
 * UI-only: they persist a preference to DataStore, but nothing else in the app reads it yet.
 * Rather than let tapping/dragging one silently do nothing, every shared control below defaults
 * to `functional = false` and routes the interaction here instead - so the option stays visible
 * (as requested) but honestly tells the user it isn't implemented, instead of pretending to work.
 */
fun showComingSoonToast(context: Context, feature: String) {
    Toast.makeText(context, "$feature - coming soon", Toast.LENGTH_SHORT).show()
}

/** A settings row that opens a dropdown menu of [options], showing [selected]'s label inline. */
@Composable
fun <T> SettingsDropdownRow(
    icon: ImageVector,
    title: String,
    options: List<T>,
    selected: T,
    labelFor: (T) -> String,
    onSelect: (T) -> Unit,
    tag: String,
    functional: Boolean = false
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (functional) expanded = true else showComingSoonToast(context, title)
                }
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag(tag),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = labelFor(selected),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Choose $title",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.testTag("${tag}_menu")
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(labelFor(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                    modifier = Modifier.testTag("${tag}_option_${labelFor(option)}")
                )
            }
        }
    }
}

/** A settings row with a slider, showing a live [valueLabel] next to the title. */
@Composable
fun SettingsSliderRow(
    icon: ImageVector,
    title: String,
    valueLabel: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    tag: String,
    steps: Int = 0,
    functional: Boolean = false
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag(tag)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box(modifier = Modifier.fillMaxWidth()) {
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("${tag}_slider")
            )
            if (!functional) {
                // A transparent overlay, not a `disabled` Slider - it should still look normal,
                // it just intercepts the drag before the Slider underneath ever sees it.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { showComingSoonToast(context, title) }
                )
            }
        }
    }
}

/** A settings row with an inline segmented control for a small, fixed set of [options]. */
@Composable
fun <T> SettingsSegmentedRow(
    icon: ImageVector,
    title: String,
    options: List<T>,
    selected: T,
    labelFor: (T) -> String,
    onSelect: (T) -> Unit,
    tag: String,
    functional: Boolean = false
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(tag)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable {
                            if (functional) onSelect(option) else showComingSoonToast(context, title)
                        }
                        .padding(vertical = 10.dp)
                        .testTag("${tag}_option_${labelFor(option)}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = labelFor(option),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isSelected) Color(0xFF0C0C0E) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** A settings row for choosing which theme color (Primary/Secondary/Tertiary) drives player controls. */
@Composable
fun PlayerButtonColorRow(
    title: String,
    selected: PlayerButtonColorOption,
    onSelect: (PlayerButtonColorOption) -> Unit,
    tag: String,
    functional: Boolean = false
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(tag)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PlayerButtonColorOption.entries.forEach { option ->
                val swatchColor = when (option) {
                    PlayerButtonColorOption.Primary -> MaterialTheme.colorScheme.primary
                    PlayerButtonColorOption.Secondary -> MaterialTheme.colorScheme.secondary
                    PlayerButtonColorOption.Tertiary -> MaterialTheme.colorScheme.tertiary
                }
                val isSelected = option == selected
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(swatchColor)
                        .clickable {
                            if (functional) onSelect(option) else showComingSoonToast(context, title)
                        }
                        .testTag("${tag}_${option.name}"),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "${option.label} selected",
                            tint = Color(0xFF0C0C0E),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
