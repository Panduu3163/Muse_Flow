package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** Search's two data sources: [ONLINE] is the existing merged JioSaavn+YouTube Music search;
 * [ON_DEVICE] scans local audio files via [LocalAudioProvider]/MediaStore instead - no network
 * involved. Only [ONLINE] has Album/Artist/Playlist tabs; MediaStore has no equivalent grouping,
 * so [ON_DEVICE] shows a single flat song list (via the same [SongResultRows] Online uses). */
private enum class SearchMode { ONLINE, ON_DEVICE }

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
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }
    var isSearchFieldFocused by remember { mutableStateOf(false) }
    var searchMode by remember { mutableStateOf(SearchMode.ONLINE) }
    val tabTitles = listOf("Songs", "Albums", "Artists", "Playlists")

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
                    .onFocusChanged { isSearchFieldFocused = it.isFocused }
                    .testTag("search_input_field")
            )

            if ((searchQuery.isEmpty() || isSearchFieldFocused) && recentQueries.isNotEmpty()) {
                SearchHistoryChips(
                    queries = recentQueries,
                    onQueryClick = { searchQuery = it },
                    onQueryRemove = { searchHistoryViewModel.delete(it) },
                    onClearAll = { searchHistoryViewModel.clearAll() }
                )
            }

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                SegmentedButton(
                    selected = searchMode == SearchMode.ONLINE,
                    onClick = { searchMode = SearchMode.ONLINE },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    modifier = Modifier.testTag("search_mode_online")
                ) {
                    Text("Online")
                }
                SegmentedButton(
                    selected = searchMode == SearchMode.ON_DEVICE,
                    onClick = {
                        searchMode = SearchMode.ON_DEVICE
                        if (!hasAudioPermission) audioPermissionLauncher.launch(audioPermission())
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    modifier = Modifier.testTag("search_mode_on_device")
                ) {
                    Text("On device")
                }
            }

            // Category Tab Row - Online only; On-device search has no Album/Artist/Playlist
            // equivalent, just one flat song list (see SongResultRows).
            if (searchMode == SearchMode.ONLINE) {
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
            }

            // Results Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (searchMode == SearchMode.ON_DEVICE) {
                    OnDeviceResults(
                        searchQuery = searchQuery,
                        hasPermission = hasAudioPermission,
                        onRequestPermission = { audioPermissionLauncher.launch(audioPermission()) },
                        onPlayTrack = onPlayTrack
                    )
                } else {
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
}

/** Search's "On device" mode: searches local audio files via [LocalAudioProvider] (MediaStore) -
 * no network, no debounce-worthy latency to hide, just a quick local query - and renders through
 * the exact same [SongResultRows] Online's Songs tab uses, so a result looks identical regardless
 * of where it came from. */
