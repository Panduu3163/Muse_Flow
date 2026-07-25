package com.example.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    /** Long-press opens the actions sheet; null leaves the row tap-only. */
    onLongClick: (() -> Unit)? = null,
    isLiked: Boolean = false,
    isDownloaded: Boolean = false,
    /** 0-100 while downloading, -1 for "started, no percentage yet", null when not downloading. */
    downloadProgress: Int? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("track_row_${title.lowercase().replace(" ", "_")}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(imageUrl = imageUrl)

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
            Text(
                text = artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Status glyphs before the duration, so a liked/downloaded row is identifiable at a glance
        // without opening its actions sheet.
        if (isLiked) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "Liked",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
        if (isDownloaded) {
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

        if (duration != null) {
            Text(
                text = duration,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        trailing?.invoke()
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
private fun Artwork(imageUrl: String?, size: androidx.compose.ui.unit.Dp = 52.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
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
    }
}
