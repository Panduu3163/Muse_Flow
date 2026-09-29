package com.example.ui.component

import androidx.compose.foundation.background
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.painterResource
import com.example.AppSettingsState
import com.example.AlbumPalette
import com.example.NowPlayingState
import com.example.R
import com.example.BackgroundStyle
import com.example.PlayerButtonColorOption
import com.example.PlayerSliderStyle
import com.example.ui.theme.MuseFlowShapes
import com.example.ui.theme.MuseFlowSpacing
import com.example.ui.theme.LocalReducedMotion

/** A local sample that updates immediately as appearance choices change. */
@Composable
fun AppearanceLivePreview(settings: AppSettingsState, modifier: Modifier = Modifier) {
    PreviewSurface("Appearance preview", modifier) {
        Text("Made for your music", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(MuseFlowSpacing.medium), verticalAlignment = Alignment.CenterVertically) {
            Image(painter = painterResource(R.drawable.preview_album_art), contentDescription = null,
                modifier = Modifier.size((settings.gridCellSize.homeSizeDp / 3).dp)
                    .clip(RoundedCornerShape(MuseFlowShapes.control)))
            Column(Modifier.weight(1f)) {
                Text("Sample track", style = MaterialTheme.typography.titleMedium)
                Text("MuseFlow artist", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MuseFlowSpacing.small)) {
            listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary,
                MaterialTheme.colorScheme.tertiary).forEach { color ->
                Box(Modifier.weight(1f).height(6.dp).background(color, RoundedCornerShape(50)))
            }
        }
        Text("${settings.fontStyle.label} type · ${settings.displayDensity.label} spacing · ${settings.gridCellSize.label} cards",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Illustrates the selected surface and control treatment without starting playback. */
@Composable
fun PlayerLivePreview(settings: AppSettingsState, modifier: Modifier = Modifier) {
    val previewRotation = if (settings.rotatingThumbnailAnimation && !LocalReducedMotion.current) {
        val transition = rememberInfiniteTransition(label = "preview_artwork_rotation")
        val angle by transition.animateFloat(0f, 360f,
            infiniteRepeatable(tween(20_000, easing = LinearEasing), RepeatMode.Restart),
            label = "preview_artwork_angle")
        angle
    } else 0f
    val accent = when (settings.playerButtonColor) {
        PlayerButtonColorOption.Primary -> MaterialTheme.colorScheme.primary
        PlayerButtonColorOption.Secondary -> MaterialTheme.colorScheme.secondary
        PlayerButtonColorOption.Tertiary -> MaterialTheme.colorScheme.tertiary
    }
    val artColor = MaterialTheme.colorScheme.tertiaryContainer
    val backdrop = when (settings.playerBackgroundStyle) {
        BackgroundStyle.Solid -> Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.surfaceContainerHigh))
        BackgroundStyle.Gradient -> Brush.verticalGradient(listOf(artColor, MaterialTheme.colorScheme.surfaceContainerHigh))
        BackgroundStyle.Blur -> Brush.verticalGradient(listOf(artColor.copy(alpha = .65f), MaterialTheme.colorScheme.surfaceContainerHigh))
        BackgroundStyle.LiveMesh -> Brush.linearGradient(listOf(artColor, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surfaceContainerHigh))
        BackgroundStyle.GlowAnimated -> Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.surfaceContainerHigh, artColor, MaterialTheme.colorScheme.surfaceContainerHigh))
        BackgroundStyle.AppleMusic -> Brush.verticalGradient(listOf(artColor.copy(alpha = .8f), artColor.copy(alpha = .4f), MaterialTheme.colorScheme.surfaceContainerHigh))
    }
    PreviewSurface("Player preview · sample artwork", modifier) {
        Box(Modifier.fillMaxWidth().heightIn(min = 250.dp)
            .clip(RoundedCornerShape(MuseFlowShapes.control))) {
            when (settings.playerBackgroundStyle) {
                BackgroundStyle.GlowAnimated -> AlbumGlowBackground(
                    palette = AlbumPalette(Color(0xFF7952C7), Color(0xFFEC7287), Color(0xFFFFCA8A)),
                    base = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.matchParentSize(), immersive = true,
                )
                BackgroundStyle.AppleMusic -> {
                    Image(painter = painterResource(R.drawable.preview_album_art), contentDescription = null,
                        modifier = Modifier.matchParentSize().blur(28.dp), contentScale = ContentScale.Crop)
                    Image(painter = painterResource(R.drawable.preview_album_art), contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(140.dp), contentScale = ContentScale.Crop)
                    Box(Modifier.matchParentSize().background(Brush.verticalGradient(
                        0f to MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .72f),
                        .45f to MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .28f),
                        .7f to MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .86f),
                        1f to MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = .94f),
                    )))
                }
                else -> Box(Modifier.matchParentSize().background(backdrop))
            }
        Column(Modifier.fillMaxWidth().padding(MuseFlowSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(MuseFlowSpacing.small)) {
            if (!settings.hidePlayerThumbnail) {
                Box(Modifier.size(88.dp).align(Alignment.CenterHorizontally)
                    .clip(if (settings.rotatingThumbnailAnimation) CircleShape
                    else RoundedCornerShape(settings.thumbnailCornerRadius.dp))) {
                    Image(painter = painterResource(R.drawable.preview_album_art), contentDescription = null,
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            if (settings.rotatingThumbnailAnimation) {
                                scaleX = 1.42f; scaleY = 1.42f; rotationZ = previewRotation
                            }
                        }, contentScale = ContentScale.Crop)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sample track", style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = accent)
            }
            when (settings.playerSliderStyle) {
                PlayerSliderStyle.Wavy -> WavySeekBar(
                    progress = .4f, onSeek = {}, playing = true,
                    activeColor = accent, inactiveColor = MaterialTheme.colorScheme.outlineVariant,
                    interactive = false,
                )
                PlayerSliderStyle.Squiggly -> SquigglySlider(
                    progress = .4f, onSeek = {}, playing = true,
                    activeColor = accent, inactiveColor = MaterialTheme.colorScheme.outlineVariant,
                    visibleCycles = 4f, phaseDurationMs = 3200, pillThumb = true,
                    interactive = false,
                )
                PlayerSliderStyle.Slim -> SlimSeekBar(progress = .4f, onSeek = {}, interactive = false,
                    activeColor = accent, inactiveColor = MaterialTheme.colorScheme.outlineVariant)
                else -> Box(Modifier.fillMaxWidth().height(5.dp)
                    .clip(RoundedCornerShape(4.dp)).background(accent))
            }
            Text("${settings.playerSliderStyle.label} · ${settings.playerTransportStyle.label}",
                style = MaterialTheme.typography.labelSmall, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
        }
    }
}

