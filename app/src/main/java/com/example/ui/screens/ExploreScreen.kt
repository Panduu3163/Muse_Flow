package com.example.ui.screens
import androidx.compose.runtime.collectAsState

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.MoodGenreCategory
import com.example.MoodGenreTile
import com.example.MusicSearchRouter
import com.example.UiState
import com.example.loadAsUiState

/**
 * YouTube Music's mood/genre tiles, grouped into categories (e.g. "Moods", "Genres") - tapping one
 * opens [BrowseScreen] on that tile's own browseId+params. Same no-local-cache, always-hits-network
 * shape as every other browse screen added this session.
 */
@Composable
fun ExploreScreen(
    onOpenBrowse: (browseId: String, params: String?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    embedded: Boolean = false,
) {
    val context = LocalContext.current
    val router = remember { MusicSearchRouter(context) }
    val online by remember(context) { com.example.observeOnline(context.applicationContext) }
        .collectAsState(initial = com.example.isOnline(context))
    var state by remember { mutableStateOf<UiState<List<MoodGenreCategory>>>(UiState.Loading) }

    LaunchedEffect(online) {
        if (!online) {
            state = UiState.Error("You're offline. Connect to browse moods and genres.")
            return@LaunchedEffect
        }
        state = UiState.Loading
        state = loadAsUiState("Couldn't load Explore.") { router.getMoodAndGenres() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .then(if (embedded) Modifier else Modifier.statusBarsPadding())
            .testTag("explore_screen"),
    ) {
        if (!embedded) Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("explore_back")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = "Explore",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
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
                val categories = current.data
                if (categories.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Nothing to explore right now.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 140.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        categories.forEach { category ->
                            item(key = "title_${category.title}", span = { GridItemSpan(2) }) {
                                Text(
                                    text = category.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                                )
                            }
                            items(category.tiles, key = { "${category.title}_${it.browseId}_${it.title}" }) { tile ->
                                MoodGenreTileCard(
                                    tile = tile,
                                    onClick = { onOpenBrowse(tile.browseId, tile.params) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The source gives each tile only a flat [MoodGenreTile.colorArgb], no per-tile artwork, so there
 * is no real photo to put in the corner the way a real YouTube Music "Browse all" grid does.
 * Rather than a photo, this uses a solid, shadowed, rotated square with a generic note glyph -
 * legible and clearly present (an earlier soft blurred-blob version read as barely-there), reading
 * as "a little cover-art card tucked in the corner" the way the reference screenshot's real photos
 * do, without fabricating content the source never provided.
 */
@Composable
fun MoodGenreTileCard(tile: MoodGenreTile, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val base = Color(tile.colorArgb.toInt())
    val deep = lerp(base, Color.Black, 0.55f)
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .padding(6.dp)
            .fillMaxWidth()
            .height(124.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(base, deep), start = Offset.Zero, end = Offset.Infinite))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 6.dp, y = 6.dp)
                .size(72.dp)
                .graphicsLayer { rotationZ = 18f }
                .shadow(elevation = 10.dp, shape = RoundedCornerShape(14.dp), clip = false)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.20f))
                .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(30.dp),
            )
        }
        Text(
            text = tile.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 14.dp, top = 12.dp, end = 64.dp),
        )
    }
}

/**
 * A short taste of [ExploreScreen]'s content for Search's landing state, headed by a "Browse all"
 * row that opens the real, full [ExploreScreen] - replacing the previous behaviour of embedding
 * the *entire* mood/genre grid inline in Search, which pushed everything else in that tab down
 * behind a long list nobody asked to see there.
 */
@Composable
fun ExplorePreview(
    onOpenBrowse: (browseId: String, params: String?) -> Unit,
    onBrowseAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val router = remember { MusicSearchRouter(context) }
    var state by remember { mutableStateOf<UiState<List<MoodGenreCategory>>>(UiState.Loading) }

    LaunchedEffect(Unit) {
        state = loadAsUiState("Couldn't load Explore.") { router.getMoodAndGenres() }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onBrowseAll)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Browse all",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val tiles = (state as? UiState.Success)?.data?.flatMap { it.tiles }?.take(6).orEmpty()
        tiles.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                row.forEach { tile ->
                    MoodGenreTileCard(
                        tile = tile,
                        onClick = { onOpenBrowse(tile.browseId, tile.params) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
