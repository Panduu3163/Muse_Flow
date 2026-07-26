package com.example.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.AlbumPalette
import com.example.BackgroundStyle
import com.example.NowPlayingState

/**
 * The compact now-playing bar that sits directly above the navigation bar.
 *
 * Slides in only once something is actually loaded, so the tab bar isn't permanently pushed up by
 * an empty player on a fresh install.
 *
 * [backgroundStyle]/[palette] mirror Now Playing's own background treatment (same
 * [BackgroundStyle] enum, same [AlbumPalette]) rather than a flat theme colour, so the bar reads as
 * an extension of whatever's playing instead of a fixed grey card - the "black background" a flat
 * `surfaceContainerHighest` reads as in dark theme is gone even on [BackgroundStyle.Solid], since
 * that style now tints itself from the artwork's own dominant colour instead of a theme constant.
 */
@Composable
fun MiniPlayer(
    state: NowPlayingState,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundStyle: BackgroundStyle = BackgroundStyle.Solid,
    palette: AlbumPalette? = null,
) {
    AnimatedVisibility(
        visible = state.hasMedia,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onClick)
                .testTag("mini_player"),
            color = Color.Transparent,
            shadowElevation = 6.dp,
        ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // matchParentSize(), not fillMaxSize() - the Box has no bounded height of its own (the
            // Column below is what determines it), so a fillMaxSize() sibling would ask for "as
            // much height as the parent allows" independently of the Column's actual content,
            // which stretched the whole bar to nearly the full screen. matchParentSize() instead
            // sizes to whatever the Box ends up being once the Column is measured.
            MiniPlayerBackground(
                modifier = Modifier.matchParentSize(),
                style = backgroundStyle,
                palette = palette,
                artworkUrl = state.artworkUrl,
            )

            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.artworkUrl != null) {
                            AsyncImage(
                                model = state.artworkUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(44.dp),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                    ) {
                        Text(
                            text = state.title.ifBlank { "Loading…" },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = state.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    IconButton(
                        onClick = onPrevious,
                        modifier = Modifier.testTag("mini_player_previous"),
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous track",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier.testTag("mini_player_play_pause"),
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = onNext) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next track",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerLow,
                )
            }
        }
        }
    }
}

/**
 * The painted layer behind the compact bar - the same four [BackgroundStyle]s Now Playing offers,
 * scaled down for a short wide bar instead of a full screen. Every branch falls back to a plain
 * theme surface color when there's no [palette]/[artworkUrl] yet, so the bar never flashes
 * unpainted on the first frame after a track loads.
 */
@Composable
private fun MiniPlayerBackground(
    modifier: Modifier,
    style: BackgroundStyle,
    palette: AlbumPalette?,
    artworkUrl: String?,
) {
    val base = MaterialTheme.colorScheme.surfaceContainerHighest

    when {
        style == BackgroundStyle.Gradient && palette != null -> Box(
            modifier = modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            palette.dominant.copy(alpha = 0.55f).compositeOver(base),
                            palette.muted.copy(alpha = 0.4f).compositeOver(base),
                        ),
                    ),
                ),
        )

        style == BackgroundStyle.Blur && artworkUrl != null -> Box(modifier = modifier) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(24.dp),
            )
            Box(modifier = Modifier.fillMaxSize().background(base.copy(alpha = 0.55f)))
        }

        // BackgroundStyle.Solid, BackgroundStyle.Glow (removed - was here, to be
        // reimplemented later), or a Gradient/Blur requested before the palette/artwork
        // arrived - an artwork-tinted flat colour rather than a fixed theme grey, so even the
        // plain style reads as "this bar belongs to this song" instead of a constant black card.
        palette != null -> Box(
            modifier = modifier
                .background(palette.dominant.copy(alpha = 0.35f).compositeOver(base)),
        )

        else -> Box(modifier = modifier.background(base))
    }
}

