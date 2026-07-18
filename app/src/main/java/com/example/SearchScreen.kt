package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

private fun audioPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

@Composable
fun SearchScreen(
    onPlayTrack: (Track, List<Track>) -> Unit,
    onAlbumClick: (AlbumResult) -> Unit,
    onArtistClick: (ArtistResult) -> Unit,
    onPlaylistClick: (PlaylistResult) -> Unit,
    modifier: Modifier = Modifier
) {
    // Scoped via viewModel() to this screen's own NavBackStackEntry, which survives Now Playing
    // being pushed on top and popped back off - so the query, results, and selected tab are still
    // here when the user comes back, instead of resetting like plain remember{} state would.
    val searchViewModel: SearchViewModel = viewModel()
    var searchQuery by searchViewModel.searchQueryState
    var selectedTab by searchViewModel.selectedTabState
    var searchMode by searchViewModel.searchModeState
    var hasSubmitted by searchViewModel.hasSubmittedState
    var isSearchFieldFocused by remember { mutableStateOf(false) }
    val tabTitles = listOf("Songs", "Albums", "Artists", "Playlists")
    val focusManager = LocalFocusManager.current

    val context = LocalContext.current
    var hasAudioPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, audioPermission()) == PackageManager.PERMISSION_GRANTED)
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasAudioPermission = granted }

    val searchHistoryViewModel: SearchHistoryViewModel = viewModel()
    val recentQueries by searchHistoryViewModel.recentQueries.collectAsState()

    // Records a query once the user actually pauses on it (same debounce every results tab
    // already waits on before firing its own search) - not on every keystroke.
    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) return@LaunchedEffect
        delay(350)
        searchHistoryViewModel.record(searchQuery)
    }

    fun submitSearch(query: String) {
        if (query.isBlank()) return
        searchQuery = query
        hasSubmitted = true
        focusManager.clearFocus()
    }

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

            // Search Box, with a mode-toggle icon beside it (globe = Online, folder = On device)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { newValue ->
                        searchQuery = newValue
                        // Clearing the bar back to empty returns to the bare, pre-search state.
                        if (newValue.isBlank()) hasSubmitted = false
                    },
                    placeholder = {
                        Text(
                            text = if (searchMode == SearchMode.ONLINE) "Search songs, artists, or a lyric..." else "Search local files...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Icon",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                searchQuery = ""
                                hasSubmitted = false
                            }) {
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
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submitSearch(searchQuery) }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { isSearchFieldFocused = it.isFocused }
                        .testTag("search_input_field")
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        searchMode = if (searchMode == SearchMode.ONLINE) SearchMode.ON_DEVICE else SearchMode.ONLINE
                        if (searchMode == SearchMode.ON_DEVICE && !hasAudioPermission) {
                            audioPermissionLauncher.launch(audioPermission())
                        }
                    },
                    modifier = Modifier.testTag("search_mode_toggle")
                ) {
                    Icon(
                        imageVector = if (searchMode == SearchMode.ONLINE) Icons.Default.Public else Icons.Default.PermMedia,
                        contentDescription = if (searchMode == SearchMode.ONLINE) "Online search - tap to switch to on-device" else "On-device search - tap to switch to online",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Recent-search chips - only while the field is actually focused (keyboard visible),
            // never persistently sitting below the bar. Capped at the 6 most recent.
            if (isSearchFieldFocused && recentQueries.isNotEmpty()) {
                SearchHistoryChips(
                    queries = recentQueries.take(6),
                    onQueryClick = { submitSearch(it) },
                    onQueryRemove = { searchHistoryViewModel.delete(it) },
                    onClearAll = { searchHistoryViewModel.clearAll() }
                )
            }

            // Category Tab Row - Online only, and only once a search has actually been
            // submitted; On-device search has no Album/Artist/Playlist equivalent, just one flat
            // song list (see SongResultRows).
            if (searchMode == SearchMode.ONLINE && hasSubmitted) {
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
                    modifier = Modifier.padding(top = 12.dp, bottom = 12.dp)
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
            }

            // Results Container - nothing shows here at all until a search is actually submitted.
            if (hasSubmitted) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = if (searchMode == SearchMode.ON_DEVICE) 12.dp else 0.dp)
                ) {
                    if (searchMode == SearchMode.ON_DEVICE) {
                        OnDeviceResults(
                            searchViewModel = searchViewModel,
                            searchQuery = searchQuery,
                            hasPermission = hasAudioPermission,
                            onRequestPermission = { audioPermissionLauncher.launch(audioPermission()) },
                            onPlayTrack = onPlayTrack
                        )
                    } else {
                        when (selectedTab) {
                            0 -> SongsResults(searchViewModel = searchViewModel, searchQuery = searchQuery, onPlayTrack = onPlayTrack)
                            1 -> AlbumsResults(searchViewModel = searchViewModel, searchQuery = searchQuery, onAlbumClick = onAlbumClick)
                            2 -> ArtistsResults(searchViewModel = searchViewModel, searchQuery = searchQuery, onArtistClick = onArtistClick)
                            3 -> PlaylistsResults(searchViewModel = searchViewModel, searchQuery = searchQuery, onPlaylistClick = onPlaylistClick)
                        }
                    }
                }
            }
        }
    }
}

