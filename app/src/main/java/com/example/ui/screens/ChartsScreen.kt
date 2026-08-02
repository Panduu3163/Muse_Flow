package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.MusicSearchRouter
import com.example.PlayerViewModel
import com.example.TrackActionsViewModel
import com.example.TrackResult
import com.example.UiState
import com.example.loadAsUiState
import com.example.ui.component.MediaGridCard
import com.example.ui.component.TrackActionsHost

/**
 * YouTube Music's real charts feed - trending, then top songs, flattened to one list (see
 * [com.example.InnerTubeMusicProvider.getChartsTracks]'s doc for why it's flattened rather than
 * split into shelves). Same read-only, no-local-cache shape as [ArtistScreen]/[AlbumScreen]: this
 * always hits the network, since a charts feed is the one kind of list where a stale cached copy
 * would be actively misleading.
 *
 * Grid-of-covers layout (adapted from Library's playlist grid, [com.example.ui.component.MediaGridCard])
 * rather than a plain scrolling row list - a charts feed is a "browse the covers" experience, not
 * a queue you scan text in.
 */
@Composable
fun ChartsScreen(
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
    var state by remember { mutableStateOf<UiState<List<TrackResult>>>(UiState.Loading) }

    LaunchedEffect(Unit) {
        state = UiState.Loading
        state = loadAsUiState("Couldn't load charts.") { router.getChartsTracks() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("charts_screen"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("charts_back")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

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
                if (tracks.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No charts data right now.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 200.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    ) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                            ChartsHero(trackCount = tracks.size)
                        }
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                            Row(
                                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                PlayButton(
                                    icon = Icons.Default.PlayArrow,
                                    label = "Play",
                                    testTag = "charts_play_all",
                                    onClick = { tracks.firstOrNull()?.let { onPlayTrack(it, tracks) } },
                                )
                                PlayButton(
                                    icon = Icons.Default.Shuffle,
                                    label = "Shuffle",
                                    testTag = "charts_shuffle",
                                    onClick = {
                                        val shuffled = tracks.shuffled()
                                        shuffled.firstOrNull()?.let { onPlayTrack(it, shuffled) }
                                    },
                                )
                            }
                        }
                        items(tracks, key = { it.id }) { track ->
                            MediaGridCard(
                                title = track.title,
                                subtitle = track.artist,
                                imageUrl = track.imageUrl,
                                placeholder = Icons.Default.MusicNote,
                                onClick = { onPlayTrack(track, tracks) },
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

/** A large, coloured hero card up top - matching the "big card with an icon badge" treatment
 * Explore/Stats already use elsewhere in the app - rather than a bare text title, so a charts
 * feed reads as a designed destination instead of a plain list screen. */
@Composable
private fun ChartsHero(trackCount: Int) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(26.dp),
                )
            }
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(
                    text = "Charts",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = "$trackCount trending tracks right now",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
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
