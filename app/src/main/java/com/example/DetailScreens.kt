package com.example

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.LibraryAddCheck
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Real tracklist for an album search result, fetched via [JioSaavnProvider.getAlbumTracks] or
 * [YouTubeMusicProvider.getAlbumTracks] depending on [AlbumResult.sourceType]. */
@Composable
fun AlbumDetailScreen(
    album: AlbumResult,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    TracklistDetailScreen(
        title = album.title,
        subtitle = album.artist + (album.songCount?.let { " • $it songs" } ?: ""),
        imageUrl = album.imageUrl,
        gradientSeed = album.id,
        sourceType = album.sourceType,
        fetchTracks = { TracklistFetchResult(provider(context, album.sourceType).getAlbumTracks(album.id)) },
        onPlayTrack = onPlayTrack,
        onBack = onBack,
        emoji = "💿",
        modifier = modifier
    )
}

/** Real top-tracks list for an artist search result, fetched via
 * [JioSaavnProvider.getArtistTracklist] or [YouTubeMusicProvider.getArtistTracklist] depending on
 * [ArtistResult.sourceType] - including their listener count, shown under the header, when the
 * source has one. If the artist's own source comes back with zero tracks (seen happening on
 * JioSaavn's live artist-page API - it can return an empty `topSongs` for an artist who
 * definitely has real songs), this looks the same artist up by name on the *other* source instead
 * of showing "nothing here" for an artist search that clearly should have results. */
@Composable
fun ArtistDetailScreen(
    artist: ArtistResult,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val jioSaavnProvider = remember { JioSaavnProvider() }
    val youTubeProvider = remember { YouTubeMusicProvider(context) }

    TracklistDetailScreen(
        title = artist.name,
        subtitle = "Top Songs",
        imageUrl = artist.imageUrl,
        gradientSeed = artist.id,
        sourceType = artist.sourceType,
        fetchTracks = {
            var tracklist = if (artist.sourceType == MusicSource.YOUTUBE_MUSIC) {
                youTubeProvider.getArtistTracklist(artist.id)
            } else {
                jioSaavnProvider.getArtistTracklist(artist.id)
            }
            if (tracklist.tracks.isEmpty()) {
                val fallback = runCatching {
                    if (artist.sourceType == MusicSource.YOUTUBE_MUSIC) {
                        jioSaavnProvider.searchArtists(artist.name).firstOrNull()
                            ?.let { jioSaavnProvider.getArtistTracklist(it.id) }
                    } else {
                        youTubeProvider.searchArtists(artist.name).firstOrNull()
                            ?.let { youTubeProvider.getArtistTracklist(it.id) }
                    }
                }.getOrNull()
                if (fallback != null) tracklist = fallback
            }
            TracklistFetchResult(
                tracks = tracklist.tracks,
                extraSubtitle = artist.listenerCount ?: tracklist.listenerCount
            )
        },
        onPlayTrack = onPlayTrack,
        onBack = onBack,
        emoji = "🎤",
        shape = CircleShape,
        modifier = modifier
    )
}

/** Real tracklist for a playlist search result, fetched via [JioSaavnProvider.getPlaylistTracks]
 * or [YouTubeMusicProvider.getPlaylistTracks] depending on [PlaylistResult.sourceType]. Also the
 * only tracklist screen with an "Add to my library" action - saves the playlist's real cover and
 * its fetched tracks as a new local playlist (see [LibraryViewModel.importPlaylist]). */
@Composable
fun PlaylistDetailScreen(
    playlist: PlaylistResult,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val libraryViewModel: LibraryViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    TracklistDetailScreen(
        title = playlist.title,
        subtitle = playlist.subtitle,
        imageUrl = playlist.imageUrl,
        gradientSeed = playlist.id,
        sourceType = playlist.sourceType,
        fetchTracks = { TracklistFetchResult(provider(context, playlist.sourceType).getPlaylistTracks(playlist.id)) },
        onPlayTrack = onPlayTrack,
        onBack = onBack,
        emoji = "🎵",
        onSaveToLibrary = { tracks -> libraryViewModel.importPlaylist(playlist.title, playlist.imageUrl, tracks) },
        modifier = modifier
    )
}

/** A single, generically-typed handle for whichever provider Album/Playlist detail screens need -
 * both [JioSaavnProvider] and [YouTubeMusicProvider] expose the same
 * getAlbumTracks/getPlaylistTracks shape, just not through a shared interface, so this picks the
 * concrete instance the caller should invoke. (Artist isn't part of this - it needs both
 * providers at once, for its cross-source fallback - see [ArtistDetailScreen].) Constructing
 * either is cheap (no I/O in the constructor), so a fresh one per call is fine here - callers
 * don't remember() it. */
private fun provider(context: Context, sourceType: MusicSource): TracklistProvider =
    when (sourceType) {
        MusicSource.YOUTUBE_MUSIC -> YouTubeMusicTracklistProvider(YouTubeMusicProvider(context))
        else -> JioSaavnTracklistProvider(JioSaavnProvider())
    }

private interface TracklistProvider {
    suspend fun getAlbumTracks(id: String): List<TrackResult>
    suspend fun getPlaylistTracks(id: String): List<TrackResult>
}

