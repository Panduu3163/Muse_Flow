package com.example.ui.screens

import com.example.ui.utils.MicroIconButton
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
 * YouTube Music's own "New releases" shelf, fetched live - same no-local-cache shape as
 * [ChartsScreen]/[ArtistScreen]/[AlbumScreen]. Renders as a playable grid of covers rather than an
 * album list: the live page no longer serves a distinct "new albums" shelf for an unauthenticated
 * client (see [com.example.InnerTubeMusicProvider.getNewReleases]'s doc), only individual songs -
 * and a cover grid (adapted from Library's playlist grid) reads better for "new releases" than a
 * flat text list either way.
 */
@Composable
fun NewReleasesScreen(
    onPlayTrack: (TrackResult, List<TrackResult>) -> Unit,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onGoToArtist: (String) -> Unit = {},
    onGoToAlbum: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val router = remember { MusicSearchRouter(context) }
    val actionsViewModel: TrackActionsViewModel = viewModel()
    var selectedTrack by remember { mutableStateOf<TrackResult?>(null) }
    var state by remember { mutableStateOf<UiState<List<TrackResult>>>(
        com.example.DiscoveryPageCache.get("new_releases")?.let { UiState.Success(it) } ?: UiState.Loading) }
    var retryAttempt by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var refreshError by remember { mutableStateOf<String?>(null) }
    val gridState = com.example.rememberRestoredLazyGridState("new_releases", state is UiState.Success)

    LaunchedEffect(retryAttempt) {
        refreshError = null
        if (state !is UiState.Success) state = UiState.Loading
        val fresh = loadAsUiState("Couldn't load new releases.") { router.getNewReleases() }
        if (fresh is UiState.Success) {
            com.example.DiscoveryPageCache.put("new_releases", fresh.data)
            state = fresh
        } else if (state !is UiState.Success) state = fresh
        else if (fresh is UiState.Error) refreshError = "Showing saved results. " + fresh.message
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("new_releases_screen"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            MicroIconButton(onClick = onBack, modifier = Modifier.testTag("new_releases_back")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text("Fresh drops", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        }

        when (val current = state) {
            is UiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            is UiState.Error -> Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                com.example.ui.component.FeedbackMessage(current.message, isError = true, onRetry = { retryAttempt++ })
            }

            is UiState.Success -> {
                val tracks = current.data
                if (tracks.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No new releases right now.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(if (androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f) 180.dp else 156.dp),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 200.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    ) {
                        if (refreshError != null) item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            com.example.ui.component.FeedbackMessage(refreshError!!, onRetry = { retryAttempt++ })
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            NewReleasesHero(trackCount = tracks.size)
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Row(
                                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                PlayButton(
                                    icon = Icons.Default.PlayArrow,
                                    label = "Play",
                                    testTag = "new_releases_play_all",
                                    onClick = { tracks.firstOrNull()?.let { onPlayTrack(it, tracks) } },
                                )
                                PlayButton(
                                    icon = Icons.Default.Shuffle,
                                    label = "Shuffle",
                                    testTag = "new_releases_shuffle",
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

@Composable
private fun NewReleasesHero(trackCount: Int) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.NewReleases,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiary,
                    modifier = Modifier.size(26.dp),
                )
            }
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(
                    text = "New releases",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = "$trackCount fresh tracks",
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
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
