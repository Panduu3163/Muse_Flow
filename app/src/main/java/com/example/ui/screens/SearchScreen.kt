package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.utils.bounceClick
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.AlbumResult
import com.example.ArtistResult
import com.example.CollectionKind
import com.example.PlayerViewModel
import com.example.PlaylistResult
import com.example.SearchFilter
import com.example.SearchViewModel
import com.example.Track
import com.example.TrackActionsViewModel
import com.example.TrackResult
import com.example.UiState
import com.example.downloadKey
import com.example.toPlayableTrack
import com.example.ui.component.CollectionRow
import com.example.ui.component.ListPlaceholder
import com.example.ui.component.TrackActionsHost
import com.example.ui.component.TrackRow
import com.example.ui.component.TrackSelection
import com.example.ui.component.TrackSelectionHost
import com.example.ui.component.rememberTrackSelection

/**
 * Search over YouTube Music, with debounced type-ahead suggestions and recent-query history.
 *
 * Results are split by kind - songs, albums, artists, playlists - each backed by its own YouTube
 * Music search filter. Albums, artists and playlists all navigate to a real destination screen
 * ([AlbumScreen]/[ArtistScreen]/[RemotePlaylistScreen]) rather than a modal sheet.
 *
 * Long-pressing a song opens its actions sheet (like / download / add to playlist); rows show
 * heart and download glyphs so their state is readable without opening the sheet.
 */
