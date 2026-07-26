package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.AlbumResult
import com.example.ArtistPageCacheEntity
import com.example.ArtistResult
import com.example.ArtistTracklist
import com.example.FollowedArtistsRepository
import com.example.MuseFlowDatabase
import com.example.MusicSearchRouter
import com.example.MusicSource
import com.example.PlayerViewModel
import com.example.TrackActionsViewModel
import com.example.TrackResult
import com.example.UiState
import com.example.loadAsUiState
import com.example.parseArtistTracklistJson
import com.example.toJson
import com.example.ui.component.ArtistActionsSheet
import com.example.ui.component.TrackActionsHost
import com.example.ui.component.TrackRow

private val ARTIST_TABS = listOf("Overview", "Songs", "Albums", "Related")

/**
 * An artist's top songs plus discography/related-artist shelves, laid out as four tabs
 * (Overview/Songs/Albums/Related - matching Echo's shape, see full-gap-audit.md §2.1) over one
 * fetch of [ArtistTracklist].
 *
 * Everything - the big cover, name, subscriber/listener capsules, About, Play/Shuffle, the sticky
 * tab row and every tab's content - lives in one [LazyColumn], so the header scrolls away with the
 * rest of the page instead of permanently eating screen space above a small scrolling window. The
 * tab row alone is a [stickyHeader] so switching tabs stays reachable without scrolling back up.
 *
 * Stale-while-revalidate cached in Room ([ArtistPageCacheEntity]): a cached copy renders
 * immediately if one exists, then a live refetch runs regardless and replaces it (updating the
 * cache too) when it lands - so a repeat visit is never blocked on the network, but never stuck
 * showing stale data either. A fetch failure with no cache falls through to the normal error state.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtistScreen(
    artistId: String,
    onPlayTrack: (TrackResult, List<TrackResult>) -> Unit,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onGoToArtist: (String) -> Unit = {},
    onGoToAlbum: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val router = remember { MusicSearchRouter(context) }
    val followedArtists = remember { FollowedArtistsRepository.getInstance(context) }
    val pageCacheDao = remember { MuseFlowDatabase.getInstance(context).artistPageCacheDao() }
    val followedIds by followedArtists.observeFollowedIds().collectAsState(initial = emptySet())
    val actionsViewModel: TrackActionsViewModel = viewModel()
    var selectedTrack by remember { mutableStateOf<TrackResult?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf<UiState<ArtistTracklist>>(UiState.Loading) }
    var selectedTab by remember(artistId) { mutableIntStateOf(0) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    // 1f at the very top, fading to 0f by the time the cover ("cover" is item 0) has fully
    // scrolled past - so the button is gone well before the artist name (item 1) reaches the top,
    // rather than permanently floating over whatever tab content ends up underneath it.
    val backButtonVisibility by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                0f
            } else {
                val coverHeight = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }?.size ?: 1
                (1f - listState.firstVisibleItemScrollOffset.toFloat() / coverHeight).coerceIn(0f, 1f)
            }
        }
    }

    LaunchedEffect(artistId) {
        selectedTab = 0
        val cached = runCatching { pageCacheDao.get(artistId) }.getOrNull()
            ?.let { parseArtistTracklistJson(it.tracklistJson) }
        if (cached != null) {
            state = UiState.Success(cached)
        } else {
            state = UiState.Loading
        }

        val fresh = loadAsUiState("Couldn't load this artist.") { router.getArtistTracklist(artistId) }
        when (fresh) {
            is UiState.Success -> {
                state = fresh
                runCatching {
                    pageCacheDao.upsert(
                        ArtistPageCacheEntity(
                            artistId = artistId,
                            tracklistJson = fresh.data.toJson(),
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

    Box(modifier = modifier.fillMaxSize()) {
        when (val current = state) {
            is UiState.Loading -> Box(
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            is UiState.Error -> Box(
                modifier = Modifier.fillMaxSize().statusBarsPadding().padding(32.dp),
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
                val tracklist = current.data
                val isFollowed = followedIds.contains(artistId)

                LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
                    item(key = "cover") { ArtistCover(imageUrl = tracklist.imageUrl) }

                    item(key = "identity") {
                        ArtistIdentity(
                            tracklist = tracklist,
                            isFollowed = isFollowed,
                            onFollow = {
                                followedArtists.follow(
                                    artistId,
                                    tracklist.name ?: "Artist",
                                    tracklist.imageUrl,
                                    MusicSource.YOUTUBE_MUSIC,
                                )
                            },
                            onUnfollow = { followedArtists.unfollow(artistId) },
                            onPlayAll = { tracklist.tracks.firstOrNull()?.let { onPlayTrack(it, tracklist.tracks) } },
                            onShuffle = {
                                val shuffled = tracklist.tracks.shuffled()
                                shuffled.firstOrNull()?.let { onPlayTrack(it, shuffled) }
                            },
                        )
                    }

                    // A plain scrolling item, not a stickyHeader - a sticky tab row parks at the
                    // same fixed screen position the floating back button always occupies, so once
                    // scrolled the two would permanently overlap instead of the back button simply
                    // passing over content momentarily during the scroll itself.
                    item(key = "tabs") {
                        Surface(color = MaterialTheme.colorScheme.background) {
                            SecondaryTabRow(selectedTabIndex = selectedTab) {
                                ARTIST_TABS.forEachIndexed { index, title ->
                                    Tab(
                                        selected = selectedTab == index,
                                        onClick = { selectedTab = index },
                                        text = { Text(title) },
                                        modifier = Modifier.testTag("artist_tab_$title"),
                                    )
                                }
                            }
                        }
                    }

                    when (selectedTab) {
                        0 -> artistOverviewItems(
                            tracklist = tracklist,
                            onPlayTrack = onPlayTrack,
                            onOpenMenu = { selectedTrack = it },
                            onGoToAlbum = onGoToAlbum,
                            onGoToArtist = onGoToArtist,
                        )
                        1 -> artistSongsItems(
                            tracks = tracklist.tracks,
                            onPlayTrack = onPlayTrack,
                            onOpenMenu = { selectedTrack = it },
                        )
                        2 -> artistAlbumsItems(albums = tracklist.albums, onGoToAlbum = onGoToAlbum)
                        3 -> artistRelatedItems(artists = tracklist.relatedArtists, onGoToArtist = onGoToArtist)
                    }
                }
            }
        }

        // A dark scrim behind the icon, not just the theme's onSurface tint - the cover behind it
        // is a different colour per artist, so a fixed tint alone reads invisible against a light
        // cover about half the time. White-on-scrim reads on every cover; over plain background
        // (loading/error states) it's the same treatment, just less necessary there.
        //
        // Fades and slides out as the cover scrolls away (see backButtonVisibility) rather than
        // staying fixed on screen for the whole scroll - a permanently floating button ends up
        // sitting on top of tab content with nothing behind it to justify the scrim. Not clickable
        // once effectively invisible, so it can't steal a tap meant for whatever's under it.
        if (backButtonVisibility > 0.01f) {
            Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp)) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = backButtonVisibility
                            translationY = (1f - backButtonVisibility) * -80f
                        }
                        .background(Color.Black.copy(alpha = 0.35f * backButtonVisibility), CircleShape)
                        .testTag("artist_back"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                val successState = state as? UiState.Success
                if (successState != null) {
                    IconButton(
                        onClick = { menuOpen = true },
                        modifier = Modifier
                            .graphicsLayer {
                                alpha = backButtonVisibility
                                translationY = (1f - backButtonVisibility) * -80f
                            }
                            .background(Color.Black.copy(alpha = 0.35f * backButtonVisibility), CircleShape)
                            .testTag("artist_menu"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Artist options",
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }

    val successState = state as? UiState.Success
    if (menuOpen && successState != null) {
        val tracklist = successState.data
        val isFollowed = followedIds.contains(artistId)
        ArtistActionsSheet(
            name = tracklist.name ?: "Artist",
            imageUrl = tracklist.imageUrl,
            isFollowed = isFollowed,
            onToggleFollow = {
                if (isFollowed) {
                    followedArtists.unfollow(artistId)
                } else {
                    followedArtists.follow(artistId, tracklist.name ?: "Artist", tracklist.imageUrl, MusicSource.YOUTUBE_MUSIC)
                }
            },
            onStartRadio = {
                tracklist.tracks.firstOrNull()?.let { seed ->
                    playerViewModel.startRadio(seed) {
                        android.widget.Toast.makeText(context, "Couldn't start radio", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onShare = { context.shareArtist(artistId, tracklist.name) },
            onDismiss = { menuOpen = false },
        )
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

private fun android.content.Context.shareArtist(artistId: String, name: String?) {
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(
            android.content.Intent.EXTRA_TEXT,
            "${name.orEmpty()}\nhttps://music.youtube.com/channel/$artistId",
        )
    }
    startActivity(android.content.Intent.createChooser(intent, "Share artist"))
}

/** A full-width, near-square cover that fades into the page background at its bottom edge rather
 * than ending with a hard cut - the "art bleeds into the page" look Echo Music's artist page uses,
 * instead of a small circular avatar sitting above plain background. */
