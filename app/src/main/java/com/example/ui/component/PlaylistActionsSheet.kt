package com.example.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ImageNotSupported
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Long-press actions for a playlist row, distinct from [TrackActionsSheet]: a playlist has no
 * like/download state of its own to show inline, but does have identity worth surfacing (cover,
 * name, track count) and actions that operate on all its tracks at once.
 *
 * The header's heart toggles pin - there is no separate "liked playlist" concept in this app, so
 * rather than add one, pin gets the header's shortcut on top of its own row further down.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistActionsSheet(
    name: String,
    songCount: Int,
    coverImageUrl: String?,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onShuffle: () -> Unit,
    onStartRadio: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    /** Opens the system image picker - absent from the row entirely (rather than calling with a
     * no-op) would be wrong here since every playlist can always get a custom cover; unlike the
     * other optional actions above, this one is never conditionally omitted. */
    onChangeCover: () -> Unit = {},
    /** Present only when a custom cover is actually set - there's nothing to remove otherwise. */
    hasCustomCover: Boolean = false,
    onRemoveCustomCover: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirmingDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(
            topStart = com.example.ui.theme.MuseFlowShapes.sheet,
            topEnd = com.example.ui.theme.MuseFlowShapes.sheet,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
                .testTag("playlist_actions_sheet"),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    if (coverImageUrl != null) {
                        AsyncImage(
                            model = coverImageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(52.dp),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 14.dp),
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "$songCount ${if (songCount == 1) "song" else "songs"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                IconButton(
                    onClick = onTogglePin,
                    modifier = Modifier.testTag("playlist_header_pin"),
                ) {
                    Icon(
                        imageVector = if (isPinned) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isPinned) "Unpin playlist" else "Pin playlist",
                        tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SheetDivider()

            // A pill row rather than list rows for Shuffle - it's the one action someone taps
            // immediately on opening a playlist they already know, so it gets the same one-tap
            // prominence PlaylistDetailScreen's own play/shuffle row gives it.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                ActionPill(
                    icon = Icons.Default.Shuffle,
                    label = "Shuffle",
                    onClick = { onShuffle(); onDismiss() },
                    testTag = "playlist_action_shuffle",
                )
            }

            SheetDivider()

            PlaylistSheetAction(
                icon = Icons.Default.Radio,
                label = "Start radio",
                supporting = "Create a station based on this playlist",
                onClick = { onStartRadio(); onDismiss() },
                testTag = "playlist_action_radio",
            )
            PlaylistSheetAction(
                icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                label = "Play next",
                supporting = "Add to the top of your queue",
                onClick = { onPlayNext(); onDismiss() },
                testTag = "playlist_action_play_next",
            )
            PlaylistSheetAction(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                label = "Add to queue",
                supporting = "Add to the bottom of your queue",
                onClick = { onAddToQueue(); onDismiss() },
                testTag = "playlist_action_add_to_queue",
            )
            PlaylistSheetAction(
                icon = Icons.Default.PushPin,
                label = if (isPinned) "Unpin playlist" else "Pin playlist",
                supporting = null,
                onClick = { onTogglePin(); onDismiss() },
                testTag = "playlist_action_pin",
            )
            PlaylistSheetAction(
                icon = Icons.Default.Download,
                label = "Download",
                supporting = "Make every song available offline",
                onClick = { onDownload(); onDismiss() },
                testTag = "playlist_action_download",
            )
            PlaylistSheetAction(
                icon = Icons.Default.Share,
                label = "Share",
                supporting = "Send this playlist to another MuseFlow user",
                onClick = { onShare(); onDismiss() },
                testTag = "playlist_action_share",
            )
            PlaylistSheetAction(
                icon = Icons.Default.Image,
                label = "Change cover",
                supporting = "Pick a photo instead of the auto-generated one",
                onClick = { onChangeCover(); onDismiss() },
                testTag = "playlist_action_change_cover",
            )
            if (hasCustomCover) {
                PlaylistSheetAction(
                    icon = Icons.Default.ImageNotSupported,
                    label = "Remove custom cover",
                    supporting = "Go back to the auto-generated cover",
                    onClick = { onRemoveCustomCover(); onDismiss() },
                    testTag = "playlist_action_remove_cover",
                )
            }
            PlaylistSheetAction(
                icon = Icons.Default.Delete,
                label = "Delete",
                supporting = "Permanently remove this playlist",
                onClick = { confirmingDelete = true },
                testTag = "playlist_action_delete",
            )
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete \"$name\"?") },
            text = { Text("This removes the playlist and its track list. Downloaded files and liked songs are unaffected. This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        onDelete()
                        onDismiss()
                    },
                    modifier = Modifier.testTag("playlist_delete_confirm"),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") }
            },
        )
    }
}

/**
 * The "⋮" menu opened from a *remote* (not-yet-saved, or already-saved-from-online) playlist's own
 * screen - the counterpart to [PlaylistActionsSheet] for a playlist that isn't necessarily a real
 * Library entry yet, so it has none of that one's pin/delete/change-cover/queue concepts.
 *
 * Replaces what used to be five circle buttons (Shuffle, Play, Save, Download, Share) crowded into
 * one row on [com.example.ui.screens.RemotePlaylistScreen] - Shuffle and Play stay as their own
 * one-tap buttons there, and this collects the other three behind a single "⋮" instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemotePlaylistActionsSheet(
    title: String,
    songCount: Int,
    imageUrl: String?,
    addedToLibrary: Boolean,
    onSaveToLibrary: () -> Unit,
    onDownload: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(
            topStart = com.example.ui.theme.MuseFlowShapes.sheet,
            topEnd = com.example.ui.theme.MuseFlowShapes.sheet,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
                .testTag("remote_playlist_actions_sheet"),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    if (imageUrl != null) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(52.dp),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 14.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "$songCount ${if (songCount == 1) "song" else "songs"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SheetDivider()

            PlaylistSheetAction(
                icon = if (addedToLibrary) Icons.Default.Check else Icons.AutoMirrored.Filled.PlaylistAdd,
                label = if (addedToLibrary) "Added to Library" else "Add to Library",
                supporting = if (addedToLibrary) null else "Save this playlist to your own library",
                onClick = { onSaveToLibrary(); onDismiss() },
                testTag = "remote_playlist_action_save",
            )
            PlaylistSheetAction(
                icon = Icons.Default.Download,
                label = "Download",
                supporting = "Make every song available offline",
                onClick = { onDownload(); onDismiss() },
                testTag = "remote_playlist_action_download",
            )
            PlaylistSheetAction(
                icon = Icons.Default.Share,
                label = "Share",
                supporting = "Send this playlist's link to another MuseFlow user",
                onClick = { onShare(); onDismiss() },
                testTag = "remote_playlist_action_share",
            )
        }
    }
}

@Composable
private fun SheetDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun ActionPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** A row with a supporting line under the label - the mockup's two-line rows - rather than
 * [TrackActionsSheet]'s single-line ones, since a whole-playlist action benefits from saying what
 * it means (e.g. that "Download" covers every song) more than a single-track one does. */
@Composable
private fun PlaylistSheetAction(
    icon: ImageVector,
    label: String,
    supporting: String?,
    onClick: () -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.padding(start = 18.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