@Composable
fun SearchScreen(
    onPlayTrack: (TrackResult, List<TrackResult>) -> Unit = { _, _ -> },
    playerViewModel: PlayerViewModel,
    onGoToArtist: (String) -> Unit = {},
    onGoToAlbum: (String) -> Unit = {},
    /** title/subtitle/imageUrl travel alongside the id - see [com.example.NavRoutes.remotePlaylist]
     * for why (no endpoint returns a remote playlist's own header by id alone). */
    onGoToPlaylist: (String, String, String, String?) -> Unit = { _, _, _, _ -> },
    onOpenCharts: () -> Unit = {},
    onOpenNewReleases: () -> Unit = {},
    onOpenExplore: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel: SearchViewModel = viewModel()
    val query by viewModel.query.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val results by viewModel.results.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val searchedPlaylists by viewModel.playlists.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val hasSearched by viewModel.hasSearched.collectAsState()
    val recentQueries by viewModel.recentQueries.collectAsState(initial = emptyList())
    val keyboard = LocalSoftwareKeyboardController.current

    val actionsViewModel: TrackActionsViewModel = viewModel()
    val likedKeys by actionsViewModel.likedKeys.collectAsState()
    val downloadedKeys by actionsViewModel.downloadedKeys.collectAsState()
    val downloadsInProgress by actionsViewModel.downloadsInProgress.collectAsState()

    val selection = rememberTrackSelection()
    var selectedTrack by remember { mutableStateOf<TrackResult?>(null) }

    // Only the Songs tab holds selectable rows, and only for the query that produced them - a new
    // search or a switch to Albums renumbers everything underneath a positional selection.
    val songResults = (results as? UiState.Success)?.data.orEmpty()
    LaunchedEffect(filter, songResults) { selection.clear() }

    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            placeholder = { Text("Songs, artists, or a lyric you remember") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = viewModel::clearQuery) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    viewModel.search()
                    keyboard?.hide()
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("search_field"),
        )

        // No backend chip: with the extractor picker removed from Settings there's only one
        // backend, so naming it was developer-facing noise rather than information.

        // A browse entry point rather than a search result - shown above recents/suggestions
        // (not inside that `when` below) so it's visible regardless of whether either has
        // anything to show, the same way a real charts page is reachable independent of history.
        //
        // Charts/New releases rows are hidden (not deleted - onOpenCharts/onOpenNewReleases,
        // ChartsScreen and NewReleasesScreen all still exist and are still routed) because both
        // backends are broken: getChartsTracks() silently returns zero tracks and
        // newReleaseAlbums() 404s. Both need real investigation (see full-gap-audit.md §2.9)
        // rather than a UI-level fix, so the entry points are pulled until that's done instead of
        // shipping a row that reliably shows an error.
        if (query.isBlank()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .bounceClick(onClick = onOpenExplore)
                    .testTag("search_open_explore"),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Explore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                        Text(
                            text = "Explore",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            text = "Moods, genres, and browse picks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                suggestions.isNotEmpty() && query.isNotBlank() -> SuggestionList(
                    suggestions = suggestions,
                    onPick = {
                        viewModel.search(it)
                        keyboard?.hide()
                    },
                )

                query.isBlank() && recentQueries.isNotEmpty() -> RecentList(
                    recents = recentQueries,
                    onPick = {
                        viewModel.search(it)
                        keyboard?.hide()
                    },
                    onDelete = viewModel::deleteRecent,
                )

                else -> Column(modifier = Modifier.fillMaxSize()) {
                    // Hidden until a query has been run: with nothing to filter, the chips would
                    // be a control that visibly does nothing.
                    if (selection.active) {
                        // Replaces the filter chips: switching tab mid-selection would leave ticks
                        // pointing at rows that are no longer on screen.
                        TrackSelectionHost(
                            selection = selection,
                            tracks = songResults,
                            playerViewModel = playerViewModel,
                            actionsViewModel = actionsViewModel,
                        )
                    } else if (hasSearched) {
                        FilterChips(selected = filter, onSelect = viewModel::selectFilter)
                    }

                    val emptyMessage = if (hasSearched) {
                        "No ${filter.label.lowercase()} found for \"$query\"."
                    } else {
                        "Search for something to get started."
                    }

                    // Weighted, so the results area is what's left below the chips - the empty and
                    // loading states inside it centre on that space rather than on the whole
                    // screen and overflow past the bottom.
                    Box(modifier = Modifier.weight(1f)) {
                        when (filter) {
                            SearchFilter.Songs -> TrackResults(
                                results = results,
                                emptyMessage = emptyMessage,
                                onPlayTrack = onPlayTrack,
                                likedKeys = likedKeys,
                                downloadedKeys = downloadedKeys,
                                downloadsInProgress = downloadsInProgress,
                                onOpenMenu = { track -> selectedTrack = track },
                                selection = selection,
                            )

                            SearchFilter.Albums -> CollectionResults(
                                results = albums,
                                emptyMessage = emptyMessage,
                                kind = CollectionKind.Album,
                                title = AlbumResult::title,
                                subtitle = { album ->
                                    listOfNotNull(
                                        album.artist.takeIf { it.isNotBlank() },
                                        album.songCount?.let { "$it songs" },
                                    ).joinToString(" · ")
                                },
                                imageUrl = AlbumResult::imageUrl,
                                // Full navigation, not a modal sheet - same reasoning as Artist below.
                                onOpen = { album -> onGoToAlbum(album.id) },
                            )

                            SearchFilter.Artists -> CollectionResults(
                                results = artists,
                                emptyMessage = emptyMessage,
                                kind = CollectionKind.Artist,
                                title = ArtistResult::name,
                                subtitle = { it.listenerCount ?: "Artist" },
                                imageUrl = ArtistResult::imageUrl,
                                // Full navigation, not the CollectionSheet modal the other three
                                // kinds use - an artist has a real destination screen (with its own
                                // tabs) to go to, unlike a song/album/playlist result.
                                onOpen = { artist -> onGoToArtist(artist.id) },
                            )

                            SearchFilter.Playlists -> CollectionResults(
                                results = searchedPlaylists,
                                emptyMessage = emptyMessage,
                                kind = CollectionKind.Playlist,
                                title = PlaylistResult::title,
                                subtitle = { playlist ->
                                    listOfNotNull(
                                        playlist.subtitle.takeIf { it.isNotBlank() },
                                        playlist.songCount?.let { "$it songs" },
                                    ).joinToString(" · ")
                                },
                                imageUrl = PlaylistResult::imageUrl,
                                onOpen = { playlist ->
                                    onGoToPlaylist(playlist.id, playlist.title, playlist.subtitle, playlist.imageUrl)
                                },
                            )
                        }
                    }
                }
            }
        }
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

@Composable
private fun FilterChips(selected: SearchFilter, onSelect: (SearchFilter) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier.padding(bottom = 4.dp),
    ) {
        items(SearchFilter.entries.toList(), key = { it.name }) { entry ->
            FilterChip(
                selected = selected == entry,
                onClick = { onSelect(entry) },
                label = { Text(entry.label) },
                modifier = Modifier.testTag("search_filter_${entry.name.lowercase()}"),
            )
        }
    }
}

