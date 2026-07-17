package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Renders a track's real cover art (via Coil, which caches in memory + on disk by default -
 * no extra setup needed) when [imageUrl] is available, over the same gradient tile every screen
 * already uses as a placeholder. Mock-catalog tracks have no [imageUrl], so they keep showing
 * [fallback] unchanged; real (e.g. JioSaavn search) results get real artwork on top of it.
 */
@Composable
fun TrackArtwork(
    imageUrl: String?,
    gradientColors: List<Color>,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    fallback: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.linearGradient(gradientColors)),
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        } else {
            fallback()
        }
    }
}
