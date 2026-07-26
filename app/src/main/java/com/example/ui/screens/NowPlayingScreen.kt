package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import kotlin.math.abs
import kotlin.math.roundToInt
import coil.compose.AsyncImage
import com.example.AlbumPalette
import com.example.BackgroundStyle
import com.example.NowPlayingState
import com.example.QueueItem
import com.example.asPlaybackTime
import com.example.ui.component.SquigglySlider

/**
 * The full-screen player: large artwork, a seekable progress bar, transport controls, and a
 * toggleable queue.
 *
 * Stateless with respect to playback - every control calls back into `PlayerViewModel`, which owns
 * the `MediaController`. That keeps this screen and the mini-player showing the same truth without
 * either of them holding player state of their own.
 */
@Composable
fun NowPlayingScreen(
    state: NowPlayingState,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onPlayQueueItem: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onCollapse: () -> Unit,
    isLiked: Boolean = false,
    isDownloaded: Boolean = false,
    downloadProgress: Int? = null,
    onToggleLike: () -> Unit = {},
    onDownload: () -> Unit = {},
    hideArtwork: Boolean = false,
    artworkCornerRadius: Int = 20,
    cropArtwork: Boolean = true,
    wavySlider: Boolean = false,
    slimSlider: Boolean = false,
    backgroundStyle: BackgroundStyle = BackgroundStyle.Solid,
    palette: AlbumPalette? = null,
    buttonColor: Color = Color.Unspecified,
    sleepTimerRemainingMs: Long? = null,
    onStartSleepTimer: (Int) -> Unit = {},
    onCancelSleepTimer: () -> Unit = {},
    onSetPlaybackSpeed: (Float, Float) -> Unit = { _, _ -> },
    /** Opens the full track actions sheet (add to playlist/share/details/view artist/view album) -
     * the "player menu" the gap audit flagged as entirely missing, reusing the same sheet every
     * other track list already opens rather than inventing a second, narrower one. */
    onOpenMenu: () -> Unit = {},
    /** Rendered in place of the artwork when the lyrics toggle is on. Passed as a slot so this
     * screen stays free of lyrics fetching and its ViewModel. */
    lyricsContent: @Composable (Modifier) -> Unit = {},
    modifier: Modifier = Modifier,
) {

    Box(modifier = modifier.fillMaxSize()) {
        PlayerBackground(
            style = backgroundStyle,
            palette = palette,
            artworkUrl = state.artworkUrl,
        )
        PlayerContent(
            state = state,
            onTogglePlayPause = onTogglePlayPause,
            onNext = onNext,
            onPrevious = onPrevious,
            onSeek = onSeek,
            onToggleShuffle = onToggleShuffle,
            onCycleRepeat = onCycleRepeat,
            onPlayQueueItem = onPlayQueueItem,
            onMoveQueueItem = onMoveQueueItem,
            onRemoveQueueItem = onRemoveQueueItem,
            onCollapse = onCollapse,
            onOpenMenu = onOpenMenu,
            isLiked = isLiked,
            isDownloaded = isDownloaded,
            downloadProgress = downloadProgress,
            onToggleLike = onToggleLike,
            onDownload = onDownload,
            hideArtwork = hideArtwork,
            artworkCornerRadius = artworkCornerRadius,
            cropArtwork = cropArtwork,
            wavySlider = wavySlider,
            slimSlider = slimSlider,
            buttonColor = buttonColor,
            sleepTimerRemainingMs = sleepTimerRemainingMs,
            onStartSleepTimer = onStartSleepTimer,
            onCancelSleepTimer = onCancelSleepTimer,
            onSetPlaybackSpeed = onSetPlaybackSpeed,
            lyricsContent = lyricsContent,
        )
    }
}

/**
 * The painted layer behind the player.
 *
 * [BackgroundStyle.Gradient] uses the artwork's own dominant/muted colours; [BackgroundStyle.Blur]
 * uses the artwork itself, blurred and dimmed. Both fall back to the plain theme background when no
 * palette or artwork is available yet, so the screen never flashes an unpainted state.
 */
