package com.example.ui.screens

import android.widget.Toast
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.MusicSearchRouter
import com.example.PlayerViewModel
import com.example.TrackActionsViewModel
import com.example.TrackResult
import com.example.UiState
import com.example.downloadKey
import com.example.loadAsUiState
import com.example.shareYouTubePlaylistLink
import com.example.toPlayableTrack
import com.example.ui.component.RemotePlaylistActionsSheet
import com.example.ui.component.TrackActionsHost
import com.example.ui.component.TrackRow

/**
 * A remote playlist's tracklist - reached from Search or Browse results, identified by a YouTube
 * browseId rather than a local Room id (see [com.example.NavRoutes.remotePlaylist] for why the
 * header comes in as nav args instead of being fetched here).
 *
 * Visually identical to [PlaylistDetailScreen] (full-bleed track-mosaic cover, capsules, About
 * card, floating fading back button, Shuffle/Play circle row) so a playlist looks the same whether
 * it's already in Library or still just a search result. The one addition is the third circle
 * button: Library's copy is a "⋮" menu (pin/delete/download - all Library-only concepts), this
 * screen's is "Add" (save the whole playlist into Library) since this playlist doesn't have those
 * to offer yet - it isn't saved anywhere until that button is tapped. No local cache table (unlike
 * Artist/Album's `MIGRATION_12_13`) - re-opening the same playlist refetches every time.
 */
@Composable
fun RemotePlaylistScreen(
    playlistId: String,
    title: String,
    subtitle: String,
    imageUrl: String?,
    onPlayTrack: (TrackResult, List<TrackResult>) -> Unit,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onGoToArtist: (String) -> Unit = {},
    onGoToAlbum: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val router = remember { MusicSearchRouter(context) }
    val actionsViewModel: TrackActionsViewModel = viewModel()
    var selectedTrack by remember { mutableStateOf<TrackResult?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    // Seeded from the in-memory cache when this exact playlist was already loaded once this
    // session (e.g. returning from Now Playing), rather than always starting at Loading - a blank
    // spinner flash on every remount was visually indistinguishable from "the scroll position got
    // reset", even once rememberRestoredLazyListState below correctly restored it a moment later.
    var state by remember {
        mutableStateOf<UiState<List<TrackResult>>>(
            RemotePlaylistTracksCache.get(playlistId)?.let { UiState.Success(it) } ?: UiState.Loading
        )
    }
    val savedPlaylists by actionsViewModel.playlists.collectAsState()
    val addedToLibrary = savedPlaylists.any { it.remoteId == playlistId }
    // Restores wherever the user last scrolled to in this playlist - tapping a track to open Now
    // Playing and pressing back used to always land back at the top instead. See
    // rememberRestoredLazyListState's own doc for the two real gotchas this needs to dodge.
    val listState = com.example.rememberRestoredLazyListState(
        key = "remote_playlist:$playlistId",
        isContentReady = state is UiState.Success,
    )
    // Same heart/download glyphs Library's own lists show - a remote playlist previously gave no
    // way to tell a song you'd already liked or downloaded elsewhere apart from one you hadn't.
    val likedKeys by actionsViewModel.likedKeys.collectAsState()
    val downloadedKeys by actionsViewModel.downloadedKeys.collectAsState()
    val downloadsInProgress by actionsViewModel.downloadsInProgress.collectAsState()
    // Drives the "now playing" equalizer badge on whichever row matches - see TrackRow's own doc.
    val nowPlayingState by playerViewModel.state.collectAsState()

    LaunchedEffect(playlistId) {
        val fresh = loadAsUiState("Couldn't load this playlist.") { router.getPlaylistTracks(playlistId) }
        if (fresh is UiState.Success) RemotePlaylistTracksCache.put(playlistId, fresh.data)
        state = fresh
    }

    // Same fade-and-slide-out as the Artist/Playlist screens' back button - see their comments.
    // "cover" is item 0 here too.
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("remote_playlist_screen"),
    ) {
        when (val current = state) {
            is UiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            is UiState.Error -> Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = current.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            is UiState.Success -> {
                val tracks = current.data
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(bottom = 200.dp),
                ) {
                    item(key = "cover") {
                        RemotePlaylistCover(tracks = tracks, fallbackCoverUrl = imageUrl)
                    }

                    item(key = "identity") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = title,
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
                                    InfoCapsule(
                                        icon = Icons.Default.MusicNote,
                                        text = "${tracks.size} ${if (tracks.size == 1) "song" else "songs"}",
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        testTag = "remote_playlist_song_count",
                                    )
                                    InfoCapsule(
                                        icon = Icons.Default.AccessTime,
                                        text = tracks.totalDurationLabel(),
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                        testTag = "remote_playlist_duration",
                                    )
                                }
                            }

                            if (subtitle.isNotBlank()) {
                                AboutCard(text = subtitle)
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
                                        testTag = "remote_playlist_shuffle",
                                        onClick = {
                                            val shuffled = tracks.shuffled()
                                            shuffled.firstOrNull()?.let { onPlayTrack(it, shuffled) }
                                        },
                                    )
                                    CircleIconButton(
                                        icon = Icons.Default.PlayArrow,
                                        contentDescription = "Play",
                                        testTag = "remote_playlist_play_all",
                                        prominent = true,
                                        onClick = { tracks.firstOrNull()?.let { onPlayTrack(it, tracks) } },
                                    )
                                    // Save/Download/Share used to be three more circle buttons
                                    // here, crowding Shuffle/Play into a five-button row - now
                                    // collected behind one menu (see RemotePlaylistActionsSheet).
                                    CircleIconButton(
                                        icon = Icons.Default.MoreVert,
                                        contentDescription = "Playlist options",
                                        testTag = "remote_playlist_menu",
                                        onClick = { menuOpen = true },
                                    )
                                }
                            }
                        }
                    }

                    if (tracks.isEmpty()) {
                        item(key = "empty") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "No songs found in this playlist.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    } else {
                        itemsIndexed(tracks, key = { index, _ -> index }) { _, track ->
                            val key = track.downloadKey()
                            TrackRow(
                                title = track.title,
                                artist = track.artist,
                                imageUrl = track.imageUrl,
                                duration = track.duration,
                                onClick = { onPlayTrack(track, tracks) },
                                isLiked = key in likedKeys,
                                isDownloaded = key in downloadedKeys,
                                downloadProgress = downloadsInProgress[key],
                                isPlaying = key == nowPlayingState.currentTrackKey,
                                onOpenMenu = { selectedTrack = track },
                            )
                        }
                    }
                }
            }
        }

        // Floating over the cover, fading/sliding out as it scrolls away - same treatment as the
        // Artist/Playlist screens' back button.
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
                        .testTag("remote_playlist_back"),
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

    if (menuOpen) {
        val currentTracks = (state as? UiState.Success)?.data.orEmpty()
        RemotePlaylistActionsSheet(
            title = title,
            songCount = currentTracks.size,
            imageUrl = imageUrl,
            addedToLibrary = addedToLibrary,
            onSaveToLibrary = {
                if (!addedToLibrary) {
                    val asTracks = currentTracks.map { it.toPlayableTrack(it.id.hashCode()) }
                    actionsViewModel.addRemotePlaylistToLibrary(title, imageUrl, asTracks, playlistId)
                    Toast.makeText(context, "Added to your library", Toast.LENGTH_SHORT).show()
                }
            },
            onDownload = {
                val asTracks = currentTracks.map { it.toPlayableTrack(it.id.hashCode()) }
                if (addedToLibrary) {
                    actionsViewModel.downloadAll(asTracks)
                } else {
                    actionsViewModel.saveAndDownloadPlaylist(title, imageUrl, asTracks, playlistId)
                }
                Toast.makeText(context, "Downloading playlist for offline listening", Toast.LENGTH_SHORT).show()
            },
            onShare = { context.shareYouTubePlaylistLink(title, playlistId) },
            onDismiss = { menuOpen = false },
        )
    }
}

