package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.AppSettingsViewModel
import coil.compose.AsyncImage
import com.example.LibrarySection
import com.example.LibraryViewModel
import com.example.PlaylistEntity
import com.example.PlaylistSortOption
import com.example.TrackSortOption
import com.example.sortedByLibraryOption
import com.example.Track
import com.example.TrackResult
import com.example.ui.component.LibrarySortHeader
import com.example.ui.component.TrackRow

/**
 * Library: playlists, liked songs, downloads, most played and recently played - every section
 * backed by a repository that survived the frontend wipe, so this screen works fully offline.
 */
@Composable
fun LibraryScreen(
    onPlayTrack: (TrackResult, List<TrackResult>) -> Unit = { _, _ -> },
    onOpenPlaylist: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel: LibraryViewModel = viewModel()
    val settingsViewModel: AppSettingsViewModel = viewModel()
    val settings by settingsViewModel.state.collectAsState()
    val section by viewModel.section.collectAsState()

    // Playlists is never hidden - with every optional section off, an empty chip row would leave
    // Library with no way back to anything.
    val visibleSections = remember(settings) {
        LibrarySection.entries.filter { entry ->
            when (entry) {
                LibrarySection.Playlists -> true
                LibrarySection.Liked -> settings.showLikedPlaylist
                LibrarySection.Downloads -> settings.showDownloadedPlaylist
                LibrarySection.TopPlayed -> settings.showTopPlaylist
                LibrarySection.Recent -> settings.showCachedPlaylist
            }
        }
    }

    // If the selected section was just hidden, fall back rather than showing a blank body.
    LaunchedEffect(visibleSections, section) {
        if (section !in visibleSections) viewModel.selectSection(LibrarySection.Playlists)
    }
    val playlists by viewModel.playlists.collectAsState()
    val liked by viewModel.likedSongs.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val topPlayed by viewModel.topPlayed.collectAsState()
    val recent by viewModel.recentlyPlayed.collectAsState()
    val trackSort by viewModel.trackSort.collectAsState()
    val playlistSort by viewModel.playlistSort.collectAsState()
    val ascending by viewModel.ascending.collectAsState()
    val gridView by viewModel.gridView.collectAsState()

    val play: (Track, List<Track>) -> Unit = { track, queue ->
        onPlayTrack(track.asTrackResult(), queue.map { it.asTrackResult() })
    }

    Column(modifier = modifier.fillMaxSize().testTag("library_screen")) {
        Text(
            text = "Library",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 12.dp),
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            items(visibleSections, key = { it.name }) { entry ->
                FilterChip(
                    selected = section == entry,
                    onClick = { viewModel.selectSection(entry) },
                    label = { Text(entry.label) },
                    modifier = Modifier.testTag("library_chip_${entry.name.lowercase()}"),
                )
            }
        }

        // One sort header drives whichever section is showing; playlists sort by their own enum
        // since "sort by artist" is meaningless for a playlist.
        if (section == LibrarySection.Playlists) {
            LibrarySortHeader(
                options = PlaylistSortOption.entries.toList(),
                selected = playlistSort,
                onSelect = viewModel::setPlaylistSort,
                ascending = ascending,
                onToggleDirection = viewModel::toggleDirection,
                optionLabel = { it.label },
                countLabel = "${playlists.size} playlist${if (playlists.size == 1) "" else "s"}",
            )
        } else {
            val count = when (section) {
                LibrarySection.Liked -> liked.size
                LibrarySection.Downloads -> downloads.size
                LibrarySection.TopPlayed -> topPlayed.size
                else -> recent.size
            }
            LibrarySortHeader(
                options = TrackSortOption.entries.toList(),
                selected = trackSort,
                onSelect = viewModel::setTrackSort,
                ascending = ascending,
                onToggleDirection = viewModel::toggleDirection,
                optionLabel = { it.label },
                countLabel = "$count song${if (count == 1) "" else "s"}",
                isGrid = gridView,
                onToggleGrid = viewModel::toggleGridView,
            )
        }

        when (section) {
            LibrarySection.Playlists -> PlaylistList(
                playlists.sortedByLibraryOption(playlistSort, ascending),
                onOpenPlaylist,
            )
            LibrarySection.Liked -> TrackList(viewModel.sortTracks(liked), "Nothing liked yet.", gridView, play)
            LibrarySection.Downloads -> TrackList(viewModel.sortTracks(downloads), "No downloads yet.", gridView, play)
            LibrarySection.TopPlayed -> TrackList(viewModel.sortTracks(topPlayed), "Play something and it'll show up here.", gridView, play)
            LibrarySection.Recent -> TrackList(viewModel.sortTracks(recent), "Nothing played yet.", gridView, play)
        }
    }
}

@Composable
private fun TrackList(
    tracks: List<Track>,
    emptyMessage: String,
    gridView: Boolean,
    onPlay: (Track, List<Track>) -> Unit,
) {
    if (tracks.isEmpty()) {
        EmptyState(emptyMessage)
        return
    }

    if (gridView) {
        // Adaptive rather than a fixed column count, so the grid stays sensible in landscape and
        // at every display-density setting instead of stretching cells.
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 140.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(tracks, key = { "${it.title}|${it.artist}" }) { track ->
                TrackGridCell(track = track, onClick = { onPlay(track, tracks) })
            }
        }
        return
    }

    LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 140.dp)) {
        items(tracks, key = { "${it.title}|${it.artist}" }) { track ->
            TrackRow(
                title = track.title,
                artist = track.artist,
                imageUrl = track.imageUrl,
                duration = track.duration,
                onClick = { onPlay(track, tracks) },
            )
        }
    }
}

/** Square-artwork cell used by the grid view. */
@Composable
private fun TrackGridCell(track: Track, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag("track_cell_${track.title.lowercase().replace(" ", "_")}"),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            if (track.imageUrl != null) {
                AsyncImage(
                    model = track.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = track.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlaylistList(playlists: List<PlaylistEntity>, onOpenPlaylist: (Long) -> Unit) {
    if (playlists.isEmpty()) {
        EmptyState("No playlists yet. Long-press any song and choose \"Add to playlist\".")
        return
    }
    LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 140.dp)) {
        items(playlists, key = { it.id }) { playlist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenPlaylist(playlist.id) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
