package com.example.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.PlayerViewModel
import com.example.Track
import com.example.TrackActionsViewModel
import com.example.TrackResult
import com.example.allSelectionIndices
import com.example.atSelected
import com.example.downloadKey
import com.example.isEverythingSelected
import com.example.toPlayableTrack
import com.example.toggledSelection

/**
 * Which rows are selected on one screen.
 *
 * Held by the screen rather than a ViewModel because it is view state in the strictest sense: it
 * indexes the list currently on display, so it must die with that list. Surviving a process death
 * or a section change would leave positions pointing at different songs.
 */
@Stable
class TrackSelection {
    var indices by mutableStateOf<Set<Int>>(emptySet())
        private set

    // Deliberately independent of `indices.isNotEmpty()`. That was the original rule, and it
    // meant deselecting the last ticked row - including via "select all" toggled back to "select
    // none" - silently dropped out of selection mode and took the bar with it. Mode now only ever
    // ends through an explicit exit (the bar's close button, or an action that completes and
    // calls `clear()`), never as a side effect of the count reaching zero.
    var active: Boolean by mutableStateOf(false)
        private set

    fun isSelected(index: Int): Boolean = indices.contains(index)

    fun toggle(index: Int) {
        indices = indices.toggledSelection(index)
    }

    /** Enters selection mode with one row ticked - what the row's long-press does. */
    fun start(index: Int) {
        active = true
        indices = setOf(index)
    }

    fun setAll(count: Int) {
        indices = allSelectionIndices(count)
    }

    /** Deselects every row without leaving selection mode - "select all" toggled back off. */
    fun deselectAll() {
        indices = emptySet()
    }

    /** Exits selection mode entirely: the bar's close button, or a batch action completing. */
    fun clear() {
        active = false
        indices = emptySet()
    }
}

@Composable
fun rememberTrackSelection(): TrackSelection = remember { TrackSelection() }

/**
 * The whole multi-select surface - the contextual bar plus the add-to-playlist dialog - as one
 * composable a screen drops in where its header sits.
 *
 * Same division of labour as [TrackActionsHost], for the same reason: a screen supplies only which
 * rows are selected and the list those positions index, and every action, observed flow and
 * dialog branch lives here once. Renders nothing at all when the selection is empty, so a screen
 * can call it unconditionally.
 *
 * Every action clears the selection when it completes - a batch is a one-shot instruction, and
 * leaving rows ticked afterwards invites a double-apply. Add-to-playlist is the exception until
 * its dialog resolves, because this composable is what keeps that dialog on screen.
 */
@Composable
fun TrackSelectionHost(
    selection: TrackSelection,
    /** The list the selection's positions index - the same list the rows are drawn from. */
    tracks: List<TrackResult>,
    playerViewModel: PlayerViewModel,
    actionsViewModel: TrackActionsViewModel,
    /** Supplied only by a screen that owns the tracks' membership - a playlist's detail screen. */
    onRemoveFromPlaylist: ((List<Track>) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val likedKeys by actionsViewModel.likedKeys.collectAsState()
    val downloadedKeys by actionsViewModel.downloadedKeys.collectAsState()
    val playlists by actionsViewModel.playlists.collectAsState()

    // Outlives the selection it came from: picking "Add to playlist" leaves the bar up, but
    // creating a playlist clears the selection the moment it is applied.
    var pendingPlaylistTracks by remember { mutableStateOf<List<Track>?>(null) }

    if (selection.active) {
        val selected = tracks.atSelected(selection.indices)
        val selectedTracks = selected.map { it.toPlayableTrack(it.id.hashCode()) }
        val keys = selectedTracks.map { it.downloadKey() }
        val downloaded = selectedTracks.filterIndexed { index, _ ->
            downloadedKeys.contains(keys[index])
        }

        // A mixed selection likes everything rather than unliking, so the action is only
        // subtractive when there is nothing left to add.
        val allLiked = keys.isNotEmpty() && keys.all(likedKeys::contains)

        BackHandler(enabled = true) { selection.clear() }

        TrackSelectionBar(
            selectedCount = selection.indices.size,
            allSelected = isEverythingSelected(selection.indices, tracks.size),
            allLiked = allLiked,
            onClearSelection = selection::clear,
            onToggleSelectAll = {
                if (isEverythingSelected(selection.indices, tracks.size)) {
                    selection.deselectAll()
                } else {
                    selection.setAll(tracks.size)
                }
            },
            onPlayNext = {
                playerViewModel.playNext(selected)
                selection.clear()
            },
            onAddToQueue = {
                playerViewModel.addToQueue(selected)
                selection.clear()
            },
            onAddToPlaylist = { pendingPlaylistTracks = selectedTracks },
            onToggleLike = {
                actionsViewModel.setLiked(selectedTracks, liked = !allLiked)
                selection.clear()
            },
            onDownload = {
                // Already-downloaded tracks are dropped rather than re-fetched: a selection is
                // usually "everything in this list", and re-downloading what is already on disk is
                // never what that means.
                actionsViewModel.downloadAll(
                    selectedTracks.filterIndexed { index, _ -> !downloadedKeys.contains(keys[index]) },
                )
                selection.clear()
            },
            onDeleteDownloads = if (downloaded.isNotEmpty()) {
                {
                    actionsViewModel.deleteDownloads(downloaded)
                    selection.clear()
                }
            } else null,
            onRemoveFromPlaylist = onRemoveFromPlaylist?.let { remove ->
                {
                    remove(selectedTracks)
                    selection.clear()
                }
            },
            modifier = modifier,
        )
    }

    pendingPlaylistTracks?.let { pending ->
        AddToPlaylistDialog(
            playlists = playlists,
            onPick = { playlistId ->
                actionsViewModel.addToPlaylist(playlistId, pending)
                pendingPlaylistTracks = null
                selection.clear()
            },
            onCreate = { name ->
                actionsViewModel.createPlaylistWith(name, pending)
                pendingPlaylistTracks = null
                selection.clear()
            },
            // Cancelling leaves the selection alone - the user backed out of one action, not out
            // of the selection they built to run it.
            onDismiss = { pendingPlaylistTracks = null },
        )
    }
}
