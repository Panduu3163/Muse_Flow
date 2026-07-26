package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.AlbumDetails
import com.example.AlbumPageCacheEntity
import com.example.MuseFlowDatabase
import com.example.MusicSearchRouter
import com.example.PlayerViewModel
import com.example.TrackActionsViewModel
import com.example.TrackResult
import com.example.UiState
import com.example.loadAsUiState
import com.example.parseAlbumDetailsJson
import com.example.toJson
import com.example.toPlayableTrack
import com.example.ui.component.AlbumActionsSheet
import com.example.ui.component.TrackActionsHost
import com.example.ui.component.TrackRow
import android.widget.Toast

/**
 * An album's tracklist, fetched from whichever backend produced the [albumId].
 *
 * Stale-while-revalidate cached in Room ([AlbumPageCacheEntity]), same pattern and reasoning as
 * [ArtistScreen]: a cached copy renders immediately if one exists, a live refetch always follows
 * and replaces it (updating the cache) when it lands.
 *
 * Cover/header treatment matches [PlaylistDetailScreen] and [RemotePlaylistScreen] (full-bleed
 * square art fading into the page background, a floating back button that fades/slides out as it
 * scrolls past) so an album looks consistent with every other "art at the top of a tracklist"
 * screen in the app. The Play/Shuffle buttons themselves are untouched - still the same pill
 * buttons this screen always had.
 */
@Composable
fun AlbumScreen(
    albumId: String,
    onPlayTrack: (TrackResult, List<TrackResult>) -> Unit,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onGoToArtist: (String) -> Unit = {},
    onGoToAlbum: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val router = remember { MusicSearchRouter(context) }
    val pageCacheDao = remember { MuseFlowDatabase.getInstance(context).albumPageCacheDao() }
    val actionsViewModel: TrackActionsViewModel = viewModel()
    var selectedTrack by remember { mutableStateOf<TrackResult?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf<UiState<AlbumDetails>>(UiState.Loading) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(albumId) {
        val cached = runCatching { pageCacheDao.get(albumId) }.getOrNull()
            ?.let { parseAlbumDetailsJson(it.detailsJson) }
        state = if (cached != null) UiState.Success(cached) else UiState.Loading

        val fresh = loadAsUiState("Couldn't load this album.") { router.getAlbumDetails(albumId) }
        when (fresh) {
            is UiState.Success -> {
                state = fresh
                runCatching {
                    pageCacheDao.upsert(
                        AlbumPageCacheEntity(
                            albumId = albumId,
                            detailsJson = fresh.data.toJson(),
                            cachedAt = System.currentTimeMillis(),
                        )
                    )
                }
            }
            // Network failed but the cache already rendered something - keep showing it.
            is UiState.Error -> if (cached == null) state = fresh
            is UiState.Loading -> Unit
        }
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
            .testTag("album_screen"),
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
                val details = current.data
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(bottom = 140.dp),
                ) {
                    item(key = "cover") {
                        AlbumCover(imageUrl = details.imageUrl)
                    }

                    item(key = "identity") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = details.title ?: "Album",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                            if (details.artist != null) {
                                Text(
                                    text = details.artist,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }

                            if (details.tracks.isNotEmpty()) {
                                AlbumInfoCapsule(
                                    text = "${details.tracks.size} ${if (details.tracks.size == 1) "song" else "songs"}",
                                    modifier = Modifier.padding(top = 12.dp),
                                )

                                Row(
                                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    PlayButton(
                                        icon = Icons.Default.PlayArrow,
                                        label = "Play",
                                        testTag = "album_play_all",
                                        onClick = {
                                            details.tracks.firstOrNull()?.let { onPlayTrack(it, details.tracks) }
                                        },
                                    )
                                    PlayButton(
                                        icon = Icons.Default.Shuffle,
                                        label = "Shuffle",
                                        testTag = "album_shuffle",
                                        onClick = {
                                            val shuffled = details.tracks.shuffled()
                                            shuffled.firstOrNull()?.let { onPlayTrack(it, shuffled) }
                                        },
                                    )
                                }
                            }
                        }
                    }

                    if (details.tracks.isEmpty()) {
                        item(key = "empty") {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "No songs found on this album.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    } else {
                        itemsIndexed(details.tracks, key = { index, _ -> index }) { _, track ->
                            TrackRow(
                                title = track.title,
                                artist = track.artist,
                                imageUrl = track.imageUrl,
                                duration = track.duration,
                                onClick = { onPlayTrack(track, details.tracks) },
                                onOpenMenu = { selectedTrack = track },
                            )
                        }
                    }
                }
            }
        }

        // Floating over the cover, fading/sliding out as it scrolls away - same treatment as the
        // Artist/Playlist screens' back button, replacing the old fixed IconButton row.
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
                        .testTag("album_back"),
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
            Spacer(modifier = Modifier.weight(1f))
            if (state is UiState.Success && topBarVisibility > 0.01f) {
                val details = (state as UiState.Success<AlbumDetails>).data
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = topBarVisibility
                            translationY = (1f - topBarVisibility) * -80f
                        }
                        .background(Color.Black.copy(alpha = 0.35f * topBarVisibility), CircleShape)
                        .testTag("album_menu"),
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Album options",
                        tint = Color.White,
                    )
                }
                if (menuOpen) {
                    AlbumActionsSheet(
                        title = details.title ?: "Album",
                        artist = details.artist,
                        imageUrl = details.imageUrl,
                        songCount = details.tracks.size,
                        onShuffle = {
                            val shuffled = details.tracks.shuffled()
                            shuffled.firstOrNull()?.let { onPlayTrack(it, shuffled) }
                        },
                        onStartRadio = {
                            details.tracks.firstOrNull()?.let { seed ->
                                playerViewModel.startRadio(seed) {
                                    Toast.makeText(context, "Couldn't start radio", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onPlayNext = { playerViewModel.playNext(details.tracks) },
                        onAddToQueue = { playerViewModel.addToQueue(details.tracks) },
                        onDownloadAll = {
                            actionsViewModel.downloadAll(details.tracks.map { it.toPlayableTrack(albumId.hashCode()) })
                        },
                        onGoToArtist = details.tracks.firstNotNullOfOrNull { it.artistId }
                            ?.let { id -> { onGoToArtist(id) } },
                        onShare = { context.shareAlbum(albumId, details) },
                        onDismiss = { menuOpen = false },
                    )
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

private fun android.content.Context.shareAlbum(albumId: String, details: AlbumDetails) {
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(
            android.content.Intent.EXTRA_TEXT,
            "${details.title.orEmpty()}${details.artist?.let { " - $it" }.orEmpty()}\n" +
                "https://music.youtube.com/browse/$albumId",
        )
    }
    startActivity(android.content.Intent.createChooser(intent, "Share album"))
}

/** Full-bleed square cover fading into the page background at its bottom edge - same treatment as
 * [PlaylistDetailScreen]/[RemotePlaylistScreen]'s cover, just a single fixed image (an album has
 * one real cover already) rather than a track-thumbnail mosaic. */
@Composable
private fun AlbumCover(imageUrl: String?) {
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).testTag("album_cover")) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Album,
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
private fun AlbumInfoCapsule(text: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("album_song_count"),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlayButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
