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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
 * Long-press/overflow actions for a track.
 *
 * Presented as a modal sheet rather than a dropdown so the same surface works from any list and
 * has room for the track's identity at the top - useful when several search results share a title.
 *
 * Actions that only make sense on some surfaces are passed as nullable lambdas and are simply
 * absent when null, rather than shown greyed out: a menu that lists things you cannot do is worse
 * than a shorter menu. Callers are expected to omit "Remove from playlist" outside a playlist and
 * "Share" for a track with no shareable source.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackActionsSheet(
    title: String,
    artist: String,
    imageUrl: String?,
    /** The source's album name, shown as "View album"'s supporting text - a [TrackResult] with a
     * real `albumId` always has this too (both come from the same source field), so it's only
     * ever read when [onGoToAlbum] is non-null. */
    albumLabel: String? = null,
    isLiked: Boolean,
    isDownloaded: Boolean,
    downloadProgress: Int?,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onToggleLike: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDismiss: () -> Unit,
    /** Absent for local files, which have no backend id to seed a mix from. */
    onStartRadio: (() -> Unit)? = null,
    /** Absent unless the track is shown inside a playlist that owns it. */
    onRemoveFromPlaylist: (() -> Unit)? = null,
    /** Absent unless a completed download exists to delete. */
    onDeleteDownload: (() -> Unit)? = null,
    /** Absent for local files, which have no public URL. */
    onShare: (() -> Unit)? = null,
    /** Present only from the History screen - the row's own dedicated remove button was folded
     * into the sheet once the row's trailing slot was needed for the menu button instead. */
    onRemoveFromHistory: (() -> Unit)? = null,
    onShowDetails: () -> Unit,
    /** Absent unless this track's source exposed an album/artist browseId - a local file, or a
     * single not attached to any album, has neither. */
    onGoToArtist: (() -> Unit)? = null,
    onGoToAlbum: (() -> Unit)? = null,
    /** False only from Now Playing's own menu: that screen already has its own dedicated Play
     * next/queue affordance context (the queue panel itself), and re-offering it here for the
     * track that's already playing was confusing more than useful. Every other call site keeps
     * these, unchanged. */
    showQueueActions: Boolean = true,
    /** False only from Now Playing's own menu: the like state already has its own header/footer
     * control there, so a second one in the sheet was a pure duplicate. */
    showLikeAction: Boolean = true,
    /** False only from Now Playing's own menu, same reasoning as [showLikeAction] - Now Playing
     * already has its own download control. */
    showDownloadAction: Boolean = true,
    /** Present only when opened from Now Playing with lyrics on screen - folds the lyrics panel's
     * own former menu into this one instead of two separate "⋮" buttons stacked on the same
     * screen. Null (and hidden) everywhere else. */
    onCopyLyrics: (() -> Unit)? = null,
    onSearchLyricsOnline: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
                .testTag("track_actions_sheet"),
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
                            imageVector = Icons.Default.MusicNote,
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
                        text = artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Same duplicate-entry-point pattern as the playlist sheet's header pin: this
                // toggles the exact flag the "Add to Liked"/"Remove from Liked" row below does,
                // just reachable without scrolling past queue actions first.
                IconButton(
                    onClick = onToggleLike,
                    modifier = Modifier.testTag("action_header_like"),
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isLiked) "Remove from Liked" else "Add to Liked",
                        tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SheetDivider()

            // The three most common actions as pills, matching the playlist sheet's Shuffle pill.
            // Fewer than three renders fine - a pill is simply omitted when its action is absent
            // for this track, rather than shown disabled.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                onStartRadio?.let { startRadio ->
                    ActionPill(
                        icon = Icons.Default.Radio,
                        label = "Start radio",
                        onClick = { startRadio(); onDismiss() },
                        testTag = "action_start_radio",
                    )
                }
                if (showQueueActions) {
                    ActionPill(
                        icon = Icons.AutoMirrored.Filled.QueueMusic,
                        label = "Add",
                        onClick = { onAddToQueue(); onDismiss() },
                        testTag = "action_add_to_queue",
                    )
                }
                onShare?.let { share ->
                    ActionPill(
                        icon = Icons.Default.Share,
                        label = "Share",
                        onClick = { share(); onDismiss() },
                        testTag = "action_share",
                    )
                }
            }

            SheetDivider()

            if (showQueueActions) {
                SheetAction(
                    icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                    label = "Play next",
                    supporting = "Add to the top of your queue",
                    onClick = { onPlayNext(); onDismiss() },
                    testTag = "action_play_next",
                )
            }

            if (showLikeAction) {
                SheetAction(
                    icon = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    label = if (isLiked) "Remove from Liked" else "Add to Liked",
                    tint = if (isLiked) MaterialTheme.colorScheme.primary else null,
                    onClick = { onToggleLike(); onDismiss() },
                    testTag = "action_like",
                )
            }

            if (showDownloadAction) {
                when {
                    // A finished download used to render as a dead "Downloaded" row that only
                    // closed the sheet. Deleting is the action someone actually wants at that point.
                    isDownloaded -> onDeleteDownload?.let { deleteDownload ->
                        SheetAction(
                            icon = Icons.Default.Delete,
                            label = "Delete download",
                            onClick = { deleteDownload(); onDismiss() },
                            testTag = "action_delete_download",
                        )
                    }
                    downloadProgress != null -> SheetAction(
                        icon = Icons.Default.Downloading,
                        // -1 is the repository's "started, no percentage yet" sentinel.
                        label = if (downloadProgress >= 0) "Downloading $downloadProgress% - tap to cancel"
                        else "Starting download - tap to cancel",
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = { onCancelDownload(); onDismiss() },
                        testTag = "action_cancel_download",
                    )
                    else -> SheetAction(
                        icon = Icons.Default.Download,
                        label = "Download",
                        supporting = "Make available for offline playback",
                        onClick = { onDownload(); onDismiss() },
                        testTag = "action_download",
                    )
                }
            }

            SheetAction(
                icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                label = "Add to playlist",
                onClick = { onAddToPlaylist(); onDismiss() },
                testTag = "action_add_to_playlist",
            )

            onRemoveFromPlaylist?.let { removeFromPlaylist ->
                SheetAction(
                    icon = Icons.Default.PlaylistRemove,
                    label = "Remove from this playlist",
                    onClick = { removeFromPlaylist(); onDismiss() },
                    testTag = "action_remove_from_playlist",
                )
            }

            onRemoveFromHistory?.let { removeFromHistory ->
                SheetAction(
                    icon = Icons.Default.Close,
                    label = "Remove from history",
                    onClick = { removeFromHistory(); onDismiss() },
                    testTag = "action_remove_from_history",
                )
            }

            onGoToArtist?.let { goToArtist ->
                SheetAction(
                    icon = Icons.Default.Person,
                    label = "View artist",
                    supporting = artist,
                    onClick = { goToArtist(); onDismiss() },
                    testTag = "action_view_artist",
                )
            }

            onGoToAlbum?.let { goToAlbum ->
                SheetAction(
                    icon = Icons.Default.Album,
                    label = "View album",
                    supporting = albumLabel,
                    onClick = { goToAlbum(); onDismiss() },
                    testTag = "action_view_album",
                )
            }

            onCopyLyrics?.let { copyLyrics ->
                SheetAction(
                    icon = Icons.Default.ContentCopy,
                    label = "Copy lyrics",
                    onClick = { copyLyrics(); onDismiss() },
                    testTag = "action_copy_lyrics",
                )
            }

            onSearchLyricsOnline?.let { searchLyrics ->
                SheetAction(
                    icon = Icons.Default.Search,
                    label = "Search lyrics online",
                    onClick = { searchLyrics(); onDismiss() },
                    testTag = "action_search_lyrics_online",
                )
            }

            SheetAction(
                icon = Icons.Default.Info,
                label = "Details",
                supporting = "View the song's information",
                onClick = { onShowDetails(); onDismiss() },
                testTag = "action_details",
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
private fun SheetAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String,
    tint: androidx.compose.ui.graphics.Color? = null,
    supporting: String? = null,
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
            tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
private fun ActionPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 6.dp)
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
