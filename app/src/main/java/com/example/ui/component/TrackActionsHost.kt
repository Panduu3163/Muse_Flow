package com.example.ui.component

import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

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
    queueItemIndex: Int? = null,
    /** Present only from Now Playing's menu, folding the lyrics panel's own former menu into this
     * one - see [TrackActionsSheet]. */
    onCopyLyrics: (() -> Unit)? = null,
    onSearchLyricsOnline: (() -> Unit)? = null,
    lyricsSourceLabel: String? = null,
    lyricsTimingLabel: String? = null,
    onChooseLyricsSource: (() -> Unit)? = null,
    onAdjustLyricsTiming: (() -> Unit)? = null,
    onShareLyricsCard: (() -> Unit)? = null,
    onFindLyrics: (() -> Unit)? = null,
    onEqualizer: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val likedKeys by actionsViewModel.likedKeys.collectAsStateWithLifecycle()
    val downloadedKeys by actionsViewModel.downloadedKeys.collectAsStateWithLifecycle()
    val downloadsInProgress by actionsViewModel.downloadsInProgress.collectAsStateWithLifecycle()
    val playlists by actionsViewModel.playlists.collectAsStateWithLifecycle()
    val currentQueueIndex by remember(playerViewModel) {
        playerViewModel.state.map { state -> state.queue.firstOrNull { it.isCurrent }?.index ?: -1 }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = -1)

    // Held outside the `track != null` branch on purpose: choosing "Add to playlist" closes the
    // sheet, so this state has to outlive the selection that created it.
    var pendingPlaylistTrack by remember { mutableStateOf<Track?>(null) }
    // Same reasoning: Details opens after the sheet dismisses, not inside it.
    var detailsTrack by remember { mutableStateOf<TrackResult?>(null) }
    var sleepDialog by remember { mutableStateOf(false) }
    var ambient by remember { mutableStateOf(false) }
    if (sleepDialog) {
        val sleepRemainingMs by playerViewModel.sleepTimerRemainingMs.collectAsStateWithLifecycle()
        SleepTimerDialog(
            remainingMs = sleepRemainingMs,
            onStart = { minutes -> playerViewModel.startSleepTimer(minutes); sleepDialog = false },
            onCancel = { playerViewModel.cancelSleepTimer(); sleepDialog = false },
            onDismiss = { sleepDialog = false },
        )
    }
    if (ambient) AmbientPlayer(playerViewModel, onDismiss = { ambient = false })

    track?.let { selected ->
        val asTrack = selected.toPlayableTrack(selected.id.hashCode())
        val key = asTrack.downloadKey()
        // Radio needs a real video id. Sharing a local song sends its title and artist only.
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
            onPlayNext = {
                if (queueItemIndex != null) {
                    val currentIndex = playerViewModel.state.value.queue.firstOrNull { it.isCurrent }?.index ?: 0
                    val targetIndex = if (queueItemIndex < currentIndex) currentIndex
                        else (currentIndex + 1).coerceAtMost(playerViewModel.state.value.queue.lastIndex)
                    playerViewModel.moveQueueItem(queueItemIndex, targetIndex)
                } else playerViewModel.playNext(selected)
            },
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
            onShare = { context.shareTrack(selected) },
            onRemoveFromHistory = onRemoveFromHistory?.let { remove -> { remove(asTrack) } },
            onShowDetails = { detailsTrack = selected },
            onGoToArtist = if (onGoToArtist != null && selected.artistId != null) {
                { onGoToArtist(selected.artistId) }
            } else null,
            onGoToAlbum = if (onGoToAlbum != null && selected.albumId != null) {
                { onGoToAlbum(selected.albumId) }
            } else null,
            showQueueActions = showQueueActions,
            showPlayNext = queueItemIndex == null || queueItemIndex != currentQueueIndex,
            showLikeAction = showLikeAction,
            showDownloadAction = showDownloadAction,
            queueContext = queueItemIndex != null,
            durationLabel = selected.duration,
            onRefetch = queueItemIndex?.let { index -> { playerViewModel.refetchQueueItem(index) } },
            onCopyLyrics = onCopyLyrics,
            onSearchLyricsOnline = onSearchLyricsOnline,
            lyricsSourceLabel = lyricsSourceLabel,
            lyricsTimingLabel = lyricsTimingLabel,
            onChooseLyricsSource = onChooseLyricsSource,
            onAdjustLyricsTiming = onAdjustLyricsTiming,
            onShareLyricsCard = onShareLyricsCard,
            onFindLyrics = onFindLyrics,
            onSleepTimer = if (onEqualizer != null) ({ sleepDialog = true }) else null,
            onEqualizer = onEqualizer,
            onAmbient = if (onEqualizer != null) ({ ambient = true }) else null,
        )
    }

    pendingPlaylistTrack?.let { pending ->
        AddToPlaylistDialog(
            playlists = playlists,
            subject = pending.title,
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
            if (track.hasRealVideoId())
                "${track.title} - ${track.artist}\nhttps://music.youtube.com/watch?v=${track.id}"
            else "${track.title} - ${track.artist}",
        )
    }
    startActivity(Intent.createChooser(intent, "Share track"))
}