@Composable
private fun ArtistCover(imageUrl: String?) {
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
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
private fun ArtistIdentity(
    tracklist: ArtistTracklist,
    isFollowed: Boolean,
    onFollow: () -> Unit,
    onUnfollow: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
) {
    var descriptionExpanded by remember(tracklist.description) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = tracklist.name ?: "Artist",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )

        if (tracklist.subscriberCountText != null || tracklist.monthlyListenerCountText != null) {
            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                tracklist.subscriberCountText?.let {
                    InfoCapsule(
                        icon = Icons.Default.Groups,
                        text = it,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        testTag = "artist_subscriber_count",
                    )
                }
                tracklist.monthlyListenerCountText?.let {
                    InfoCapsule(
                        icon = Icons.Default.Equalizer,
                        text = it,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        testTag = "artist_listener_count",
                    )
                }
            }
        }

        if (!tracklist.description.isNullOrBlank()) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
                Text(
                    text = "About",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Start,
                )
                Text(
                    text = tracklist.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (descriptionExpanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.padding(top = 6.dp).fillMaxWidth(),
                )
                if (tracklist.description.length > 150) {
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .clickable { descriptionExpanded = !descriptionExpanded }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .testTag("artist_about_more"),
                    ) {
                        Text(
                            text = if (descriptionExpanded) "Less" else "More",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        // Filled once followed, outlined before - same read as a like button, matching the follow
        // control that already exists on the search collection sheet.
        if (isFollowed) {
            Button(
                onClick = onUnfollow,
                modifier = Modifier
                    .padding(top = 20.dp)
                    .testTag("artist_following"),
            ) { Text("Following") }
        } else {
            OutlinedButton(
                onClick = onFollow,
                modifier = Modifier
                    .padding(top = 20.dp)
                    .testTag("artist_follow"),
            ) { Text("Follow") }
        }

        if (tracklist.tracks.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PlayButton(
                    icon = Icons.Default.PlayArrow,
                    label = "Play",
                    testTag = "artist_play_all",
                    onClick = onPlayAll,
                )
                PlayButton(
                    icon = Icons.Default.Shuffle,
                    label = "Shuffle",
                    testTag = "artist_shuffle",
                    onClick = onShuffle,
                )
            }
        }
    }
}

