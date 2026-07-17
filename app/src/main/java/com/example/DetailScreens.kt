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
        fetchTracks = { provider(context, album.sourceType).getAlbumTracks(album.id) },
        onPlayTrack = onPlayTrack,
        onBack = onBack,
        emoji = "💿",
        modifier = modifier
    )
}

/** Real top-tracks list for an artist search result, fetched via [JioSaavnProvider.getArtistTracks]
 * or [YouTubeMusicProvider.getArtistTracks] depending on [ArtistResult.sourceType]. */
@Composable
fun ArtistDetailScreen(
    artist: ArtistResult,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    TracklistDetailScreen(
        title = artist.name,
        subtitle = "Top Songs",
        imageUrl = artist.imageUrl,
        gradientSeed = artist.id,
        sourceType = artist.sourceType,
        fetchTracks = { provider(context, artist.sourceType).getArtistTracks(artist.id) },
        onPlayTrack = onPlayTrack,
        onBack = onBack,
        emoji = "🎤",
        shape = CircleShape,
        modifier = modifier
    )
}

/** Real tracklist for a playlist search result, fetched via [JioSaavnProvider.getPlaylistTracks]
 * or [YouTubeMusicProvider.getPlaylistTracks] depending on [PlaylistResult.sourceType]. */
@Composable
fun PlaylistDetailScreen(
    playlist: PlaylistResult,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    TracklistDetailScreen(
        title = playlist.title,
        subtitle = playlist.subtitle,
        imageUrl = playlist.imageUrl,
        gradientSeed = playlist.id,
        sourceType = playlist.sourceType,
        fetchTracks = { provider(context, playlist.sourceType).getPlaylistTracks(playlist.id) },
        onPlayTrack = onPlayTrack,
        onBack = onBack,
        emoji = "🎵",
        modifier = modifier
    )
}

/** A single, generically-typed handle for whichever provider a detail screen's [sourceType]
 * needs - both [JioSaavnProvider] and [YouTubeMusicProvider] expose the same
 * getAlbumTracks/getArtistTracks/getPlaylistTracks shape, just not through a shared interface, so
 * this picks the concrete instance the caller should invoke. Constructing either is cheap (no I/O
 * in the constructor), so a fresh one per call is fine here - callers don't remember() it. */
private fun provider(context: Context, sourceType: MusicSource): TracklistProvider =
    when (sourceType) {
        MusicSource.YOUTUBE_MUSIC -> YouTubeMusicTracklistProvider(YouTubeMusicProvider(context))
        else -> JioSaavnTracklistProvider(JioSaavnProvider())
    }

private interface TracklistProvider {
    suspend fun getAlbumTracks(id: String): List<TrackResult>
    suspend fun getArtistTracks(id: String): List<TrackResult>
    suspend fun getPlaylistTracks(id: String): List<TrackResult>
}

private class JioSaavnTracklistProvider(private val provider: JioSaavnProvider) : TracklistProvider {
    override suspend fun getAlbumTracks(id: String) = provider.getAlbumTracks(id)
    override suspend fun getArtistTracks(id: String) = provider.getArtistTracks(id)
    override suspend fun getPlaylistTracks(id: String) = provider.getPlaylistTracks(id)
}

private class YouTubeMusicTracklistProvider(private val provider: YouTubeMusicProvider) : TracklistProvider {
    override suspend fun getAlbumTracks(id: String) = provider.getAlbumTracks(id)
    override suspend fun getArtistTracks(id: String) = provider.getArtistTracks(id)
    override suspend fun getPlaylistTracks(id: String) = provider.getPlaylistTracks(id)
}

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
    fetchTracks: suspend () -> List<TrackResult>,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onBack: () -> Unit,
    emoji: String,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp),
    modifier: Modifier = Modifier
) {
    var state by remember { mutableStateOf<UiState<List<Track>>>(UiState.Loading) }
    val sourceName = if (sourceType == MusicSource.YOUTUBE_MUSIC) "YouTube Music" else "JioSaavn"

    LaunchedEffect(gradientSeed) {
        state = UiState.Loading
        state = loadAsUiState(errorMessage = "Couldn't reach $sourceName. Check your connection and try again.") {
            fetchTracks()
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back_button")) {
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
                            // YouTube-sourced tracks stream via a fresh per-play resolve, not a
                            // fixed URL - same reason SongsResults hides this for them too.
                            if (track.sourceType != MusicSource.YOUTUBE_MUSIC) {
                                DownloadButton(track = track)
                            }
                        }
                    }
                }
            }
        }
    }
}
