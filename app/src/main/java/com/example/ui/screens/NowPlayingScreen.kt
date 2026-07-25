package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
            onCollapse = onCollapse,
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
    lyricsContent: @Composable (Modifier) -> Unit,
) {
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }

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
        }

        if (showLyrics) {
            lyricsContent(Modifier.weight(1f))
        } else if (showQueue) {
            QueueList(
                queue = state.queue,
                onPlayQueueItem = onPlayQueueItem,
                modifier = Modifier.weight(1f),
            )
        } else if (!hideArtwork) {
            Spacer(Modifier.weight(0.5f))
            Artwork(
                imageUrl = state.artworkUrl,
                isBuffering = state.isBuffering,
                cornerRadius = artworkCornerRadius.dp,
                cropToSquare = cropArtwork,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
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

@Composable
private fun QueueList(
    queue: List<QueueItem>,
    onPlayQueueItem: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 12.dp),
    ) {
        items(queue, key = { it.index }) { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPlayQueueItem(item.index) }
                    .padding(vertical = 8.dp),
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