@Composable
private fun InfoCapsule(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
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

/** Preview of everything - the top songs plus a taste of both shelves - so a first-time visitor
 * doesn't have to hunt across four tabs to see what this artist has. The Songs/Albums/Related tabs
 * hold the same data in full. */
private fun androidx.compose.foundation.lazy.LazyListScope.artistOverviewItems(
    tracklist: ArtistTracklist,
    onPlayTrack: (TrackResult, List<TrackResult>) -> Unit,
    onOpenMenu: (TrackResult) -> Unit,
    onGoToAlbum: (String) -> Unit,
    onGoToArtist: (String) -> Unit,
) {
    val topTracks = tracklist.tracks.take(5)
    if (topTracks.isEmpty() && tracklist.albums.isEmpty() && tracklist.relatedArtists.isEmpty()) {
        item(key = "overview_empty") {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "No songs found for this artist.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    if (topTracks.isNotEmpty()) {
        item(key = "songs_header") { ShelfTitle("Top songs") }
        itemsIndexed(topTracks, key = { index, _ -> "top_$index" }) { _, track ->
            TrackRow(
                title = track.title,
                artist = track.artist,
                imageUrl = track.imageUrl,
                duration = track.duration,
                onClick = { onPlayTrack(track, tracklist.tracks) },
                onOpenMenu = { onOpenMenu(track) },
            )
        }
    }

    if (tracklist.albums.isNotEmpty()) {
        item(key = "albums_header") { ShelfTitle("Albums") }
        item(key = "albums_row") {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                items(tracklist.albums, key = { it.id }) { album ->
                    ShelfCard(
                        title = album.title,
                        subtitle = album.artist,
                        imageUrl = album.imageUrl,
                        placeholder = Icons.Default.Album,
                        shape = RoundedCornerShape(10.dp),
                        onClick = { onGoToAlbum(album.id) },
                    )
                }
            }
        }
    }

    if (tracklist.relatedArtists.isNotEmpty()) {
        item(key = "related_header") { ShelfTitle("Fans might also like") }
        item(key = "related_row") {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                items(tracklist.relatedArtists, key = { it.id }) { artist ->
                    ShelfCard(
                        title = artist.name,
                        subtitle = null,
                        imageUrl = artist.imageUrl,
                        placeholder = Icons.Default.Person,
                        shape = CircleShape,
                        onClick = { onGoToArtist(artist.id) },
                    )
                }
            }
        }
    }

    item(key = "overview_bottom_padding") { Box(modifier = Modifier.padding(bottom = 140.dp)) }
}