/** Search's "On device" mode: searches local audio files via [LocalAudioProvider] (MediaStore) -
 * no network, no debounce-worthy latency to hide, just a quick local query - and renders through
 * the exact same [SongResultRows] Online's Songs tab uses, so a result looks identical regardless
 * of where it came from. Results are cached on [searchViewModel] so they survive navigating away
 * and back. */
@Composable
private fun OnDeviceResults(
    searchViewModel: SearchViewModel,
    searchQuery: String,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit
) {
    val state by searchViewModel.onDeviceResultState

    LaunchedEffect(searchQuery, hasPermission) {
        searchViewModel.ensureOnDeviceLoaded(hasPermission)
    }

    if (!hasPermission) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("🎵", fontSize = 48.sp, modifier = Modifier.padding(bottom = 16.dp))
            Text(
                text = "Permission needed",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Allow access to audio files to search music stored on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRequestPermission, modifier = Modifier.testTag("on_device_grant_permission")) {
                Text("Grant access")
            }
        }
        return
    }

    val currentState = state
    when {
        currentState is UiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        currentState is UiState.Error -> EmptySearchState(
            title = "Couldn't search this device",
            message = currentState.message
        )
        currentState is UiState.Success && currentState.data.isEmpty() -> EmptySearchState(
            title = if (searchQuery.isBlank()) "No local music found" else "No results found",
            message = if (searchQuery.isBlank()) {
                "No audio files were found on this device."
            } else {
                "Double check your spelling or search for something else."
            }
        )
        currentState is UiState.Success -> SongResultRows(results = currentState.data, onPlayTrack = onPlayTrack)
    }
}

/** Tappable recent-search chips shown below the search box (see [SearchScreen]) - each carries
 * its own small remove ("x") target, plus a trailing "Clear all" action for the whole list. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SearchHistoryChips(
    queries: List<String>,
    onQueryClick: (String) -> Unit,
    onQueryRemove: (String) -> Unit,
    onClearAll: () -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recent searches",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Clear all",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(onClick = onClearAll)
                    .testTag("search_history_clear_all")
            )
        }
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            queries.forEach { query ->
                InputChip(
                    selected = false,
                    onClick = { onQueryClick(query) },
                    label = { Text(query, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Remove \"$query\" from search history",
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onQueryRemove(query) }
                        )
                    },
                    modifier = Modifier.testTag("search_history_chip_${query.lowercase().replace(" ", "_")}")
                )
            }
        }
    }
}

@Composable
private fun SongsResults(
    searchViewModel: SearchViewModel,
    searchQuery: String,
    onPlayTrack: (Track, List<Track>) -> Unit
) {
    val state by searchViewModel.songsResultState

    LaunchedEffect(searchQuery) {
        searchViewModel.ensureSongsLoaded()
    }

    val currentState = state
    when {
        currentState is UiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        currentState is UiState.Error -> EmptySearchState(
            title = "Search failed",
            message = currentState.message
        )
        currentState is UiState.Success && currentState.data.isEmpty() -> EmptySearchState()
        currentState is UiState.Success -> SongResultRows(results = currentState.data, onPlayTrack = onPlayTrack)
    }
}

/** The actual track-row list shared by every song-result source - Online search ([SongsResults])
 * and On-device search ([OnDeviceResults]) both render through this, so switching modes changes
 * only where the data comes from, never how a result looks. */