private class JioSaavnTracklistProvider(private val provider: JioSaavnProvider) : TracklistProvider {
    override suspend fun getAlbumTracks(id: String) = provider.getAlbumTracks(id)
    override suspend fun getPlaylistTracks(id: String) = provider.getPlaylistTracks(id)
}

private class YouTubeMusicTracklistProvider(private val provider: YouTubeMusicProvider) : TracklistProvider {
    override suspend fun getAlbumTracks(id: String) = provider.getAlbumTracks(id)
    override suspend fun getPlaylistTracks(id: String) = provider.getPlaylistTracks(id)
}

/** What [TracklistDetailScreen] needs from a fetch: the tracklist itself, plus an optional extra
 * metadata line shown under the header subtitle - only [ArtistDetailScreen] currently populates
 * this, with a listener count. */
private data class TracklistFetchResult(val tracks: List<TrackResult>, val extraSubtitle: String? = null)

/** Shared shell for the three detail screens above: a header (art/title/subtitle/back button)
 * over a real, network-fetched tracklist, with loading/error/empty states. Tapping a track plays
 * it with the rest of the tracklist as its queue, via the same [onPlayTrack] wiring every other
 * screen (Home, Search, Library) uses. */
@Composable
private fun TracklistDetailScreen(
    title: String,
    subtitle: String,
    imageUrl: String?,
    gradientSeed: String,
    sourceType: MusicSource,
    fetchTracks: suspend () -> TracklistFetchResult,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onBack: () -> Unit,
    emoji: String,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp),
    /** Only [PlaylistDetailScreen] passes this - Album/Artist detail have no "add to my library"
     * equivalent. Called once, with whatever tracklist actually finished loading. */
    onSaveToLibrary: ((List<Track>) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var state by remember { mutableStateOf<UiState<List<Track>>>(UiState.Loading) }
    var extraSubtitle by remember(gradientSeed) { mutableStateOf<String?>(null) }
    var savedToLibrary by remember(gradientSeed) { mutableStateOf(false) }
    val sourceName = if (sourceType == MusicSource.YOUTUBE_MUSIC) "YouTube Music" else "JioSaavn"
    val context = LocalContext.current

    LaunchedEffect(gradientSeed) {
        state = UiState.Loading
        extraSubtitle = null
        state = loadAsUiState(errorMessage = "Couldn't reach $sourceName. Check your connection and try again.") {
            val result = fetchTracks()
            extraSubtitle = result.extraSubtitle
            result.tracks
                // A JioSaavn row is only worth showing if it's directly playable (a small
                // fraction fail DES decryption); a YouTube row has no stream URL of its own by
                // design - it's resolved fresh right before playback - so it's always kept.
                .filter { it.sourceType == MusicSource.YOUTUBE_MUSIC || it.directStreamUrl != null }
                .mapIndexed { index, result -> result.toPlayableTrack(gradientIndex = index) }
        }
    }

    val gradientColors = MusicData.Gradients[(gradientSeed.hashCode().mod(MusicData.Gradients.size))]

    ThemedBackground(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back_button")) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                if (onSaveToLibrary != null) {
                    val currentTracks = (state as? UiState.Success)?.data
                    IconButton(
                        onClick = {
                            if (!savedToLibrary && currentTracks != null && currentTracks.isNotEmpty()) {
                                onSaveToLibrary(currentTracks)
                                savedToLibrary = true
                                android.widget.Toast.makeText(context, "Added to your library", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !savedToLibrary && !currentTracks.isNullOrEmpty(),
                        modifier = Modifier.testTag("detail_add_to_library_button")
                    ) {
                        Icon(
                            imageVector = if (savedToLibrary) Icons.Default.LibraryAddCheck else Icons.Default.LibraryAdd,
                            contentDescription = if (savedToLibrary) "Added to your library" else "Add to my library",
                            tint = if (savedToLibrary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TrackArtwork(
                    imageUrl = imageUrl,
                    gradientColors = gradientColors,
                    shape = shape,
                    modifier = Modifier.size(88.dp)
                ) {
                    Text(emoji, fontSize = 32.sp)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("detail_title")
                    )
                    // Only populated once the fetch resolves (e.g. an artist's listener count) -
                    // absent for Album/Playlist, which have nothing to add here.
                    extraSubtitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("detail_listener_count")
                        )
                    }
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            val currentState = state
            when {
                currentState is UiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                currentState is UiState.Error -> EmptySearchState(
                    title = "Couldn't load this",
                    message = currentState.message
                )
                currentState is UiState.Success && currentState.data.isEmpty() -> EmptySearchState(
                    title = "Nothing here",
                    message = "No playable tracks were found."
                )
                currentState is UiState.Success -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 90.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(currentState.data) { track ->
                        val rowGradient = MusicData.Gradients[track.gradientIndex % MusicData.Gradients.size]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onPlayTrack(track, currentState.data) }
                                .padding(8.dp)
                                .testTag("detail_track_row_${track.title.lowercase().replace(" ", "_")}"),
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
                            Text(
                                text = track.duration,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            // No per-row download button here, for any source - downloading now
                            // happens from Now Playing only (see SongResultRows in SearchScreen.kt).
                        }
                    }
                }
            }
        }
    }
}
