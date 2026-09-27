package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.HistoryViewModel
import com.example.PlayerViewModel
import com.example.Track
import com.example.TrackActionsViewModel
import com.example.TrackResult
import com.example.ui.component.TrackActionsHost
import com.example.ui.component.TrackRow
import com.example.ui.component.TrackSelectionHost
import com.example.ui.component.rememberTrackSelection

/**
 * Every recorded play, newest first, grouped by day. Pre-event-log history remains aggregated.
 *
 * Distinct from Library's "Recent" tile, which is a capped, sortable slice of the same table meant
 * for getting back to something quickly. This is the full record, so it's the place that can also
 * edit it - forget one track, or clear the lot.
 */
@Composable
fun HistoryScreen(
    onPlayTrack: (TrackResult, List<TrackResult>) -> Unit,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onGoToArtist: (String) -> Unit = {},
    onGoToAlbum: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel: HistoryViewModel = viewModel()
    val actionsViewModel: TrackActionsViewModel = viewModel()
    val days by viewModel.days.collectAsState()
    val query by viewModel.query.collectAsState()
    val source by viewModel.source.collectAsState()
    val selection = rememberTrackSelection()
    var confirmingClear by remember { mutableStateOf(false) }
    var selectedTrack by remember { mutableStateOf<TrackResult?>(null) }

    // One flat queue across every day, so playing something from three days ago continues into
    // what followed it rather than stopping at that day's boundary.
    val allTracks = remember(days) { days.flatMap { day -> day.entries.map { it.track } } }
    val allResults = remember(allTracks) { allTracks.map { it.asTrackResult() } }

    // Rows are drawn per day but selected against the flat list, so each day's rows need to know
    // where they start in it. Computed once here rather than per row, which would be quadratic.
    val dayOffsets = remember(days) {
        days.runningFold(0) { offset, day -> offset + day.entries.size }
    }

    Column(modifier = modifier.fillMaxSize().statusBarsPadding().testTag("history_screen")) {
        androidx.compose.material3.OutlinedTextField(
            value = query, onValueChange = { viewModel.query.value = it },
            placeholder = { Text("Search listening history") }, singleLine = true,
            shape = androidx.compose.foundation.shape.CircleShape,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Row(Modifier.padding(horizontal = 24.dp)) {
            listOf("All", "Online", "Local").forEach { label ->
                androidx.compose.material3.FilterChip(selected = source == label,
                    onClick = { viewModel.source.value = label; selection.clear() },
                    label = { Text(label) }, modifier = Modifier.padding(end = 8.dp),
                    shape = androidx.compose.foundation.shape.CircleShape)
            }
        }
        TrackSelectionHost(
            selection = selection,
            tracks = allResults,
            playerViewModel = playerViewModel,
            actionsViewModel = actionsViewModel,
        )

        LazyColumn(contentPadding = PaddingValues(bottom = 200.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("history_back")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        text = "History",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    if (days.isNotEmpty()) {
                        IconButton(
                            onClick = { confirmingClear = true },
                            modifier = Modifier.testTag("history_clear"),
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear history",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (days.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Nothing played yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            days.forEachIndexed { dayIndex, day ->
                item(key = "header_${day.label}") { DayHeader(day.label) }

                // Position-keyed: a track's identity here is its row in the table, and titles can
                // repeat across sources, so a repeated Compose key would throw.
                itemsIndexed(
                    items = day.entries,
                    key = { index, _ -> "${day.label}_$index" },
                ) { index, entry ->
                    val track = entry.track
                    val flatIndex = dayOffsets[dayIndex] + index
                    TrackRow(
                        title = track.title,
                        // The count explains why a track appears once rather than once per play.
                        artist = if (entry.playCount > 1) {
                            "${track.artist} · ${entry.playCount} plays"
                        } else {
                            track.artist
                        },
                        imageUrl = track.imageUrl,
                        duration = track.duration,
                        onClick = {
                            if (selection.active) {
                                selection.toggle(flatIndex)
                            } else {
                                // Each history row is a play event. Start with this event's
                                // track and keep later rows in order, without repeated media IDs
                                // that would otherwise make PlayerViewModel select the first
                                // occurrence of a different event.
                                onPlayTrack(track.asTrackResult(), allResults.drop(flatIndex).distinctBy { it.id })
                            }
                        },
                        onLongClick = {
                            if (selection.active) {
                                selection.toggle(flatIndex)
                            } else {
                                selection.start(flatIndex)
                            }
                        },
                        selected = selection.isSelected(flatIndex),
                        // Remove-from-history moved into the sheet (see onRemoveFromHistory below)
                        // once the row's trailing slot was needed for the menu button instead.
                        onOpenMenu = if (selection.active) null else {
                            { selectedTrack = track.asTrackResult() }
                        },
                    )
                }
            }
        }
    }

    if (confirmingClear) {
        AlertDialog(
            onDismissRequest = { confirmingClear = false },
            title = { Text("Clear history?") },
            // Says what else changes: these are views onto this table, and someone clearing
            // history would otherwise be surprised to find Home's shelves emptied too.
            text = { Text("Home's shelves and your Top 50 are built from this, so they'll empty too. This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clear()
                        confirmingClear = false
                    },
                    modifier = Modifier.testTag("history_clear_confirm"),
                ) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingClear = false }) { Text("Cancel") }
            },
        )
    }

    TrackActionsHost(
        track = selectedTrack,
        onDismiss = { selectedTrack = null },
        playerViewModel = playerViewModel,
        actionsViewModel = actionsViewModel,
        onRemoveFromHistory = { track -> viewModel.forget(track) },
        onGoToArtist = onGoToArtist,
        onGoToAlbum = onGoToAlbum,
    )
}

@Composable
private fun DayHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
    )
}
