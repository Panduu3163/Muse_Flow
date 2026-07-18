package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

private enum class AddSongsTab { SEARCH, DOWNLOADS, LIKED }

/**
 * Full-screen picker for the "+ New Playlist" flow's add-songs step: three sources (online
 * search, Downloads, Liked Songs), multi-select by tapping a row, confirm adds every selected
 * track to the playlist being created. Has no ViewModel of its own - selection is ephemeral to
 * this one dialog instance, so plain [remember] state is enough.
 */
@Composable
fun AddSongsToPlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (List<Track>) -> Unit
) {
    var selectedTab by remember { mutableStateOf(AddSongsTab.SEARCH) }
    val selectedTracks = remember { mutableStateMapOf<String, Track>() }

    val downloadViewModel: DownloadViewModel = viewModel()
    val downloadedTracks by downloadViewModel.downloadedTracks.collectAsState()
    val likedSongsViewModel: LikedSongsViewModel = viewModel()
    val likedSongs by likedSongsViewModel.likedSongs.collectAsState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Add Songs",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("add_songs_close_button")) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                TabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = selectedTab == AddSongsTab.SEARCH,
                        onClick = { selectedTab = AddSongsTab.SEARCH },
                        text = { Text("Search") },
                        modifier = Modifier.testTag("add_songs_tab_search")
                    )
                    Tab(
                        selected = selectedTab == AddSongsTab.DOWNLOADS,
                        onClick = { selectedTab = AddSongsTab.DOWNLOADS },
                        text = { Text("Downloads") },
                        modifier = Modifier.testTag("add_songs_tab_downloads")
                    )
                    Tab(
                        selected = selectedTab == AddSongsTab.LIKED,
                        onClick = { selectedTab = AddSongsTab.LIKED },
                        text = { Text("Liked") },
                        modifier = Modifier.testTag("add_songs_tab_liked")
                    )
                }

                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    when (selectedTab) {
                        AddSongsTab.SEARCH -> SearchSourceTab(
                            selectedKeys = selectedTracks.keys,
                            onToggle = { track -> toggleSelection(selectedTracks, track) }
                        )
                        AddSongsTab.DOWNLOADS -> PickableTrackList(
                            tracks = downloadedTracks,
                            emptyMessage = "Nothing downloaded yet.",
                            selectedKeys = selectedTracks.keys,
                            onToggle = { track -> toggleSelection(selectedTracks, track) }
                        )
                        AddSongsTab.LIKED -> PickableTrackList(
                            tracks = likedSongs,
                            emptyMessage = "No liked songs yet.",
                            selectedKeys = selectedTracks.keys,
                            onToggle = { track -> toggleSelection(selectedTracks, track) }
                        )
                    }
                }

                Button(
                    onClick = { onConfirm(selectedTracks.values.toList()) },
                    enabled = selectedTracks.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .testTag("add_songs_confirm_button")
                ) {
                    Text(
                        text = if (selectedTracks.isEmpty()) "Add Songs" else "Add ${selectedTracks.size} Song${if (selectedTracks.size == 1) "" else "s"}",
                        color = Color(0xFF090A0F),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun toggleSelection(selectedTracks: MutableMap<String, Track>, track: Track) {
    val key = track.downloadKey()
    if (selectedTracks.containsKey(key)) selectedTracks.remove(key) else selectedTracks[key] = track
}

@Composable
private fun SearchSourceTab(
    selectedKeys: Set<String>,
    onToggle: (Track) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val context = LocalContext.current
    val jioSaavnProvider = remember { JioSaavnProvider() }
    val youTubeProvider = remember { YouTubeMusicProvider(context) }
    var state by remember { mutableStateOf<UiState<List<Track>>>(UiState.Success(emptyList())) }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            state = UiState.Success(emptyList())
            return@LaunchedEffect
        }
        state = UiState.Loading
        delay(350)
        state = loadAsUiState(errorMessage = "Couldn't reach JioSaavn or YouTube Music. Check your connection and try again.") {
            val (jioResults, ytResults) = coroutineScope {
                val jioDeferred = async {
                    withTimeoutOrNull(DEFAULT_LOAD_TIMEOUT_MS) {
                        runCatching { jioSaavnProvider.search(query).filter { it.directStreamUrl != null } }.getOrNull()
                    } ?: emptyList()
                }
                val ytDeferred = async {
                    withTimeoutOrNull(DEFAULT_LOAD_TIMEOUT_MS) {
                        runCatching { youTubeProvider.search(query) }.getOrNull()
                    } ?: emptyList()
                }
                jioDeferred.await() to ytDeferred.await()
            }
            mergeSearchResults(jioResults, ytResults).mapIndexed { index, result -> result.toPlayableTrack(gradientIndex = index) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search for songs to add") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("add_songs_search_field")
        )

        val currentState = state
        when {
            query.isBlank() -> EmptySearchState(title = "Search for a song", message = "Find any track to add to this playlist.")
            currentState is UiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            currentState is UiState.Error -> EmptySearchState(title = "Search failed", message = currentState.message)
            currentState is UiState.Success -> PickableTrackList(
                tracks = currentState.data,
                emptyMessage = "No results found.",
                selectedKeys = selectedKeys,
                onToggle = onToggle
            )
        }
    }
}

@Composable
private fun PickableTrackList(
    tracks: List<Track>,
    emptyMessage: String,
    selectedKeys: Set<String>,
    onToggle: (Track) -> Unit
) {
    if (tracks.isEmpty()) {
        EmptySearchState(title = "Nothing here", message = emptyMessage)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(tracks) { track ->
            val isSelected = selectedKeys.contains(track.downloadKey())
            val gradientColors = MusicData.Gradients[track.gradientIndex % MusicData.Gradients.size]

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onToggle(track) }
                    .padding(8.dp)
                    .testTag("add_songs_row_${track.title.lowercase().replace(" ", "_")}"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TrackArtwork(imageUrl = track.imageUrl, gradientColors = gradientColors, modifier = Modifier.size(48.dp)) {
                    Text("🎵", fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color(0xFF090A0F),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
