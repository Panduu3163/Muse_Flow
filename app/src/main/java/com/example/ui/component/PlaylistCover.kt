package com.example.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.Track

/**
 * A playlist's cover: a user-picked photo wins, then a 2x2 mosaic of its own first four distinct
 * track thumbnails, then a real assigned cover URL, then one track's own art, then a plain icon.
 * The one shared renderer for this fallback chain - previously duplicated per screen (Library's
 * grid tile had its own copy; Home's "Your playlists" shelf had none at all and just showed
 * [PlaylistEntity.coverImageUrl] directly, which is null for anything that reached this app
 * through an import rather than a real fetched cover - e.g. every Spotify-imported playlist,
 * which is why those looked blank on Home specifically while the exact same playlist already
 * showed a mosaic everywhere else).
 */
@Composable
fun PlaylistCover(
    tracks: List<Track>,
    fallbackCoverUrl: String?,
    modifier: Modifier = Modifier,
    customCoverUri: String? = null,
    shape: Shape = RoundedCornerShape(14.dp),
) {
    val thumbnails = remember(tracks) { tracks.mapNotNull { it.imageUrl }.distinct().take(4) }
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        when {
            customCoverUri != null -> AsyncImage(
                model = customCoverUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            thumbnails.size == 4 -> Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    MosaicCell(thumbnails[0], Modifier.weight(1f).fillMaxSize())
                    MosaicCell(thumbnails[1], Modifier.weight(1f).fillMaxSize())
                }
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    MosaicCell(thumbnails[2], Modifier.weight(1f).fillMaxSize())
                    MosaicCell(thumbnails[3], Modifier.weight(1f).fillMaxSize())
                }
            }

            fallbackCoverUrl != null -> AsyncImage(
                model = fallbackCoverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            thumbnails.isNotEmpty() -> AsyncImage(
                model = thumbnails.first(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxSize(fraction = 0.4f),
                )
            }
        }
    }
}

/** Square variant, for grid tiles ([Modifier.aspectRatio] applied). */
@Composable
fun SquarePlaylistCover(
    tracks: List<Track>,
    fallbackCoverUrl: String?,
    modifier: Modifier = Modifier,
    customCoverUri: String? = null,
    shape: Shape = RoundedCornerShape(14.dp),
) {
    PlaylistCover(
        tracks = tracks,
        fallbackCoverUrl = fallbackCoverUrl,
        customCoverUri = customCoverUri,
        shape = shape,
        modifier = modifier.fillMaxWidth().aspectRatio(1f),
    )
}

@Composable
private fun MosaicCell(imageUrl: String, modifier: Modifier) {
    AsyncImage(
        model = imageUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}
