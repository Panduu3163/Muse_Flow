package com.example.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.AppSettingsViewModel
import com.example.GridCellSize
import com.example.HomeViewModel
import com.example.PlaylistEntity
import com.example.PlaylistResult
import com.example.Track
import com.example.TrackResult
import com.example.UiState
import com.example.MusicSource
import com.example.ui.component.MediaCard
import com.example.ui.component.PlaylistCover
import com.example.ui.component.ShimmerBlock
import com.example.ui.utils.bounceClick
import com.example.ui.utils.slowMarquee

/**
 * Big-cover-art auto-advancing hero, replacing the old "Moods & genres" / "Fresh drops" pills.
 * Seeded from [HomeViewModel.dailyDiscover] - radio tracks grown from the user's own liked songs -
 * so it reads as "your taste" rather than a generic chart. No card/border/elevation behind the
 * artwork: a vignette brush fades the image into [MaterialTheme.colorScheme.background] at the
 * edges (same colour the screen itself paints), so the hero reads as part of the page rather than
 * a tile floating on it.
 */
@Composable
private fun HeroCarousel(tracks: List<Track>, onPlay: (Track, List<Track>) -> Unit) {
    if (tracks.isEmpty()) return
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { tracks.size })
    LaunchedEffect(tracks.size) {
        while (true) {
            kotlinx.coroutines.delay(4500)
            val next = (pagerState.currentPage + 1) % tracks.size
            pagerState.animateScrollToPage(next)
        }
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Text(
            text = "Made for you",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 10.dp),
        )
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 28.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth().height(230.dp),
        ) { page ->
            val track = tracks[page]
            HeroCard(track = track, onClick = { onPlay(track, tracks) })
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center) {
            repeat(tracks.size) { i ->
                val active = pagerState.currentPage == i
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(if (active) 8.dp else 6.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(
                            if (active) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        ),
                )
            }
        }
    }
}

@Composable
private fun HeroCard(track: Track, onClick: () -> Unit) {
    val background = MaterialTheme.colorScheme.background
    Box(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .clip(RoundedCornerShape(28.dp))
            .bounceClick(onClick = onClick),
    ) {
        AsyncImage(
            model = track.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        // Fades into the page's own background colour toward the bottom - the same treatment
        // NineGridCover/ArtistCover use for their own covers, so this hero reads consistently with
        // them. A *radial* vignette was here before: on a card this wide, radialGradient's default
        // radius is the smaller of width/height (the card's own height), so the transparent centre
        // was a small circle and everything past it - most of a wide card's left/right - was
        // already clamped to solid background colour. A vertical fade doesn't have that failure
        // mode regardless of aspect ratio.
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(0f to Color.Transparent, 0.45f to Color.Transparent, 1f to background),
                    ),
                ),
        )
        // Separate bottom-only scrim so the title/artist stay legible over bright artwork.
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)))),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, end = 84.dp, bottom = 18.dp),
        ) {
            Text(
                text = "Recommended for you",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
            Text(
                text = track.title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().slowMarquee(),
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().slowMarquee(),
            )
        }
        // Purely visual - no click modifier of its own. It used to carry a second, independent
        // bounceClick on top of the whole card's own, both calling the same onPlay: two
        // overlapping pointer-input gesture detectors that could both fire off one tap, racing
        // two concurrent PlayerViewModel.play() calls against the same MediaController and
        // occasionally leaving playback in a half-started state. The card's own click already
        // covers this entire area.
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .size(52.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.Black)
        }
    }
}

/**
 * Home: local sections first (recently played, most played, playlists), then shelves fetched
 * through the selected extractor and cached to Room for offline use.
 */