@Composable
private fun OnDeviceResults(
    searchQuery: String,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit
) {
    val context = LocalContext.current
    val localAudioProvider = remember { LocalAudioProvider(context) }
    var state by remember { mutableStateOf<UiState<List<Track>>>(UiState.Loading) }

    LaunchedEffect(searchQuery, hasPermission) {
        if (!hasPermission) return@LaunchedEffect
        state = UiState.Loading
        delay(150) // still worth a light debounce so fast typing doesn't fire a query per key
        state = loadAsUiState(errorMessage = "Couldn't scan audio files on this device.") {
            localAudioProvider.search(searchQuery)
                .mapIndexed { index, result -> result.toPlayableTrack(gradientIndex = index) }
        }
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
fun SongsResults(
    searchQuery: String,
    onPlayTrack: (Track, List<Track>) -> Unit
) {
    val context = LocalContext.current
    val jioSaavnProvider = remember { JioSaavnProvider() }
    val youTubeProvider = remember { YouTubeMusicProvider(context) }
    var state by remember { mutableStateOf<UiState<List<Track>>>(UiState.Loading) }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            state = UiState.Loading
            return@LaunchedEffect
        }
        state = UiState.Loading
        delay(350) // debounce so we don't fire a search per keystroke
        var jioFailed = false
        var ytFailed = false
        // JioSaavn and YouTube Music are queried in parallel - neither should have to wait on
        // the other - and each is individually bounded so a slow one can't hold the whole search
        // "loading" forever; timing out counts the same as that source throwing.
        val (jioResults, ytResults) = coroutineScope {
            val jioDeferred = async {
                withTimeoutOrNull(DEFAULT_LOAD_TIMEOUT_MS) {
                    try {
                        // Unlike YouTube results (never directly playable, resolved on tap
                        // instead), a JioSaavn row is only worth showing if it's actually
                        // playable - a small fraction fail DES decryption and come back null.
                        jioSaavnProvider.search(searchQuery).filter { it.directStreamUrl != null }
                    } catch (e: Exception) {
                        null
                    }
                } ?: run { jioFailed = true; emptyList() }
            }
            val ytDeferred = async {
                withTimeoutOrNull(DEFAULT_LOAD_TIMEOUT_MS) {
                    try {
                        youTubeProvider.search(searchQuery)
                    } catch (e: Exception) {
                        null
                    }
                } ?: run { ytFailed = true; emptyList() }
            }
            jioDeferred.await() to ytDeferred.await()
        }
        val merged = mergeSearchResults(jioResults, ytResults)
            .mapIndexed { index, result -> result.toPlayableTrack(gradientIndex = index) }
        // Only a genuine failure (both sources unreachable or timed out) counts as an error
        // state - one source failing while the other succeeds should just show partial results.
        state = if (jioFailed && ytFailed) {
            UiState.Error("Couldn't reach JioSaavn or YouTube Music. Check your connection and try again.")
        } else {
            UiState.Success(merged)
        }
    }

    val currentState = state
    when {
        searchQuery.isBlank() -> EmptySearchState(
            title = "Search for a song",
            message = "Find any track, artist, or album to start listening."
        )
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
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(results) { track ->
            val gradientColors = MusicData.Gradients[track.gradientIndex % MusicData.Gradients.size]
            val isFromYouTube = track.sourceType == MusicSource.YOUTUBE_MUSIC
            val isLocalDevice = track.sourceType == MusicSource.LOCAL_DEVICE

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
                // Neither a YouTube track (streams via a fresh per-play resolve, not a fixed URL)
                // nor a local-device track (already sitting on-device - nothing to download) needs
                // this.
                if (!isFromYouTube && !isLocalDevice) {
                    DownloadButton(track = track, modifier = Modifier.testTag("search_download_button_${track.title.lowercase().replace(" ", "_")}"))
                }
            }
        }
    }
}

