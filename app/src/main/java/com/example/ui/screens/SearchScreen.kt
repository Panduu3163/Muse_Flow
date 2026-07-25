package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.SearchViewModel
import com.example.Track
import com.example.TrackActionsViewModel
import com.example.TrackResult
import com.example.UiState
import com.example.downloadKey
import com.example.toPlayableTrack
import com.example.ui.component.AddToPlaylistDialog
import com.example.ui.component.TrackActionsSheet
import com.example.ui.component.TrackRow

/**
 * Search over YouTube Music, with debounced type-ahead suggestions and recent-query history.
 *
 * Long-pressing a result opens its actions sheet (like / download / add to playlist); rows show
 * heart and download glyphs so their state is readable without opening the sheet.
 */
@Composable
fun SearchScreen(
    onPlayTrack: (TrackResult, List<TrackResult>) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val viewModel: SearchViewModel = viewModel()
    val query by viewModel.query.collectAsState()
    val results by viewModel.results.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val recentQueries by viewModel.recentQueries.collectAsState(initial = emptyList())
    val keyboard = LocalSoftwareKeyboardController.current

    val actionsViewModel: TrackActionsViewModel = viewModel()
    val likedKeys by actionsViewModel.likedKeys.collectAsState()
    val downloadedKeys by actionsViewModel.downloadedKeys.collectAsState()
    val downloadsInProgress by actionsViewModel.downloadsInProgress.collectAsState()
    val playlists by actionsViewModel.playlists.collectAsState()

    var selectedTrack by remember { mutableStateOf<TrackResult?>(null) }
    var pendingPlaylistTrack by remember { mutableStateOf<Track?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            placeholder = { Text("Songs, artists, or a lyric you remember") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = viewModel::clearQuery) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    viewModel.search()
                    keyboard?.hide()
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("search_field"),
        )

        // No backend chip: with the extractor picker removed from Settings there's only one
        // backend, so naming it was developer-facing noise rather than information.

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                suggestions.isNotEmpty() && query.isNotBlank() -> SuggestionList(
                    suggestions = suggestions,
                    onPick = {
                        viewModel.search(it)
                        keyboard?.hide()
                    },
                )

                query.isBlank() && recentQueries.isNotEmpty() -> RecentList(
                    recents = recentQueries,
                    onPick = {
                        viewModel.search(it)
                        keyboard?.hide()
                    },
                    onDelete = viewModel::deleteRecent,
                )

                else -> ResultsList(
                    results = results,
                    onPlayTrack = onPlayTrack,
                    likedKeys = likedKeys,
                    downloadedKeys = downloadedKeys,
                    downloadsInProgress = downloadsInProgress,
                    onLongPress = { selectedTrack = it },
                )
            }
        }
    }

    selectedTrack?.let { track ->
        val asTrack = track.toPlayableTrack(track.id.hashCode())
        val key = asTrack.downloadKey()
        TrackActionsSheet(
            title = track.title,
            artist = track.artist,
            isLiked = likedKeys.contains(key),
            isDownloaded = downloadedKeys.contains(key),
            downloadProgress = downloadsInProgress[key],
            onToggleLike = { actionsViewModel.toggleLike(asTrack) },
            onDownload = { actionsViewModel.download(asTrack) },
            onCancelDownload = { actionsViewModel.cancelDownload(asTrack) },
            onAddToPlaylist = { pendingPlaylistTrack = asTrack },
            onDismiss = { selectedTrack = null },
        )
    }

    pendingPlaylistTrack?.let { track ->
        AddToPlaylistDialog(
            playlists = playlists,
            onPick = { playlistId ->
                actionsViewModel.addToPlaylist(playlistId, track)
                pendingPlaylistTrack = null
            },
            onCreate = { name ->
                actionsViewModel.createPlaylistWith(name, track)
                pendingPlaylistTrack = null
            },
            onDismiss = { pendingPlaylistTrack = null },
        )
    }
}

@Composable
private fun ResultsList(
    results: UiState<List<TrackResult>>,
    onPlayTrack: (TrackResult, List<TrackResult>) -> Unit,
    likedKeys: Set<String>,
    downloadedKeys: Set<String>,
    downloadsInProgress: Map<String, Int>,
    onLongPress: (TrackResult) -> Unit,
) {
    when (results) {
        is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        is UiState.Error -> CenteredMessage(
            text = results.message,
            color = MaterialTheme.colorScheme.error,
        )

        is UiState.Success -> if (results.data.isEmpty()) {
            CenteredMessage("Search for something to get started.")
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
                items(results.data, key = { it.id }) { track ->
                    val key = track.toPlayableTrack(0).downloadKey()
                    TrackRow(
                        title = track.title,
                        artist = track.artist,
                        imageUrl = track.imageUrl,
                        duration = track.duration,
                        onClick = { onPlayTrack(track, results.data) },
                        onLongClick = { onLongPress(track) },
                        isLiked = likedKeys.contains(key),
                        isDownloaded = downloadedKeys.contains(key),
                        downloadProgress = downloadsInProgress[key],
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionList(suggestions: List<String>, onPick: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
        items(suggestions, key = { it }) { suggestion ->
            Row(
                // Clickable before padding, so the whole row - not just the text - responds.
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(suggestion) }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = suggestion,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun RecentList(
    recents: List<String>,
    onPick: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
        items(recents, key = { it }) { recent ->
            Row(
                // Clickable before padding so the whole row responds, not just the text. The
                // delete IconButton keeps its own handler and isn't swallowed by this.
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(recent) }
                    .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
                    .testTag("recent_query_$recent"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = recent,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 14.dp),
                )
                IconButton(onClick = { onDelete(recent) }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove from history",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(
    text: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = color, textAlign = TextAlign.Center)
    }
}