@Composable
private fun TrackResults(
    results: UiState<List<TrackResult>>,
    emptyMessage: String,
    onPlayTrack: (TrackResult, List<TrackResult>) -> Unit,
    likedKeys: Set<String>,
    downloadedKeys: Set<String>,
    downloadsInProgress: Map<String, Int>,
    onOpenMenu: (TrackResult) -> Unit,
    selection: TrackSelection,
) {
    ResultsFrame(results, emptyMessage) { tracks ->
        LazyColumn(contentPadding = PaddingValues(bottom = 200.dp)) {
            itemsIndexed(tracks, key = { index, track -> "$index-${track.id}" }) { index, track ->
                val key = track.toPlayableTrack(0).downloadKey()
                TrackRow(
                    title = track.title,
                    artist = track.artist,
                    imageUrl = track.imageUrl,
                    duration = track.duration,
                    onClick = {
                        if (selection.active) selection.toggle(index) else onPlayTrack(track, tracks)
                    },
                    onLongClick = {
                        if (selection.active) selection.toggle(index) else selection.start(index)
                    },
                    selected = selection.isSelected(index),
                    isLiked = likedKeys.contains(key),
                    isDownloaded = downloadedKeys.contains(key),
                    downloadProgress = downloadsInProgress[key],
                    onOpenMenu = if (selection.active) null else { { onOpenMenu(track) } },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

/**
 * Album/artist/playlist results.
 *
 * Generic over the three result types rather than written out three times: they differ only in
 * which fields carry the title, subtitle and image, so the accessors are parameters.
 */
@Composable
private fun <T> CollectionResults(
    results: UiState<List<T>>,
    emptyMessage: String,
    kind: CollectionKind,
    title: (T) -> String,
    subtitle: (T) -> String,
    imageUrl: (T) -> String?,
    onOpen: (T) -> Unit,
) {
    ResultsFrame(results, emptyMessage) { items ->
        LazyColumn(contentPadding = PaddingValues(bottom = 200.dp)) {
            // Position-based keys: YouTube can return the same browseId twice in one result set,
            // and a repeated Compose key is a crash rather than a cosmetic glitch.
            itemsIndexed(items, key = { index, _ -> index }) { _, item ->
                CollectionRow(
                    title = title(item),
                    subtitle = subtitle(item),
                    imageUrl = imageUrl(item),
                    kind = kind,
                    onClick = { onOpen(item) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

/** Loading spinner / error / empty handling, shared by every result tab so the four behave
 * identically when there is nothing to show. */
@Composable
private fun <T> ResultsFrame(
    results: UiState<List<T>>,
    emptyMessage: String,
    content: @Composable (List<T>) -> Unit,
) {
    when (results) {
        is UiState.Loading -> ListPlaceholder()

        is UiState.Error -> CenteredMessage(
            text = results.message,
            color = MaterialTheme.colorScheme.error,
        )

        is UiState.Success -> if (results.data.isEmpty()) {
            CenteredMessage(emptyMessage)
        } else {
            content(results.data)
        }
    }
}

@Composable
private fun SuggestionList(suggestions: List<String>, onPick: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = 200.dp)) {
        items(suggestions, key = { it }) { suggestion ->
            Row(
                // Clickable before padding, so the whole row - not just the text - responds.
                modifier = Modifier
                    .animateItem()
                    .fillMaxWidth()
                    .clickable { onPick(suggestion) }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = suggestion,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun RecentList(
    recents: List<String>,
    onPick: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 200.dp)) {
        items(recents, key = { it }) { recent ->
            Row(
                // Clickable before padding so the whole row responds, not just the text. The
                // delete IconButton keeps its own handler and isn't swallowed by this.
                modifier = Modifier
                    .animateItem()
                    .fillMaxWidth()
                    .clickable { onPick(recent) }
                    .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
                    .testTag("recent_query_$recent"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = recent,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 14.dp),
                )
                IconButton(onClick = { onDelete(recent) }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove from history",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(
    text: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = color, textAlign = TextAlign.Center)
    }
}