@Composable
fun MiniPlayerLivePreview(settings: AppSettingsState, modifier: Modifier = Modifier) {
    val palette = remember { AlbumPalette(
        dominant = Color(0xFF7952C7), muted = Color(0xFFEC7287),
        vibrant = Color(0xFFFFCA8A), accent = Color(0xFF7952C7),
    ) }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(MuseFlowSpacing.small)) {
        Text("Mini player preview", style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = MuseFlowSpacing.medium))
        Box(Modifier.clearAndSetSemantics { contentDescription = "Mini player appearance preview" }) {
            MiniPlayer(
                state = NowPlayingState(title = "Sample track", artist = "MuseFlow",
                    hasMedia = true, positionMs = 42_000, durationMs = 180_000),
                onTogglePlayPause = {}, onPrevious = {}, onNext = {}, onClick = {},
                backgroundStyle = settings.miniPlayerBackgroundStyle, palette = palette,
                artworkModel = R.drawable.preview_album_art,
            )
        }
        Text("Demo artwork · controls are preview only", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = MuseFlowSpacing.medium))
    }
}

@Composable
private fun PreviewSurface(title: String, modifier: Modifier, content: @Composable () -> Unit) {
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(MuseFlowShapes.card),
        color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(MuseFlowSpacing.medium), verticalArrangement = Arrangement.spacedBy(MuseFlowSpacing.medium)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}
