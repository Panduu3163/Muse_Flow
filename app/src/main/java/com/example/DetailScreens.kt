package com.example

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Real tracklist for a JioSaavn album search result, fetched via [JioSaavnProvider.getAlbumTracks]. */
@Composable
fun AlbumDetailScreen(
    album: AlbumResult,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    TracklistDetailScreen(
        title = album.title,
        subtitle = album.artist + (album.songCount?.let { " • $it songs" } ?: ""),
        imageUrl = album.imageUrl,
        gradientSeed = album.id,
        fetchTracks = { provider -> provider.getAlbumTracks(album.id) },
        onPlayTrack = onPlayTrack,
        onBack = onBack,
        emoji = "💿",
        modifier = modifier
    )
}

/** Real top-tracks list for a JioSaavn artist search result, fetched via [JioSaavnProvider.getArtistTracks]. */
@Composable
fun ArtistDetailScreen(
    artist: ArtistResult,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    TracklistDetailScreen(
        title = artist.name,
        subtitle = "Top Songs",
        imageUrl = artist.imageUrl,
        gradientSeed = artist.id,
        fetchTracks = { provider -> provider.getArtistTracks(artist.id) },
        onPlayTrack = onPlayTrack,
        onBack = onBack,
        emoji = "🎤",
        shape = CircleShape,
        modifier = modifier
    )
}

/** Real tracklist for a JioSaavn playlist search result, fetched via [JioSaavnProvider.getPlaylistTracks]. */
@Composable
fun PlaylistDetailScreen(
    playlist: PlaylistResult,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    TracklistDetailScreen(
        title = playlist.title,
        subtitle = playlist.subtitle,
        imageUrl = playlist.imageUrl,
        gradientSeed = playlist.id,
        fetchTracks = { provider -> provider.getPlaylistTracks(playlist.id) },
        onPlayTrack = onPlayTrack,
        onBack = onBack,
        emoji = "🎵",
        modifier = modifier
    )
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
    fetchTracks: suspend (JioSaavnProvider) -> List<TrackResult>,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onBack: () -> Unit,
    emoji: String,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp),
    modifier: Modifier = Modifier
) {
    val provider = remember { JioSaavnProvider() }
    var state by remember { mutableStateOf<UiState<List<Track>>>(UiState.Loading) }

    LaunchedEffect(gradientSeed) {
        state = UiState.Loading
        state = loadAsUiState(errorMessage = "Couldn't reach JioSaavn. Check your connection and try again.") {
            fetchTracks(provider)
                .filter { it.directStreamUrl != null }
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
                            DownloadButton(track = track)
                        }
                    }
                }
            }
        }
    }
}
