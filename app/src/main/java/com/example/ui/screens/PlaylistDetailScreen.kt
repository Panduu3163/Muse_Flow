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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.LibraryViewModel
import com.example.PlayerViewModel
import com.example.Track
import com.example.TrackActionsViewModel
import com.example.TrackResult
import com.example.sharePlaylist
import com.example.downloadKey
import com.example.TrackSortOption
import com.example.sortedByLibraryOption
import com.example.totalDurationLabel
import com.example.ui.component.LibrarySortHeader
import com.example.ui.component.PlaylistActionsSheet
import com.example.ui.component.TrackActionsHost
import com.example.ui.component.TrackRow
import com.example.ui.component.TrackSelectionHost
import com.example.ui.component.rememberTrackSelection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A single playlist's tracks: cover art, shuffle/play/menu, an About card, its own sort control,
 * and an in-playlist search filter.
 *
 * Reads through [LibraryViewModel.tracksForPlaylist], so the list stays live as tracks are added
 * from a search result's actions sheet while this screen is open. Sort and search state are local
 * to this screen rather than shared with Library's - sorting a playlist you're viewing shouldn't
 * silently resort Library's Liked/Downloads sections the next time they're opened.
 */
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    onPlayTrack: (TrackResult, List<TrackResult>) -> Unit,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onGoToArtist: (String) -> Unit = {},
    onGoToAlbum: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel: LibraryViewModel = viewModel()
    val actionsViewModel: TrackActionsViewModel = viewModel()
    val context = LocalContext.current
    val playlists by viewModel.playlists.collectAsState()
    val tracks by viewModel.tracksForPlaylist(playlistId).collectAsState()
    val online by remember(context) { com.example.observeOnline(context.applicationContext) }
        .collectAsState(initial = com.example.isOnline(context))
    val downloaded by remember(context) { com.example.DownloadRepository.getInstance(context).completedDownloads }
        .collectAsState(initial = emptyList())
    val offlineKeys = remember(downloaded) { downloaded.map { it.key }.toSet() }
    val offlineTracks = remember(tracks, offlineKeys) {
        tracks.filter { it.sourceType == com.example.MusicSource.LOCAL_DEVICE || it.downloadKey() in offlineKeys }
    }
    // Drives each row's heart/download glyphs, same as Library's own track lists - a playlist
    // previously gave no way to tell which of its songs were already liked/downloaded.
    val likedKeys by actionsViewModel.likedKeys.collectAsState()
    val downloadsInProgress by actionsViewModel.downloadsInProgress.collectAsState()
    // Drives the "now playing" equalizer badge on whichever row matches - see TrackRow's own doc.
    val nowPlayingState by playerViewModel.state.collectAsState()
    val selection = rememberTrackSelection()
    var selectedTrack by remember { mutableStateOf<TrackResult?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    // Persisted (rather than a plain read-once grant) so the picked cover survives the app being
    // killed and the URI re-read on a later launch - a `content://` URI is otherwise only readable
    // for the lifetime of the Activity result that granted it.
    val coverPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        viewModel.setCustomCover(playlistId, uri.toString())
    }

    var sort by remember { mutableStateOf(TrackSortOption.DEFAULT) }
    var ascending by remember { mutableStateOf(true) }
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var preSearchScrollIndex by remember { mutableStateOf(0) }
    var preSearchScrollOffset by remember { mutableStateOf(0) }
    // Restores wherever the user last scrolled to in this playlist - tapping a track to open Now
    // Playing and pressing back used to always land back at the top instead. See
    // rememberRestoredLazyListState's own doc for the two real gotchas this needs to dodge.
    val listState = com.example.rememberRestoredLazyListState(
        key = "playlist:$playlistId",
        isContentReady = tracks.isNotEmpty(),
    )
    // Same fade-and-slide-out as the Artist screen's back button, for the same reason - see its
    // comment. "cover" is item 0 here too.
    val topBarVisibility by remember {
        androidx.compose.runtime.derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                0f
            } else {
                val coverHeight = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }?.size ?: 1
                (1f - listState.firstVisibleItemScrollOffset.toFloat() / coverHeight).coerceIn(0f, 1f)
            }
        }
    }
    // Same slide-to-the-search-field behaviour as Library's own detail sections - see
    // LibraryScreen's toggleSearch/searchFieldIndex doc for the full reasoning. "search_field" is
    // always item 1 here (right after "cover"), no branching needed since this screen has only
    // one shape.
    fun toggleSearch() {
        if (!searchActive) {
            preSearchScrollIndex = listState.firstVisibleItemIndex
            preSearchScrollOffset = listState.firstVisibleItemScrollOffset
        }
        searchActive = !searchActive
        if (!searchActive) searchQuery = ""
    }
    androidx.activity.compose.BackHandler(enabled = searchActive) { toggleSearch() }
    // Skips its very first run (the mount right after this composable enters composition, e.g.
    // returning from Now Playing) - see LibraryScreen's own identical fix for the full reasoning.
    // Unconditionally acting on that first run unconditionally animated the list back to (0, 0) on
    // every single return from Now Playing, immediately undoing the just-restored scroll position.
    var hasHandledInitialSearchState by remember { mutableStateOf(false) }
    LaunchedEffect(searchActive) {
        if (!hasHandledInitialSearchState) {
            hasHandledInitialSearchState = true
            return@LaunchedEffect
        }
        if (searchActive) {
            listState.animateScrollToItem(1)
        } else {
            listState.animateScrollToItem(preSearchScrollIndex, preSearchScrollOffset)
        }
    }

    val playlist = playlists.find { it.id == playlistId }

    val sortedTracks = remember(tracks, sort, ascending) {
        tracks.sortedByLibraryOption(sort, ascending)
    }
    val visibleTracks = remember(sortedTracks, searchQuery) {
        if (searchQuery.isBlank()) {
            sortedTracks
        } else {
            sortedTracks.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                    it.artist.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    val trackResults = remember(visibleTracks) { visibleTracks.map { it.asTrackResult() } }

    // Anything that renumbers the visible list invalidates positional selection - same rule
    // Library follows for its own sort/section changes.
    LaunchedEffect(sort, ascending, searchQuery) { selection.clear() }

    val play: (Track, List<Track>) -> Unit = { track, queue ->
        val playableQueue = if (online) queue else queue.filter { it in offlineTracks }
        if (track !in playableQueue) {
            android.widget.Toast.makeText(context, "This song is not downloaded", android.widget.Toast.LENGTH_SHORT).show()
        } else onPlayTrack(track.asTrackResult(), playableQueue.map { it.asTrackResult() })
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("playlist_detail_screen"),
    ) {
        // Everything - cover, buttons, metadata, About card, the sort header, and the tracks
        // themselves - lives in one LazyColumn rather than a fixed header above a separately-
        // scrolling list. A pinned header here was a large, mostly-decorative block (cover art
        // alone is 140dp) permanently eating the screen; a long playlist needs that space for
        // songs, not for art that's already been seen once. The cover starts at the true top of
        // the screen (no reserved space above it for the back/search row - those float over it,
        // same as the Artist screen's back button) rather than leaving a blank gap before it.
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            // More than the 140.dp every other screen uses: this screen has no bottom nav bar of
            // its own competing for that space (it's a pushed detail screen, not a tab), so the
            // mini-player floats lower here and the last track row was landing tucked behind it
            // with no way to scroll further and clear it.
            contentPadding = PaddingValues(bottom = 200.dp),
        ) {
            item(key = "cover") {
                PlaylistCover(
                    tracks = tracks,
                    fallbackCoverUrl = playlist?.coverImageUrl,
                    customCoverUri = playlist?.customCoverUri,
                )
            }

            if (searchActive) {
                item(key = "search_field") {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search this playlist") },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = ::toggleSearch) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Close search")
                            }
                        },
                        // statusBarsPadding here, not just on the floating back/search icons -
                        // this field becomes the LazyColumn's top-most item once toggleSearch
                        // scrolls to it, so its own top edge needs to clear the status bar itself
                        // rather than relying on some other item's padding above it.
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .testTag("playlist_search_field"),
                    )
                }
            }

            item(key = "identity") {
                // Centered identity block - title, the song-count/duration capsules, the About
                // card and the three primary actions - same order as the Artist screen's header
                // (cover, name, capsules, about, buttons), so both destination screens read the
                // same way.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = playlist?.name ?: "Playlist",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )

                    if (tracks.isNotEmpty()) {
                        Row(
                            modifier = Modifier.padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            PlaylistInfoCapsule(
                                icon = Icons.Default.MusicNote,
                                text = "${tracks.size} ${if (tracks.size == 1) "song" else "songs"}",
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                testTag = "playlist_song_count",
                            )
                            PlaylistInfoCapsule(
                                icon = Icons.Default.AccessTime,
                                text = tracks.totalDurationLabel(),
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                testTag = "playlist_duration",
                            )
                        }
                    }

                    if (playlist != null) {
                        AboutCard(createdAt = playlist.createdAt)
                    }

                    if (tracks.isNotEmpty()) {
                        Row(
                            modifier = Modifier.padding(top = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircleIconButton(
                                icon = Icons.Default.Shuffle,
                                contentDescription = "Shuffle",
                                testTag = "playlist_shuffle",
                                onClick = {
                                    val shuffled = (if (online) tracks else offlineTracks).shuffled()
                                    shuffled.firstOrNull()?.let { play(it, shuffled) }
                                },
                            )
                            CircleIconButton(
                                icon = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                testTag = "playlist_play_all",
                                prominent = true,
                                onClick = {
                                    val queue = if (online) tracks else offlineTracks
                                    if (queue.isEmpty()) android.widget.Toast.makeText(context, "No downloaded songs in this playlist", android.widget.Toast.LENGTH_SHORT).show()
                                    else play(queue.first(), queue)
                                },
                            )
                            CircleIconButton(
                                icon = Icons.Default.MoreVert,
                                contentDescription = "Playlist options",
                                testTag = "playlist_menu",
                                onClick = { menuOpen = true },
                            )
                        }
                    }
                }
            }

            item(key = "selection_or_sort") {
                TrackSelectionHost(
                    selection = selection,
                    tracks = trackResults,
                    playerViewModel = playerViewModel,
                    actionsViewModel = actionsViewModel,
                    onRemoveFromPlaylist = { removed ->
                        actionsViewModel.removeFromPlaylist(playlistId, removed)
                    },
                )

                if (visibleTracks.isNotEmpty() && !selection.active) {
                    LibrarySortHeader(
                        options = TrackSortOption.entries.toList(),
                        selected = sort,
                        onSelect = { sort = it },
                        ascending = ascending,
                        onToggleDirection = { ascending = !ascending },
                        optionLabel = { it.label },
                        countLabel = "${visibleTracks.size} song${if (visibleTracks.size == 1) "" else "s"}",
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }

            if (tracks.isEmpty()) {
                item(key = "empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "This playlist is empty.\nLong-press any song and choose \"Add to playlist\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else if (visibleTracks.isEmpty()) {
                item(key = "no_matches") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No songs match \"$searchQuery\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                // Positional keys, matching every other list in the app: they are what the
                // selection indexes, and the dedupe in `PlaylistRepository.addTracks` is the only
                // thing keeping a content key unique here.
                itemsIndexed(visibleTracks, key = { index, _ -> index }) { index, track ->
                    val key = track.downloadKey()
                    TrackRow(
                        title = track.title,
                        artist = track.artist,
                        imageUrl = track.imageUrl,
                        duration = track.duration,
                        onClick = {
                            // Queues the playlist's real order (sortedTracks), not visibleTracks -
                            // while searching within the playlist, visibleTracks is only the
                            // handful of on-screen matches, which used to become the *entire*
                            // queue: tapping a search hit that wasn't the playlist's last track
                            // still left nothing queued after it.
                            if (selection.active) selection.toggle(index) else play(track, sortedTracks)
                        },
                        onLongClick = {
                            if (selection.active) selection.toggle(index) else selection.start(index)
                        },
                        selected = selection.isSelected(index),
                        isLiked = key in likedKeys,
                        isDownloaded = key in offlineKeys,
                        isLocalDevice = track.sourceType == com.example.MusicSource.LOCAL_DEVICE,
                        downloadProgress = downloadsInProgress[key],
                        isPlaying = key == nowPlayingState.currentTrackKey,
                        onOpenMenu = if (selection.active) null else {
                            { selectedTrack = track.asTrackResult() }
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }

        // Floating over the cover, not a row reserving space above it - same scrim treatment as
        // the Artist screen's back button, for the same reason: a playlist cover's colour varies
        // per playlist, so a fixed icon tint alone isn't reliably visible against it.
        //
        // The back button itself fades and slides out as the cover scrolls away (same as the
        // Artist screen - see its comment) so it's gone before the playlist name reaches the top,
        // rather than sitting fixed over tab content with nothing behind it to justify the scrim.
        // The search toggle stays visible at every scroll position - unlike Artist, this screen has
        // a real always-useful feature (searching within the playlist) behind that icon.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (topBarVisibility > 0.01f) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = topBarVisibility
                            translationY = (1f - topBarVisibility) * -80f
                        }
                        .background(Color.Black.copy(alpha = 0.35f * topBarVisibility), CircleShape)
                        .testTag("playlist_back"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(48.dp))
            }
            Box(modifier = Modifier.weight(1f))
            // Fades/slides out with the back button rather than staying fixed - once search is
            // active, closing it is also reachable from the search field's own close affordance
            // inside the scrolling list, so this floating copy doesn't need to stay reachable.
            if (topBarVisibility > 0.01f) {
                IconButton(
                    onClick = ::toggleSearch,
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = topBarVisibility
                            translationY = (1f - topBarVisibility) * -80f
                        }
                        .background(Color.Black.copy(alpha = 0.35f * topBarVisibility), CircleShape)
                        .testTag("playlist_search_toggle"),
                ) {
                    Icon(
                        imageVector = if (searchActive) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = if (searchActive) "Close search" else "Search this playlist",
                        tint = Color.White,
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(48.dp))
            }
        }
    }

    TrackActionsHost(
        track = selectedTrack,
        onDismiss = { selectedTrack = null },
        playerViewModel = playerViewModel,
        actionsViewModel = actionsViewModel,
        // Only this screen knows which playlist the row belongs to, so it is the only one that can
        // offer the removal - and the only one where "remove" is unambiguous.
        onRemoveFromPlaylist = { track -> actionsViewModel.removeFromPlaylist(playlistId, track) },
        onGoToArtist = onGoToArtist,
        onGoToAlbum = onGoToAlbum,
    )

    if (menuOpen && playlist != null) {
        PlaylistActionsSheet(
            name = playlist.name,
            songCount = tracks.size,
            coverImageUrl = playlist.coverImageUrl,
            isPinned = playlist.isPinned,
            onTogglePin = { viewModel.togglePin(playlist) },
            onShuffle = {
                val shuffled = (if (online) tracks else offlineTracks).shuffled()
                shuffled.firstOrNull()?.let { play(it, shuffled) }
            },
            onStartRadio = {
                tracks.firstOrNull()?.let { seed ->
                    playerViewModel.startRadio(seed.asTrackResult()) {
                        android.widget.Toast.makeText(
                            context,
                            "Couldn't start radio",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            },
            onPlayNext = { playerViewModel.playNext(tracks.map { it.asTrackResult() }) },
            onAddToQueue = { playerViewModel.addToQueue(tracks.map { it.asTrackResult() }) },
            onDownload = { actionsViewModel.downloadAll(tracks) },
            onShare = { context.sharePlaylist(playlist.name, tracks, playlist.remoteId) },
            // Deleting the playlist we're currently looking at leaves nothing to show here, so
            // this is the one PlaylistActionsSheet call site that also has to navigate back.
            onDelete = {
                viewModel.deletePlaylist(playlist)
                onBack()
            },
            onDismiss = { menuOpen = false },
            onChangeCover = { coverPickerLauncher.launch(arrayOf("image/*")) },
            hasCustomCover = playlist.customCoverUri != null,
            onRemoveCustomCover = { viewModel.setCustomCover(playlistId, null) },
        )
    }
}

/** A full-bleed 2x2 mosaic of the playlist's first four track covers, fading into the page
 * background at its bottom edge - the same "art bleeds into the page" treatment as the Artist
 * screen's cover, built from the tracks themselves (a Spotify-style generated cover) rather than a
 * single fixed image, since most playlists here have no assigned cover of their own. [customCoverUri]
 * (a user-picked photo) wins over everything else - it's an explicit choice, not an incidental
 * fallback. Otherwise falls back to [fallbackCoverUrl] (an imported/online playlist's real cover)
 * when there aren't four tracks to mosaic, then to a single track's art, then to a plain icon for a
 * brand new empty playlist. */
@Composable
private fun PlaylistCover(tracks: List<Track>, fallbackCoverUrl: String?, customCoverUri: String? = null) {
    val thumbnails = remember(tracks) { tracks.mapNotNull { it.imageUrl }.distinct().take(4) }
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).testTag("playlist_cover")) {
        when {
            customCoverUri != null -> AsyncImage(
                model = customCoverUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            thumbnails.size == 4 -> Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    MosaicCell(thumbnails[0], Modifier.weight(1f).fillMaxHeight())
                    MosaicCell(thumbnails[1], Modifier.weight(1f).fillMaxHeight())
                }
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    MosaicCell(thumbnails[2], Modifier.weight(1f).fillMaxHeight())
                    MosaicCell(thumbnails[3], Modifier.weight(1f).fillMaxHeight())
                }
            }

            fallbackCoverUrl != null -> AsyncImage(
                model = fallbackCoverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            thumbnails.isNotEmpty() -> AsyncImage(
                model = thumbnails.first(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            else -> Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(96.dp),
                )
            }
        }

        val background = MaterialTheme.colorScheme.background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            0.45f to Color.Transparent,
                            1f to background,
                        ),
                    ),
                ),
        )
    }
}

@Composable
private fun MosaicCell(imageUrl: String, modifier: Modifier) {
    AsyncImage(
        model = imageUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

@Composable
private fun PlaylistInfoCapsule(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    containerColor: Color,
    contentColor: Color,
    testTag: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(containerColor)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag(testTag),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/** Created-date card - the one fact about a playlist MuseFlow tracks that isn't already on
 * screen elsewhere (name, cover, track count and duration are all above it). */
@Composable
private fun AboutCard(createdAt: Long) {
    val formatted = remember(createdAt) {
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(createdAt))
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .testTag("playlist_about_card"),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "About",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Created $formatted",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun CircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    testTag: String,
    onClick: () -> Unit,
    prominent: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(if (prominent) 64.dp else 48.dp)
            .clip(CircleShape)
            .background(
                if (prominent) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
            )
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (prominent) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(if (prominent) 32.dp else 22.dp),
        )
    }
}
