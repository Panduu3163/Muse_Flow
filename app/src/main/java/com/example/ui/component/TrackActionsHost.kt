package com.example.ui.component

import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.MusicSource
import com.example.PlayerViewModel
import com.example.Track
import com.example.TrackActionsViewModel
import com.example.TrackResult
import com.example.downloadKey
import com.example.hasRealVideoId
import com.example.toPlayableTrack

/**
 * The whole track context menu - the actions sheet plus the add-to-playlist dialog it opens - as
 * one composable a screen drops in.
 *
 * Every list that shows a song needs the identical wiring: four observed repository flows, the
 * key derivation, the queue calls, the dialog and its create-playlist branch. That was written out
 * once in Search and nowhere else, which is why long-pressing a row in Library, History, a playlist
 * or a collection sheet did nothing at all. Centralising it means a new action reaches all five
 * surfaces at once instead of being wired five times and forgotten in three.
 *
 * The screen owns only [track] - which row is selected - because that is the one piece of this
 * that is genuinely per-screen.
 */
@Composable
fun TrackActionsHost(
    track: TrackResult?,
    onDismiss: () -> Unit,
    playerViewModel: PlayerViewModel,
    actionsViewModel: TrackActionsViewModel,
    /**
     * Supplied only by a screen that owns the track's membership - a playlist's detail screen.
     * Null everywhere else, which hides the action rather than showing one that cannot work.
     */
    onRemoveFromPlaylist: ((Track) -> Unit)? = null,
    /** Supplied only by the History screen - the one surface where a track's row identity is a
     * history entry, not a library membership. */
    onRemoveFromHistory: ((Track) -> Unit)? = null,
    /** Navigates to the artist/album screen for the given browseId. Absent on any screen that
     * hasn't been wired to a `NavHostController` (there are none today, but the type stays
     * nullable for the same reason every other optional action here is). Gated per-track on
     * [com.example.TrackResult.artistId]/[com.example.TrackResult.albumId] being non-null, not on
     * this being null - a stored track with no id genuinely has nowhere to go. */
    onGoToArtist: ((String) -> Unit)? = null,
    onGoToAlbum: ((String) -> Unit)? = null,
    /** False only from Now Playing, which already has its own dedicated queue/like/download
     * controls elsewhere on screen - see [TrackActionsSheet]'s own doc on each flag. */
    showQueueActions: Boolean = true,
    showLikeAction: Boolean = true,
    showDownloadAction: Boolean = true,
    /** Present only from Now Playing's menu, folding the lyrics panel's own former menu into this
     * one - see [TrackActionsSheet]. */
    onCopyLyrics: (() -> Unit)? = null,
    onSearchLyricsOnline: (() -> Unit)? = null,
    onEqualizer: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val likedKeys by actionsViewModel.likedKeys.collectAsState()
    val downloadedKeys by actionsViewModel.downloadedKeys.collectAsState()
    val downloadsInProgress by actionsViewModel.downloadsInProgress.collectAsState()
    val playlists by actionsViewModel.playlists.collectAsState()

    // Held outside the `track != null` branch on purpose: choosing "Add to playlist" closes the
    // sheet, so this state has to outlive the selection that created it.
    var pendingPlaylistTrack by remember { mutableStateOf<Track?>(null) }
    // Same reasoning: Details opens after the sheet dismisses, not inside it.
    var detailsTrack by remember { mutableStateOf<TrackResult?>(null) }
    var sleepDialog by remember { mutableStateOf(false) }
    var ambient by remember { mutableStateOf(false) }
    if (sleepDialog) androidx.compose.material3.AlertDialog(
        onDismissRequest = { sleepDialog = false }, title = { androidx.compose.material3.Text("Sleep timer") },
        text = { androidx.compose.foundation.layout.Column {
            listOf(15, 30, 45, 60).forEach { minutes ->
                androidx.compose.material3.TextButton(onClick = { playerViewModel.startSleepTimer(minutes); sleepDialog = false }) {
                    androidx.compose.material3.Text("$minutes minutes")
                }
            }
        } },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { playerViewModel.cancelSleepTimer(); sleepDialog = false }) { androidx.compose.material3.Text("Turn off timer") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { sleepDialog = false }) { androidx.compose.material3.Text("Close") } },
    )
    if (ambient) AmbientPlayer(playerViewModel, onDismiss = { ambient = false })

    track?.let { selected ->
        val asTrack = selected.toPlayableTrack(selected.id.hashCode())
        val key = asTrack.downloadKey()
        // Radio and Share are omitted unless the id is real - see [hasRealVideoId], which exists
        // because a stored track with no sourceId still reports itself as a YouTube one.
        val hasVideoId = selected.hasRealVideoId()

        TrackActionsSheet(
            title = selected.title,
            artist = selected.artist,
            imageUrl = selected.imageUrl,
            albumLabel = selected.source,
            isLiked = likedKeys.contains(key),
            // A local file already on the device - see TrackActionsSheet's own doc for why liking
            // one is disabled rather than offered and broken. Still available if it's already
            // liked from before this existed, though, so that can be undone - permanently hiding
            // this would leave an already-liked local file stuck in Liked with no way back out.
            likeAvailable = selected.sourceType != MusicSource.LOCAL_DEVICE || likedKeys.contains(key),
            isDownloaded = downloadedKeys.contains(key),
            downloadProgress = downloadsInProgress[key],
            onPlayNext = { playerViewModel.playNext(selected) },
            onAddToQueue = { playerViewModel.addToQueue(selected) },
            onToggleLike = { actionsViewModel.toggleLike(asTrack) },
            onDownload = { actionsViewModel.download(asTrack) },
            onCancelDownload = { actionsViewModel.cancelDownload(asTrack) },
            onAddToPlaylist = { pendingPlaylistTrack = asTrack },
            onDismiss = onDismiss,
            onStartRadio = if (hasVideoId) {
                {
                    playerViewModel.startRadio(selected) {
                        Toast.makeText(context, "Couldn't start radio", Toast.LENGTH_SHORT).show()
                    }
                }
            } else null,
            onRemoveFromPlaylist = onRemoveFromPlaylist?.let { remove -> { remove(asTrack) } },
            onDeleteDownload = { actionsViewModel.deleteDownload(asTrack) },
            onShare = if (hasVideoId) {
                { context.shareTrack(selected) }
            } else null,
            onRemoveFromHistory = onRemoveFromHistory?.let { remove -> { remove(asTrack) } },
            onShowDetails = { detailsTrack = selected },
            onGoToArtist = if (onGoToArtist != null && selected.artistId != null) {
                { onGoToArtist(selected.artistId) }
            } else null,
            onGoToAlbum = if (onGoToAlbum != null && selected.albumId != null) {
                { onGoToAlbum(selected.albumId) }
            } else null,
            showQueueActions = showQueueActions,
            showLikeAction = showLikeAction,
            showDownloadAction = showDownloadAction,
            onCopyLyrics = onCopyLyrics,
            onSearchLyricsOnline = onSearchLyricsOnline,
            onSleepTimer = if (onEqualizer != null) ({ sleepDialog = true }) else null,
            onEqualizer = onEqualizer,
            onAmbient = if (onEqualizer != null) ({ ambient = true }) else null,
        )
    }

    pendingPlaylistTrack?.let { pending ->
        AddToPlaylistDialog(
            playlists = playlists,
            onPick = { playlistId ->
                actionsViewModel.addToPlaylist(playlistId, pending)
                pendingPlaylistTrack = null
            },
            onCreate = { name ->
                actionsViewModel.createPlaylistWith(name, pending)
                pendingPlaylistTrack = null
            },
            onDismiss = { pendingPlaylistTrack = null },
        )
    }

    detailsTrack?.let { details ->
        val detailsKey = details.toPlayableTrack(details.id.hashCode()).downloadKey()
        TrackDetailsDialog(
            track = details,
            isLiked = likedKeys.contains(detailsKey),
            isDownloaded = downloadedKeys.contains(detailsKey),
            onDismiss = { detailsTrack = null },
        )
    }
}

/**
 * Hands the track to the system share sheet as a watch link.
 *
 * `music.youtube.com` rather than `youtu.be` so the link opens in a music context for anyone who
 * has the app, and still resolves in a browser for anyone who doesn't.
 */
private fun android.content.Context.shareTrack(track: TrackResult) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(
            Intent.EXTRA_TEXT,
            "${track.title} - ${track.artist}\nhttps://music.youtube.com/watch?v=${track.id}",
        )
    }
    startActivity(Intent.createChooser(intent, "Share track"))
}
