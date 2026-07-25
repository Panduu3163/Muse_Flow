package com.example.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.AppSettingsViewModel
import com.example.GridCellSize
import com.example.HomeViewModel
import com.example.Track
import com.example.TrackResult
import com.example.UiState
import com.example.MusicSource

/**
 * Home: local sections first (resume, most played, playlists), then genre/mood shelves fetched
 * through the selected extractor and cached to Room for offline use.
 */
@Composable
fun HomeScreen(
    onPlayTrack: (TrackResult, List<TrackResult>) -> Unit = { _, _ -> },
    /** True while the player already has something loaded - the mini-player is showing it, so the
     * resume card would be duplicate (and misleading) chrome. */
    isPlayerActive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val viewModel: HomeViewModel = viewModel()
    val settingsViewModel: AppSettingsViewModel = viewModel()
    val settings by settingsViewModel.state.collectAsState()

    // Card width follows the user's grid-size preference; the shelves are horizontal carousels, so
    // this is what "grid size" actually means here.
    val cardSize = when (settings.gridCellSize) {
        GridCellSize.Small -> 112.dp
        GridCellSize.Medium -> 140.dp
        GridCellSize.Large -> 172.dp
    }
    val continueListening by viewModel.continueListening.collectAsState()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val topPlayed by viewModel.topPlayed.collectAsState()
    val shelves by viewModel.shelves.collectAsState()
    val forgottenFavourites by viewModel.forgottenFavourites.collectAsState()
    val similarToFavourite by viewModel.similarToFavourite.collectAsState()
    val favouriteArtist by viewModel.favouriteArtist.collectAsState()

    // Home's local sections hold Track (from Room); playback takes TrackResult, so they're mapped
    // at the point of the tap rather than storing two parallel shapes everywhere.
    val playTracks: (Track, List<Track>) -> Unit = { track, queue ->
        onPlayTrack(track.asTrackResult(), queue.map { it.asTrackResult() })
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen"),
        contentPadding = PaddingValues(top = 24.dp, bottom = 140.dp),
    ) {
        item {
            Text(
                text = "MuseFlow",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, bottom = 16.dp),
            )
        }

        // Only shown when nothing is loaded in the player. Previously this appeared even mid-song,
        // where it either advertised a *different* track than the one playing, or showed the same
        // track with a play icon that restarted it from zero - both confusing when the mini-player
        // is right there showing the truth.
        if (!isPlayerActive) {
            continueListening?.let { track ->
                item {
                    ContinueListeningCard(
                        track = track,
                        // Queue the whole recent list, not just this track: a one-item queue ends
                        // immediately and has nothing to advance to.
                        onClick = { playTracks(track, recentlyPlayed.ifEmpty { listOf(track) }) },
                    )
                }
            }
        }

        if (recentlyPlayed.isNotEmpty()) {
            item {
                Shelf(title = "Recently played") {
                    TrackCarousel(recentlyPlayed, cardSize) { playTracks(it, recentlyPlayed) }
                }
            }
        }

        if (topPlayed.isNotEmpty()) {
            item {
                Shelf(title = "On repeat") {
                    TrackCarousel(topPlayed, cardSize) { playTracks(it, topPlayed) }
                }
            }
        }

        if (forgottenFavourites.isNotEmpty()) {
            item {
                Shelf(title = "Forgotten favourites") {
                    TrackCarousel(forgottenFavourites, cardSize) {
                        playTracks(it, forgottenFavourites)
                    }
                }
            }
        }

        if (similarToFavourite.isNotEmpty()) {
            item {
                Shelf(title = favouriteArtist?.let { "More from $it" } ?: "More like this") {
                    TrackCarousel(similarToFavourite, cardSize) {
                        playTracks(it, similarToFavourite)
                    }
                }
            }
        }

        items(viewModel.shelfTitles, key = { it }) { title ->
            Shelf(title = title) {
                Crossfade(targetState = shelves[title] ?: UiState.Loading, label = "shelf_$title") { state ->
                    when (state) {
                        is UiState.Loading -> ShelfSkeleton()
                        is UiState.Error -> ShelfMessage(state.message)
                        is UiState.Success -> if (state.data.isEmpty()) {
                            ShelfMessage("Nothing here yet.")
                        } else {
                            TrackCarousel(state.data, cardSize) { playTracks(it, state.data) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinueListeningCard(track: Track, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("continue_listening_card"),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(imageUrl = track.imageUrl, size = 60.dp, corner = 12.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
            ) {
                Text(
                    text = "Continue listening",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Resume",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun Shelf(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(top = 20.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
        )
        content()
    }
}

@Composable
private fun TrackCarousel(
    tracks: List<Track>,
    cardSize: androidx.compose.ui.unit.Dp,
    onPlay: (Track) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(tracks, key = { "${it.title}|${it.artist}" }) { track ->
            Column(
                modifier = Modifier
                    .width(cardSize)
                    .clickable { onPlay(track) },
            ) {
                Artwork(imageUrl = track.imageUrl, size = cardSize, corner = 14.dp)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Placeholder cards while a shelf loads - keeps the row's height stable so the list doesn't
 * jump when real content arrives. */
@Composable
private fun ShelfSkeleton() {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(4) { index ->
            Column(modifier = Modifier.width(140.dp)) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                )
            }
        }
    }
}

@Composable
private fun ShelfMessage(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun Artwork(imageUrl: String?, size: androidx.compose.ui.unit.Dp, corner: androidx.compose.ui.unit.Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        } else {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size / 3),
            )
        }
    }
}

/** Local [Track]s already carry everything playback needs; this just restates them in the shape
 * the player takes. A track with a real [Track.streamUrl] (a download) keeps it, so it plays from
 * disk instead of being re-resolved. */
internal fun Track.asTrackResult(): TrackResult = TrackResult(
    id = sourceId ?: "${title}|${artist}",
    title = title,
    artist = artist,
    duration = duration,
    source = album,
    sourceType = sourceType ?: MusicSource.YOUTUBE_MUSIC,
    directStreamUrl = streamUrl,
    imageUrl = imageUrl,
)