@Composable
fun HomeScreen(
    onPlayTrack: (TrackResult, List<TrackResult>) -> Unit = { _, _ -> },
    onOpenPlaylist: (Long) -> Unit = {},
    onOpenRemotePlaylist: (String, String, String, String?) -> Unit = { _, _, _, _ -> },
    onOpenHistory: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTogether: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: HomeViewModel = viewModel()
    val settingsViewModel: AppSettingsViewModel = viewModel()
    val settings by settingsViewModel.state.collectAsState()

    // Card width follows the user's grid-size preference; the shelves are horizontal carousels, so
    // this is what "grid size" actually means here.
    val cardSize = when (settings.gridCellSize) {
        GridCellSize.Small -> 112.dp
        GridCellSize.Medium -> 140.dp
        GridCellSize.Large -> 172.dp
    }
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsState()
    val likedSongs by viewModel.likedSongs.collectAsState()
    val topPlayed by viewModel.topPlayed.collectAsState()
    val shelves by viewModel.shelves.collectAsState()
    val forgottenFavourites by viewModel.forgottenFavourites.collectAsState()
    val shelfSpecs by viewModel.shelfSpecs.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val dailyDiscover by viewModel.dailyDiscover.collectAsState()
    val communityPlaylists by viewModel.communityPlaylists.collectAsState()

    // Home's local sections hold Track (from Room); playback takes TrackResult, so they're mapped
    // at the point of the tap rather than storing two parallel shapes everywhere.
    val playTracks: (Track, List<Track>) -> Unit = { track, queue ->
        onPlayTrack(track.asTrackResult(), queue.map { it.asTrackResult() })
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("home_screen"),
        contentPadding = PaddingValues(top = 24.dp, bottom = 200.dp),
    ) {
        item {
          Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "MuseFlow",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            val iconBackground = Modifier.size(40.dp)
            androidx.compose.material3.IconButton(onClick = onOpenHistory, modifier = iconBackground) { Icon(Icons.Default.History, "History", tint = Color.White) }
            androidx.compose.material3.IconButton(onClick = onOpenTogether, modifier = iconBackground) { Icon(Icons.Default.Group, "Listen together", tint = Color.White) }
            androidx.compose.material3.IconButton(onClick = onOpenStats, modifier = iconBackground) { Icon(Icons.Default.BarChart, "Stats", tint = Color.White) }
            androidx.compose.material3.IconButton(onClick = onOpenSettings, modifier = iconBackground) { Icon(Icons.Default.Settings, "Settings", tint = Color.White) }
          }
        }
        // Falls back to locally-available data (no network needed) whenever dailyDiscover has
        // nothing yet - not just while it's still loading, but genuinely offline too. dailyDiscover
        // itself needs live radio/search requests, so being offline (or a slow/failed fetch) used
        // to mean the hero simply never appeared, even though there was perfectly good local data
        // (liked songs, recently played) to show instead.
        val heroTracks = ((dailyDiscover as? UiState.Success)?.data?.takeIf { it.isNotEmpty() }
            ?: likedSongs.takeIf { it.isNotEmpty() }
            ?: recentlyPlayed).take(6)
        if (heroTracks.isNotEmpty()) {
            item(key = "hero") {
                HeroCarousel(tracks = heroTracks, onPlay = playTracks)
            }
        }

        if (recentlyPlayed.isNotEmpty()) {
            item(key = "recently_played") {
                Shelf(title = "Recently played", modifier = Modifier.animateItem()) {
                    TrackCarousel(recentlyPlayed, cardSize) { playTracks(it, recentlyPlayed) }
                }
            }
        }

        if (topPlayed.isNotEmpty()) {
            item(key = "on_repeat") {
                Shelf(title = "On repeat", modifier = Modifier.animateItem()) {
                    TrackCarousel(topPlayed, cardSize) { playTracks(it, topPlayed) }
                }
            }
        }

        if (forgottenFavourites.isNotEmpty()) {
            item(key = "forgotten_favourites") {
                Shelf(title = "Forgotten favourites", modifier = Modifier.animateItem()) {
                    TrackCarousel(forgottenFavourites, cardSize) {
                        playTracks(it, forgottenFavourites)
                    }
                }
            }
        }

        if (playlists.isNotEmpty()) {
            item(key = "your_playlists") {
                Shelf(title = "Your playlists", modifier = Modifier.animateItem()) {
                    PlaylistCarousel(playlists, cardSize, viewModel) { onOpenPlaylist(it.id) }
                }
            }
        }

        when (dailyDiscover) {
            is UiState.Success -> {
                val discovered = (dailyDiscover as UiState.Success<List<Track>>).data
                if (discovered.isNotEmpty()) {
                    item(key = "daily_discover") {
                        Shelf(title = "Daily Discover", modifier = Modifier.animateItem()) {
                            TrackCarousel(discovered, cardSize) { playTracks(it, discovered) }
                        }
                    }
                }
            }
            is UiState.Loading -> item(key = "daily_discover") {
                Shelf(title = "Daily Discover", modifier = Modifier.animateItem()) { ShelfSkeleton() }
            }
            is UiState.Error -> Unit
        }

        when (communityPlaylists) {
            is UiState.Success -> {
                val results = (communityPlaylists as UiState.Success<List<PlaylistResult>>).data
                if (results.isNotEmpty()) {
                    item(key = "from_the_community") {
                        Shelf(title = "From the community", modifier = Modifier.animateItem()) {
                            RemotePlaylistCarousel(results, cardSize) { playlist ->
                                onOpenRemotePlaylist(playlist.id, playlist.title, playlist.subtitle, playlist.imageUrl)
                            }
                        }
                    }
                }
            }
            is UiState.Loading -> item(key = "from_the_community") {
                Shelf(title = "From the community", modifier = Modifier.animateItem()) { ShelfSkeleton() }
            }
            is UiState.Error -> Unit
        }

        items(shelfSpecs, key = { it.title }) { spec ->
            Shelf(title = spec.title, modifier = Modifier.animateItem()) {
                Crossfade(targetState = shelves[spec.title] ?: UiState.Loading, label = "shelf_${spec.title}") { state ->
                    when (state) {
                        is UiState.Loading -> ShelfSkeleton()
                        is UiState.Error -> ShelfMessage(state.message)
                        is UiState.Success -> if (state.data.isEmpty()) {
                            ShelfMessage("Nothing here yet.")
                        } else {
                            TrackCarousel(state.data, cardSize) { playTracks(it, state.data) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Shelf(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier = modifier.padding(top = 20.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
        )
        content()
    }
}

@Composable
private fun TrackCarousel(
    tracks: List<Track>,
    cardSize: androidx.compose.ui.unit.Dp,
    onPlay: (Track) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        // Position-keyed, not "title|artist" - a shelf (esp. "Forgotten favourites"/a personalized
        // search-based shelf) can legitimately contain the same title+artist twice (e.g. a single
        // vs. an album cut of the same song), which crashed with "Key ... was already used" under
        // the title+artist key. Every other list in the app with this same duplicate-content risk
        // (Library, Search) already keys by position for exactly this reason.
        itemsIndexed(tracks, key = { index, _ -> index }) { _, track ->
            MediaCard(
                title = track.title,
                subtitle = track.artist,
                imageUrl = track.imageUrl,
                placeholder = Icons.Default.MusicNote,
                onClick = { onPlay(track) },
                size = cardSize,
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun PlaylistCarousel(
    playlists: List<PlaylistEntity>,
    cardSize: androidx.compose.ui.unit.Dp,
    viewModel: HomeViewModel,
    onOpen: (PlaylistEntity) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(playlists, key = { it.id }) { playlist ->
            val tracks by viewModel.tracksForPlaylist(playlist.id).collectAsState()
            MediaCard(
                title = playlist.name,
                subtitle = null,
                imageUrl = playlist.coverImageUrl,
                placeholder = Icons.Default.MusicNote,
                onClick = { onOpen(playlist) },
                size = cardSize,
                modifier = Modifier.animateItem(),
                artwork = { artworkModifier ->
                    PlaylistCover(
                        tracks = tracks,
                        fallbackCoverUrl = playlist.coverImageUrl,
                        customCoverUri = playlist.customCoverUri,
                        modifier = artworkModifier,
                    )
                },
            )
        }
    }
}

@Composable
private fun RemotePlaylistCarousel(
    playlists: List<PlaylistResult>,
    cardSize: androidx.compose.ui.unit.Dp,
    onOpen: (PlaylistResult) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(playlists, key = { it.id }) { playlist ->
            MediaCard(
                title = playlist.title,
                subtitle = playlist.subtitle,
                imageUrl = playlist.imageUrl,
                placeholder = Icons.Default.MusicNote,
                onClick = { onOpen(playlist) },
                size = cardSize,
                modifier = Modifier.animateItem(),
            )
        }
    }
}

/** Placeholder cards while a shelf loads - keeps the row's height stable so the list doesn't
 * jump when real content arrives. */
@Composable
private fun ShelfSkeleton() {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(4) { index ->
            Column(modifier = Modifier.width(140.dp)) {
                ShimmerBlock(
                    modifier = Modifier.size(140.dp),
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.height(8.dp))
                ShimmerBlock(
                    modifier = Modifier.fillMaxWidth().height(12.dp),
                    shape = RoundedCornerShape(4.dp),
                )
            }
        }
    }
}

@Composable
private fun ShelfMessage(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}


/** Local [Track]s already carry everything playback needs; this just restates them in the shape
 * the player takes. A track with a real [Track.streamUrl] (a download) keeps it, so it plays from
 * disk instead of being re-resolved. */
internal fun Track.asTrackResult(): TrackResult = TrackResult(
    id = sourceId ?: "${title}|${artist}",
    title = title,
    artist = artist,
    duration = duration,
    source = album,
    sourceType = sourceType ?: MusicSource.YOUTUBE_MUSIC,
    directStreamUrl = streamUrl,
    imageUrl = imageUrl,
    albumId = albumId,
    artistId = artistId,
)