private fun androidx.compose.foundation.lazy.LazyListScope.artistSongsItems(
    tracks: List<TrackResult>,
    onPlayTrack: (TrackResult, List<TrackResult>) -> Unit,
    onOpenMenu: (TrackResult) -> Unit,
) {
    if (tracks.isEmpty()) {
        item(key = "songs_empty") { EmptyTabMessage("No songs found for this artist.") }
        return
    }
    itemsIndexed(tracks, key = { index, _ -> "song_$index" }) { _, track ->
        TrackRow(
            title = track.title,
            artist = track.artist,
            imageUrl = track.imageUrl,
            duration = track.duration,
            onClick = { onPlayTrack(track, tracks) },
            onOpenMenu = { onOpenMenu(track) },
        )
    }
    item(key = "songs_bottom_padding") { Box(modifier = Modifier.padding(bottom = 140.dp)) }
}

private fun androidx.compose.foundation.lazy.LazyListScope.artistAlbumsItems(
    albums: List<AlbumResult>,
    onGoToAlbum: (String) -> Unit,
) {
    if (albums.isEmpty()) {
        item(key = "albums_empty") { EmptyTabMessage("No albums found for this artist.") }
        return
    }
    val rows = albums.chunked(2)
    itemsIndexed(rows, key = { index, _ -> "album_row_$index" }) { _, rowAlbums ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            rowAlbums.forEach { album ->
                Box(modifier = Modifier.weight(1f)) {
                    GridCard(
                        title = album.title,
                        subtitle = album.artist,
                        imageUrl = album.imageUrl,
                        placeholder = Icons.Default.Album,
                        shape = RoundedCornerShape(10.dp),
                        onClick = { onGoToAlbum(album.id) },
                    )
                }
            }
            if (rowAlbums.size == 1) Box(modifier = Modifier.weight(1f))
        }
    }
    item(key = "albums_bottom_padding") { Box(modifier = Modifier.padding(bottom = 140.dp)) }
}

private fun androidx.compose.foundation.lazy.LazyListScope.artistRelatedItems(
    artists: List<ArtistResult>,
    onGoToArtist: (String) -> Unit,
) {
    if (artists.isEmpty()) {
        item(key = "related_empty") { EmptyTabMessage("No related artists found.") }
        return
    }
    val rows = artists.chunked(2)
    itemsIndexed(rows, key = { index, _ -> "related_row_$index" }) { _, rowArtists ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            rowArtists.forEach { artist ->
                Box(modifier = Modifier.weight(1f)) {
                    GridCard(
                        title = artist.name,
                        subtitle = null,
                        imageUrl = artist.imageUrl,
                        placeholder = Icons.Default.Person,
                        shape = CircleShape,
                        onClick = { onGoToArtist(artist.id) },
                    )
                }
            }
            if (rowArtists.size == 1) Box(modifier = Modifier.weight(1f))
        }
    }
    item(key = "related_bottom_padding") { Box(modifier = Modifier.padding(bottom = 140.dp)) }
}

@Composable
private fun EmptyTabMessage(text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ShelfTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

/** One card in a horizontal discography/related-artist shelf - [shape] is the one thing that
 * differs between an album (rounded square) and an artist (circle), same convention the header
 * cover above already uses. */
@Composable
private fun ShelfCard(
    title: String,
    subtitle: String?,
    imageUrl: String?,
    placeholder: androidx.compose.ui.graphics.vector.ImageVector,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(120.dp),
                )
            } else {
                Icon(
                    imageVector = placeholder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (subtitle != null && subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Same visual as [ShelfCard] but sized to fill its grid cell rather than a fixed 120dp width -
 * the Albums/Related tabs' full-list grids use this, the Overview shelf previews use [ShelfCard]. */
@Composable
private fun GridCard(
    title: String,
    subtitle: String?,
    imageUrl: String?,
    placeholder: androidx.compose.ui.graphics.vector.ImageVector,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = placeholder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp),
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (subtitle != null && subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
