package com.example.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * The one song row every list in the app uses - search results, playlists, downloads, liked songs.
 *
 * Deliberately a single shared renderer rather than a per-screen copy: the previous UI
 * re-implemented this in each screen, which is a large part of why those files grew past
 * 900 lines each.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    title: String,
    artist: String,
    imageUrl: String?,
    duration: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Long-press enters multi-select with this row ticked; null leaves the row tap-only. */
    onLongClick: (() -> Unit)? = null,
    isLiked: Boolean = false,
    isDownloaded: Boolean = false,
    /** A file already on the device (On Device section, or an on-device track that's since been
     * liked/added to a playlist) - shown with its own glyph rather than [isDownloaded]'s, since
     * "Download" is a meaningless action/status for something that was never fetched through the
     * app's own download feature in the first place. Takes priority when both are somehow true. */
    isLocalDevice: Boolean = false,
    /** 0-100 while downloading, -1 for "started, no percentage yet", null when not downloading. */
    downloadProgress: Int? = null,
    /** True when this row's track is the one currently loaded in the player (playing or paused) -
     * shows a small animated equalizer over the cover art so the currently-playing song is
     * identifiable at a glance in a long list, the same way Spotify/YouTube Music mark it. Callers
     * compare their own track's `downloadKey()` against the player's current track to compute
     * this - see e.g. `LibraryScreen`'s `nowPlayingKey`. */
    isPlaying: Boolean = false,
    /** Ticked in multi-select mode. Tints the row and replaces the artwork with a checkmark. */
    selected: Boolean = false,
    /**
     * Opens the row's actions sheet. The sheet's own long-press entry point was removed in favour
     * of this - long-press now goes straight to multi-select, so a tap-reachable menu is the only
     * way left to reach the sheet. Null hides the button, e.g. in multi-select mode where the row
     * has nothing to open a single-track sheet onto.
     */
    onOpenMenu: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Before the click handler so the press ripple draws over the tint rather than under
            // it. Both cues together on purpose: the row tint alone is easy to miss at a glance,
            // and the artwork check alone doesn't read as "this whole row".
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    androidx.compose.ui.graphics.Color.Transparent
                },
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("track_row_${title.lowercase().replace(" ", "_")}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(imageUrl = imageUrl, selected = selected, isPlaying = isPlaying)

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Duration sits beside the artist rather than out at the row's trailing edge - with
            // the menu button now living there too, a third element competing for that space read
            // as cluttered. Its own smaller style keeps it visually subordinate to the artist name
            // it's now attached to, rather than reading as a second title.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // fill = false lets this shrink to the artist's natural width when short,
                    // rather than always claiming the full remaining row - so the duration text
                    // sits right after the name instead of pinned to the far side of empty space.
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (duration != null) {
                    Text(
                        text = " · $duration",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Status glyphs, so a liked/downloaded row is identifiable at a glance without opening its
        // actions sheet.
        if (isLiked) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "Liked",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
        if (isLocalDevice) {
            Icon(
                imageVector = Icons.Default.Storage,
                contentDescription = "On this device",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(16.dp),
            )
        } else if (isDownloaded) {
            Icon(
                imageVector = Icons.Default.DownloadDone,
                contentDescription = "Downloaded",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(16.dp),
            )
        } else if (downloadProgress != null) {
            DownloadProgressRing(
                percent = downloadProgress,
                modifier = Modifier.padding(start = 6.dp),
            )
        }

        onOpenMenu?.let { openMenu ->
            IconButton(
                onClick = openMenu,
                modifier = Modifier.testTag("track_row_menu_${title.lowercase().replace(" ", "_")}"),
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Song options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * A small ring showing download progress.
 *
 * A known percentage draws a determinate arc; the repository's `-1` "started, no size reported
 * yet" sentinel spins indeterminately instead, so a server that never sends Content-Length still
 * looks like it's doing something rather than sitting frozen at 0%.
 */
@Composable
private fun DownloadProgressRing(percent: Int, modifier: Modifier = Modifier) {
    if (percent >= 0) {
        CircularProgressIndicator(
            progress = { percent / 100f },
            modifier = modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    } else {
        CircularProgressIndicator(
            modifier = modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Square cover art with a music-note placeholder for results that have no image. */
@Composable
private fun Artwork(
    imageUrl: String?,
    selected: Boolean = false,
    isPlaying: Boolean = false,
    size: androidx.compose.ui.unit.Dp = 52.dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        // Replaces the artwork rather than overlaying it: at 52dp a badge in the corner is smaller
        // than the tick itself needs to be to register.
        if (selected) {
            Box(
                modifier = Modifier
                    .size(size)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(26.dp),
                )
            }
            return@Box
        }

        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        } else {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }

        // Overlaid rather than replacing the art (unlike the selected checkmark above) - the cover
        // is still worth seeing; this only needs to be noticeable, not exclusive.
        if (isPlaying) {
            Box(
                modifier = Modifier
                    .size(size)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                NowPlayingIndicator()
            }
        }
    }
}

/** A small animated equalizer (three bars bouncing out of phase) marking whichever row's track is
 * currently loaded in the player - the same "this one's playing" convention Spotify/YouTube Music
 * use, so a long list of songs doesn't require opening Now Playing just to tell which one it is.
 * Not `private`: also used directly by grid-view cells (e.g. `LibraryScreen`'s `TrackGridCell`),
 * which render their own artwork box rather than going through [TrackRow]'s. */
@Composable
fun NowPlayingIndicator() {
    val transition = rememberInfiniteTransition(label = "now_playing_bars")
    val heights = List(3) { index ->
        val duration = 480 + index * 130
        transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(duration, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "now_playing_bar_$index",
        )
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(18.dp),
    ) {
        heights.forEach { height ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(18.dp * height.value)
                    .background(Color.White, RoundedCornerShape(1.dp)),
            )
        }
    }
}