@Composable
private fun SongResultRows(results: List<Track>, onPlayTrack: (Track, List<Track>) -> Unit) {
    // Shared with the Library/Now Playing heart - same Room-backed liked state, so liking a
    // result here shows up everywhere else that track appears.
    val likedSongsViewModel: LikedSongsViewModel = viewModel()
    val likedKeys by likedSongsViewModel.likedKeys.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(results) { track ->
            val gradientColors = MusicData.Gradients[track.gradientIndex % MusicData.Gradients.size]
            val isFromYouTube = track.sourceType == MusicSource.YOUTUBE_MUSIC
            val isLocalDevice = track.sourceType == MusicSource.LOCAL_DEVICE
            val isLiked = likedKeys.contains(track.downloadKey())

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    // YouTube tracks play directly now, same as JioSaavn - PlaybackService
                    // resolves a fresh, real stream URL right before actual playback (see
                    // YouTubeStreamResolver), so no upfront resolve/substitute step is needed
                    // here anymore. A local-device track's content:// URI is already directly
                    // playable too, same as it always was.
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
                        if (isFromYouTube || isLocalDevice) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isFromYouTube) "• YouTube Music" else "• On this device",
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
                IconButton(
                    onClick = { likedSongsViewModel.toggle(track) },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("search_like_button_${track.title.lowercase().replace(" ", "_")}")
                ) {
                    Icon(
                        imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isLiked) "Remove from Liked" else "Add to Liked",
                        tint = if (isLiked) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                // No per-row download button for any source here - same as YouTube always was
                // (streams via a fresh per-play resolve, not a fixed URL); downloading now happens
                // from Now Playing only, for every source alike.
            }
        }
    }
}

@Composable
private fun AlbumsResults(searchViewModel: SearchViewModel, searchQuery: String, onAlbumClick: (AlbumResult) -> Unit) {
    val state by searchViewModel.albumsResultState

    LaunchedEffect(searchQuery) {
        searchViewModel.ensureAlbumsLoaded()
    }

    val currentState = state
    when {
        currentState is UiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        currentState is UiState.Error -> EmptySearchState(
            title = "Search failed",
            message = currentState.message
        )
        currentState is UiState.Success && currentState.data.isEmpty() -> EmptySearchState()
        currentState is UiState.Success -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(currentState.data) { album ->
                val gradientColors = MusicData.Gradients[(album.id.hashCode().mod(MusicData.Gradients.size))]
                val isFromYouTube = album.sourceType == MusicSource.YOUTUBE_MUSIC

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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = album.artist + (album.songCount?.let { " • $it Songs" } ?: ""),
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
                }
            }
        }
    }
}

@Composable
private fun ArtistsResults(searchViewModel: SearchViewModel, searchQuery: String, onArtistClick: (ArtistResult) -> Unit) {
    val state by searchViewModel.artistsResultState

    LaunchedEffect(searchQuery) {
        searchViewModel.ensureArtistsLoaded()
    }

    val currentState = state
    when {
        currentState is UiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        currentState is UiState.Error -> EmptySearchState(
            title = "Search failed",
            message = currentState.message
        )
        currentState is UiState.Success && currentState.data.isEmpty() -> EmptySearchState()
        currentState is UiState.Success -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(currentState.data) { artist ->
                val gradientColors = MusicData.Gradients[(artist.id.hashCode().mod(MusicData.Gradients.size))]
                val isFromYouTube = artist.sourceType == MusicSource.YOUTUBE_MUSIC

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
                            text = if (isFromYouTube) "Artist • YouTube Music" else "Artist",
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
private fun PlaylistsResults(searchViewModel: SearchViewModel, searchQuery: String, onPlaylistClick: (PlaylistResult) -> Unit) {
    val state by searchViewModel.playlistsResultState

    LaunchedEffect(searchQuery) {
        searchViewModel.ensurePlaylistsLoaded()
    }

    val currentState = state
    when {
        currentState is UiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        currentState is UiState.Error -> EmptySearchState(
            title = "Search failed",
            message = currentState.message
        )
        currentState is UiState.Success && currentState.data.isEmpty() -> EmptySearchState()
        currentState is UiState.Success -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(currentState.data) { playlist ->
                val gradientColors = MusicData.Gradients[(playlist.id.hashCode().mod(MusicData.Gradients.size))]
                val isFromYouTube = playlist.sourceType == MusicSource.YOUTUBE_MUSIC

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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = playlist.subtitle,
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
