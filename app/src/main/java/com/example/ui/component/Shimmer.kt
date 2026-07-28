package com.example.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * A soft band of highlight sweeping left-to-right over the base surface colour, looped forever -
 * the standard "content is loading" cue (mirrors Echo's shimmer package) used instead of a blank
 * row or a single centered spinner, so a loading list reads as "about to be populated" rather than
 * "broken/empty".
 */
@Composable
private fun rememberShimmerBrush(): Brush {
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.colorScheme.surfaceContainerHighest
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateX by transition.animateFloat(
        initialValue = -1000f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_translate",
    )
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(translateX - 300f, 0f),
        end = Offset(translateX, 0f),
    )
}

/** A single shimmering block - the building unit every placeholder below is made of. */
@Composable
fun ShimmerBlock(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(6.dp),
) {
    val brush = rememberShimmerBrush()
    Box(
        modifier = modifier
            .clip(shape)
            .background(brush),
    )
}

/** Stand-in for a [TrackRow]/[CollectionRow] while its real data is loading - same artwork size
 * (52dp, matching [TrackRow]'s default) and two text-line placeholders in its place. */
@Composable
fun RowPlaceholder(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShimmerBlock(modifier = Modifier.size(52.dp), shape = RoundedCornerShape(10.dp))
        Column(modifier = Modifier.padding(start = 12.dp)) {
            ShimmerBlock(modifier = Modifier.width(160.dp).height(14.dp))
            Spacer(modifier = Modifier.height(8.dp))
            ShimmerBlock(modifier = Modifier.width(100.dp).height(11.dp))
        }
    }
}

/** [count] rows of [RowPlaceholder], stacked - the loading state for any track/collection list. */
@Composable
fun ListPlaceholder(modifier: Modifier = Modifier, count: Int = 6) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        repeat(count) { RowPlaceholder() }
    }
}

/** Square-artwork placeholder matching [LibraryScreen]'s grid-cell layout. */
@Composable
fun GridTilePlaceholder(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        ShimmerBlock(
            modifier = Modifier.fillMaxWidth().height(140.dp),
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(modifier = Modifier.height(6.dp))
        ShimmerBlock(modifier = Modifier.fillMaxWidth(0.8f).height(12.dp))
    }
}
