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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.snapshotFlow
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
    onOpenBrowse: (String, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SearchViewModel = viewModel()
    // Drives the "now playing" equalizer badge on whichever row matches - see TrackRow's own doc.
    val nowPlayingState by playerViewModel.state.collectAsState()
    val query by viewModel.query.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val summary by viewModel.summary.collectAsState()
    val results by viewModel.results.collectAsState()
    val videos by viewModel.videos.collectAsState()
    val albums by viewModel.albums.collectAsState()
    val artists by viewModel.artists.collectAsState()
    val searchedPlaylists by viewModel.playlists.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val hasSearched by viewModel.hasSearched.collectAsState()
    val recommendationsStartAt by viewModel.recommendationsStartAt.collectAsState()
    val isLoadingMoreSongs by viewModel.isLoadingMore.collectAsState()
    val recentQueries by viewModel.recentQueries.collectAsState(initial = emptyList())
    val keyboard = LocalSoftwareKeyboardController.current

    val actionsViewModel: TrackActionsViewModel = viewModel()
    val likedKeys by actionsViewModel.likedKeys.collectAsState()
    val downloadedKeys by actionsViewModel.downloadedKeys.collectAsState()
    val downloadsInProgress by actionsViewModel.downloadsInProgress.collectAsState()

    val selection = rememberTrackSelection()
    var selectedTrack by remember { mutableStateOf<TrackResult?>(null) }
    var showRecents by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

    // Songs and Videos hold selectable rows, and only for the query that produced them - a new
    // search or a switch to Albums renumbers everything underneath a positional selection.
    val selectedResults = ((if (filter == SearchFilter.Videos) videos else results) as? UiState.Success)?.data.orEmpty()
    LaunchedEffect(filter, selectedResults) { selection.clear() }

    Column(modifier = modifier.fillMaxSize().statusBarsPadding()) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            placeholder = { Text("What do you want to hear?") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = viewModel::clearQuery) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = CircleShape,
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

        // Browse entry points rather than search results - shown above recents/suggestions (not
        // inside that `when` below) so they're visible regardless of whether either has anything
        // to show, the same way a real charts page is reachable independent of history.
        if (query.isBlank()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(!showRecents, { showRecents = false }, { Text("Explore") }, shape = CircleShape) }
                item { FilterChip(false, onOpenCharts, { Text("Musechart") }, shape = CircleShape) }
                item { FilterChip(false, onOpenNewReleases, { Text("Fresh drops") }, shape = CircleShape) }
                item { FilterChip(showRecents, { showRecents = true }, { Text("Recent searches") }, shape = CircleShape) }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                query.isBlank() && !showRecents -> ExplorePreview(onOpenBrowse = onOpenBrowse, onBrowseAll = onOpenExplore)
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
                            tracks = selectedResults,
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
                            SearchFilter.All -> AllResults(
                                shelves = summary,
                                emptyMessage = emptyMessage,
                                onPlayTrack = onPlayTrack,
                                likedKeys = likedKeys,
                                downloadedKeys = downloadedKeys,
                                downloadsInProgress = downloadsInProgress,
                                onOpenMenu = { track -> selectedTrack = track },
                                onGoToAlbum = onGoToAlbum,
                                onGoToArtist = onGoToArtist,
                                onGoToPlaylist = onGoToPlaylist,
                                nowPlayingKey = nowPlayingState.currentTrackKey,
                            )

                            SearchFilter.Songs -> TrackResults(
                                results = results,
                                emptyMessage = emptyMessage,
                                onPlayTrack = onPlayTrack,
                                likedKeys = likedKeys,
                                downloadedKeys = downloadedKeys,
                                downloadsInProgress = downloadsInProgress,
                                onOpenMenu = { track -> selectedTrack = track },
                                selection = selection,
                                recommendationsStartAt = recommendationsStartAt,
                                isLoadingMore = isLoadingMoreSongs,
                                onLoadMore = viewModel::loadMoreSongs,
                                nowPlayingKey = nowPlayingState.currentTrackKey,
                            )

                            SearchFilter.Videos -> TrackResults(
                                results = videos,
                                emptyMessage = emptyMessage,
                                onPlayTrack = onPlayTrack,
                                likedKeys = likedKeys,
                                downloadedKeys = downloadedKeys,
                                downloadsInProgress = downloadsInProgress,
                                onOpenMenu = { track -> selectedTrack = track },
                                selection = selection,
                                recommendationsStartAt = null,
                                isLoadingMore = false,
                                onLoadMore = {},
                                nowPlayingKey = nowPlayingState.currentTrackKey,
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
private fun BrowseTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.bounceClick(onClick = onClick).testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
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

/**
 * The "All" tab - YouTube Music's own mixed results page: Top result, Songs, Videos, Albums,
 * Artists, Playlists shelves, in whichever titles/order/counts that response actually returned
 * (see [com.example.SearchShelf]'s own doc - nothing here re-orders or re-groups them). Tapping a
 * song/video queues the rest of *that shelf's* songs behind it, not the whole page, since a shelf
 * is the only grouping with real relatedness to autoplay into.
 */
@Composable
private fun AllResults(
    shelves: UiState<List<com.example.SearchShelf>>,
    emptyMessage: String,
    onPlayTrack: (TrackResult, List<TrackResult>) -> Unit,
    likedKeys: Set<String>,
    downloadedKeys: Set<String>,
    downloadsInProgress: Map<String, Int>,
    onOpenMenu: (TrackResult) -> Unit,
    onGoToAlbum: (String) -> Unit,
    onGoToArtist: (String) -> Unit,
    onGoToPlaylist: (String, String, String, String?) -> Unit,
    nowPlayingKey: String? = null,
) {
    ResultsFrame(shelves, emptyMessage) { shelfList ->
        LazyColumn(contentPadding = PaddingValues(bottom = 200.dp)) {
            shelfList.forEachIndexed { shelfIndex, shelf ->
                item(key = "shelf_header_$shelfIndex") {
                    Text(
                        text = shelf.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
                    )
                }
                // Queue for a tap in this shelf: just this shelf's own songs, in the order shown -
                // a shelf mixing a song with an artist/playlist card has nothing else to queue.
                val shelfSongs = shelf.items.filterIsInstance<com.example.SearchResultItem.Song>().map { it.track }
                itemsIndexed(shelf.items, key = { itemIndex, _ -> "shelf_${shelfIndex}_item_$itemIndex" }) { _, resultItem ->
                    when (resultItem) {
                        is com.example.SearchResultItem.Song -> {
                            val key = resultItem.track.downloadKey()
                            TrackRow(
                                title = resultItem.track.title,
                                artist = resultItem.track.artist,
                                imageUrl = resultItem.track.imageUrl,
                                duration = resultItem.track.duration,
                                onClick = { onPlayTrack(resultItem.track, shelfSongs) },
                                isLiked = key in likedKeys,
                                isDownloaded = key in downloadedKeys,
                                downloadProgress = downloadsInProgress[key],
                                isPlaying = key == nowPlayingKey,
                                onOpenMenu = { onOpenMenu(resultItem.track) },
                                modifier = Modifier.animateItem(),
                            )
                        }

                        is com.example.SearchResultItem.AlbumRow -> CollectionRow(
                            title = resultItem.album.title,
                            subtitle = listOfNotNull(
                                resultItem.album.artist.takeIf { it.isNotBlank() },
                                resultItem.album.songCount?.let { "$it songs" },
                            ).joinToString(" · "),
                            imageUrl = resultItem.album.imageUrl,
                            kind = CollectionKind.Album,
                            onClick = { onGoToAlbum(resultItem.album.id) },
                            modifier = Modifier.animateItem(),
                        )

                        is com.example.SearchResultItem.ArtistRow -> CollectionRow(
                            title = resultItem.artist.name,
                            subtitle = resultItem.artist.listenerCount ?: "Artist",
                            imageUrl = resultItem.artist.imageUrl,
                            kind = CollectionKind.Artist,
                            onClick = { onGoToArtist(resultItem.artist.id) },
                            modifier = Modifier.animateItem(),
                        )

                        is com.example.SearchResultItem.PlaylistRow -> CollectionRow(
                            title = resultItem.playlist.title,
                            subtitle = resultItem.playlist.subtitle,
                            imageUrl = resultItem.playlist.imageUrl,
                            kind = CollectionKind.Playlist,
                            onClick = {
                                onGoToPlaylist(
                                    resultItem.playlist.id,
                                    resultItem.playlist.title,
                                    resultItem.playlist.subtitle,
                                    resultItem.playlist.imageUrl,
                                )
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The Songs tab's result list - direct search matches, seamlessly extending into a personalized,
 * effectively infinite recommendation feed once those run out (mirroring how YouTube's own search
 * behaves). [recommendationsStartAt] marks where that switch happened so a section header can drop
 * in at exactly that row; [onLoadMore] is called as the list is scrolled near its current end,
 * regardless of which phase it's in - the caller (SearchViewModel) decides what "more" means.
 */
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
    recommendationsStartAt: Int?,
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
    nowPlayingKey: String? = null,
) {
    ResultsFrame(results, emptyMessage) { tracks ->
        val listState = rememberLazyListState()

        // Fires as the list nears its current end - a fixed lookahead rather than "at the very
        // last item", so the next batch has time to arrive before the user actually catches up to
        // it. Re-armed whenever the track count changes, since a fetch that lands closes the
        // window this snapshotFlow was watching for.
        LaunchedEffect(listState, tracks.size) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
                .collect { lastVisible ->
                    if (lastVisible != null && lastVisible >= tracks.size - 5) onLoadMore()
                }
        }

        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 200.dp)) {
            itemsIndexed(tracks, key = { index, track -> "$index-${track.id}" }) { index, track ->
                if (recommendationsStartAt == index) {
                    RecommendationsHeader(modifier = Modifier.animateItem())
                }

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
                    isPlaying = key == nowPlayingKey,
                    onOpenMenu = if (selection.active) null else { { onOpenMenu(track) } },
                    modifier = Modifier.animateItem(),
                )
            }

            if (isLoadingMore) {
                item(key = "loading_more") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}

/** Drops into the Songs list right where direct search matches end and the personalized
 * recommendation tail begins, so the transition reads as an intentional feature rather than search
 * quietly getting worse. */
@Composable
private fun RecommendationsHeader(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "Recommended for you",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 10.dp),
            )
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

        is UiState.Error -> CenteredMessage(results.message, isError = true)

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
    isError: Boolean = false,
) {
    com.example.ui.component.FeedbackMessage(
        message = text,
        isError = isError,
        modifier = Modifier.fillMaxSize().padding(32.dp),
    )
}