/** A full-bleed 2x2 mosaic of the playlist's first four track covers, fading into the page
 * background at its bottom edge - identical treatment to [PlaylistDetailScreen]'s own cover.
 * Falls back to [fallbackCoverUrl] (the search result's own cover) when there aren't four tracks
 * to mosaic, then to a single track's art, then to a plain icon. */
@Composable
private fun RemotePlaylistCover(tracks: List<TrackResult>, fallbackCoverUrl: String?) {
    val thumbnails = remember(tracks) { tracks.mapNotNull { it.imageUrl }.distinct().take(4) }
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).testTag("remote_playlist_cover")) {
        when {
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
private fun InfoCapsule(
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

/** [PlaylistDetailScreen]'s About card shows "Created {date}" - a remote playlist has no creation
 * date of its own, so this shows the source's own subtitle (curator/description text) instead,
 * same card treatment. */
@Composable
private fun AboutCard(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .testTag("remote_playlist_about_card"),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "About",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = text,
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

/**
 * Last successfully loaded tracklist per remote playlist id, kept in memory only - lets a
 * same-playlist remount (e.g. returning from Now Playing) show its content immediately instead of
 * a blank loading spinner while a fresh fetch runs in the background, the same stale-while-
 * revalidate treatment [ArtistScreen]/[AlbumScreen] get from their own Room page cache. This
 * screen has no such cache (its own doc explains why - a remote playlist's header comes from nav
 * args, not a re-fetchable id-only lookup), so this is the lightweight in-memory equivalent -
 * enough to avoid the visible reload, not meant to survive process death.
 */
private object RemotePlaylistTracksCache {
    private val cache = mutableMapOf<String, List<TrackResult>>()
    fun get(playlistId: String): List<TrackResult>? = cache[playlistId]
    fun put(playlistId: String, tracks: List<TrackResult>) { cache[playlistId] = tracks }
}

/** Same duration-summing logic as [com.example.totalDurationLabel] (`List<Track>`), duplicated for
 * `List<TrackResult>` rather than converting every track just to reuse it - this screen only ever
 * needs the one number. */
private fun List<TrackResult>.totalDurationLabel(): String {
    val totalSeconds = sumOf { track ->
        val parts = track.duration?.split(":").orEmpty()
        val minutes = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val seconds = parts.getOrNull(1)?.toIntOrNull() ?: 0
        minutes * 60 + seconds
    }
    val totalMinutes = totalSeconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "$hours hr $minutes min"
        hours > 0 -> "$hours hr"
        minutes > 0 -> "$minutes min"
        else -> "Less than a minute"
    }
}
