package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.TrackSortOption
import com.example.TrackActionsViewModel
import com.example.UiState
import com.example.sortedByLibraryOption
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

/**
 * Library: playlists, liked songs, downloads, most played and recently played - every section
 * backed by a repository that survived the frontend wipe, so this screen works fully offline.
 */
@Composable
fun LibraryScreen(
    onPlayTrack: (TrackResult, List<TrackResult>) -> Unit = { _, _ -> },
    playerViewModel: PlayerViewModel,
    onOpenPlaylist: (Long) -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenStats: () -> Unit = {},
    onGoToArtist: (String) -> Unit = {},
    onGoToAlbum: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel: LibraryViewModel = viewModel()
    val settingsViewModel: AppSettingsViewModel = viewModel()
    val settings by settingsViewModel.state.collectAsState()
    val section by viewModel.section.collectAsState()

    // Playlists is never hidden - with every optional section off, an empty chip row would leave
    // Library with no way back to anything.
    val visibleSections = remember(settings) {
        LibrarySection.entries.filter { entry ->
            when (entry) {
                LibrarySection.Playlists -> true
                LibrarySection.Liked -> settings.showLikedPlaylist
                LibrarySection.Downloads -> settings.showDownloadedPlaylist
                LibrarySection.TopPlayed -> settings.showTopPlaylist
                LibrarySection.Recent -> settings.showCachedPlaylist
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
        if (section !in visibleSections) viewModel.selectSection(LibrarySection.Playlists)
    }

    // Re-checked on every resume, not just at composition: the gate can send the user to system
    // settings to grant access, and coming back from there has to flip this without a restart.
    val context = LocalContext.current
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

    val play: (Track, List<Track>) -> Unit = { track, queue ->
        onPlayTrack(track.asTrackResult(), queue.map { it.asTrackResult() })
    }

    val actionsViewModel: TrackActionsViewModel = viewModel()
    val selection = rememberTrackSelection()
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

    Column(modifier = modifier.fillMaxSize().statusBarsPadding().testTag("library_screen")) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 24.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Library",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            // Stats' one entry point - Library has no dedicated tab for it, and it isn't tied to
            // any one section (it summarizes across all of them), so it lives in the header rather
            // than as an eighth chip.
            IconButton(
                onClick = {
                    searchActive = !searchActive
                    if (!searchActive) viewModel.setSearchQuery("")
                },
                modifier = Modifier.testTag("library_search_toggle"),
            ) {
                Icon(
                    imageVector = if (searchActive) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = if (searchActive) "Close search" else "Search this section",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onOpenStats, modifier = Modifier.testTag("library_open_stats")) {
                Icon(
                    imageVector = Icons.Default.BarChart,
                    contentDescription = "Listening stats",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (searchActive) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::setSearchQuery,
                placeholder = { Text("Search ${section.label.lowercase()}") },
                singleLine = true,
                leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp)
                    .testTag("library_search_field"),
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            items(visibleSections, key = { it.name }) { entry ->
                FilterChip(
                    selected = section == entry,
                    onClick = { viewModel.selectSection(entry) },
                    label = { Text(entry.label) },
                    modifier = Modifier.testTag("library_chip_${entry.name.lowercase()}"),
                )
            }
        }

        val awaitingPermission = section == LibrarySection.OnDevice && !hasLocalPermission

        // One sort header drives whichever section is showing; playlists sort by their own enum
        // since "sort by artist" is meaningless for a playlist.
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

        when (section) {
            LibrarySection.Playlists -> PlaylistList(
                filteredPlaylists.sortedByLibraryOption(playlistSort, ascending),
                viewModel,
                onOpenPlaylist,
                onLongPress = { selectedPlaylistId = it.id },
                emptyMessage = emptyMessageFor("No playlists yet. Long-press any song and choose \"Add to playlist\"."),
            )
            LibrarySection.Liked -> TrackList(sectionTracks, emptyMessageFor("Nothing liked yet."), gridView, play, openMenu, selection)
            LibrarySection.Downloads -> TrackList(sectionTracks, emptyMessageFor("No downloads yet."), gridView, play, openMenu, selection)
            LibrarySection.TopPlayed -> TrackList(sectionTracks, emptyMessageFor("Play something and it'll show up here."), gridView, play, openMenu, selection)
            // The capped, sortable slice for getting back to something quickly - the full record,
            // with its own editing, lives on the History screen this links to.
            LibrarySection.Recent -> Column {
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
                TrackList(sectionTracks, emptyMessageFor("Nothing played yet."), gridView, play, openMenu, selection)
            }
            LibrarySection.OnDevice -> when {
                !hasLocalPermission -> LocalMediaGate(
                    onGranted = { hasLocalPermission = true },
                )

                localTracks is UiState.Loading -> ListPlaceholder()

                localTracks is UiState.Error -> EmptyState((localTracks as UiState.Error).message)

                else -> TrackList(
                    sectionTracks,
                    emptyMessageFor("No music files found on this device."),
                    gridView,
                    play,
                    openMenu,
                    selection,
                )
            }
            LibrarySection.Following -> FollowedArtistsList(
                filteredFollowedArtists,
                onGoToArtist,
                emptyMessage = emptyMessageFor("Not following anyone yet. Follow an artist from their page or a search result."),
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
        val playlistResults = remember(playlistTracks) { playlistTracks.map { it.asTrackResult() } }

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
                playlistResults.firstOrNull()?.let { seed ->
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
            onDelete = { viewModel.deletePlaylist(playlist) },
            onDismiss = { selectedPlaylistId = null },
            onChangeCover = { coverPickerLauncher.launch(arrayOf("image/*")) },
            hasCustomCover = playlist.customCoverUri != null,
            onRemoveCustomCover = { viewModel.setCustomCover(playlist.id, null) },
        )
    }
}

@Composable
private fun TrackList(
    tracks: List<Track>,
    emptyMessage: String,
    gridView: Boolean,
    onPlay: (Track, List<Track>) -> Unit,
    /** Opens the row's actions sheet - reached via the row's own `⋮` now, not long-press. */
    onOpenMenu: (Track) -> Unit,
    selection: TrackSelection,
) {
    if (tracks.isEmpty()) {
        EmptyState(emptyMessage)
        return
    }

    if (gridView) {
        // Adaptive rather than a fixed column count, so the grid stays sensible in landscape and
        // at every display-density setting instead of stretching cells.
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 200.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Position-keyed: a phone's own music folder routinely holds the same song twice, and
            // "title|artist" collides there - a repeated Compose key throws rather than just
            // looking odd.
            itemsIndexed(tracks, key = { index, _ -> index }) { index, track ->
                TrackGridCell(
                    track = track,
                    // Once anything is ticked a plain tap toggles instead of playing: with a
                    // selection on screen, tapping a row to start a song would look like a misfire.
                    onClick = {
                        if (selection.active) selection.toggle(index) else onPlay(track, tracks)
                    },
                    // Enters selection directly - a grid cell has no room for its own menu button,
                    // so unlike the list this is the only way into multi-select from the grid.
                    onLongClick = {
                        if (selection.active) selection.toggle(index) else selection.start(index)
                    },
                    selected = selection.isSelected(index),
                    modifier = Modifier.animateItem(),
                )
            }
        }
        return
    }

    LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 200.dp)) {
        itemsIndexed(tracks, key = { index, _ -> index }) { index, track ->
            TrackRow(
                title = track.title,
                artist = track.artist,
                imageUrl = track.imageUrl,
                duration = track.duration,
                onClick = {
                    if (selection.active) selection.toggle(index) else onPlay(track, tracks)
                },
                onLongClick = {
                    if (selection.active) selection.toggle(index) else selection.start(index)
                },
                selected = selection.isSelected(index),
                // No menu button while selecting - the selection bar is the row's controls then.
                onOpenMenu = if (selection.active) null else { { onOpenMenu(track) } },
                modifier = Modifier.animateItem(),
            )
        }
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

@OptIn(ExperimentalFoundationApi::class)
/**
 * A 2-column grid of big playlist tiles, each showing a mosaic of its own tracks' artwork - the
 * same "cover generated from the tracks themselves" idea [PlaylistDetailScreen] uses for a single
 * playlist's own cover, now applied per-tile here so browsing Library's playlist list looks like a
 * wall of covers instead of a list of icons.
 */
@Composable
private fun PlaylistList(
    playlists: List<PlaylistEntity>,
    viewModel: LibraryViewModel,
    onOpenPlaylist: (Long) -> Unit,
    onLongPress: (PlaylistEntity) -> Unit,
    emptyMessage: String = "No playlists yet. Long-press any song and choose \"Add to playlist\".",
) {
    if (playlists.isEmpty()) {
        EmptyState(emptyMessage)
        return
    }
    // A LazyColumn of manually chunked rows, not LazyVerticalGrid - LazyVerticalGrid clips a row
    // that's newly appearing with fewer items than columns (e.g. a third playlist landing alone
    // right after being added) to a too-short height, cutting the cover and the title/count text
    // below it off entirely.
    LazyColumn(
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 200.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(playlists.chunked(2), key = { row -> row.joinToString("_") { it.id.toString() } }) { row ->
            Row(
                modifier = Modifier.fillMaxWidth().animateItem(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { playlist ->
                    PlaylistGridTile(
                        playlist = playlist,
                        viewModel = viewModel,
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistGridTile(
    playlist: PlaylistEntity,
    viewModel: LibraryViewModel,
    onOpenPlaylist: (Long) -> Unit,
    onLongPress: (PlaylistEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tracks by viewModel.tracksForPlaylist(playlist.id).collectAsState()
    Column(
        modifier = modifier
            .combinedClickable(
                onClick = { onOpenPlaylist(playlist.id) },
                onLongClick = { onLongPress(playlist) },
            )
            .testTag("playlist_row_${playlist.name.lowercase().replace(" ", "_")}"),
    ) {
        SquarePlaylistCover(tracks = tracks, fallbackCoverUrl = playlist.coverImageUrl, customCoverUri = playlist.customCoverUri, shape = RoundedCornerShape(14.dp))
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
@Composable
private fun FollowedArtistsList(
    artists: List<FollowedArtistEntity>,
    onOpenArtist: (String) -> Unit,
    emptyMessage: String = "Not following anyone yet. Follow an artist from their page or a search result.",
) {
    if (artists.isEmpty()) {
        EmptyState(emptyMessage)
        return
    }
    LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 200.dp)) {
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
