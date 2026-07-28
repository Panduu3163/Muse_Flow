package com.example.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.example.PlaylistEntity
import com.example.PlaylistResult
import com.example.Track
import com.example.TrackResult
import com.example.UiState
import com.example.MusicSource
import com.example.ui.component.MediaCard
import com.example.ui.component.PlaylistCover
import com.example.ui.component.ShimmerBlock

/**
 * Home: local sections first (recently played, most played, playlists), then shelves fetched
 * through the selected extractor and cached to Room for offline use.
 */
@Composable
fun HomeScreen(
    onPlayTrack: (TrackResult, List<TrackResult>) -> Unit = { _, _ -> },
    onOpenPlaylist: (Long) -> Unit = {},
    onOpenRemotePlaylist: (String, String, String, String?) -> Unit = { _, _, _, _ -> },
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
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val topPlayed by viewModel.topPlayed.collectAsState()
    val shelves by viewModel.shelves.collectAsState()
    val forgottenFavourites by viewModel.forgottenFavourites.collectAsState()
    val shelfSpecs by viewModel.shelfSpecs.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val dailyDiscover by viewModel.dailyDiscover.collectAsState()
    val communityPlaylists by viewModel.communityPlaylists.collectAsState()

    // Home's local sections hold Track (from Room); playback takes TrackResult, so they're mapped
    // at the point of the tap rather than storing two parallel shapes everywhere.
    val playTracks: (Track, List<Track>) -> Unit = { track, queue ->
        onPlayTrack(track.asTrackResult(), queue.map { it.asTrackResult() })
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("home_screen"),
        contentPadding = PaddingValues(top = 24.dp, bottom = 200.dp),
    ) {
        item {
            Text(
                text = "MuseFlow",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, bottom = 16.dp),
            )
        }

        if (recentlyPlayed.isNotEmpty()) {
            item(key = "recently_played") {
                Shelf(title = "Recently played", modifier = Modifier.animateItem()) {
                    TrackCarousel(recentlyPlayed, cardSize) { playTracks(it, recentlyPlayed) }
                }
            }
        }

        if (topPlayed.isNotEmpty()) {
            item(key = "on_repeat") {
                Shelf(title = "On repeat", modifier = Modifier.animateItem()) {
                    TrackCarousel(topPlayed, cardSize) { playTracks(it, topPlayed) }
                }
            }
        }

        if (forgottenFavourites.isNotEmpty()) {
            item(key = "forgotten_favourites") {
                Shelf(title = "Forgotten favourites", modifier = Modifier.animateItem()) {
                    TrackCarousel(forgottenFavourites, cardSize) {
                        playTracks(it, forgottenFavourites)
                    }
                }
            }
        }

        if (playlists.isNotEmpty()) {
            item(key = "your_playlists") {
                Shelf(title = "Your playlists", modifier = Modifier.animateItem()) {
                    PlaylistCarousel(playlists, cardSize, viewModel) { onOpenPlaylist(it.id) }
                }
            }
        }

        when (dailyDiscover) {
            is UiState.Success -> {
                val discovered = (dailyDiscover as UiState.Success<List<Track>>).data
                if (discovered.isNotEmpty()) {
                    item(key = "daily_discover") {
                        Shelf(title = "Daily Discover", modifier = Modifier.animateItem()) {
                            TrackCarousel(discovered, cardSize) { playTracks(it, discovered) }
                        }
                    }
                }
            }
            is UiState.Loading -> item(key = "daily_discover") {
                Shelf(title = "Daily Discover", modifier = Modifier.animateItem()) { ShelfSkeleton() }
            }
            is UiState.Error -> Unit
        }

        when (communityPlaylists) {
            is UiState.Success -> {
                val results = (communityPlaylists as UiState.Success<List<PlaylistResult>>).data
                if (results.isNotEmpty()) {
                    item(key = "from_the_community") {
                        Shelf(title = "From the community", modifier = Modifier.animateItem()) {
                            RemotePlaylistCarousel(results, cardSize) { playlist ->
                                onOpenRemotePlaylist(playlist.id, playlist.title, playlist.subtitle, playlist.imageUrl)
                            }
                        }
                    }
                }
            }
            is UiState.Loading -> item(key = "from_the_community") {
                Shelf(title = "From the community", modifier = Modifier.animateItem()) { ShelfSkeleton() }
            }
            is UiState.Error -> Unit
        }

        items(shelfSpecs, key = { it.title }) { spec ->
            Shelf(title = spec.title, modifier = Modifier.animateItem()) {
                Crossfade(targetState = shelves[spec.title] ?: UiState.Loading, label = "shelf_${spec.title}") { state ->
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
private fun Shelf(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier = modifier.padding(top = 20.dp)) {
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
        // Position-keyed, not "title|artist" - a shelf (esp. "Forgotten favourites"/a personalized
        // search-based shelf) can legitimately contain the same title+artist twice (e.g. a single
        // vs. an album cut of the same song), which crashed with "Key ... was already used" under
        // the title+artist key. Every other list in the app with this same duplicate-content risk
        // (Library, Search) already keys by position for exactly this reason.
        itemsIndexed(tracks, key = { index, _ -> index }) { _, track ->
            MediaCard(
                title = track.title,
                subtitle = track.artist,
                imageUrl = track.imageUrl,
                placeholder = Icons.Default.MusicNote,
                onClick = { onPlay(track) },
                size = cardSize,
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun PlaylistCarousel(
    playlists: List<PlaylistEntity>,
    cardSize: androidx.compose.ui.unit.Dp,
    viewModel: HomeViewModel,
    onOpen: (PlaylistEntity) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(playlists, key = { it.id }) { playlist ->
            val tracks by viewModel.tracksForPlaylist(playlist.id).collectAsState()
            MediaCard(
                title = playlist.name,
                subtitle = null,
                imageUrl = playlist.coverImageUrl,
                placeholder = Icons.Default.MusicNote,
                onClick = { onOpen(playlist) },
                size = cardSize,
                modifier = Modifier.animateItem(),
                artwork = { artworkModifier ->
                    PlaylistCover(
                        tracks = tracks,
                        fallbackCoverUrl = playlist.coverImageUrl,
                        customCoverUri = playlist.customCoverUri,
                        modifier = artworkModifier,
                    )
                },
            )
        }
    }
}

@Composable
private fun RemotePlaylistCarousel(
    playlists: List<PlaylistResult>,
    cardSize: androidx.compose.ui.unit.Dp,
    onOpen: (PlaylistResult) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(playlists, key = { it.id }) { playlist ->
            MediaCard(
                title = playlist.title,
                subtitle = playlist.subtitle,
                imageUrl = playlist.imageUrl,
                placeholder = Icons.Default.MusicNote,
                onClick = { onOpen(playlist) },
                size = cardSize,
                modifier = Modifier.animateItem(),
            )
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
                ShimmerBlock(
                    modifier = Modifier.size(140.dp),
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.height(8.dp))
                ShimmerBlock(
                    modifier = Modifier.fillMaxWidth().height(12.dp),
                    shape = RoundedCornerShape(4.dp),
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
    albumId = albumId,
    artistId = artistId,
)
