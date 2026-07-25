package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.LibraryViewModel
import com.example.Track
import com.example.TrackResult
import com.example.ui.component.TrackRow

/**
 * A single playlist's tracks, with play-all and shuffle-play entry points.
 *
 * Reads through [LibraryViewModel.tracksForPlaylist], so the list stays live as tracks are added
 * from a search result's actions sheet while this screen is open.
 */
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    onPlayTrack: (TrackResult, List<TrackResult>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: LibraryViewModel = viewModel()
    val playlists by viewModel.playlists.collectAsState()
    val tracks by viewModel.tracksForPlaylist(playlistId).collectAsState()

    val playlist = playlists.find { it.id == playlistId }

    val play: (Track, List<Track>) -> Unit = { track, queue ->
        onPlayTrack(track.asTrackResult(), queue.map { it.asTrackResult() })
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("playlist_detail_screen"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("playlist_back")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist?.name ?: "Playlist",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${tracks.size} ${if (tracks.size == 1) "song" else "songs"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (tracks.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayButton(
                    icon = Icons.Default.PlayArrow,
                    label = "Play",
                    onClick = { tracks.firstOrNull()?.let { play(it, tracks) } },
                    testTag = "playlist_play_all",
                )
                PlayButton(
                    icon = Icons.Default.Shuffle,
                    label = "Shuffle",
                    // Shuffling the queue here rather than toggling the player's shuffle mode, so
                    // this stays a one-shot action and doesn't leave shuffle stuck on afterwards.
                    onClick = {
                        val shuffled = tracks.shuffled()
                        shuffled.firstOrNull()?.let { play(it, shuffled) }
                    },
                    testTag = "playlist_shuffle",
                )
            }
        }

        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "This playlist is empty.\nLong-press any song and choose \"Add to playlist\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 140.dp)) {
                items(tracks, key = { "${it.title}|${it.artist}" }) { track ->
                    TrackRow(
                        title = track.title,
                        artist = track.artist,
                        imageUrl = track.imageUrl,
                        duration = track.duration,
                        onClick = { play(track, tracks) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
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
