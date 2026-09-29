package com.example.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ui.utils.slowMarquee
import com.example.ui.theme.MuseFlowShapes
import com.example.ui.theme.MuseFlowSpacing

/**
 * One card for an album/artist/playlist/track (image, title, optional subtitle) in a horizontal
 * carousel - the shared renderer for every shelf across Home and Artist, mirroring [TrackRow]'s
 * and [CollectionRow]'s role for their own row-based lists. [shape] is the one thing callers
 * usually vary (a rounded square for albums/playlists/tracks, [androidx.compose.foundation.shape.CircleShape]
 * for artists).
 */
@Composable
fun MediaCard(
    title: String,
    subtitle: String?,
    imageUrl: String?,
    placeholder: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
    shape: Shape = RoundedCornerShape(MuseFlowShapes.control),
    /** Overrides the default single-[imageUrl] artwork box when set - e.g. a playlist's mosaic
     * cover, which needs its own track data rather than one flat URL. [imageUrl] is unused when
     * this is provided. */
    artwork: (@Composable (Modifier) -> Unit)? = null,
) {
    val largeText = LocalDensity.current.fontScale > 1.2f
    Column(
        modifier = modifier
            .width(size)
            .liquidSurface(MuseFlowShapes.card)
            .clickable(onClick = onClick)
            .padding(MuseFlowSpacing.small),
    ) {
        if (artwork != null) {
            artwork(Modifier.fillMaxWidth().aspectRatio(1f).clip(shape))
        } else {
            CardArtwork(imageUrl = imageUrl, placeholder = placeholder, shape = shape, modifier = Modifier.fillMaxWidth().aspectRatio(1f), iconSize = 24.dp)
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (largeText) 2 else 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).then(if (largeText) Modifier else Modifier.slowMarquee()),
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().then(if (largeText) Modifier else Modifier.slowMarquee()),
            )
        }
    }
}

/** Same visual as [MediaCard] but sized to fill its grid cell (a square aspect ratio) rather than
 * a fixed width - full-list grids (e.g. Artist's Albums/Related tabs) use this; horizontal shelf
 * previews use [MediaCard]. */
@Composable
fun MediaGridCard(
    title: String,
    subtitle: String?,
    imageUrl: String?,
    placeholder: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(MuseFlowShapes.control),
) {
    val largeText = LocalDensity.current.fontScale > 1.2f
    Column(modifier = modifier.fillMaxWidth().liquidSurface().clickable(onClick = onClick).padding(MuseFlowSpacing.small)) {
        CardArtwork(
            imageUrl = imageUrl,
            placeholder = placeholder,
            shape = shape,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            iconSize = 48.dp,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = if (largeText) 2 else 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).then(if (largeText) Modifier else Modifier.slowMarquee()),
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().then(if (largeText) Modifier else Modifier.slowMarquee()),
            )
        }
    }
}

@Composable
private fun CardArtwork(
    imageUrl: String?,
    placeholder: ImageVector,
    shape: Shape,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = placeholder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}
