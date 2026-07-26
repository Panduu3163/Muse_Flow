package com.example.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.CollectionTracks
import com.example.TrackResult
import com.example.UiState

/**
 * The tracks behind an album, artist, or playlist, shown as a sheet over the search results.
 *
 * A sheet rather than a screen on purpose: it keeps the user's search results underneath, so
 * checking three albums in a row costs no navigation. The dedicated album/artist screens are a
 * later, larger piece of work that needs the expanded schema behind it - this makes the results
 * playable now without pre-empting that design.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionSheet(
    collection: CollectionTracks,
    onPlayTrack: (TrackResult, List<TrackResult>) -> Unit,
    onDismiss: () -> Unit,
    likedKeys: Set<String> = emptySet(),
    downloadedKeys: Set<String> = emptySet(),
    downloadsInProgress: Map<String, Int> = emptyMap(),
    trackKey: (TrackResult) -> String = { it.id },
    /** Null for anything that isn't a followable artist, which hides the follow control. */
    isFollowed: Boolean? = null,
    onToggleFollow: () -> Unit = {},
    /**
     * Long-press on a track. The caller renders the resulting actions sheet, which stacks over
     * this one - so dismissing it returns to the tracklist rather than losing the user's place in
     * an album they were part-way through.
     */
    onLongPressTrack: (TrackResult) -> Unit = {},
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
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
                .testTag("collection_sheet"),
        ) {
            Header(collection, isFollowed, onToggleFollow)

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            when (val tracks = collection.tracks) {
                is UiState.Loading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                is UiState.Error -> Message(
                    text = tracks.message,
                    color = MaterialTheme.colorScheme.error,
                )

                is UiState.Success -> if (tracks.data.isEmpty()) {
                    Message("No tracks here.")
                } else {
                    PlayButtons(
                        tracks = tracks.data,
                        onPlayTrack = onPlayTrack,
                        onDismiss = onDismiss,
                    )
                    LazyColumn(
                        // Capped rather than filled, so a three-track single gets a short sheet
                        // instead of a mostly-empty full-height one.
                        modifier = Modifier.heightIn(max = 420.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        // Keyed by position, not videoId: a playlist may legitimately hold the same
                        // track twice, and duplicate Compose keys throw.
                        itemsIndexed(tracks.data, key = { index, _ -> index }) { _, track ->
                            val key = trackKey(track)
                            TrackRow(
                                title = track.title,
                                artist = track.artist,
                                imageUrl = track.imageUrl,
                                duration = track.duration,
                                onClick = {
                                    onPlayTrack(track, tracks.data)
                                    onDismiss()
                                },
                                onLongClick = { onLongPressTrack(track) },
                                isLiked = likedKeys.contains(key),
                                isDownloaded = downloadedKeys.contains(key),
                                downloadProgress = downloadsInProgress[key],
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(
    collection: CollectionTracks,
    isFollowed: Boolean?,
    onToggleFollow: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CollectionArtwork(
            imageUrl = collection.imageUrl,
            kind = collection.kind,
            size = 64.dp,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp),
        ) {
            Text(
                text = collection.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (collection.subtitle.isNotBlank()) {
                Text(
                    text = collection.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (isFollowed != null) {
            // Filled once followed, outlined before - the same read as a like button, so its
            // state is obvious without reading the label.
            if (isFollowed) {
                Button(
                    onClick = onToggleFollow,
                    modifier = Modifier.testTag("collection_following"),
                ) { Text("Following") }
            } else {
                OutlinedButton(
                    onClick = onToggleFollow,
                    modifier = Modifier.testTag("collection_follow"),
                ) { Text("Follow") }
            }
        }
    }
}

@Composable
private fun PlayButtons(
    tracks: List<TrackResult>,
    onPlayTrack: (TrackResult, List<TrackResult>) -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = {
                onPlayTrack(tracks.first(), tracks)
                onDismiss()
            },
            modifier = Modifier
                .weight(1f)
                .testTag("collection_play_all"),
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text = "Play", modifier = Modifier.padding(start = 8.dp))
        }

        OutlinedButton(
            onClick = {
                // Shuffled into a new queue rather than toggling the player's shuffle mode, so the
                // order the user hears is the order the queue actually holds.
                val shuffled = tracks.shuffled()
                onPlayTrack(shuffled.first(), shuffled)
                onDismiss()
            },
            modifier = Modifier
                .weight(1f)
                .testTag("collection_shuffle"),
        ) {
            Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text = "Shuffle", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun Message(text: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            textAlign = TextAlign.Center,
        )
    }
}
