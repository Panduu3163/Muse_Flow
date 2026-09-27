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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

/**
 * A big mosaic hero for a whole section (Liked/Downloads/Top 50/On device) rather than one
 * playlist - same fallback-thumbnail idea as [PlaylistCover], scaled up to as many as 9 cells.
 *
 * Packed by however many *distinct* cover-art thumbnails actually exist, capped at 9 - not padded
 * out to a fixed 3x3 by repeating them. Rows are `ceil(sqrt(n))` wide (capped at 3), filled in
 * order with the remainder landing in the last row: 3 thumbnails is 2 across the top and 1
 * spanning the bottom, 5 is 3 then 2, and so on, up to a genuine 3x3 once there are 9 or more.
 * A track with no cover art contributes nothing to [tracks] worth drawing here at all - it's
 * simply absent, not a blank cell.
 *
 * Full-bleed under the status bar plus a bottom fade into the page background, the same "the
 * cover *is* the top of the screen" treatment [ArtistScreen]'s own cover uses - the caller omits
 * `statusBarsPadding` around this rather than this composable adding its own inset.
 */
@Composable
fun NineGridCover(
    tracks: List<Track>,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(0.dp),
) {
    val thumbnails = remember(tracks) { tracks.mapNotNull { it.imageUrl }.distinct().take(9) }
    val background = MaterialTheme.colorScheme.background
    Box(modifier = modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        if (thumbnails.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxSize(fraction = 0.3f),
                )
            }
        } else {
            val columns = kotlin.math.ceil(kotlin.math.sqrt(thumbnails.size.toDouble())).toInt().coerceIn(1, 3)
            Column(modifier = Modifier.fillMaxSize()) {
                thumbnails.chunked(columns).forEach { row ->
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        row.forEach { url -> MosaicCell(url, Modifier.weight(1f).fillMaxSize()) }
                    }
                }
            }
        }
        // Same fade as ArtistScreen's ArtistCover: transparent until nearly the bottom, then into
        // the page's own background colour, so the mosaic dissolves into the screen rather than
        // ending on a hard edge.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(0f to Color.Transparent, 0.45f to Color.Transparent, 1f to background),
                    ),
                ),
        )
    }
}
