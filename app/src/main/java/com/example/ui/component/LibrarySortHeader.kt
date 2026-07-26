package com.example.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Sort control + direction toggle + optional list/grid switch + item count, shared by every
 * sortable list in Library.
 *
 * The direction arrow *rotates* between ascending and descending rather than swapping icons, so
 * the change reads as one control flipping instead of two different buttons.
 */
@Composable
fun <T : Enum<T>> LibrarySortHeader(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    ascending: Boolean,
    onToggleDirection: () -> Unit,
    optionLabel: (T) -> String,
    countLabel: String,
    modifier: Modifier = Modifier,
    /** Null hides the list/grid switch - not every section has a grid form. */
    isGrid: Boolean? = null,
    onToggleGrid: (() -> Unit)? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (ascending) 180f else 0f,
        label = "sort_direction",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box {
            TextButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.testTag("library_sort_button"),
            ) {
                Text(
                    text = optionLabel(selected),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Change sort order",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option)) },
                        onClick = {
                            onSelect(option)
                            menuExpanded = false
                        },
                        leadingIcon = if (option == selected) {
                            { Icon(Icons.Default.Check, contentDescription = null) }
                        } else null,
                        modifier = Modifier.testTag("library_sort_option_${option.name.lowercase()}"),
                    )
                }
            }
        }

        IconButton(
            onClick = onToggleDirection,
            modifier = Modifier.testTag("library_sort_direction"),
        ) {
            Icon(
                imageVector = Icons.Default.ArrowDownward,
                contentDescription = if (ascending) "Ascending" else "Descending",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(arrowRotation),
            )
        }

        Box(modifier = Modifier.weight(1f))

        Text(
            text = countLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (isGrid != null && onToggleGrid != null) {
            IconButton(
                onClick = onToggleGrid,
                modifier = Modifier.testTag("library_view_toggle"),
            ) {
                Icon(
                    imageVector = if (isGrid) {
                        Icons.AutoMirrored.Filled.List
                    } else {
                        Icons.Default.GridView
                    },
                    contentDescription = if (isGrid) "Switch to list" else "Switch to grid",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
