package com.example.ui.screens
import com.example.ui.component.liquidSurface
import com.example.downloadKey

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import com.example.AppSettingsViewModel
import coil.compose.AsyncImage
import com.example.CollectionKind
import com.example.FollowedArtistEntity
import com.example.LibrarySection
import com.example.LibraryViewModel
import com.example.LocalMediaPermission
import com.example.PlayerViewModel
import com.example.PlaylistEntity
import com.example.PlaylistSortOption
import com.example.rememberRestoredLazyListState
import com.example.sharePlaylist
import com.example.TrackSortOption
import com.example.TrackActionsViewModel
import com.example.UiState
import com.example.sortedByLibraryOption
import com.example.totalDurationLabel
import com.example.Track
import com.example.TrackResult
import com.example.ui.component.CollectionRow
import com.example.ui.component.LibrarySortHeader
import com.example.ui.component.ListPlaceholder
import com.example.ui.component.TrackActionsHost
import com.example.ui.component.LocalMediaGate
import com.example.ui.component.PlaylistActionsSheet
import com.example.ui.component.SquarePlaylistCover
import com.example.ui.component.TrackRow
import com.example.ui.component.TrackSelection
import com.example.ui.component.TrackSelectionHost
import com.example.ui.component.rememberTrackSelection

/** Fixed grid-cell column count derived from screen width, standing in for
 * `GridCells.Adaptive` now that track/playlist grids are chunked rows inside the screen's single
 * shared [androidx.compose.foundation.lazy.LazyColumn] rather than their own nested grid. */
@Composable
private fun rememberLibraryGridColumns(): Int {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    return remember(configuration.screenWidthDp) { (configuration.screenWidthDp / 150).coerceAtLeast(2) }
}

/** A soft pastel identity per section so the four top tiles read apart at a glance instead of
 * being identical cards distinguished only by their icon/label. */
private fun libraryChipTint(entry: LibrarySection): androidx.compose.ui.graphics.Color? = when (entry) {
    LibrarySection.Liked -> androidx.compose.ui.graphics.Color(0xFFFFB3D1)        // soft baby pink
    LibrarySection.Downloads -> androidx.compose.ui.graphics.Color(0xFFA8E6B8)    // soft green
    LibrarySection.TopPlayed -> androidx.compose.ui.graphics.Color(0xFFFFA3A3)    // soft red
    LibrarySection.OnDevice -> androidx.compose.ui.graphics.Color(0xFFFFE28A)     // soft yellow
    else -> null
}

/** The search field shared by both the plain Library list and a detail section - each mounts it
 * at a different point in the shared LazyColumn (see the two call sites' own comments for why). */
@Composable
private fun LibrarySearchField(query: String, section: LibrarySection, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search ${section.label.lowercase()}") },
        singleLine = true,
        leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Clear search")
                }
            }
        },
        // statusBarsPadding here, not just on the floating back/search icons - when this field is
        // the item scrolled to the top of the LazyColumn (see toggleSearch/searchFieldIndex), its
        // own top edge lands at the screen's true top with no other item's padding above it to
        // clear the status bar, so it needs its own inset rather than inheriting one.
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("library_search_field"),
    )
}

/** The dynamic blurb under a detail section's Play/Shuffle row - same three-sentence shape for
 * every section, with only the collection-specific clause changing. */
private fun collectionAboutText(section: LibrarySection, tracks: List<Track>): String {
    val count = tracks.size
    val songWord = if (count == 1) "song" else "songs"
    val descriptor = when (section) {
        LibrarySection.Liked -> "a personalized collection featuring $count $songWord"
        LibrarySection.Downloads -> "a personalized collection featuring $count $songWord, ready to play offline"
        LibrarySection.TopPlayed -> "a personalized collection featuring your $count most played $songWord"
        LibrarySection.OnDevice -> "a personalized collection featuring $count $songWord stored on this device"
        else -> "a personalized collection featuring $count $songWord"
    }
    return "${section.label} is $descriptor. Total listening time is ${tracks.totalDurationLabel()}. " +
        "This playlist is automatically curated for your musical enjoyment."
}

/**
 * Library: playlists, liked songs, downloads, most played and recently played - every section
 * backed by a repository that survived the frontend wipe, so this screen works fully offline.
 */