@Composable
private fun PlayerBackground(
    style: BackgroundStyle,
    palette: AlbumPalette?,
    artworkUrl: String?,
) {
    val base = MaterialTheme.colorScheme.background

    when {
        style == BackgroundStyle.Gradient && palette != null -> {
            val top by animateColorAsState(palette.dominant, label = "bg_top")
            val bottom by animateColorAsState(palette.muted, label = "bg_bottom")
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            // Blended toward the base so text contrast stays usable regardless of
                            // how bright the artwork happens to be.
                            listOf(
                                top.copy(alpha = 0.55f).compositeOver(base),
                                bottom.copy(alpha = 0.35f).compositeOver(base),
                                base,
                            )
                        )
                    )
            )
        }

        // BackgroundStyle.Glow removed - was here, to be reimplemented later.

        style == BackgroundStyle.Blur && artworkUrl != null -> {
            Box(modifier = Modifier.fillMaxSize().background(base)) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(40.dp)
                        .alpha(0.45f),
                )
                // Scrim: a blurred cover is still busy enough to hurt text legibility.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(base.copy(alpha = 0.45f))
                )
            }
        }

        else -> Box(modifier = Modifier.fillMaxSize().background(base))
    }
}

/**
 * Swipe left/right on the artwork to skip tracks, swipe up/down to adjust the *device's* media
 * volume (via [AudioManager], the same stream the hardware buttons control) - not
 * [MediaController.volume], which is already driven by crossfade's fade-out/fade-in (see
 * [PlayerViewModel.applyCrossfadeVolume]); fighting over that property would make a swipe get
 * silently overwritten by the next 500ms crossfade tick.
 *
 * A drag is classified once by whichever axis moved further at gesture start (read from a
 * [remember]ed [mutableStateOf], not a `val` closed over from the composition - see the documented
 * pointerInput gotcha in `docs/SESSION-HANDOFF.md` §3), so a mostly-vertical swipe never also
 * registers as a skip and vice versa. Horizontal commits once on release past a distance
 * threshold; vertical adjusts volume continuously as the finger moves, for the same immediate
 * feedback the hardware volume buttons give.
 */