@Composable
fun AlbumsResults(searchQuery: String, onAlbumClick: (AlbumResult) -> Unit) {
    val context = LocalContext.current
    val jioSaavnProvider = remember { JioSaavnProvider() }
    val youTubeProvider = remember { YouTubeMusicProvider(context) }
    var state by remember { mutableStateOf<UiState<List<AlbumResult>>>(UiState.Loading) }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            state = UiState.Loading
            return@LaunchedEffect
        }
        state = UiState.Loading
        delay(350)
        var jioFailed = false
        var ytFailed = false
        val (jioResults, ytResults) = coroutineScope {
            val jioDeferred = async {
                withTimeoutOrNull(DEFAULT_LOAD_TIMEOUT_MS) {
                    try {
                        jioSaavnProvider.searchAlbums(searchQuery)
                    } catch (e: Exception) {
                        null
                    }
                } ?: run { jioFailed = true; emptyList() }
            }
            val ytDeferred = async {
                withTimeoutOrNull(DEFAULT_LOAD_TIMEOUT_MS) {
                    try {
                        youTubeProvider.searchAlbums(searchQuery)
                    } catch (e: Exception) {
                        null
                    }
                } ?: run { ytFailed = true; emptyList() }
            }
            jioDeferred.await() to ytDeferred.await()
        }
        state = if (jioFailed && ytFailed) {
            UiState.Error("Couldn't reach JioSaavn or YouTube Music. Check your connection and try again.")
        } else {
            UiState.Success(mergeAlbumResults(jioResults, ytResults))
        }
    }

    val currentState = state
    when {
        searchQuery.isBlank() -> EmptySearchState(
            title = "Search for an album",
            message = "Find any album to browse its tracklist."
        )
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
fun ArtistsResults(searchQuery: String, onArtistClick: (ArtistResult) -> Unit) {
    val context = LocalContext.current
    val jioSaavnProvider = remember { JioSaavnProvider() }
    val youTubeProvider = remember { YouTubeMusicProvider(context) }
    var state by remember { mutableStateOf<UiState<List<ArtistResult>>>(UiState.Loading) }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            state = UiState.Loading
            return@LaunchedEffect
        }
        state = UiState.Loading
        delay(350)
        var jioFailed = false
        var ytFailed = false
        val (jioResults, ytResults) = coroutineScope {
            val jioDeferred = async {
                withTimeoutOrNull(DEFAULT_LOAD_TIMEOUT_MS) {
                    try {
                        jioSaavnProvider.searchArtists(searchQuery)
                    } catch (e: Exception) {
                        null
                    }
                } ?: run { jioFailed = true; emptyList() }
            }
            val ytDeferred = async {
                withTimeoutOrNull(DEFAULT_LOAD_TIMEOUT_MS) {
                    try {
                        youTubeProvider.searchArtists(searchQuery)
                    } catch (e: Exception) {
                        null
                    }
                } ?: run { ytFailed = true; emptyList() }
            }
            jioDeferred.await() to ytDeferred.await()
        }
        state = if (jioFailed && ytFailed) {
            UiState.Error("Couldn't reach JioSaavn or YouTube Music. Check your connection and try again.")
        } else {
            UiState.Success(mergeArtistResults(jioResults, ytResults))
        }
    }

    val currentState = state
    when {
        searchQuery.isBlank() -> EmptySearchState(
            title = "Search for an artist",
            message = "Find any artist to browse their top songs."
        )
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
fun PlaylistsResults(searchQuery: String, onPlaylistClick: (PlaylistResult) -> Unit) {
    val context = LocalContext.current
    val jioSaavnProvider = remember { JioSaavnProvider() }
    val youTubeProvider = remember { YouTubeMusicProvider(context) }
    var state by remember { mutableStateOf<UiState<List<PlaylistResult>>>(UiState.Loading) }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            state = UiState.Loading
            return@LaunchedEffect
        }
        state = UiState.Loading
        delay(350)
        var jioFailed = false
        var ytFailed = false
        val (jioResults, ytResults) = coroutineScope {
            val jioDeferred = async {
                withTimeoutOrNull(DEFAULT_LOAD_TIMEOUT_MS) {
                    try {
                        jioSaavnProvider.searchPlaylists(searchQuery)
                    } catch (e: Exception) {
                        null
                    }
                } ?: run { jioFailed = true; emptyList() }
            }
            val ytDeferred = async {
                withTimeoutOrNull(DEFAULT_LOAD_TIMEOUT_MS) {
                    try {
                        youTubeProvider.searchPlaylists(searchQuery)
                    } catch (e: Exception) {
                        null
                    }
                } ?: run { ytFailed = true; emptyList() }
            }
            jioDeferred.await() to ytDeferred.await()
        }
        state = if (jioFailed && ytFailed) {
            UiState.Error("Couldn't reach JioSaavn or YouTube Music. Check your connection and try again.")
        } else {
            UiState.Success(mergePlaylistResults(jioResults, ytResults))
        }
    }

    val currentState = state
    when {
        searchQuery.isBlank() -> EmptySearchState(
            title = "Search for a playlist",
            message = "Find any playlist to browse its tracklist."
        )
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
