package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import kotlinx.coroutines.launch

/**
 * TEMPORARY test/debug screen for [Provider] implementations. Lets you type a real song name,
 * search a chosen source for it, and play a real result to confirm search + stream resolution +
 * playback actually work end to end. Not part of the app's real UX - safe to delete once a
 * provider is wired into the actual player.
 */
@Composable
fun YouTubeMusicTestScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val youTubeProvider = remember { YouTubeMusicProvider() }
    val jioSaavnProvider = remember { JioSaavnProvider() }
    val providers = remember { listOf<Provider<TrackResult>>(youTubeProvider, jioSaavnProvider) }
    var activeProvider by remember { mutableStateOf(providers.first()) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val exoPlayer = remember { ExoPlayer.Builder(context).build() }
    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<TrackResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var resolvingId by remember { mutableStateOf<String?>(null) }
    var playingId by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var playingTitle by remember { mutableStateOf<String?>(null) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> statusMessage = "Playing \"${playingTitle}\""
                    Player.STATE_ENDED -> {
                        statusMessage = "Finished playing \"${playingTitle}\""
                        playingId = null
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                statusMessage = "Playback error: ${error.errorCodeName} - ${error.message}"
                playingId = null
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    fun runSearch() {
        val trimmed = query.trim()
        if (trimmed.isEmpty() || isSearching) return
        isSearching = true
        statusMessage = null
        results = emptyList()
        coroutineScope.launch {
            try {
                val found = activeProvider.search(trimmed)
                results = found
                if (found.isEmpty()) statusMessage = "No results found."
            } catch (e: Exception) {
                statusMessage = "Search failed: ${e.message}"
            } finally {
                isSearching = false
            }
        }
    }

    fun playResult(result: TrackResult) {
        resolvingId = result.id
        statusMessage = "Resolving stream URL for \"${result.title}\"..."
        coroutineScope.launch {
            try {
                val resolution = activeProvider.getStreamUrl(result)
                if (resolution == null) {
                    statusMessage = "No playable audio URL found for \"${result.title}\"."
                    return@launch
                }

                statusMessage = "Starting playback…"
                playingTitle = result.title

                // ExoPlayer's HTTP data source correctly handles Range requests, redirects, and
                // custom headers for CDN streaming.
                val dataSourceFactory = DefaultHttpDataSource.Factory().apply {
                    resolution.userAgent?.let { setUserAgent(it) }
                }
                val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(resolution.url))

                exoPlayer.setMediaSource(mediaSource)
                exoPlayer.prepare()
                exoPlayer.play()
                playingId = result.id
            } catch (e: Exception) {
                statusMessage = "Playback failed: ${e.message}"
            } finally {
                resolvingId = null
            }
        }
    }

    ThemedBackground(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 8.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("yt_test_back_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back to Settings",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Provider Test (temporary)",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // Source toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                providers.forEach { provider ->
                    val isSelected = provider === activeProvider
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable {
                                activeProvider = provider
                                results = emptyList()
                                statusMessage = null
                            }
                            .padding(vertical = 10.dp)
                            .testTag("yt_test_source_${provider.name}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = provider.name,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (isSelected) androidx.compose.ui.graphics.Color(0xFF0C0C0E)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("yt_test_query_field"),
                    placeholder = { Text("Type a real song name…") },
                    singleLine = true,
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSearch = { runSearch() }
                    ),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Search
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { runSearch() },
                    enabled = !isSearching,
                    modifier = Modifier.testTag("yt_test_search_button")
                ) {
                    Text("Search")
                }
            }

            if (isSearching) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            statusMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("yt_test_status")
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 90.dp), // Spacer for compact player
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(results, key = { it.id }) { result ->
                    val isResolving = resolvingId == result.id
                    val isPlaying = playingId == result.id
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isResolving) { playResult(result) }
                            .testTag("yt_test_result_${result.id}"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = result.title,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                Text(
                                    text = listOfNotNull(result.artist, result.duration).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            when {
                                isResolving -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                isPlaying -> Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = "Playing",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                else -> Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
