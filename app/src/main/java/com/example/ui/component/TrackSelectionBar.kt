package com.example.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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

/**
 * The contextual bar shown while rows are selected.
 *
 * It takes the place of the screen's own header rather than stacking below it, which is what makes
 * positional selection safe: the sort controls it covers are exactly the ones that would renumber
 * the list underneath a live selection.
 *
 * Close and select-all are the only bare icons - everything else lives in the overflow with a
 * text label. Bare icons with no label (as Play next/Add to queue/Add to playlist used to be)
 * read as unclear at a glance; a labelled dropdown row costs one extra tap but leaves nothing to
 * guess. The two actions that can lose data (deleting downloads, removing from a playlist) sit
 * there too, which doubles as the "needs a second, deliberate tap" gate they warrant anyway.
 */
@Composable
fun TrackSelectionBar(
    selectedCount: Int,
    allSelected: Boolean,
    /** True when every selected track is already liked, which flips the like action's direction. */
    allLiked: Boolean,
    onClearSelection: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleLike: () -> Unit,
    onDownload: () -> Unit,
    /** Absent unless at least one selected track has a completed download to delete. */
    onDeleteDownloads: (() -> Unit)? = null,
    /** Absent outside a playlist that owns the rows - the same rule the single-track sheet uses. */
    onRemoveFromPlaylist: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var overflowOpen by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .testTag("selection_bar"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onClearSelection,
                modifier = Modifier.testTag("selection_clear"),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel selection",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }

            BarAction(
                icon = Icons.Default.SelectAll,
                label = if (allSelected) "Select none" else "Select all",
                onClick = onToggleSelectAll,
                testTag = "selection_select_all",
            )

            Text(
                text = "$selectedCount selected",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
                    .testTag("selection_count"),
            )

            BarAction(
                icon = Icons.Default.MoreVert,
                label = "More actions",
                onClick = { overflowOpen = true },
                testTag = "selection_overflow",
            )

            DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Play next") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null) },
                    onClick = {
                        overflowOpen = false
                        onPlayNext()
                    },
                    modifier = Modifier.testTag("selection_play_next"),
                )
                DropdownMenuItem(
                    text = { Text("Add to queue") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null) },
                    onClick = {
                        overflowOpen = false
                        onAddToQueue()
                    },
                    modifier = Modifier.testTag("selection_add_to_queue"),
                )
                DropdownMenuItem(
                    text = { Text("Add to playlist") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
                    onClick = {
                        overflowOpen = false
                        onAddToPlaylist()
                    },
                    modifier = Modifier.testTag("selection_add_to_playlist"),
                )
                DropdownMenuItem(
                    text = { Text(if (allLiked) "Remove from Liked" else "Add to Liked") },
                    leadingIcon = {
                        Icon(
                            imageVector = if (allLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        overflowOpen = false
                        onToggleLike()
                    },
                    modifier = Modifier.testTag("selection_like"),
                )
                DropdownMenuItem(
                    text = { Text("Download") },
                    leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                    onClick = {
                        overflowOpen = false
                        onDownload()
                    },
                    modifier = Modifier.testTag("selection_download"),
                )
                onDeleteDownloads?.let { deleteDownloads ->
                    DropdownMenuItem(
                        text = { Text("Delete downloads") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = {
                            overflowOpen = false
                            deleteDownloads()
                        },
                        modifier = Modifier.testTag("selection_delete_downloads"),
                    )
                }
                onRemoveFromPlaylist?.let { removeFromPlaylist ->
                    DropdownMenuItem(
                        text = { Text("Remove from this playlist") },
                        leadingIcon = { Icon(Icons.Default.PlaylistRemove, contentDescription = null) },
                        onClick = {
                            overflowOpen = false
                            removeFromPlaylist()
                        },
                        modifier = Modifier.testTag("selection_remove_from_playlist"),
                    )
                }
            }
        }
    }
}

@Composable
private fun BarAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String,
) {
    IconButton(onClick = onClick, modifier = Modifier.testTag(testTag)) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(22.dp),
        )
    }
}