@Composable
private fun Modifier.artworkSwipeGestures(onNext: () -> Unit, onPrevious: () -> Unit): Modifier {
    val context = LocalContext.current
    val audioManager = remember {
        context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
    }
    var axis by remember { mutableStateOf<Char?>(null) } // 'h' or 'v', decided once per gesture
    var horizontalAccum by remember { mutableFloatStateOf(0f) }
    var verticalAccum by remember { mutableFloatStateOf(0f) }

    return this.pointerInput(Unit) {
        detectDragGestures(
            onDragStart = {
                axis = null
                horizontalAccum = 0f
                verticalAccum = 0f
            },
            onDragEnd = {
                if (axis == 'h' && abs(horizontalAccum) > 120f) {
                    if (horizontalAccum < 0) onNext() else onPrevious()
                }
                axis = null
            },
            onDrag = { change, dragAmount ->
                change.consume()
                if (axis == null) {
                    axis = if (abs(dragAmount.x) > abs(dragAmount.y)) 'h' else 'v'
                }
                when (axis) {
                    'h' -> horizontalAccum += dragAmount.x
                    'v' -> {
                        verticalAccum += dragAmount.y
                        // One volume step per ~24px of vertical travel - up (negative dy) raises.
                        if (abs(verticalAccum) > 24f) {
                            val direction = if (verticalAccum < 0) {
                                android.media.AudioManager.ADJUST_RAISE
                            } else {
                                android.media.AudioManager.ADJUST_LOWER
                            }
                            audioManager.adjustStreamVolume(
                                android.media.AudioManager.STREAM_MUSIC,
                                direction,
                                android.media.AudioManager.FLAG_SHOW_UI,
                            )
                            verticalAccum = 0f
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun PlayerContent(
    state: NowPlayingState,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onPlayQueueItem: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onCollapse: () -> Unit,
    isLiked: Boolean,
    isDownloaded: Boolean,
    downloadProgress: Int?,
    onToggleLike: () -> Unit,
    onDownload: () -> Unit,
    hideArtwork: Boolean,
    artworkCornerRadius: Int,
    cropArtwork: Boolean,
    wavySlider: Boolean,
    slimSlider: Boolean,
    buttonColor: Color,
    sleepTimerRemainingMs: Long? = null,
    onStartSleepTimer: (Int) -> Unit = {},
    onCancelSleepTimer: () -> Unit = {},
    onSetPlaybackSpeed: (Float, Float) -> Unit = { _, _ -> },
    onOpenMenu: () -> Unit = {},
    lyricsContent: @Composable (Modifier) -> Unit,
) {
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .testTag("now_playing_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCollapse, modifier = Modifier.testTag("now_playing_collapse")) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Collapse player",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .clickable { showSleepTimerDialog = true }
                    .testTag("now_playing_sleep_timer"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The remaining time next to the icon, not just a tinted icon - the tint alone
                // said "a timer is running" but not "how much longer," which is the one thing
                // someone glancing at this actually wants to know.
                if (sleepTimerRemainingMs != null) {
                    Text(
                        text = sleepTimerRemainingMs.asPlaybackTime(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 2.dp),
                    )
                }
                IconButton(onClick = { showSleepTimerDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Bedtime,
                        contentDescription = "Sleep timer",
                        tint = if (sleepTimerRemainingMs != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
            IconButton(
                onClick = { showSpeedDialog = true },
                modifier = Modifier.testTag("now_playing_speed"),
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = "Playback speed",
                    tint = if (state.speed != 1f) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            IconButton(
                onClick = {
                    showLyrics = !showLyrics
                    if (showLyrics) showQueue = false
                },
                modifier = Modifier.testTag("now_playing_lyrics_toggle"),
            ) {
                Icon(
                    imageVector = Icons.Default.Lyrics,
                    contentDescription = if (showLyrics) "Hide lyrics" else "Show lyrics",
                    tint = if (showLyrics) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            IconButton(onClick = {
                showQueue = !showQueue
                if (showQueue) showLyrics = false
            }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = if (showQueue) "Hide queue" else "Show queue",
                    tint = if (showQueue) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            IconButton(onClick = onOpenMenu, modifier = Modifier.testTag("now_playing_menu")) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (showLyrics) {
            lyricsContent(Modifier.weight(1f))
        } else if (showQueue) {
            // Sized and centred exactly like the artwork it replaces - fillMaxWidth + a 1:1
            // aspect ratio, flanked by the same two half-weight spacers - rather than the queue
            // panel stretching to fill all the leftover vertical space down to the transport
            // controls. Opening the queue used to grow the interactive area (and the swipe
            // gesture's own reach) well past where the artwork ever sat.
            Spacer(Modifier.weight(0.5f))
            QueueList(
                queue = state.queue,
                onPlayQueueItem = onPlayQueueItem,
                onMoveQueueItem = onMoveQueueItem,
                onRemoveQueueItem = onRemoveQueueItem,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
            Spacer(Modifier.weight(0.5f))
        } else if (!hideArtwork) {
            Spacer(Modifier.weight(0.5f))
            Artwork(
                imageUrl = state.artworkUrl,
                isBuffering = state.isBuffering,
                cornerRadius = artworkCornerRadius.dp,
                cropToSquare = cropArtwork,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .artworkSwipeGestures(onNext = onNext, onPrevious = onPrevious),
            )
            Spacer(Modifier.weight(0.5f))
        } else {
            // Artwork hidden: absorb the freed space so the controls stay vertically centred
            // instead of collapsing to the top of the screen.
            Spacer(Modifier.weight(1f))
        }

        // Like and download flank the title rather than joining the transport row: they're
        // track-level actions, not playback controls, and keeping them apart stops the row of
        // primary controls from growing to six equally-weighted buttons.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onToggleLike,
                enabled = state.hasMedia,
                modifier = Modifier.testTag("now_playing_like"),
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (isLiked) "Remove from Liked" else "Add to Liked",
                    tint = if (isLiked) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.title.ifBlank { "Nothing playing" },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = state.artist,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }

            IconButton(
                onClick = onDownload,
                enabled = state.hasMedia && !isDownloaded && downloadProgress == null,
                modifier = Modifier.testTag("now_playing_download"),
            ) {
                when {
                    isDownloaded -> Icon(
                        imageVector = Icons.Default.DownloadDone,
                        contentDescription = "Downloaded",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    downloadProgress != null && downloadProgress >= 0 -> CircularProgressIndicator(
                        progress = { downloadProgress / 100f },
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                    downloadProgress != null -> CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    else -> Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        SeekBar(state = state, onSeek = onSeek, wavySlider = wavySlider, slimSlider = slimSlider)

        TransportControls(
            state = state,
            buttonColor = buttonColor,
            onTogglePlayPause = onTogglePlayPause,
            onNext = onNext,
            onPrevious = onPrevious,
            onToggleShuffle = onToggleShuffle,
            onCycleRepeat = onCycleRepeat,
        )

        Spacer(Modifier.height(24.dp))
    }

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            remainingMs = sleepTimerRemainingMs,
            onStart = { minutes ->
                onStartSleepTimer(minutes)
                showSleepTimerDialog = false
            },
            onCancel = {
                onCancelSleepTimer()
                showSleepTimerDialog = false
            },
            onDismiss = { showSleepTimerDialog = false },
        )
    }

    if (showSpeedDialog) {
        SpeedDialog(
            speed = state.speed,
            pitch = state.pitch,
            onSetSpeed = onSetPlaybackSpeed,
            onDismiss = { showSpeedDialog = false },
        )
    }
}

@Composable
private fun SleepTimerDialog(
    remainingMs: Long?,
    onStart: (Int) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep timer") },
        text = {
            Column {
                if (remainingMs != null) {
                    Text(
                        text = "Stopping in ${remainingMs.asPlaybackTime()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                listOf(15, 30, 45, 60).forEach { minutes ->
                    androidx.compose.material3.TextButton(
                        onClick = { onStart(minutes) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("$minutes minutes", modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {
            if (remainingMs != null) {
                androidx.compose.material3.TextButton(onClick = onCancel) { Text("Stop timer") }
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

/**
 * [pitch] independent of [speed] is a real, distinct DSP path (Sonic-style time-stretching, not
 * just resampling) - "Preserve pitch" pins it to 1x while [speed] changes; turning it off lets
 * pitch follow speed for the classic chipmunk/slow-motion effect.
 */
@Composable
private fun SpeedDialog(
    speed: Float,
    pitch: Float,
    onSetSpeed: (Float, Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var sliderSpeed by remember(speed) { mutableFloatStateOf(speed) }
    var preservePitch by remember(speed, pitch) { mutableStateOf(pitch == 1f) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback speed") },
        text = {
            Column {
                Text(
                    text = "${String.format("%.2f", sliderSpeed)}x",
                    style = MaterialTheme.typography.titleMedium,
                )
                Slider(
                    value = sliderSpeed,
                    onValueChange = {
                        sliderSpeed = it
                        onSetSpeed(it, if (preservePitch) 1f else it)
                    },
                    valueRange = 0.5f..2f,
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Preserve pitch", modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(
                        checked = preservePitch,
                        onCheckedChange = {
                            preservePitch = it
                            onSetSpeed(sliderSpeed, if (it) 1f else sliderSpeed)
                        },
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = {
                    sliderSpeed = 1f
                    preservePitch = true
                    onSetSpeed(1f, 1f)
                },
            ) { Text("Reset") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

/**
 * While the user is dragging, the thumb follows the finger rather than the player's reported
 * position - otherwise the 500ms position poll would fight the drag and make the thumb stutter.
 * The seek is committed once on release.
 */
@Composable
private fun SeekBar(
    state: NowPlayingState,
    onSeek: (Float) -> Unit,
    wavySlider: Boolean,
    slimSlider: Boolean,
) {
    var scrubPosition by remember { mutableStateOf<Float?>(null) }
    val displayed = scrubPosition ?: state.progress

    Column(modifier = Modifier.padding(top = 24.dp)) {
        if (wavySlider) {
            SquigglySlider(
                progress = state.progress,
                onSeek = onSeek,
                playing = state.isPlaying,
                activeColor = MaterialTheme.colorScheme.primary,
                inactiveColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        } else {
            Slider(
                value = displayed,
                onValueChange = { scrubPosition = it },
                onValueChangeFinished = {
                    scrubPosition?.let(onSeek)
                    scrubPosition = null
                },
                // Slim keeps the same touch target but draws a visually lighter track.
                modifier = Modifier
                    .testTag("now_playing_seekbar")
                    .then(if (slimSlider) Modifier.height(24.dp) else Modifier),
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = (displayed * state.durationMs).toLong().asPlaybackTime(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = state.durationMs.asPlaybackTime(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TransportControls(
    state: NowPlayingState,
    buttonColor: Color,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggleShuffle) {
            Icon(
                imageVector = Icons.Default.Shuffle,
                contentDescription = "Shuffle",
                tint = if (state.shuffleEnabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }

        IconButton(onClick = onPrevious, enabled = state.hasPrevious) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "Previous track",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(36.dp),
            )
        }

        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(buttonColor.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.primary)
                .clickable(onClick = onTogglePlayPause)
                .testTag("now_playing_play_pause"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (state.isPlaying) "Pause" else "Play",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(34.dp),
            )
        }

        IconButton(onClick = onNext, enabled = state.hasNext) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Next track",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(36.dp),
            )
        }

        IconButton(onClick = onCycleRepeat) {
            Icon(
                imageVector = if (state.repeatMode == Player.REPEAT_MODE_ONE) {
                    Icons.Default.RepeatOne
                } else {
                    Icons.Default.Repeat
                },
                contentDescription = "Repeat mode",
                tint = if (state.repeatMode == Player.REPEAT_MODE_OFF) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
    }
}

/** Fixed so a drag can convert pixels travelled into rows travelled without measuring anything. */
private val QueueRowHeight = 60.dp

/**
 * The queue, reorderable by dragging a row's handle, and editable by swiping a row left-to-right
 * to remove it (replacing the old per-row "⋮" menu, which only ever held Play now/Play next/
 * Remove - Play now already duplicates the row's own tap-to-play, and swipe-to-remove is the more
 * direct gesture for the one action that's left).
 *
 * The move is committed once, on drag end, rather than continuously as the finger crosses each
 * row boundary. Committing mid-drag would reorder the list under the very composable owning the
 * gesture - and since rows are keyed by their queue position, that item would be disposed and
 * recreated, cancelling the drag halfway through. Holding the change until the end keeps the list
 * stable for the whole gesture; the shifting of the other rows is a visual offset only, so it
 * still looks like a live reorder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueList(
    queue: List<QueueItem>,
    onPlayQueueItem: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowHeightPx = with(LocalDensity.current) { QueueRowHeight.toPx() }

    // The row picked up, and how far it has travelled since. Null index means no drag in progress.
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }

    // Where the dragged row would land if the finger lifted now.
    val targetIndex = draggingIndex?.let { from ->
        (from + (dragOffsetPx / rowHeightPx).roundToInt()).coerceIn(0, queue.lastIndex)
    }

    LazyColumn(
        // Clipped to its own bounds - the queue panel is now sized to match the artwork's box
        // (see the call site), and without a clip a swipe's drag/overshoot could otherwise paint
        // past that box's rounded edge instead of being contained by it.
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentPadding = PaddingValues(vertical = 12.dp),
    ) {
        // Keyed on mediaId+index, not index alone - after a removal shifts every later item up
        // one slot, index-only keys would make Compose treat "song B now at slot 2" as the exact
        // same row identity as "song A that used to be at slot 2," reusing its remembered
        // dismissState along with it. That's what made a removed row's red swipe-reveal appear to
        // "stick" onto whatever song took its place instead of the list settling cleanly - the new
        // occupant was silently born already mid-swipe. Including mediaId means a genuinely
        // different song at that slot gets a fresh key (and fresh, Settled dismiss state) instead.
        items(queue, key = { "${it.mediaId}_${it.index}" }) { item ->
            val isDragging = draggingIndex == item.index

            // Rows between the dragged row's origin and its target slide one place to make the
            // gap, so the landing position is visible before the finger lifts.
            val origin = draggingIndex
            val destination = targetIndex
            val shiftPx = when {
                isDragging || origin == null || destination == null -> 0f
                item.index in (origin + 1)..destination -> -rowHeightPx
                item.index in destination..(origin - 1) -> rowHeightPx
                else -> 0f
            }

            // A fresh state per row identity - re-created (not reused) whenever the item's own
            // position in the swipe animation would otherwise carry over onto a different song
            // after removal/reorder, since it's keyed on the composition slot the same as the row
            // itself.
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = { value ->
                    if (value == SwipeToDismissBoxValue.StartToEnd) {
                        onRemoveQueueItem(item.index)
                    }
                    true
                },
            )

            SwipeToDismissBox(
                state = dismissState,
                enableDismissFromStartToEnd = true,
                enableDismissFromEndToStart = false,
                // The rows above/below sliding smoothly into a removed row's gap, instead of
                // snapping straight to their new position the instant it's gone.
                modifier = Modifier
                    .animateItem()
                    .testTag("queue_swipe_${item.index}"),
                backgroundContent = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(start = 20.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove from queue",
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                },
            ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(QueueRowHeight)
                    .background(MaterialTheme.colorScheme.background)
                    // The dragged row rides above its neighbours instead of being clipped by them.
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (isDragging) dragOffsetPx else shiftPx
                        alpha = if (isDragging) 0.9f else 1f
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Only the artwork and titles play the track. If the whole row were clickable, a tap
                // that missed the drag handle - or a drag too short to register - would silently jump
                // playback to that item instead of doing nothing.
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onPlayQueueItem(item.index) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (item.artworkUrl != null) {
                            AsyncImage(
                                model = item.artworkUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(44.dp),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleSmall,
                            // The playing row is tinted rather than badged - reads instantly without
                            // adding another element to every row.
                            color = if (item.isCurrent) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = item.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Dragging starts from this handle rather than from a long-press on the row, so
                // the gesture can never be mistaken for a scroll of the queue itself.
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Reorder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("queue_drag_${item.index}")
                        .pointerInput(item.index, queue.size) {
                            detectDragGestures(
                                onDragStart = {
                                    draggingIndex = item.index
                                    dragOffsetPx = 0f
                                },
                                onDragEnd = {
                                    // Recomputed here from the live state rather than reusing the
                                    // `targetIndex` above: that one is a composition-scope value
                                    // this lambda would capture once, at gesture-setup time, and
                                    // still be reading as null when the finger lifts.
                                    val from = draggingIndex
                                    if (from != null) {
                                        val to = (from + (dragOffsetPx / rowHeightPx).roundToInt())
                                            .coerceIn(0, queue.lastIndex)
                                        if (to != from) onMoveQueueItem(from, to)
                                    }
                                    draggingIndex = null
                                    dragOffsetPx = 0f
                                },
                                onDragCancel = {
                                    draggingIndex = null
                                    dragOffsetPx = 0f
                                },
                            ) { change, dragAmount ->
                                change.consume()
                                dragOffsetPx += dragAmount.y
                            }
                        },
                )
            }
            }
        }
    }
}

@Composable
private fun Artwork(
    imageUrl: String?,
    isBuffering: Boolean,
    cornerRadius: androidx.compose.ui.unit.Dp = 20.dp,
    cropToSquare: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                // Fit keeps a non-square cover fully visible inside the frame instead of
                // cropping its edges away.
                contentScale = if (cropToSquare) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(72.dp),
            )
        }

        AnimatedVisibility(visible = isBuffering) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
