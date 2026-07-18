package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.ui.theme.MusePrimary
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

/** Which content is shown below the quick-access tile row - each is a direct tile shortcut,
 * there's no separate filter-chip layer above these. */
private enum class LibrarySection { PLAYLISTS, LOCAL, LIKED, DOWNLOADED, CACHED, TOP_50 }

private fun audioPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

@Composable
fun LibraryScreen(
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlaylistClick: (PlaylistEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedSection by remember { mutableStateOf(LibrarySection.PLAYLISTS) }

    var showNewPlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    // Set once the name step is confirmed - drives the add-songs step that follows it. Null means
    // neither dialog is showing.
    var pendingPlaylistName by remember { mutableStateOf<String?>(null) }

    val downloadViewModel: DownloadViewModel = viewModel()
    val downloadedTracks by downloadViewModel.downloadedTracks.collectAsState()

    val libraryViewModel: LibraryViewModel = viewModel()
    val playlists by libraryViewModel.playlists.collectAsState()
    val topPlayed by libraryViewModel.topPlayed.collectAsState()
    val cachedTracks by libraryViewModel.cachedTracks.collectAsState()

    val likedSongsViewModel: LikedSongsViewModel = viewModel()
    val likedSongs by likedSongsViewModel.likedSongs.collectAsState()

    val context = LocalContext.current
    var hasAudioPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, audioPermission()) == PackageManager.PERMISSION_GRANTED)
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasAudioPermission = granted }

    ThemedBackground(
        modifier = modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Library",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp)
            )

            // Quick-access tiles
            QuickAccessTiles(
                selectedSection = selectedSection,
                onSelect = { selectedSection = it }
            )

            Spacer(modifier = Modifier.height(20.dp))

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when (selectedSection) {
                    LibrarySection.PLAYLISTS -> PlaylistsSection(
                        playlists = playlists,
                        libraryViewModel = libraryViewModel,
                        onNewPlaylist = { showNewPlaylistDialog = true },
                        onPlaylistClick = onPlaylistClick
                    )
                    LibrarySection.LOCAL -> LocalSection(
                        hasPermission = hasAudioPermission,
                        onRequestPermission = { audioPermissionLauncher.launch(audioPermission()) },
                        onPlayTrack = onPlayTrack
                    )
                    LibrarySection.LIKED -> LikedSongsSection(
                        tracks = likedSongs,
                        downloadViewModel = downloadViewModel,
                        onPlayTrack = onPlayTrack
                    )
                    LibrarySection.DOWNLOADED -> TrackListSection(
                        tracks = downloadedTracks,
                        emptyMessage = "Nothing downloaded yet. Download a track from Search or Now Playing.",
                        leadingIcon = { Icon(Icons.Default.DownloadDone, contentDescription = "Downloaded", tint = MusePrimary, modifier = Modifier.size(16.dp)) },
                        trailingContent = { track -> DownloadButton(track = track, downloadViewModel = downloadViewModel) },
                        onPlayTrack = onPlayTrack
                    )
                    LibrarySection.CACHED -> TrackListSection(
                        tracks = cachedTracks,
                        emptyMessage = "Nothing cached yet. Browse Home while online to build an offline cache.",
                        leadingIcon = { Icon(Icons.Default.CloudDone, contentDescription = "Cached", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) },
                        onPlayTrack = onPlayTrack
                    )
                    LibrarySection.TOP_50 -> TrackListSection(
                        tracks = topPlayed,
                        emptyMessage = "No listening history yet. Play some tracks to build your top 50.",
                        onPlayTrack = onPlayTrack
                    )
                }
            }
        }
    }

    if (showNewPlaylistDialog) {
        AlertDialog(
            onDismissRequest = {
                showNewPlaylistDialog = false
                newPlaylistName = ""
            },
            title = {
                Text(
                    text = "Create Playlist",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            },
            text = {
                Column {
                    Text(
                        text = "Give your playlist a name.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        placeholder = { Text("My Awesome Playlist") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_new_playlist_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // Naming is step 1 - step 2 (AddSongsToPlaylistDialog below) is what
                        // actually creates the playlist, once songs are picked or skipped.
                        pendingPlaylistName = newPlaylistName
                        showNewPlaylistDialog = false
                        newPlaylistName = ""
                    },
                    enabled = newPlaylistName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("dialog_confirm_button")
                ) {
                    Text("Next", color = Color(0xFF090A0F), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showNewPlaylistDialog = false
                        newPlaylistName = ""
                    },
                    modifier = Modifier.testTag("dialog_cancel_button")
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Step 2 of playlist creation: pick songs from Search/Downloads/Liked, or skip entirely for
    // an empty playlist (same as the old single-step flow did).
    pendingPlaylistName?.let { name ->
        AddSongsToPlaylistDialog(
            onDismiss = {
                libraryViewModel.createPlaylist(name)
                pendingPlaylistName = null
            },
            onConfirm = { tracks ->
                libraryViewModel.createPlaylistWithTracks(name, tracks)
                pendingPlaylistName = null
            }
        )
    }
}

private data class QuickTile(val label: String, val icon: ImageVector, val section: LibrarySection)

// Playlists sits right before Local so the two land beside each other in the 2-column grid.
private val quickTiles = listOf(
    QuickTile("Liked", Icons.Default.Favorite, LibrarySection.LIKED),
    QuickTile("Downloaded", Icons.Default.DownloadDone, LibrarySection.DOWNLOADED),
    QuickTile("Cached", Icons.Default.CloudDone, LibrarySection.CACHED),
    QuickTile("My Top 50", Icons.Default.TrendingUp, LibrarySection.TOP_50),
    QuickTile("Playlists", Icons.Default.QueueMusic, LibrarySection.PLAYLISTS),
    QuickTile("Local", Icons.Default.Folder, LibrarySection.LOCAL)
)

@Composable
private fun QuickAccessTiles(selectedSection: LibrarySection, onSelect: (LibrarySection) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false
    ) {
        items(quickTiles) { tile ->
            val isSelected = selectedSection == tile.section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                    .clickable { onSelect(tile.section) }
                    .padding(horizontal = 14.dp, vertical = 16.dp)
                    .testTag("library_tile_${tile.label.lowercase().replace(" ", "_")}"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = tile.icon,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = tile.label,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PlaylistsSection(
    playlists: List<PlaylistEntity>,
    libraryViewModel: LibraryViewModel,
    onNewPlaylist: () -> Unit,
    onPlaylistClick: (PlaylistEntity) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Playlists",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            IconButton(onClick = onNewPlaylist, modifier = Modifier.testTag("new_playlist_button")) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Playlist",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (playlists.isEmpty()) {
            OfflineEmptyState(message = "No playlists yet. Tap + to create one.")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 90.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                items(playlists) { playlist ->
                    val gradientColors = MusicData.Gradients[(playlist.id % MusicData.Gradients.size).toInt()]
                    val tracks by libraryViewModel.tracksForPlaylist(playlist.id).collectAsState()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPlaylistClick(playlist) }
                            .testTag("library_playlist_row_${playlist.name.lowercase().replace(" ", "_")}")
                    ) {
                        PlaylistCoverArt(
                            coverImageUrl = playlist.coverImageUrl,
                            tracks = tracks,
                            fallbackGradient = gradientColors,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = playlist.name,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${tracks.size} song${if (tracks.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackListSection(
    tracks: List<Track>,
    emptyMessage: String,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable (Track) -> Unit)? = null,
    onPlayTrack: (Track, List<Track>) -> Unit
) {
    if (tracks.isEmpty()) {
        OfflineEmptyState(message = emptyMessage)
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(tracks) { track ->
                LibrarySongRow(
                    track = track,
                    queue = tracks,
                    leadingIcon = leadingIcon,
                    trailingContent = trailingContent?.let { { it(track) } },
                    onPlayTrack = onPlayTrack
                )
            }
        }
    }
}

/** Library's "Liked" section: the real, Room-backed Liked Songs list, with a "Download all"
 * action that queues every liked track for offline download in one tap - the same per-track
 * download path [DownloadButton] uses, just looped over the whole list. */
@Composable
private fun LikedSongsSection(
    tracks: List<Track>,
    downloadViewModel: DownloadViewModel,
    onPlayTrack: (Track, List<Track>) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (tracks.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Liked Songs",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                TextButton(
                    onClick = { tracks.forEach { downloadViewModel.download(it) } },
                    modifier = Modifier.testTag("download_all_liked_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DownloadForOffline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Download all", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        TrackListSection(
            tracks = tracks,
            emptyMessage = "No liked songs yet. Tap the heart on a track to like it.",
            onPlayTrack = onPlayTrack
        )
    }
}

/** Library's "Local" section: on-device audio files via [LocalAudioProvider]/MediaStore, the same
 * source and permission flow Search's "On device" mode already uses, just always unfiltered (no
 * query box here - Library browses everything on the device). */
@Composable
private fun LocalSection(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit
) {
    val context = LocalContext.current
    val localAudioProvider = remember { LocalAudioProvider(context) }
    var state by remember { mutableStateOf<UiState<List<Track>>>(UiState.Loading) }

    LaunchedEffect(hasPermission) {
        if (!hasPermission) return@LaunchedEffect
        state = UiState.Loading
        delay(150)
        state = loadAsUiState(errorMessage = "Couldn't scan audio files on this device.") {
            localAudioProvider.search("").mapIndexed { index, result -> result.toPlayableTrack(gradientIndex = index) }
        }
    }

    if (!hasPermission) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Allow access to audio files to browse music stored on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRequestPermission, modifier = Modifier.testTag("library_grant_permission")) {
                Text("Grant Permission")
            }
        }
        return
    }

    when (val current = state) {
        is UiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        is UiState.Error -> OfflineEmptyState(message = current.message)
        is UiState.Success -> TrackListSection(
            tracks = current.data,
            emptyMessage = "No local audio files found on this device.",
            onPlayTrack = onPlayTrack
        )
    }
}

@Composable
fun LibrarySongRow(
    track: Track,
    queue: List<Track>,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onPlayTrack: (Track, List<Track>) -> Unit
) {
    val gradientColors = MusicData.Gradients[track.gradientIndex % MusicData.Gradients.size]

    // Real, Room-backed liked state - same heart shown on Search results and Now Playing.
    val likedSongsViewModel: LikedSongsViewModel = viewModel()
    val likedKeys by likedSongsViewModel.likedKeys.collectAsState()
    val isLiked = likedKeys.contains(track.downloadKey())

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onPlayTrack(track, queue) }
            .padding(8.dp)
            .testTag("library_song_row_${track.title.lowercase().replace(" ", "_")}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TrackArtwork(
            imageUrl = track.imageUrl,
            gradientColors = gradientColors,
            modifier = Modifier.size(52.dp)
        ) {
            Text("🎵", fontSize = 20.sp)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leadingIcon != null) {
                    leadingIcon()
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = "${track.artist} • ${track.album}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailingContent != null) {
            trailingContent()
        } else {
            Text(
                text = track.duration,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(
            onClick = { likedSongsViewModel.toggle(track) },
            modifier = Modifier
                .size(36.dp)
                .testTag("library_like_button_${track.title.lowercase().replace(" ", "_")}")
        ) {
            Icon(
                imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (isLiked) "Remove from Liked" else "Add to Liked",
                tint = if (isLiked) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun OfflineEmptyState(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.DownloadDone,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
