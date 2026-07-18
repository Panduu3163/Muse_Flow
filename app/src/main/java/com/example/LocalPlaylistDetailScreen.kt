package com.example

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

/** A user-created (or imported) playlist's own detail screen - Library's Playlists grid had
 * nowhere to send a tap before this (see [PlaylistCoverArt] for how its cover is derived, and
 * [LibraryViewModel.tracksForPlaylist]/[LibraryViewModel.removeTrackFromPlaylist] for the
 * Room-backed track list behind it). */
@Composable
fun LocalPlaylistDetailScreen(
    playlist: PlaylistEntity,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val libraryViewModel: LibraryViewModel = viewModel()
    val tracks by libraryViewModel.tracksForPlaylist(playlist.id).collectAsState()
    val gradientColors = MusicData.Gradients[(playlist.id % MusicData.Gradients.size).toInt()]

    ThemedBackground(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("local_playlist_back_button")) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlaylistCoverArt(
                    coverImageUrl = playlist.coverImageUrl,
                    tracks = tracks,
                    fallbackGradient = gradientColors,
                    modifier = Modifier.size(88.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("local_playlist_title")
                    )
                    Text(
                        text = "${tracks.size} song${if (tracks.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (tracks.isEmpty()) {
                EmptySearchState(
                    title = "No songs yet",
                    message = "Add songs to this playlist from Search, Downloads, or Liked Songs."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 90.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(tracks) { track ->
                        val rowGradient = MusicData.Gradients[track.gradientIndex % MusicData.Gradients.size]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onPlayTrack(track, tracks) }
                                .padding(8.dp)
                                .testTag("local_playlist_track_row_${track.title.lowercase().replace(" ", "_")}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TrackArtwork(
                                imageUrl = track.imageUrl,
                                gradientColors = rowGradient,
                                modifier = Modifier.size(52.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play Track",
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = track.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { libraryViewModel.removeTrackFromPlaylist(playlist.id, track) },
                                modifier = Modifier.testTag("local_playlist_remove_${track.title.lowercase().replace(" ", "_")}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Remove from playlist",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
