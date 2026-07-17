package com.example

import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
    onPlayTrack: (Track, List<Track>) -> Unit,
    onAlbumClick: (AlbumResult) -> Unit,
    onArtistClick: (ArtistResult) -> Unit,
    onPlaylistClick: (PlaylistResult) -> Unit,
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
                    1 -> AlbumsResults(searchQuery = searchQuery, onAlbumClick = onAlbumClick)
                    2 -> ArtistsResults(searchQuery = searchQuery, onArtistClick = onArtistClick)
                    3 -> PlaylistsResults(searchQuery = searchQuery, onPlaylistClick = onPlaylistClick)
                }
            }
        }
    }
}

@Composable
fun SongsResults(
    searchQuery: String,
    onPlayTrack: (Track, List<Track>) -> Unit
) {
    val context = LocalContext.current
    val jioSaavnProvider = remember { JioSaavnProvider() }
    val youTubeProvider = remember { YouTubeMusicProvider(context) }
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
        var jioFailed = false
        var ytFailed = false
        try {
            // JioSaavn and YouTube Music are queried in parallel - neither should have to wait
            // on the other, and a failure in one shouldn't block results from the other.
            val (jioResults, ytResults) = coroutineScope {
                val jioDeferred = async {
                    try {
                        // Unlike YouTube results (never directly playable, resolved on tap
                        // instead), a JioSaavn row is only worth showing if it's actually
                        // playable - a small fraction fail DES decryption and come back null.
                        jioSaavnProvider.search(searchQuery).filter { it.directStreamUrl != null }
                    } catch (e: Exception) {
                        jioFailed = true
                        emptyList()
                    }
                }
                val ytDeferred = async {
                    try {
                        youTubeProvider.search(searchQuery)
                    } catch (e: Exception) {
                        ytFailed = true
                        emptyList()
                    }
                }
                jioDeferred.await() to ytDeferred.await()
            }
            results = mergeSearchResults(jioResults, ytResults)
                .mapIndexed { index, result -> result.toPlayableTrack(gradientIndex = index) }
            // Only a genuine failure (both sources unreachable) counts as an error state -
            // one source failing while the other succeeds should just show partial results.
            hasError = jioFailed && ytFailed
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
            message = "Couldn't reach JioSaavn or YouTube Music. Check your connection and try again."
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
                val isFromYouTube = track.sourceType == MusicSource.YOUTUBE_MUSIC

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        // YouTube tracks play directly now, same as JioSaavn - PlaybackService
                        // resolves a fresh, real stream URL right before actual playback (see
                        // YouTubeStreamResolver), so no upfront resolve/substitute step is needed
                        // here anymore.
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isFromYouTube) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "• YouTube Music",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    Text(
                        text = track.duration,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!isFromYouTube) {
                        DownloadButton(track = track, modifier = Modifier.testTag("search_download_button_${track.title.lowercase().replace(" ", "_")}"))
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumsResults(searchQuery: String, onAlbumClick: (AlbumResult) -> Unit) {
    val provider = remember { JioSaavnProvider() }
    var albums by remember { mutableStateOf<List<AlbumResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            albums = emptyList()
            isLoading = false
            hasError = false
            return@LaunchedEffect
        }
        isLoading = true
        hasError = false
        delay(350)
        try {
            albums = provider.searchAlbums(searchQuery)
            hasError = false
        } catch (e: Exception) {
            albums = emptyList()
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
            title = "Search for an album",
            message = "Find any album to browse its tracklist."
        )
        albums.isEmpty() -> EmptySearchState()
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(albums) { album ->
                val gradientColors = MusicData.Gradients[(album.id.hashCode().mod(MusicData.Gradients.size))]

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onAlbumClick(album) }
                        .padding(8.dp)
                        .testTag("search_album_row_${album.title.lowercase().replace(" ", "_")}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TrackArtwork(
                        imageUrl = album.imageUrl,
                        gradientColors = gradientColors,
                        modifier = Modifier.size(56.dp)
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
                            text = album.artist + (album.songCount?.let { " • $it Songs" } ?: ""),
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
fun ArtistsResults(searchQuery: String, onArtistClick: (ArtistResult) -> Unit) {
    val provider = remember { JioSaavnProvider() }
    var artists by remember { mutableStateOf<List<ArtistResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            artists = emptyList()
            isLoading = false
            hasError = false
            return@LaunchedEffect
        }
        isLoading = true
        hasError = false
        delay(350)
        try {
            artists = provider.searchArtists(searchQuery)
            hasError = false
        } catch (e: Exception) {
            artists = emptyList()
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
            title = "Search for an artist",
            message = "Find any artist to browse their top songs."
        )
        artists.isEmpty() -> EmptySearchState()
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(artists) { artist ->
                val gradientColors = MusicData.Gradients[(artist.id.hashCode().mod(MusicData.Gradients.size))]

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onArtistClick(artist) }
                        .padding(8.dp)
                        .testTag("search_artist_row_${artist.name.lowercase().replace(" ", "_")}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TrackArtwork(
                        imageUrl = artist.imageUrl,
                        gradientColors = gradientColors,
                        shape = CircleShape,
                        modifier = Modifier.size(56.dp)
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
                            text = "Artist",
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
fun PlaylistsResults(searchQuery: String, onPlaylistClick: (PlaylistResult) -> Unit) {
    val provider = remember { JioSaavnProvider() }
    var playlists by remember { mutableStateOf<List<PlaylistResult>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            playlists = emptyList()
            isLoading = false
            hasError = false
            return@LaunchedEffect
        }
        isLoading = true
        hasError = false
        delay(350)
        try {
            playlists = provider.searchPlaylists(searchQuery)
            hasError = false
        } catch (e: Exception) {
            playlists = emptyList()
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
            title = "Search for a playlist",
            message = "Find any playlist to browse its tracklist."
        )
        playlists.isEmpty() -> EmptySearchState()
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(playlists) { playlist ->
                val gradientColors = MusicData.Gradients[(playlist.id.hashCode().mod(MusicData.Gradients.size))]

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onPlaylistClick(playlist) }
                        .padding(8.dp)
                        .testTag("search_playlist_row_${playlist.title.lowercase().replace(" ", "_")}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TrackArtwork(
                        imageUrl = playlist.imageUrl,
                        gradientColors = gradientColors,
                        modifier = Modifier.size(56.dp)
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
                            text = playlist.subtitle,
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
