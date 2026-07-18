package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A playlist's cover: its real [coverImageUrl] when it has one (set only for playlists imported
 * from an online source - see [PlaylistRepository.importOnlinePlaylist]), otherwise a collage
 * generated from [tracks]' own cover art - exactly like Spotify/Apple Music-style generated
 * playlist covers. Cell count follows track count, not how many of those tracks actually have
 * real art (a track missing one just shows its own gradient fallback tile, same as anywhere else
 * [TrackArtwork] is used):
 * - 4+ tracks: a full 2x2 grid from the first 4
 * - exactly 3: a 2x2 grid with the 4th cell left empty
 * - exactly 2: two cells side by side filling the whole square, no empty space
 * - exactly 1: a single full-size cover, no grid at all
 * - 0: the same gradient+emoji placeholder every empty [TrackArtwork] falls back to
 */
@Composable
fun PlaylistCoverArt(
    coverImageUrl: String?,
    tracks: List<Track>,
    fallbackGradient: List<Color>,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp)
) {
    when {
        coverImageUrl != null -> TrackArtwork(
            imageUrl = coverImageUrl,
            gradientColors = fallbackGradient,
            shape = shape,
            modifier = modifier
        ) {
            Text("🎵", fontSize = 32.sp)
        }
        tracks.isEmpty() -> TrackArtwork(
            imageUrl = null,
            gradientColors = fallbackGradient,
            shape = shape,
            modifier = modifier
        ) {
            Text("🎵", fontSize = 32.sp)
        }
        tracks.size == 1 -> TrackArtwork(
            imageUrl = tracks[0].imageUrl,
            gradientColors = MusicData.Gradients[tracks[0].gradientIndex % MusicData.Gradients.size],
            shape = shape,
            modifier = modifier
        ) {
            Text("🎵", fontSize = 32.sp)
        }
        tracks.size == 2 -> Row(modifier = modifier.clip(shape)) {
            CoverCell(tracks[0], Modifier.weight(1f).fillMaxHeight())
            CoverCell(tracks[1], Modifier.weight(1f).fillMaxHeight())
        }
        tracks.size == 3 -> Column(modifier = modifier.clip(shape)) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                CoverCell(tracks[0], Modifier.weight(1f).fillMaxHeight())
                CoverCell(tracks[1], Modifier.weight(1f).fillMaxHeight())
            }
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                CoverCell(tracks[2], Modifier.weight(1f).fillMaxHeight())
                // 4th cell intentionally left empty - exactly 3 tracks never fakes a 4th cover.
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                )
            }
        }
        else -> { // 4+
            val four = tracks.take(4)
            Column(modifier = modifier.clip(shape)) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    CoverCell(four[0], Modifier.weight(1f).fillMaxHeight())
                    CoverCell(four[1], Modifier.weight(1f).fillMaxHeight())
                }
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    CoverCell(four[2], Modifier.weight(1f).fillMaxHeight())
                    CoverCell(four[3], Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
    }
}

/** One quadrant of the collage - unshaped (the outer container already clips to [PlaylistCoverArt]'s
 * overall [Shape]), just this track's real art or its own gradient fallback. */
@Composable
private fun CoverCell(track: Track, modifier: Modifier = Modifier) {
    TrackArtwork(
        imageUrl = track.imageUrl,
        gradientColors = MusicData.Gradients[track.gradientIndex % MusicData.Gradients.size],
        shape = androidx.compose.ui.graphics.RectangleShape,
        modifier = modifier
    ) {
        Text("🎵", fontSize = 14.sp)
    }
}
