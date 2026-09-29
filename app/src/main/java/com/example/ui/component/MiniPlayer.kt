package com.example.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import com.example.ui.utils.bounceClick
import com.example.ui.utils.slowMarquee
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
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
    artworkModel: Any? = state.artworkUrl,
) {
    // Keep an 8dp inset beyond the nav pill, but preserve text room on narrow devices.
    val sideInset = when {
        LocalConfiguration.current.screenWidthDp < 340 -> 24.dp
        LocalConfiguration.current.screenWidthDp < 380 -> 32.dp
        else -> 40.dp
    }
    AnimatedVisibility(
        visible = state.hasMedia,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = sideInset)
                .liquidSurface(50.dp)
                .bounceClick(onClick = onClick)
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
                artworkModel = artworkModel,
            )

            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Keyed on title+artist (no stable track id in this state) so a genuine track
                    // change - not just a metadata refresh of the same track - triggers the
                    // transition, subtle since this is chrome seen dozens of times a session.
                    androidx.compose.animation.AnimatedContent(
                        targetState = state.title to state.artist,
                        transitionSpec = {
                            (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(250)) +
                                androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(250)) { it / 3 })
                                .togetherWith(
                                    androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200)) +
                                        androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(200)) { -it / 3 }
                                )
                        },
                        label = "mini_player_artwork_crossfade",
                    ) { (_, _) ->
                        val ringColor = MaterialTheme.colorScheme.primary
                        val ringTrack = MaterialTheme.colorScheme.onSurface.copy(alpha = .22f)
                        Box(
                            modifier = Modifier.size(50.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier.size(43.dp).clip(RoundedCornerShape(50))
                                    .background(MaterialTheme.colorScheme.surfaceContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (artworkModel != null) {
                                    AsyncImage(
                                        model = artworkModel,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
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
                            Canvas(Modifier.fillMaxSize().testTag("mini_player_artwork_progress")) {
                                val width = 2.dp.toPx()
                                val stroke = Stroke(width = width, cap = StrokeCap.Round)
                                val inset = width / 2f + 1.dp.toPx()
                                val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
                                val arcOrigin = Offset(inset, inset)
                                drawArc(ringTrack, -90f, 360f, false,
                                    topLeft = arcOrigin, size = arcSize, style = stroke)
                                if (state.progress > 0f) {
                                    drawArc(ringColor, -90f, state.progress.coerceIn(0f, 1f) * 360f,
                                        false, topLeft = arcOrigin, size = arcSize, style = stroke)
                                }
                            }
                        }
                    }

                    androidx.compose.animation.AnimatedContent(
                        targetState = state.title to state.artist,
                        transitionSpec = {
                            (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(250)) +
                                androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(250)) { it / 3 })
                                .togetherWith(
                                    androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200)) +
                                        androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(200)) { -it / 3 }
                                )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp),
                        label = "mini_player_text_crossfade",
                    ) { (title, artist) ->
                        Column {
                            Text(
                                text = title.ifBlank { "Loading…" },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.slowMarquee(),
                            )
                            Text(
                                text = artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.slowMarquee(),
                            )
                        }
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

            }
        }
        }
    }
}

/**
 * The painted layer behind the compact bar - the same four [BackgroundStyle]s Now Playing offers,
 * scaled down for a short wide bar instead of a full screen. Every branch falls back to a plain
 * theme surface color when there's no [palette]/[artworkModel] yet, so the bar never flashes
 * unpainted on the first frame after a track loads.
 */
@Composable
private fun MiniPlayerBackground(
    modifier: Modifier,
    style: BackgroundStyle,
    palette: AlbumPalette?,
    artworkModel: Any?,
) {
    val base = MaterialTheme.colorScheme.surfaceContainerHighest

    when {
        style == BackgroundStyle.GlowAnimated && palette != null ->
            AlbumGlowBackground(palette = palette, base = base, modifier = modifier)

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

        style == BackgroundStyle.Blur && artworkModel != null -> Box(modifier = modifier) {
            AsyncImage(
                model = artworkModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(24.dp),
            )
            Box(modifier = Modifier.fillMaxSize().background(base.copy(alpha = 0.55f)))
        }

        // A single slow-rotating blurred/saturated copy - one layer rather than Now Playing's
        // three, matching Echo-Music's own MiniPlayer LIVE_MESH branch (MiniPlayer.kt) exactly:
        // container-level 1.5x oversize, 40dp blur, 60s rotation, 128x128 software-decoded
        // source. The large blur radius is what actually hides the rotation's edge (a smaller one
        // was tried for frame-rate headroom, but stops masking the seam - see NowPlayingScreen's
        // LiveMeshBackground doc for the fuller reasoning), so this deliberately matches Echo's
        // real number rather than a lighter one.
        style == BackgroundStyle.LiveMesh && artworkModel != null -> Box(modifier = modifier) {
            // A fully OPAQUE base fill first, painted before anything else - this bar's own outer
            // Surface is Color.Transparent (see MiniPlayer's call site), so without a solid layer
            // under the rotating image, any gap the single 1.5x-scaled layer doesn't cover at some
            // rotation angle shows straight through to whatever's on screen behind the bar, which
            // is what actually read as "the mini player looks transparent." Echo-Music's own
            // MiniPlayer never has this problem for exactly this reason: its outer container always
            // paints a genuinely opaque `backgroundColor` first (`MiniPlayer.kt`, the Box wrapping
            // `MiniPlayerBackgroundLayer`), with every background style layered on top of that, not
            // relying on the style's own layer to provide full coverage by itself.
            Box(modifier = Modifier.fillMaxSize().background(base))

            if (com.example.ui.theme.LocalReducedMotion.current) {
                AsyncImage(
                    model = artworkModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(40.dp),
                )
                return@Box
            }

            val rotation by rememberInfiniteTransition(label = "mini_live_mesh").animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(60_000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "mini_live_mesh_rotation",
            )
            val context = androidx.compose.ui.platform.LocalContext.current
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.5f
                        scaleY = 1.5f
                    },
            ) {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(context)
                        .data(artworkModel)
                        .size(128, 128)
                        .allowHardware(false)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.6f) }) },
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(40.dp)
                        .graphicsLayer { rotationZ = rotation },
                )
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
            }
            Box(modifier = Modifier.fillMaxSize().background(base.copy(alpha = 0.4f)))
        }

        // BackgroundStyle.Solid, or a Gradient/Blur/LiveMesh requested before the
        // palette/artwork arrived - an artwork-tinted flat colour rather than a fixed theme grey,
        // so even the plain style reads as "this bar belongs to this song" instead of a constant
        // black card.
        palette != null -> Box(
            modifier = modifier
                .background(palette.dominant.copy(alpha = 0.35f).compositeOver(base)),
        )

        else -> Box(modifier = modifier.background(base))
    }
}