@Composable
fun LibraryScreen(
    onPlayTrack: (TrackResult, List<TrackResult>) -> Unit = { _, _ -> },
    playerViewModel: PlayerViewModel,
    onOpenPlaylist: (Long) -> Unit = {},
    onOpenCollection: (LibrarySection) -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenStats: () -> Unit = {},
    onOpenStorage: () -> Unit = {},
    onImportSharedPlaylist: () -> Unit = {},
    onGoToArtist: (String) -> Unit = {},
    onGoToAlbum: (String) -> Unit = {},
    detailSection: LibrarySection? = null,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel: LibraryViewModel = viewModel()
    val settingsViewModel: AppSettingsViewModel = viewModel()
    val settings by settingsViewModel.state.collectAsState()
    val selectedSection by viewModel.section.collectAsState()
    val section = detailSection ?: selectedSection

    // Playlists is never hidden - with every optional section off, an empty chip row would leave
    // Library with no way back to anything.
    val visibleSections = remember(settings) {
        LibrarySection.entries.filter { entry ->
            when (entry) {
                LibrarySection.Playlists -> true
                LibrarySection.Liked -> settings.showLikedPlaylist
                LibrarySection.Downloads -> settings.showDownloadedPlaylist
                LibrarySection.TopPlayed -> settings.showTopPlaylist
                // Retired as a chip - the header's History shortcut and "View full history" link
                // are the one path to recent plays now, not a duplicate chip section.
                LibrarySection.Recent -> false
                // Always offered: it needs no prior activity to be worth opening, unlike the
                // history-driven sections, and it's the only route to files already on the phone.
                LibrarySection.OnDevice -> true
                // Always offered too - the whole point is that this was previously nowhere to
                // find at all (follow/unfollow only ever existed on the artist sheet), so it
                // needs to be discoverable before the first follow, not hidden until after.
                LibrarySection.Following -> true
            }
        }
    }

    // If the selected section was just hidden, fall back rather than showing a blank body.
    LaunchedEffect(visibleSections, section) {
        if (detailSection == null && section !in visibleSections) viewModel.selectSection(LibrarySection.Playlists)
    }

    // Re-checked on every resume, not just at composition: the gate can send the user to system
    // settings to grant access, and coming back from there has to flip this without a restart.
    val context = LocalContext.current
    val online by remember(context) { com.example.observeOnline(context.applicationContext) }
        .collectAsState(initial = com.example.isOnline(context))
    // Drives the "now playing" equalizer badge on whichever row matches - see TrackRow's own doc.
    val nowPlayingState by playerViewModel.state.collectAsState()
    var hasLocalPermission by remember { mutableStateOf(LocalMediaPermission.isGranted(context)) }
    LifecycleResumeEffect(Unit) {
        hasLocalPermission = LocalMediaPermission.isGranted(context)
        onPauseOrDispose { }
    }

    // Rescanned each time the section is opened, so files added since the last visit appear.
    LaunchedEffect(section, hasLocalPermission) {
        if (section == LibrarySection.OnDevice && hasLocalPermission) viewModel.scanLocalTracks()
    }
    var searchActive by remember { mutableStateOf(false) }
    // Where the list was before search opened, so closing it (the toggle button again, or the
    // system back gesture/button - see BackHandler below) puts the screen back rather than
    // leaving it wherever the search-field scroll landed.
    var preSearchScrollIndex by remember { mutableStateOf(0) }
    var preSearchScrollOffset by remember { mutableStateOf(0) }
    val searchQuery by viewModel.searchQuery.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val liked by viewModel.likedSongs.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val topPlayed by viewModel.topPlayed.collectAsState()
    val recent by viewModel.recentlyPlayed.collectAsState()
    val localTracks by viewModel.localTracks.collectAsState()
    val followedArtists by viewModel.followedArtists.collectAsState()
    val trackSort by viewModel.trackSort.collectAsState()
    val playlistSort by viewModel.playlistSort.collectAsState()
    val ascending by viewModel.ascending.collectAsState()
    val gridView by viewModel.gridView.collectAsState()
    val downloadedKeys = remember(downloads) { downloads.map { it.downloadKey() }.toSet() }
    val likedKeys = remember(liked) { liked.map { it.downloadKey() }.toSet() }

    val play: (Track, List<Track>) -> Unit = { track, queue ->
        val playable = if (online) queue else queue.filter {
            it.sourceType == com.example.MusicSource.LOCAL_DEVICE || it.downloadKey() in downloadedKeys
        }
        if (track !in playable) {
            android.widget.Toast.makeText(context, "This song is not available offline", android.widget.Toast.LENGTH_SHORT).show()
        } else onPlayTrack(track.asTrackResult(), playable.map { it.asTrackResult() })
    }

    val actionsViewModel: TrackActionsViewModel = viewModel()
    val downloadFailures by actionsViewModel.downloadFailures.collectAsState()
    val downloadsInProgress by actionsViewModel.downloadsInProgress.collectAsState()
    val selection = rememberTrackSelection()
    var showCollectionSheet by remember { mutableStateOf(false) }
    var confirmingDeleteAllDownloaded by remember { mutableStateOf(false) }
    // Held as an id, not the entity itself: capturing the entity would freeze the sheet on
    // whatever isPinned was at long-press time, so toggling pin from inside it never visibly
    // flipped until the sheet was closed and reopened. Re-deriving from `playlists` every
    // recomposition keeps it live.
    var selectedPlaylistId by remember { mutableStateOf<Long?>(null) }
    val selectedPlaylist = selectedPlaylistId?.let { id -> playlists.find { it.id == id } }
    var selectedTrack by remember { mutableStateOf<TrackResult?>(null) }
    // Long-press enters multi-select directly; the row's ⋮ button is what opens the sheet now, so
    // this only needs the track, not a position to hand a "Select" action.
    val openMenu: (Track) -> Unit = { track -> selectedTrack = track.asTrackResult() }
    val coverPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = selectedPlaylistId
        if (uri == null || id == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        viewModel.setCustomCover(id, uri.toString())
    }

    // The list every visible row and every selected position refers to. Hoisted out of the body's
    // `when` so the selection bar and the rows cannot disagree about what index 3 means.
    val sectionTracks: List<Track> = when (section) {
        LibrarySection.Playlists -> emptyList()
        LibrarySection.Liked -> viewModel.sortTracks(liked)
        LibrarySection.Downloads -> viewModel.sortTracks(downloads)
        LibrarySection.TopPlayed -> viewModel.sortTracks(topPlayed)
        LibrarySection.Recent -> viewModel.sortTracks(recent)
        LibrarySection.OnDevice ->
            (localTracks as? UiState.Success)?.data?.let(viewModel::sortTracks).orEmpty()
        LibrarySection.Following -> emptyList()
    }
    // The queue a tap actually starts, as opposed to sectionTracks' own search-filtered display -
    // see sortTracksIgnoringSearch's own doc for why these need to diverge while searching.
    val queueSectionTracks: List<Track> = when (section) {
        LibrarySection.Playlists -> emptyList()
        LibrarySection.Liked -> viewModel.sortTracksIgnoringSearch(liked)
        LibrarySection.Downloads -> viewModel.sortTracksIgnoringSearch(downloads)
        LibrarySection.TopPlayed -> viewModel.sortTracksIgnoringSearch(topPlayed)
        LibrarySection.Recent -> viewModel.sortTracksIgnoringSearch(recent)
        LibrarySection.OnDevice ->
            (localTracks as? UiState.Success)?.data?.let(viewModel::sortTracksIgnoringSearch).orEmpty()
        LibrarySection.Following -> emptyList()
    }
    // The cover mosaic's own source - the repository's raw order, not sectionTracks' sorted/
    // filtered view. Otherwise switching "Date added" to "Name" or flipping ascending/descending
    // would reshuffle which thumbnails the mosaic shows, which read as the cover randomly
    // changing for no reason a user did anything to the collection itself.
    val coverTracks: List<Track> = when (section) {
        LibrarySection.Liked -> liked
        LibrarySection.Downloads -> downloads
        LibrarySection.TopPlayed -> topPlayed
        LibrarySection.Recent -> recent
        LibrarySection.OnDevice -> (localTracks as? UiState.Success)?.data.orEmpty()
        LibrarySection.Playlists, LibrarySection.Following -> emptyList()
    }
    val sectionResults = remember(sectionTracks) { sectionTracks.map { it.asTrackResult() } }
    // sectionTracks (Liked/Downloads/Top 50/Recent/On device) already runs through
    // viewModel.sortTracks(), which folds the search query in - Playlists and Following aren't
    // Track lists, so they need their own filtered view here instead.
    val filteredPlaylists = remember(playlists, searchQuery) { viewModel.filterPlaylists(playlists) }
    val filteredFollowedArtists = remember(followedArtists, searchQuery) { viewModel.filterArtists(followedArtists) }
    // "Nothing liked yet"-style messages are wrong once a search is active and just came up
    // empty - swap in a search-specific one so an empty result doesn't read as "you have nothing
    // here at all".
    fun emptyMessageFor(whenNoSearch: String): String =
        if (searchQuery.isBlank()) whenNoSearch else "No results for \"$searchQuery\"."

    // Anything that renumbers the list invalidates positional selection, so switching section or
    // re-sorting drops it rather than silently retargeting the ticks onto other songs.
    LaunchedEffect(section, trackSort, ascending) { selection.clear() }
    // A sheet/dialog left open from one section shouldn't reappear over a different one navigated
    // to next (e.g. Liked's delete-confirm surviving into Downloads).
    LaunchedEffect(section) { showCollectionSheet = false; confirmingDeleteAllDownloaded = false }

    val gridColumns = rememberLibraryGridColumns()
    val awaitingPermission = section == LibrarySection.OnDevice && !hasLocalPermission

    // Shared by the Shuffle/Play pills and the "..." sheet's Add to queue action - computed once
    // here rather than separately in each, so they can't disagree about what's actually playable
    // offline.
    val playable = remember(sectionTracks, online, downloadedKeys) {
        if (online) sectionTracks else sectionTracks.filter {
            it.sourceType == com.example.MusicSource.LOCAL_DEVICE || it.downloadKey() in downloadedKeys
        }
    }
    // Local-device tracks were never "downloaded" in the first place, so On Device's own list
    // (all local by definition) never counts toward this - only meaningful for Liked/Top 50, the
    // two sections the "..." sheet actually shows a download row for.
    val hasDownloadableTracks = sectionTracks.any { it.sourceType != com.example.MusicSource.LOCAL_DEVICE }
    val allDownloaded = sectionTracks.isNotEmpty() && sectionTracks.all {
        it.sourceType == com.example.MusicSource.LOCAL_DEVICE || it.downloadKey() in downloadedKeys
    }

    // Restores wherever the user last scrolled to in this exact section - tapping a track to open
    // Now Playing and pressing back used to always land back at the top instead. See
    // rememberRestoredLazyListState's own doc for the two real gotchas this needs to dodge.
    val listReady = when (section) {
        LibrarySection.Playlists -> filteredPlaylists.isNotEmpty()
        LibrarySection.Following -> filteredFollowedArtists.isNotEmpty()
        else -> sectionTracks.isNotEmpty()
    }
    val listState = rememberRestoredLazyListState(key = "library:${section.name}", isContentReady = listReady)
    // Same fade-with-scroll idea [ArtistScreen] uses for its own floating back/menu buttons: 1f
    // while still within the cover (item 0), fading to 0f as it scrolls past, back to 0f outright
    // once anything below the cover reaches the top. Only meaningful (and only rendered) for a
    // detail section, whose item 0 is [NineGridCover] below - the plain Library list has no
    // floating header to fade.
    val topBarVisibility by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                0f
            } else {
                val coverHeight = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }?.size ?: 1
                (1f - listState.firstVisibleItemScrollOffset.toFloat() / coverHeight).coerceIn(0f, 1f)
            }
        }
    }

    // Opening search used to just insert the field inline and leave the scroll position alone,
    // so on a long list it could land off-screen below whatever was currently in view. Toggling
    // now captures where the list was first, and a LaunchedEffect below animates to/from the
    // field's own item once it actually exists in the LazyColumn (has to wait for that
    // recomposition - scrolling in the same click handler that flips searchActive would race the
    // field's item not being composed yet).
    fun toggleSearch() {
        if (!searchActive) {
            preSearchScrollIndex = listState.firstVisibleItemIndex
            preSearchScrollOffset = listState.firstVisibleItemScrollOffset
        }
        searchActive = !searchActive
        if (!searchActive) viewModel.setSearchQuery("")
    }
    // System back closes search and restores the scroll position, rather than leaving the search
    // screen (this only intercepts back while search is active; a normal back press otherwise
    // still exits the section as usual).
    androidx.activity.compose.BackHandler(enabled = searchActive) { toggleSearch() }

    // Fixed indices, not a key-based scroll (LazyColumn has no first-class "scroll to key" API):
    // the search field is always the item right after the header for the plain list, or right
    // after cover/heading/actions/stats/about (5 items) for a detail section - see where each is
    // actually emitted below.
    val searchFieldIndex = if (detailSection == null) 1 else 5
    // Skips its very first run (the mount right after this composable enters composition, e.g.
    // returning from Now Playing) - LaunchedEffect always runs once on mount regardless of whether
    // searchActive "actually changed" from some prior value, and searchActive/preSearchScrollIndex/
    // preSearchScrollOffset are all plain remember state that resets to false/0/0 on every fresh
    // mount. Unconditionally acting on that first run meant this unconditionally animated the list
    // back to (0, 0) on every single return from Now Playing, immediately undoing whatever
    // rememberRestoredLazyListState had just restored a moment earlier - the real cause of the
    // scroll-position bug surviving two earlier attempts at rememberRestoredLazyListState itself.
    var hasHandledInitialSearchState by remember { mutableStateOf(false) }
    LaunchedEffect(searchActive) {
        if (!hasHandledInitialSearchState) {
            hasHandledInitialSearchState = true
            return@LaunchedEffect
        }
        if (searchActive) {
            listState.animateScrollToItem(searchFieldIndex)
        } else {
            listState.animateScrollToItem(preSearchScrollIndex, preSearchScrollOffset)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
    // A single LazyColumn for the whole screen - header, chips/hero, sort row, and the section's
    // own rows all scroll together. This used to be a static Column wrapping an inner
    // LazyColumn/LazyVerticalGrid for just the list, which meant only that inner list scrolled and
    // everything above it (title, chips, hero) stayed pinned - annoying to fight past to see more
    // of a long list.
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            // No statusBarsPadding here for a detail section - NineGridCover (item 0) is meant to
            // draw full-bleed under the status bar, the same "cover is the top of the screen"
            // treatment ArtistScreen's own cover uses; the floating back/search pair below carries
            // its own statusBarsPadding instead. The plain Library list's header applies its own.
            .testTag("library_screen")
            // Blurred, not just dimmed, behind the delete-all confirmation - see
            // BlurredConfirmDialog's own doc for why that dialog isn't a normal AlertDialog.
            .let { if (confirmingDeleteAllDownloaded) it.blur(20.dp) else it },
        contentPadding = PaddingValues(bottom = 200.dp),
    ) {
        // Plain static header - only for the main Library list. A detail section uses the
        // floating, scroll-fading back/search pair below instead (see topBarVisibility), not this.
        if (detailSection == null) item {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(start = 16.dp, end = 8.dp, top = 24.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Your library",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                // Stats' one entry point - Library has no dedicated tab for it, and it isn't tied to
                // any one section (it summarizes across all of them), so it lives in the header rather
                // than as an eighth chip.
                IconButton(
                    onClick = ::toggleSearch,
                    modifier = Modifier.testTag("library_search_toggle"),
                ) {
                    Icon(
                        imageVector = if (searchActive) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = if (searchActive) "Close search" else "Search this section",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (settings.showRecentlyPlayedShortcut) {
                    // Opens the real History screen directly - the same destination "View full
                    // history" inside the old Recent chip pointed to - rather than a second,
                    // separate recently-played surface with its own copy of the same data.
                    IconButton(
                        onClick = onOpenHistory,
                        modifier = Modifier.testTag("library_open_recently_played"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Recently played",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onOpenStats, modifier = Modifier.testTag("library_open_stats")) {
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = "Listening stats",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Only here for the main list - a detail section renders this same field further down
        // (after the cover/heading/actions), so the cover stays item 0 for topBarVisibility's
        // scroll calc regardless of whether search is active.
        if (searchActive && detailSection == null) {
            item { LibrarySearchField(searchQuery, section, viewModel::setSearchQuery) }
        }

        if (detailSection == null) {
            item {
                Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    visibleSections.filter { it in listOf(LibrarySection.Liked, LibrarySection.Downloads, LibrarySection.TopPlayed, LibrarySection.OnDevice) }
                        .chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { entry ->
                                    val count = when (entry) {
                                        LibrarySection.Liked -> liked.size
                                        LibrarySection.Downloads -> downloads.size
                                        LibrarySection.TopPlayed -> topPlayed.size
                                        LibrarySection.OnDevice -> (localTracks as? UiState.Success)?.data?.size ?: 0
                                        else -> 0
                                    }
                                    Column(Modifier.weight(1f).height(104.dp).liquidSurface(tint = libraryChipTint(entry))
                                        .clickable { onOpenCollection(entry) }
                                        .testTag("library_chip_${entry.name.lowercase()}")
                                        .padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                        Icon(when (entry) {
                                            LibrarySection.Liked -> Icons.Default.Favorite
                                            LibrarySection.Downloads -> Icons.Default.Download
                                            LibrarySection.TopPlayed -> Icons.Default.BarChart
                                            else -> Icons.Default.MusicNote
                                        }, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Text(entry.label, style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface)
                                        Text(if (entry == LibrarySection.OnDevice) "Browse files" else "$count songs",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        visibleSections.filter { it == LibrarySection.Playlists || it == LibrarySection.Following }.forEach { entry ->
                            FilterChip(selected = section == entry, onClick = { viewModel.selectSection(entry) },
                                label = { Text(entry.label) }, shape = RoundedCornerShape(50),
                                modifier = Modifier.testTag("library_chip_${entry.name.lowercase()}"))
                        }
                    }
                }
            }
        }

        if (detailSection != null) {
            // Item 0: the big mosaic hero - topBarVisibility (see above) reads this item's own
            // measured size to fade the floating back/search pair as it scrolls past. Sourced from
            // coverTracks (the section's raw order), not sectionTracks - see coverTracks' own doc.
            item(key = "cover") {
                com.example.ui.component.NineGridCover(
                    tracks = coverTracks,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                )
            }
            item(key = "heading") {
                Text(
                    text = section.label,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            item(key = "collection_actions") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = {
                            val shuffled = playable.shuffled()
                            shuffled.firstOrNull()?.let { play(it, shuffled) }
                        },
                        enabled = playable.isNotEmpty(),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.weight(1f).testTag("library_collection_shuffle"),
                    ) {
                        Icon(Icons.Default.Shuffle, null)
                        Text("Shuffle", modifier = Modifier.padding(start = 8.dp))
                    }
                    Button(
                        onClick = { playable.firstOrNull()?.let { play(it, playable) } },
                        enabled = playable.isNotEmpty(),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.weight(1f).testTag("library_collection_play"),
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Text("Play", modifier = Modifier.padding(start = 8.dp))
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .clickable { showCollectionSheet = true }
                            .testTag("library_collection_menu"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
            item(key = "stats_line") {
                val count = sectionTracks.size
                Text(
                    text = "$count song${if (count == 1) "" else "s"} • ${sectionTracks.totalDurationLabel()}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            item(key = "about") {
                Text(
                    text = collectionAboutText(section, sectionTracks),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
            // Search field's other home - see the detailSection == null one above for why this
            // has to come after the cover rather than at the very top for a detail section.
            if (searchActive) {
                item { LibrarySearchField(searchQuery, section, viewModel::setSearchQuery) }
            }
        }

        if (section == LibrarySection.Downloads && downloadFailures.isNotEmpty()) {
            item {
                Text("${downloadFailures.size} download(s) failed. ${downloadFailures.values.last()}",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            }
        }

        // One sort header drives whichever section is showing; playlists sort by their own enum
        // since "sort by artist" is meaningless for a playlist.
        item {
            when {
                // The selection bar takes the sort header's place rather than sitting beside it: the
                // controls it covers are exactly the ones that would renumber the list under a live
                // selection.
                selection.active -> TrackSelectionHost(
                    selection = selection,
                    tracks = sectionResults,
                    playerViewModel = playerViewModel,
                    actionsViewModel = actionsViewModel,
                )

                // Nothing to sort behind a permission prompt, and "0 songs" over one reads like a
                // result the user should act on rather than a question they haven't answered yet.
                awaitingPermission -> Unit

                // A follow list is short and chronological by nature - nothing worth sorting.
                section == LibrarySection.Following -> Unit

                section == LibrarySection.Playlists -> LibrarySortHeader(
                    options = PlaylistSortOption.entries.toList(),
                    selected = playlistSort,
                    onSelect = viewModel::setPlaylistSort,
                    ascending = ascending,
                    onToggleDirection = viewModel::toggleDirection,
                    optionLabel = { it.label },
                    countLabel = "${filteredPlaylists.size} playlist${if (filteredPlaylists.size == 1) "" else "s"}",
                )

                else -> {
                    val count = sectionTracks.size
                    LibrarySortHeader(
                        options = TrackSortOption.entries.toList(),
                        selected = trackSort,
                        onSelect = viewModel::setTrackSort,
                        ascending = ascending,
                        onToggleDirection = viewModel::toggleDirection,
                        optionLabel = { it.label },
                        countLabel = "$count song${if (count == 1) "" else "s"}",
                        isGrid = gridView,
                        onToggleGrid = viewModel::toggleGridView,
                    )
                }
            }
        }

        if (section == LibrarySection.Playlists) {
            item(key = "import_shared_playlist") {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onImportSharedPlaylist)
                        .padding(horizontal = 24.dp, vertical = 10.dp)
                        .testTag("import_shared_playlist_row"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "Import shared playlist",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 14.dp),
                    )
                }
            }
        }

        when (section) {
            LibrarySection.Playlists -> playlistItems(
                playlists = filteredPlaylists.sortedByLibraryOption(playlistSort, ascending),
                viewModel = viewModel,
                downloadedKeys = downloadedKeys,
                onOpenPlaylist = onOpenPlaylist,
                onLongPress = { selectedPlaylistId = it.id },
                emptyMessage = emptyMessageFor("No playlists yet. Long-press any song and choose \"Add to playlist\"."),
            )
            LibrarySection.Liked -> trackItems(sectionTracks, emptyMessageFor("Nothing liked yet."), gridView, gridColumns, play, openMenu, selection, likedKeys, downloadedKeys, downloadsInProgress, queueTracks = queueSectionTracks, nowPlayingKey = nowPlayingState.currentTrackKey)
            LibrarySection.Downloads -> trackItems(sectionTracks, emptyMessageFor("No downloads yet."), gridView, gridColumns, play, openMenu, selection, likedKeys, downloadedKeys, downloadsInProgress, queueTracks = queueSectionTracks, nowPlayingKey = nowPlayingState.currentTrackKey)
            LibrarySection.TopPlayed -> trackItems(sectionTracks, emptyMessageFor("Play something and it'll show up here."), gridView, gridColumns, play, openMenu, selection, likedKeys, downloadedKeys, downloadsInProgress, queueTracks = queueSectionTracks, nowPlayingKey = nowPlayingState.currentTrackKey)
            // The capped, sortable slice for getting back to something quickly - the full record,
            // with its own editing, lives on the History screen this links to.
            LibrarySection.Recent -> {
                item {
                    TextButton(
                        onClick = onOpenHistory,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .testTag("library_open_history"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(text = "View full history", modifier = Modifier.padding(start = 8.dp))
                    }
                }
                trackItems(sectionTracks, emptyMessageFor("Nothing played yet."), gridView, gridColumns, play, openMenu, selection, likedKeys, downloadedKeys, downloadsInProgress, queueTracks = queueSectionTracks, nowPlayingKey = nowPlayingState.currentTrackKey)
            }
            LibrarySection.OnDevice -> when {
                !hasLocalPermission -> item {
                    LocalMediaGate(onGranted = { hasLocalPermission = true })
                }

                localTracks is UiState.Loading -> item { ListPlaceholder() }

                localTracks is UiState.Error -> item { EmptyState((localTracks as UiState.Error).message) }

                else -> trackItems(
                    sectionTracks,
                    emptyMessageFor("No music files found on this device."),
                    gridView,
                    gridColumns,
                    play,
                    openMenu,
                    selection,
                    likedKeys,
                    downloadedKeys,
                    downloadsInProgress,
                    showLocalDeviceBadge = false,
                    queueTracks = queueSectionTracks,
                    nowPlayingKey = nowPlayingState.currentTrackKey,
                )
            }
            LibrarySection.Following -> followedArtistItems(
                filteredFollowedArtists,
                onGoToArtist,
                emptyMessage = emptyMessageFor("Not following anyone yet. Follow an artist from their page or a search result."),
            )
        }
    }

        // Floating back/search, fading with scroll exactly like ArtistScreen's own back/menu pair
        // (see topBarVisibility above) - only for a detail section, whose big cover up top this
        // floats over; the plain Library list keeps its normal static header instead.
        if (detailSection != null && topBarVisibility > 0.01f) {
            Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp)) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = topBarVisibility
                            translationY = (1f - topBarVisibility) * -80f
                        }
                        .background(Color.Black.copy(alpha = 0.35f * topBarVisibility), CircleShape)
                        .testTag("library_collection_back_floating"),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = ::toggleSearch,
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = topBarVisibility
                            translationY = (1f - topBarVisibility) * -80f
                        }
                        .background(Color.Black.copy(alpha = 0.35f * topBarVisibility), CircleShape)
                        .testTag("library_collection_search_floating"),
                ) {
                    Icon(
                        imageVector = if (searchActive) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = if (searchActive) "Close search" else "Search this section",
                        tint = Color.White,
                    )
                }
            }
        }

        if (showCollectionSheet && detailSection != null) {
            com.example.ui.component.LibraryCollectionActionsSheet(
                section = section,
                allDownloaded = allDownloaded,
                hasDownloadableTracks = hasDownloadableTracks,
                onDownloadAll = {
                    actionsViewModel.downloadAll(sectionTracks.filter { it.sourceType != com.example.MusicSource.LOCAL_DEVICE })
                    showCollectionSheet = false
                },
                onRequestDeleteAllDownloaded = {
                    showCollectionSheet = false
                    confirmingDeleteAllDownloaded = true
                },
                onAddToQueue = {
                    playerViewModel.addToQueue(playable.map { it.asTrackResult() })
                    showCollectionSheet = false
                },
                onDismiss = { showCollectionSheet = false },
                onManageDownloads = if (section == LibrarySection.Downloads) {
                    { showCollectionSheet = false; onOpenStorage() }
                } else null,
                onOpenStats = if (section == LibrarySection.TopPlayed) {
                    { showCollectionSheet = false; onOpenStats() }
                } else null,
                onRescanDevice = if (section == LibrarySection.OnDevice) {
                    { showCollectionSheet = false; viewModel.scanLocalTracks() }
                } else null,
            )
        }

        if (confirmingDeleteAllDownloaded) {
            val label = when (section) {
                LibrarySection.Downloads -> "all downloads"
                else -> "all downloaded ${section.label.lowercase()} songs"
            }
            com.example.ui.component.BlurredConfirmDialog(
                title = "Delete $label?",
                text = "The downloaded audio files are removed from this device. " +
                    if (section == LibrarySection.Downloads) {
                        "Your liked songs and playlists aren't affected - you can re-download any track later."
                    } else {
                        "$label stay in your library and can be re-downloaded any time."
                    },
                confirmLabel = "Delete",
                onConfirm = {
                    if (section == LibrarySection.Downloads) {
                        actionsViewModel.deleteDownloads(downloads)
                    } else {
                        actionsViewModel.deleteDownloads(sectionTracks.filter { it.sourceType != com.example.MusicSource.LOCAL_DEVICE })
                    }
                    confirmingDeleteAllDownloaded = false
                },
                onDismiss = { confirmingDeleteAllDownloaded = false },
            )
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


    selectedPlaylist?.let { playlist ->
        // Subscribes this playlist's track flow so it's populated even if its detail screen has
        // never been opened this session - `tracksForPlaylist` only starts emitting once collected.
        val playlistTracks by viewModel.tracksForPlaylist(playlist.id).collectAsState()
        val playableTracks = remember(playlistTracks, downloadedKeys, online) {
            if (online) playlistTracks else playlistTracks.filter {
                it.sourceType == com.example.MusicSource.LOCAL_DEVICE || it.downloadKey() in downloadedKeys
            }
        }
        val playlistResults = remember(playableTracks) { playableTracks.map { it.asTrackResult() } }

        PlaylistActionsSheet(
            name = playlist.name,
            songCount = playlistTracks.size,
            coverImageUrl = playlist.coverImageUrl,
            isPinned = playlist.isPinned,
            onTogglePin = { viewModel.togglePin(playlist) },
            onShuffle = {
                val shuffled = playlistResults.shuffled()
                shuffled.firstOrNull()?.let { onPlayTrack(it, shuffled) }
            },
            onStartRadio = {
                if (!online) {
                    android.widget.Toast.makeText(context, "Radio needs an internet connection", android.widget.Toast.LENGTH_SHORT).show()
                } else playlistResults.firstOrNull()?.let { seed ->
                    playerViewModel.startRadio(seed) {
                        android.widget.Toast.makeText(
                            context,
                            "Couldn't start radio",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            },
            onPlayNext = { playerViewModel.playNext(playlistResults) },
            onAddToQueue = { playerViewModel.addToQueue(playlistResults) },
            onDownload = { actionsViewModel.downloadAll(playlistTracks) },
            onShare = { context.sharePlaylist(playlist.name, playlistTracks, playlist.remoteId) },
            onDelete = { viewModel.deletePlaylist(playlist) },
            onDismiss = { selectedPlaylistId = null },
            onChangeCover = { coverPickerLauncher.launch(arrayOf("image/*")) },
            hasCustomCover = playlist.customCoverUri != null,
            onRemoveCustomCover = { viewModel.setCustomCover(playlist.id, null) },
        )
    }
}

/** How much of a playlist's tracks are downloaded/on-device - drives the badge on
 * [PlaylistGridTile], the only way [LibrarySection.Playlists] told full and partial downloads
 * apart before (not at all: a downloaded and a purely-online playlist looked identical). */
private enum class PlaylistDownloadState { None, Partial, Full }

private fun playlistDownloadState(tracks: List<Track>, downloadedKeys: Set<String>): PlaylistDownloadState {
    if (tracks.isEmpty()) return PlaylistDownloadState.None
    val downloadedCount = tracks.count { it.sourceType == com.example.MusicSource.LOCAL_DEVICE || it.downloadKey() in downloadedKeys }
    return when {
        downloadedCount == 0 -> PlaylistDownloadState.None
        downloadedCount == tracks.size -> PlaylistDownloadState.Full
        else -> PlaylistDownloadState.Partial
    }
}

private fun LazyListScope.trackItems(
    tracks: List<Track>,
    emptyMessage: String,
    gridView: Boolean,
    columns: Int,
    onPlay: (Track, List<Track>) -> Unit,
    /** Opens the row's actions sheet - reached via the row's own `⋮` now, not long-press. */
    onOpenMenu: (Track) -> Unit,
    selection: TrackSelection,
    /** Drives each row's heart/download glyphs - reactive per song rather than a static row, since
     * both can change while this same list is on screen (liking/unliking, a download completing). */
    likedKeys: Set<String> = emptySet(),
    downloadedKeys: Set<String> = emptySet(),
    downloadsInProgress: Map<String, Int> = emptyMap(),
    /** False for the On Device section itself, where every single row is trivially on-device -
     * the glyph would just be noise repeated on every row instead of the distinguishing signal it
     * is in Liked/Downloads/Top 50/a playlist, where only *some* tracks are local files. */
    showLocalDeviceBadge: Boolean = true,
    /** What a tap actually queues - defaults to [tracks] itself, but a caller mid-search passes
     * the section's real (unfiltered) tracklist here instead, so playing a search hit still queues
     * the whole section starting there rather than just the handful of on-screen matches. See
     * [com.example.LibraryViewModel.sortTracksIgnoringSearch]'s own doc. */
    queueTracks: List<Track> = tracks,
    /** The player's current track key, for the "now playing" badge - see TrackRow's own doc. */
    nowPlayingKey: String? = null,
) {
    if (tracks.isEmpty()) {
        item { Box(Modifier.fillParentMaxSize()) { EmptyState(emptyMessage) } }
        return
    }

    if (gridView) {
        // Fixed column count computed from screen width (see rememberLibraryGridColumns) rather
        // than LazyVerticalGrid's own GridCells.Adaptive - these rows now live directly in the
        // screen's single LazyColumn (see LibraryScreen's doc for why), so columns are chunked by
        // hand instead of delegated to a nested grid.
        val rows = tracks.withIndex().toList().chunked(columns)
        items(rows, key = { row -> row.joinToString("_") { it.index.toString() } }) { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).animateItem(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { (index, track) ->
                    TrackGridCell(
                        track = track,
                        // Once anything is ticked a plain tap toggles instead of playing: with a
                        // selection on screen, tapping a row to start a song would look like a misfire.
                        onClick = {
                            if (selection.active) selection.toggle(index) else onPlay(track, queueTracks)
                        },
                        // Enters selection directly - a grid cell has no room for its own menu button,
                        // so unlike the list this is the only way into multi-select from the grid.
                        onLongClick = {
                            if (selection.active) selection.toggle(index) else selection.start(index)
                        },
                        selected = selection.isSelected(index),
                        isPlaying = track.downloadKey() == nowPlayingKey,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        return
    }

    itemsIndexed(tracks, key = { index, _ -> index }) { index, track ->
        val key = track.downloadKey()
        TrackRow(
            title = track.title,
            artist = track.artist,
            imageUrl = track.imageUrl,
            duration = track.duration,
            onClick = {
                if (selection.active) selection.toggle(index) else onPlay(track, queueTracks)
            },
            onLongClick = {
                if (selection.active) selection.toggle(index) else selection.start(index)
            },
            selected = selection.isSelected(index),
            isLiked = key in likedKeys,
            isDownloaded = key in downloadedKeys,
            isLocalDevice = showLocalDeviceBadge && track.sourceType == com.example.MusicSource.LOCAL_DEVICE,
            downloadProgress = downloadsInProgress[key],
            isPlaying = key == nowPlayingKey,
            // No menu button while selecting - the selection bar is the row's controls then.
            onOpenMenu = if (selection.active) null else { { onOpenMenu(track) } },
            modifier = Modifier.animateItem(),
        )
    }
}

/** Square-artwork cell used by the grid view. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackGridCell(
    track: Track,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    selected: Boolean = false,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .testTag("track_cell_${track.title.lowercase().replace(" ", "_")}"),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(36.dp),
                    )
                }
            } else if (track.imageUrl != null) {
                AsyncImage(
                    model = track.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Same overlay treatment as TrackRow's own list-view artwork - see its doc.
            if (isPlaying && !selected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    com.example.ui.component.NowPlayingIndicator()
                }
            }
        }
        Text(
            text = track.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A 2-column grid of big playlist tiles, each showing a mosaic of its own tracks' artwork - the
 * same "cover generated from the tracks themselves" idea [PlaylistDetailScreen] uses for a single
 * playlist's own cover, now applied per-tile here so browsing Library's playlist list looks like a
 * wall of covers instead of a list of icons.
 */
private fun LazyListScope.playlistItems(
    playlists: List<PlaylistEntity>,
    viewModel: LibraryViewModel,
    downloadedKeys: Set<String>,
    onOpenPlaylist: (Long) -> Unit,
    onLongPress: (PlaylistEntity) -> Unit,
    emptyMessage: String = "No playlists yet. Long-press any song and choose \"Add to playlist\".",
) {
    if (playlists.isEmpty()) {
        item { Box(Modifier.fillParentMaxSize()) { EmptyState(emptyMessage) } }
        return
    }
    items(playlists.chunked(2), key = { row -> row.joinToString("_") { it.id.toString() } }) { row ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).animateItem(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            row.forEach { playlist ->
                PlaylistGridTile(
                    playlist = playlist,
                    viewModel = viewModel,
                    downloadedKeys = downloadedKeys,
                    onOpenPlaylist = onOpenPlaylist,
                    onLongPress = onLongPress,
                    modifier = Modifier.weight(1f),
                )
            }
            // An odd final row: a spacer holds the empty slot's width so the lone tile stays
            // sized like its siblings instead of stretching to fill the row.
            if (row.size == 1) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistGridTile(
    playlist: PlaylistEntity,
    viewModel: LibraryViewModel,
    downloadedKeys: Set<String>,
    onOpenPlaylist: (Long) -> Unit,
    onLongPress: (PlaylistEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tracks by viewModel.tracksForPlaylist(playlist.id).collectAsState()
    val downloadState = remember(tracks, downloadedKeys) { playlistDownloadState(tracks, downloadedKeys) }
    Column(
        modifier = modifier
            .liquidSurface(24.dp)
            .combinedClickable(
                onClick = { onOpenPlaylist(playlist.id) },
                onLongClick = { onLongPress(playlist) },
            )
            .padding(8.dp)
            .testTag("playlist_row_${playlist.name.lowercase().replace(" ", "_")}"),
    ) {
        Box {
            SquarePlaylistCover(tracks = tracks, fallbackCoverUrl = playlist.coverImageUrl, customCoverUri = playlist.customCoverUri, shape = RoundedCornerShape(14.dp))
            // Previously a downloaded and a purely-online playlist looked identical here - this
            // badge is the only signal on the whole Playlists grid for "is this actually available
            // offline". Full download gets a solid check (common "ready offline" language, e.g.
            // Spotify's own green download tick); partial gets an outlined download glyph plus a
            // fraction, since "some of this is offline" needs the count to be useful rather than a
            // binary badge that would look identical to a single-song playlist.
            if (downloadState != PlaylistDownloadState.None) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (downloadState == PlaylistDownloadState.Full) Icons.Default.Check else Icons.Default.Download,
                        contentDescription = if (downloadState == PlaylistDownloadState.Full) "Fully downloaded" else "Partially downloaded",
                        tint = if (downloadState == PlaylistDownloadState.Full) Color(0xFF8BE28B) else Color.White,
                        modifier = Modifier.size(12.dp),
                    )
                    if (downloadState == PlaylistDownloadState.Partial) {
                        Text(
                            text = "${tracks.count { it.sourceType == com.example.MusicSource.LOCAL_DEVICE || it.downloadKey() in downloadedKeys }}/${tracks.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(start = 3.dp),
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // Explains why a pinned playlist sits above ones created after it - otherwise the
            // reordering next to "Date created" would look like a bug.
            if (playlist.isPinned) {
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = "Pinned",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp).padding(start = 4.dp),
                )
            }
        }
        Text(
            text = "${tracks.size} song${if (tracks.size == 1) "" else "s"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The list this whole session's "no way to see who you follow" gap was blocking on - reuses
 * [CollectionRow], the same row Search's album/artist/playlist results already render with, so a
 * followed artist looks exactly like it would if you'd just found them again in Search. */
private fun LazyListScope.followedArtistItems(
    artists: List<FollowedArtistEntity>,
    onOpenArtist: (String) -> Unit,
    emptyMessage: String = "Not following anyone yet. Follow an artist from their page or a search result.",
) {
    if (artists.isEmpty()) {
        item { Box(Modifier.fillParentMaxSize()) { EmptyState(emptyMessage) } }
        return
    }
    items(artists, key = { it.artistId }) { artist ->
        CollectionRow(
            title = artist.name,
            subtitle = "Artist",
            imageUrl = artist.imageUrl,
            kind = CollectionKind.Artist,
            onClick = { onOpenArtist(artist.artistId) },
            modifier = Modifier.animateItem(),
        )
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
