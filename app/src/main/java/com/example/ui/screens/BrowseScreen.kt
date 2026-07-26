package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.BrowsePage
import com.example.BrowseSection
import com.example.CollectionKind
import com.example.CollectionTracks
import com.example.MusicSearchRouter
import com.example.PlayerViewModel
import com.example.PlaylistResult
import com.example.TrackActionsViewModel
import com.example.TrackResult
import com.example.UiState
import com.example.loadAsUiState
import com.example.ui.component.CollectionRow
import com.example.ui.component.CollectionSheet
import com.example.ui.component.TrackActionsHost
import com.example.ui.component.TrackRow

/**
 * A generic browse page - what a mood/genre tile from [ExploreScreen] opens. Genuinely mixed
 * content: one page can mix track shelves with album/artist/playlist shelves, so every shelf
 * renders whichever of the four kinds it actually has (see [BrowseSection]).
 *
 * Playlists here open a [CollectionSheet] rather than navigating away, matching how Search's own
 * playlist results already behave - there's no dedicated screen for a remote (non-Room) playlist
 * to navigate to yet. Albums and artists use the real [ArtistScreen]/[AlbumScreen] destinations.
 */
@Composable
fun BrowseScreen(
    browseId: String,
    params: String?,
    onPlayTrack: (TrackResult, List<TrackResult>) -> Unit,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onGoToArtist: (String) -> Unit = {},
    onGoToAlbum: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val router = remember { MusicSearchRouter(context) }
    val actionsViewModel: TrackActionsViewModel = viewModel()
    var selectedTrack by remember { mutableStateOf<TrackResult?>(null) }
    // Captured directly from the tapped row, not looked up again from the page state - a
    // playlist's own metadata (title/subtitle/image) is already in hand at tap time.
    var selectedPlaylist by remember { mutableStateOf<PlaylistResult?>(null) }
    var playlistTracks by remember { mutableStateOf<UiState<List<TrackResult>>>(UiState.Loading) }
    var state by remember { mutableStateOf<UiState<BrowsePage>>(UiState.Loading) }

    LaunchedEffect(browseId, params) {
        state = UiState.Loading
        state = loadAsUiState("Couldn't load this page.") { router.browse(browseId, params) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("browse_screen"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("browse_back")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            val title = (state as? UiState.Success)?.data?.title
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        when (val current = state) {
            is UiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            is UiState.Error -> Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = current.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            is UiState.Success -> {
                val sections = current.data.sections
                if (sections.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Nothing here right now.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 140.dp)) {
                        sections.forEachIndexed { sectionIndex, section ->
                            if (section.title != null) {
                                item(key = "title_$sectionIndex") {
                                    Text(
                                        text = section.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                                    )
                                }
                            }
                            items(section.tracks, key = { "track_${sectionIndex}_${it.id}" }) { track ->
                                TrackRow(
                                    title = track.title,
                                    artist = track.artist,
                                    imageUrl = track.imageUrl,
                                    duration = track.duration,
                                    onClick = { onPlayTrack(track, section.tracks) },
                                    onOpenMenu = { selectedTrack = track },
                                )
                            }
                            items(section.albums, key = { "album_${sectionIndex}_${it.id}" }) { album ->
                                CollectionRow(
                                    title = album.title,
                                    subtitle = album.artist,
                                    imageUrl = album.imageUrl,
                                    kind = CollectionKind.Album,
                                    onClick = { onGoToAlbum(album.id) },
                                )
                            }
                            items(section.artists, key = { "artist_${sectionIndex}_${it.id}" }) { artist ->
                                CollectionRow(
                                    title = artist.name,
                                    subtitle = "Artist",
                                    imageUrl = artist.imageUrl,
                                    kind = CollectionKind.Artist,
                                    onClick = { onGoToArtist(artist.id) },
                                )
                            }
                            items(section.playlists, key = { "playlist_${sectionIndex}_${it.id}" }) { playlist ->
                                CollectionRow(
                                    title = playlist.title,
                                    subtitle = playlist.subtitle,
                                    imageUrl = playlist.imageUrl,
                                    kind = CollectionKind.Playlist,
                                    onClick = {
                                        selectedPlaylist = playlist
                                        playlistTracks = UiState.Loading
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    selectedPlaylist?.let { playlist ->
        LaunchedEffect(playlist.id) {
            playlistTracks = loadAsUiState("Couldn't load this playlist.") {
                router.getPlaylistTracks(playlist.id)
            }
        }
        CollectionSheet(
            collection = CollectionTracks(
                title = playlist.title,
                subtitle = playlist.subtitle,
                imageUrl = playlist.imageUrl,
                kind = CollectionKind.Playlist,
                tracks = playlistTracks,
            ),
            onPlayTrack = onPlayTrack,
            onDismiss = { selectedPlaylist = null },
        )
    }

    TrackActionsHost(
        track = selectedTrack,
        onDismiss = { selectedTrack = null },
        playerViewModel = playerViewModel,
        actionsViewModel = actionsViewModel,
        onGoToArtist = onGoToArtist,
        onGoToAlbum = onGoToAlbum,
    )
}
