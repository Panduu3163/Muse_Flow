package com.example

import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
    onPlayTrack: (Track, List<Track>) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("Songs", "Albums", "Artists", "Playlists")

    ThemedBackground(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Search Header
            Text(
                text = "Search",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 12.dp)
            )

            // Search Box
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("What do you want to listen to?", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Icon",
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    .testTag("search_input_field")
            )

            // Category Tab Row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                divider = {
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                },
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        },
                        modifier = Modifier.testTag("search_tab_${title.lowercase()}")
                    )
                }
            }

            // Results Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when (selectedTab) {
                    0 -> SongsResults(searchQuery = searchQuery, onPlayTrack = onPlayTrack)
                    1 -> AlbumsResults(searchQuery = searchQuery)
                    2 -> ArtistsResults(searchQuery = searchQuery)
                    3 -> PlaylistsResults(searchQuery = searchQuery)
                }
            }
        }
    }
}

/** JioSaavn results mapped to real, playable [Track]s: [Track.streamUrl] and [Track.imageUrl]
 * carry the actual CDN/cover-art URLs straight from search, so playing one needs no further
 * resolution step and its artwork is already known everywhere the track flows (mini-player,
 * Now Playing, notification). Results without a resolvable stream URL are dropped - they'd be
 * unplayable dead ends if shown. */
private fun TrackResult.toPlayableTrack(gradientIndex: Int): Track = Track(
    title = title,
    artist = artist,
    album = source,
    duration = duration ?: "-:--",
    plays = "",
    gradientIndex = gradientIndex,
    imageUrl = imageUrl,
    streamUrl = directStreamUrl
)

@Composable
fun SongsResults(
    searchQuery: String,
    onPlayTrack: (Track, List<Track>) -> Unit
) {
    val provider = remember { JioSaavnProvider() }
    var results by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            results = emptyList()
            isLoading = false
            hasError = false
            return@LaunchedEffect
        }
        isLoading = true
        hasError = false
        delay(350) // debounce so we don't fire a search per keystroke
        try {
            results = provider.search(searchQuery)
                .filter { it.directStreamUrl != null }
                .mapIndexed { index, result -> result.toPlayableTrack(gradientIndex = index) }
            hasError = false
        } catch (e: Exception) {
            results = emptyList()
            hasError = true
        } finally {
            isLoading = false
        }
    }

    when {
        isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        hasError -> EmptySearchState(
            title = "Search failed",
            message = "Couldn't reach JioSaavn. Check your connection and try again."
        )
        searchQuery.isBlank() -> EmptySearchState(
            title = "Search for a song",
            message = "Find any track, artist, or album to start listening."
        )
        results.isEmpty() -> EmptySearchState()
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(results) { track ->
                val gradientColors = MusicData.Gradients[track.gradientIndex % MusicData.Gradients.size]

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onPlayTrack(track, results) }
                        .padding(8.dp)
                        .testTag("search_song_row_${track.title.lowercase().replace(" ", "_")}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TrackArtwork(
                        imageUrl = track.imageUrl,
                        gradientColors = gradientColors,
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
                    DownloadButton(track = track, modifier = Modifier.testTag("search_download_button_${track.title.lowercase().replace(" ", "_")}"))
                }
            }
        }
    }
}

@Composable
fun AlbumsResults(searchQuery: String) {
    val filteredAlbums = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            MusicData.albums
        } else {
            MusicData.albums.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    if (filteredAlbums.isEmpty()) {
        EmptySearchState()
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filteredAlbums) { album ->
                val gradientColors = MusicData.Gradients[album.gradientIndex % MusicData.Gradients.size]

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { }
                        .padding(8.dp)
                        .testTag("search_album_row_${album.title.lowercase().replace(" ", "_")}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Brush.linearGradient(gradientColors)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("💿", fontSize = 24.sp)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = album.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${album.artist} • ${album.trackCount} Songs (${album.year})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ArtistsResults(searchQuery: String) {
    val filteredArtists = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            MusicData.artists
        } else {
            MusicData.artists.filter {
                it.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    if (filteredArtists.isEmpty()) {
        EmptySearchState()
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filteredArtists) { artist ->
                val gradientColors = MusicData.Gradients[artist.gradientIndex % MusicData.Gradients.size]

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { }
                        .padding(8.dp)
                        .testTag("search_artist_row_${artist.name.lowercase().replace(" ", "_")}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Circle avatar for Artist
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(gradientColors)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = artist.name.take(1),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = artist.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = artist.followers,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistsResults(searchQuery: String) {
    val filteredPlaylists = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            MusicData.playlists
        } else {
            MusicData.playlists.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.creator.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    if (filteredPlaylists.isEmpty()) {
        EmptySearchState()
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filteredPlaylists) { playlist ->
                val gradientColors = MusicData.Gradients[playlist.gradientIndex % MusicData.Gradients.size]

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { }
                        .padding(8.dp)
                        .testTag("search_playlist_row_${playlist.title.lowercase().replace(" ", "_")}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Brush.linearGradient(gradientColors)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🎵", fontSize = 24.sp)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playlist.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "By ${playlist.creator} • ${playlist.trackCount} tracks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmptySearchState(
    title: String = "No results found",
    message: String = "Double check your spelling or search for something else."
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🔍",
            fontSize = 48.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
