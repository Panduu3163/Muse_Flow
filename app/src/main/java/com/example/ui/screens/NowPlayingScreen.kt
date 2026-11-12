package com.example.ui.screens

import coil3.request.crossfade

import coil3.request.allowHardware

import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.example.ui.utils.feedbackClickable
import com.example.ui.utils.MotionIcon
import com.example.ui.utils.MicroIconButton
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import coil3.imageLoader
import com.example.ui.component.PlayerBufferingIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import coil3.compose.AsyncImage
import coil3.compose.rememberAsyncImagePainter
import com.example.AlbumPalette
import com.example.BackgroundStyle
import com.example.NowPlayingState
import com.example.PlayerTransportStyle
import com.example.QueueItem
import com.example.asPlaybackTime
import androidx.compose.foundation.clickable
import com.example.ui.component.SquigglySlider
import com.example.ui.component.WavySeekBar
import com.example.ui.component.SlimSeekBar
import com.example.ui.component.SleepTimerDialog
import com.example.ui.component.sleepTimerRemainingLabel
import com.example.ui.utils.bounceClick
import com.example.ui.utils.slowMarquee
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * The full-screen player: large artwork, a seekable progress bar, transport controls, and a
 * toggleable queue.
 *
 * Stateless with respect to playback - every control calls back into `PlayerViewModel`, which owns
 * the `MediaController`. That keeps this screen and the mini-player showing the same truth without
 * either of them holding player state of their own.
 */
@Composable
fun NowPlayingScreen(
    state: NowPlayingState,
    modifier: Modifier = Modifier,
    playbackFailure: String? = null,
    artworkRetryRevision: Int = 0,
    onRetryPlayback: () -> Unit = {},
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onPlayQueueItem: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onUndoRemoveQueueItem: () -> Unit,
    onClearUpcomingQueue: () -> Unit,
    onAddRadio: ((Int) -> Unit) -> Unit,
    onSaveQueueAsPlaylist: suspend (String) -> Boolean,
    onCollapse: () -> Unit,
    isLiked: Boolean = false,
    isDownloaded: Boolean = false,
    downloadProgress: Int? = null,
    onToggleLike: () -> Unit = {},
    onDownload: () -> Unit = {},
    hideArtwork: Boolean = false,
    artworkCornerRadius: Int = 20,
    cropArtwork: Boolean = true,
    rotatingArtwork: Boolean = false,
    wavySlider: Boolean = false,
    slimSlider: Boolean = false,
    squigglySlider: Boolean = false,
    swipeToChangeSongEnabled: Boolean = true,
    showCodecInfo: Boolean = false,
    transportStyle: PlayerTransportStyle = PlayerTransportStyle.Static,
    backgroundStyle: BackgroundStyle = BackgroundStyle.Solid,
    palette: AlbumPalette? = null,
    buttonColor: Color = Color.Unspecified,
    sleepTimerRemainingMs: Long? = null,
    onStartSleepTimer: (Int) -> Unit = {},
    onCancelSleepTimer: () -> Unit = {},
    onSetPlaybackSpeed: (Float, Float) -> Unit = { _, _ -> },
    /** Opens the full track actions sheet (add to playlist/share/details/view artist/view album) -
     * the "player menu" the gap audit flagged as entirely missing, reusing the same sheet every
     * other track list already opens rather than inventing a second, narrower one. */
    onOpenMenu: () -> Unit = {},
    onOpenQueueTrackMenu: (QueueItem) -> Unit = {},
    onGoToArtist: (String) -> Unit = {},
    onResolveArtistName: (String) -> Unit = {},
    /** Rendered in place of the artwork when the lyrics toggle is on. Passed as a slot so this
     * screen stays free of lyrics fetching and its ViewModel. */
    lyricsContent: @Composable (Modifier) -> Unit = {},
) {
    var showLyrics by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        key(artworkRetryRevision) {
            PlayerBackground(
                style = backgroundStyle,
                palette = palette,
                artworkUrl = state.artworkUrl,
                isPlaying = state.isPlaying,
            )
        }
        PlayerContent(
            state = state,
            artworkRetryRevision = artworkRetryRevision,
            onTogglePlayPause = onTogglePlayPause,
            onNext = onNext,
            onPrevious = onPrevious,
            onSeek = onSeek,
            onToggleShuffle = onToggleShuffle,
            onCycleRepeat = onCycleRepeat,
            onPlayQueueItem = onPlayQueueItem,
            onMoveQueueItem = onMoveQueueItem,
            onRemoveQueueItem = onRemoveQueueItem,
            onUndoRemoveQueueItem = onUndoRemoveQueueItem,
            onClearUpcomingQueue = onClearUpcomingQueue,
            onAddRadio = onAddRadio,
            onSaveQueueAsPlaylist = onSaveQueueAsPlaylist,
            onCollapse = onCollapse,
            onOpenMenu = onOpenMenu,
            onOpenQueueTrackMenu = onOpenQueueTrackMenu,
            onGoToArtist = onGoToArtist,
            onResolveArtistName = onResolveArtistName,
            isLiked = isLiked,
            isDownloaded = isDownloaded,
            isLocalDevice = state.isLocalDevice,
            downloadProgress = downloadProgress,
            onToggleLike = onToggleLike,
            onDownload = onDownload,
            hideArtwork = hideArtwork,
            artworkCornerRadius = artworkCornerRadius,
            cropArtwork = cropArtwork,
            rotatingArtwork = rotatingArtwork,
            wavySlider = wavySlider,
            slimSlider = slimSlider,
            squigglySlider = squigglySlider,
            swipeToChangeSongEnabled = swipeToChangeSongEnabled,
            showCodecInfo = showCodecInfo,
            transportStyle = transportStyle,
            backgroundStyle = backgroundStyle,
            palette = palette,
            buttonColor = buttonColor,
            sleepTimerRemainingMs = sleepTimerRemainingMs,
            onStartSleepTimer = onStartSleepTimer,
            onCancelSleepTimer = onCancelSleepTimer,
            onSetPlaybackSpeed = onSetPlaybackSpeed,
            lyricsContent = lyricsContent,
            showLyrics = showLyrics,
            onShowLyricsChange = { showLyrics = it },
        )
        if (playbackFailure != null) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 60.dp).fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                shadowElevation = 12.dp,
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(13.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.13f)) {
                        Icon(Icons.Default.CloudOff, contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(9.dp).size(22.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Playback interrupted", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text(playbackFailure, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Button(onClick = onRetryPlayback) { Text("Retry") }
                }
            }
        }
    }
}

/**
 * The painted layer behind the player.
 *
 * [BackgroundStyle.Gradient] uses the artwork's own dominant/muted colours; [BackgroundStyle.Blur]
 * uses the artwork itself, blurred and dimmed. Both fall back to the plain theme background when no
 * palette or artwork is available yet, so the screen never flashes an unpainted state.
 */
@Composable
private fun PlayerBackground(
    style: BackgroundStyle,
    palette: AlbumPalette?,
    artworkUrl: String?,
    isPlaying: Boolean,
) {
    val base = MaterialTheme.colorScheme.background

    when {
        style == BackgroundStyle.GlowAnimated ->
            com.example.ui.component.AlbumGlowBackground(
                palette = palette ?: AlbumPalette(
                    dominant = MaterialTheme.colorScheme.primary,
                    muted = MaterialTheme.colorScheme.secondary,
                    vibrant = MaterialTheme.colorScheme.tertiary,
                ),
                base = base,
                immersive = true,
            )

        style == BackgroundStyle.Gradient && palette != null -> {
            val top by animateColorAsState(palette.dominant, label = "bg_top")
            val bottom by animateColorAsState(palette.muted, label = "bg_bottom")
            val middle by animateColorAsState(palette.vibrant, label = "bg_middle")
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            // Blended toward the base so text contrast stays usable regardless of
                            // how bright the artwork happens to be.
                            listOf(
                                top.copy(alpha = 0.55f).compositeOver(base),
                                middle.copy(alpha = 0.32f).compositeOver(base),
                                bottom.copy(alpha = 0.35f).compositeOver(base),
                                base,
                            )
                        )
                    )
            )
        }

        style == BackgroundStyle.LiveMesh && artworkUrl != null -> LiveMeshBackground(artworkUrl = artworkUrl, base = base)

        style == BackgroundStyle.AppleMusic && artworkUrl != null -> {
            val breatheState = if (isPlaying && !com.example.ui.theme.LocalReducedMotion.current) {
                val transition = rememberInfiniteTransition(label = "apple_backdrop_breathe")
                transition.animateFloat(1.30f, 1.40f,
                    infiniteRepeatable(tween(12_000, easing = LinearEasing), RepeatMode.Reverse),
                    label = "apple_backdrop_scale")
            } else null
            val fallbackTop = palette?.dominant ?: MaterialTheme.colorScheme.primaryContainer
            val fallbackBottom = palette?.muted ?: MaterialTheme.colorScheme.secondaryContainer
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                listOf(fallbackTop.copy(alpha = .45f).compositeOver(base),
                    fallbackBottom.copy(alpha = .35f).compositeOver(base), base),
            ))) {
                AnimatedContent(
                    targetState = artworkUrl,
                    transitionSpec = { fadeIn(tween(850)) togetherWith fadeOut(tween(850)) },
                    label = "apple_artwork_background",
                ) { url ->
                    Box(Modifier.fillMaxSize()) {
                        // Echo's useful idea is clear cover art dissolving into a blurred copy.
                        // MuseFlow uses the current artwork only, with no external Canvas video.
                        AsyncImage(
                            model = url, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().graphicsLayer {
                                val breathe = breatheState?.value ?: 1.35f
                                scaleX = breathe
                                scaleY = breathe
                            }.blur(110.dp),
                        )
                        AsyncImage(
                            model = url, contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().fillMaxHeight(.66f)
                                .graphicsLayer {
                                    compositingStrategy = CompositingStrategy.Offscreen
                                }
                                .drawWithContent {
                                    drawContent()
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            0f to Color.Black,
                                            .73f to Color.Black,
                                            .91f to Color.Black.copy(alpha = .35f),
                                            1f to Color.Transparent,
                                        ),
                                        blendMode = BlendMode.DstIn,
                                    )
                                },
                        )
                    }
                }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                    0f to base.copy(alpha = .66f),
                    .18f to base.copy(alpha = .27f),
                    .45f to base.copy(alpha = .43f),
                    .62f to base.copy(alpha = .85f),
                    1f to base.copy(alpha = .93f),
                )))
            }
        }

        style == BackgroundStyle.Blur && artworkUrl != null -> {
            Box(modifier = Modifier.fillMaxSize().background(base)) {
                // Keyed on the URL so a track change cross-fades the blurred backdrop in/out
                // instead of the new cover popping in over the old one mid-frame.
                AnimatedContent(
                    targetState = artworkUrl,
                    transitionSpec = {
                        fadeIn(tween(700)) togetherWith fadeOut(tween(700))
                    },
                    label = "blur_bg_crossfade",
                ) { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(64.dp)
                            .alpha(0.68f),
                    )
                }
                // Scrim: a blurred cover is still busy enough to hurt text legibility.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(base.copy(alpha = 0.50f))
                )
            }
        }

        else -> Box(modifier = Modifier.fillMaxSize().background(base))
    }
}

/**
 * [BackgroundStyle.LiveMesh]: three blurred, saturated copies of the current artwork, each
 * rotating independently at its own slow speed. Ported directly from Echo-Music's own
 * `LIVE_MESH`/`LIQUID_GLASS` player background (`Player.kt`'s `PlayerBackgroundStyle.LIVE_MESH,
 * PlayerBackgroundStyle.LIQUID_GLASS ->` branch) rather than derived independently - their real
 * numbers (1.7x container-level oversize, 100-120dp blur, 128x128 software-decoded source) are
 * what actually keeps the rotation's edge hidden. A smaller blur radius (tried in an earlier pass,
 * for frame-rate headroom) technically still covers every pixel but stops masking the seam: a
 * large blur radius smears the geometric edge into invisibility, which is the real mechanism at
 * work here, not any particular oversize-scale formula - so this deliberately does NOT chase a
 * tighter one.
 */
@Composable
private fun LiveMeshBackground(artworkUrl: String, base: Color) {
    val context = LocalContext.current
    if (com.example.ui.theme.LocalReducedMotion.current) {
        Box(modifier = Modifier.fillMaxSize().background(base)) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(100.dp),
            )
        }
        return
    }
    val infiniteTransition = rememberInfiniteTransition(label = "liveMeshRotation")
    val anchorRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(animation = tween(80_000, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "anchorRotation",
    )
    val fastRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(40_000, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "fastRotation",
    )
    val slowRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(60_000, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "slowRotation",
    )
    val saturatedColorFilter = remember {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.8f) })
    }
    val imageRequest = remember(artworkUrl) {
        coil3.request.ImageRequest.Builder(context)
            .data(artworkUrl)
            .size(128, 128)
            .allowHardware(false)
            .build()
    }

    Box(modifier = Modifier.fillMaxSize().background(base)) {
        // Cross-fades the whole mesh on track change, rather than each layer's own AsyncImage
        // popping to the new artwork independently.
        AnimatedContent(
            targetState = imageRequest,
            transitionSpec = { fadeIn(tween(1500)) togetherWith fadeOut(tween(1500)) },
            label = "liveMeshBackground",
        ) { request ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.7f
                        scaleY = 1.7f
                    },
            ) {
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = saturatedColorFilter,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(100.dp)
                        .graphicsLayer { rotationZ = anchorRotation },
                )
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = saturatedColorFilter,
                    alignment = Alignment.TopStart,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(120.dp)
                        .graphicsLayer {
                            rotationZ = fastRotation
                            alpha = 0.6f
                        },
                )
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = saturatedColorFilter,
                    alignment = Alignment.BottomEnd,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(120.dp)
                        .graphicsLayer {
                            rotationZ = slowRotation
                            alpha = 0.5f
                        },
                )
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.2f)))
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.25f)))),
                )
            }
        }
    }
}

/**
 * Swipe left/right on the artwork to skip tracks, swipe up/down to adjust the *device's* media
 * volume (via [AudioManager], the same stream the hardware buttons control) - not
 * [MediaController.volume], which is already driven by crossfade's fade-out/fade-in (see
 * [PlayerViewModel.applyCrossfadeVolume]); fighting over that property would make a swipe get
 * silently overwritten by the next 500ms crossfade tick.
 *
 * A drag is classified once by whichever axis moved further at gesture start (read from a
 * [remember]ed [mutableStateOf], not a `val` closed over from the composition - see the documented
 * pointerInput gotcha in `docs/SESSION-HANDOFF.md` §3), so a mostly-vertical swipe never also
 * registers as a skip and vice versa. Horizontal commits once on release past a distance
 * threshold; vertical adjusts volume continuously as the finger moves, for the same immediate
 * feedback the hardware volume buttons give.
 */
@Composable
private fun Modifier.artworkSwipeGestures(
    onHorizontalDrag: (Float) -> Unit,
    onHorizontalEnd: (Float) -> Unit,
    swipeToChangeSongEnabled: Boolean = true,
): Modifier {
    val context = LocalContext.current
    val audioManager = remember {
        context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
    }
    var axis by remember { mutableStateOf<Char?>(null) } // 'h' or 'v', decided once per gesture
    var horizontalAccum by remember { mutableFloatStateOf(0f) }
    var verticalAccum by remember { mutableFloatStateOf(0f) }
    val dragAction by rememberUpdatedState(onHorizontalDrag)
    val endAction by rememberUpdatedState(onHorizontalEnd)
    val swipeEnabled by rememberUpdatedState(swipeToChangeSongEnabled)

    return this.pointerInput(Unit) {
        detectDragGestures(
            onDragStart = {
                axis = null
                horizontalAccum = 0f
                verticalAccum = 0f
            },
            onDragEnd = {
                if (axis == 'h') endAction(horizontalAccum)
                axis = null
                horizontalAccum = 0f
            },
            onDragCancel = {
                if (axis == 'h') endAction(0f)
                axis = null
                horizontalAccum = 0f
            },
            onDrag = { change, dragAmount ->
                change.consume()
                if (axis == null) {
                    // Vertical volume-swipe always available regardless of the setting - only the
                    // horizontal skip gesture is what "swipe to change song" turns off.
                    axis = if (swipeEnabled && abs(dragAmount.x) > abs(dragAmount.y)) 'h' else 'v'
                }
                when (axis) {
                    'h' -> { horizontalAccum += dragAmount.x; dragAction(horizontalAccum) }
                    'v' -> {
                        verticalAccum += dragAmount.y
                        // One volume step per ~24px of vertical travel - up (negative dy) raises.
                        if (abs(verticalAccum) > 24f) {
                            val direction = if (verticalAccum < 0) {
                                android.media.AudioManager.ADJUST_RAISE
                            } else {
                                android.media.AudioManager.ADJUST_LOWER
                            }
                            audioManager.adjustStreamVolume(
                                android.media.AudioManager.STREAM_MUSIC,
                                direction,
                                android.media.AudioManager.FLAG_SHOW_UI,
                            )
                            verticalAccum = 0f
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun PlayerContent(
    state: NowPlayingState,
    artworkRetryRevision: Int,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Float) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onPlayQueueItem: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onUndoRemoveQueueItem: () -> Unit,
    onClearUpcomingQueue: () -> Unit,
    onAddRadio: ((Int) -> Unit) -> Unit,
    onSaveQueueAsPlaylist: suspend (String) -> Boolean,
    onCollapse: () -> Unit,
    isLiked: Boolean,
    isDownloaded: Boolean,
    isLocalDevice: Boolean = false,
    downloadProgress: Int?,
    onToggleLike: () -> Unit,
    onDownload: () -> Unit,
    hideArtwork: Boolean,
    artworkCornerRadius: Int,
    cropArtwork: Boolean,
    rotatingArtwork: Boolean,
    wavySlider: Boolean,
    slimSlider: Boolean,
    squigglySlider: Boolean = false,
    swipeToChangeSongEnabled: Boolean = true,
    showCodecInfo: Boolean = false,
    transportStyle: PlayerTransportStyle = PlayerTransportStyle.Static,
    backgroundStyle: BackgroundStyle,
    palette: AlbumPalette?,
    buttonColor: Color,
    sleepTimerRemainingMs: Long? = null,
    onStartSleepTimer: (Int) -> Unit = {},
    onCancelSleepTimer: () -> Unit = {},
    onSetPlaybackSpeed: (Float, Float) -> Unit = { _, _ -> },
    onOpenMenu: () -> Unit = {},
    onOpenQueueTrackMenu: (QueueItem) -> Unit = {},
    onGoToArtist: (String) -> Unit = {},
    onResolveArtistName: (String) -> Unit = {},
    lyricsContent: @Composable (Modifier) -> Unit,
    showLyrics: Boolean,
    onShowLyricsChange: (Boolean) -> Unit,
) {
    var showQueue by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var artworkDirection by remember { mutableIntStateOf(1) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Wide artwork must not push transport controls outside a short window. Large text
        // also needs scrollable content; keep nested lyrics bounded inside that scroll area.
        val compactWindow = maxHeight < 600.dp || LocalDensity.current.fontScale > 1.3f
        val mediaHeight = (maxHeight * 0.45f).coerceIn(120.dp, 320.dp)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .then(if (compactWindow) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .testTag("now_playing_screen"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MicroIconButton(onClick = onCollapse, modifier = Modifier.testTag("now_playing_collapse")) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse player",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.weight(1f))
                // Sleep timer/lyrics/queue moved out of this bar and into PlayerQuickActionsRow
                // (below the transport controls) - see its own doc for why.
                MicroIconButton(
                    onClick = { showSpeedDialog = true },
                    modifier = Modifier.testTag("now_playing_speed"),
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = "Playback speed",
                        tint = if (state.speed != 1f) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                MicroIconButton(onClick = onOpenMenu, modifier = Modifier.testTag("now_playing_menu")) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (showLyrics) {
                if (backgroundStyle == BackgroundStyle.AppleMusic) {
                    // Keep the same Apple backdrop visible through a light glass panel.
                    Surface(
                        modifier = (if (compactWindow) Modifier.height(mediaHeight) else Modifier.weight(1f)).fillMaxWidth().padding(vertical = 8.dp),
                        shape = RoundedCornerShape(24.dp),
                        color = if (MaterialTheme.colorScheme.background.luminance() < .5f)
                            Color.White.copy(alpha = .13f) else Color.Black.copy(alpha = .52f),
                        contentColor = Color.White,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .15f)),
                    ) {
                        lyricsContent(Modifier.fillMaxSize())
                    }
                } else {
                    lyricsContent(if (compactWindow) Modifier.height(mediaHeight).fillMaxWidth() else Modifier.weight(1f))
                }
            } else if (!hideArtwork) {
                Spacer(Modifier.weight(0.5f))
                Artwork(
                    imageUrl = state.artworkUrl,
                    artworkKey = "${state.queue.indexOfFirst { it.isCurrent }}:${state.currentPlaybackId ?: state.artworkUrl.orEmpty()}:$artworkRetryRevision",
                    isBuffering = state.isBuffering,
                    isPlaying = state.isPlaying,
                    rotating = rotatingArtwork,
                    transitionDirection = artworkDirection,
                    previousImageUrl = state.previousArtworkUrl,
                    nextImageUrl = state.nextArtworkUrl,
                    canPrevious = state.hasPrevious,
                    canNext = state.hasNext,
                    swipeEnabled = swipeToChangeSongEnabled,
                    onSwipeNext = { artworkDirection = 1; onNext() },
                    onSwipePrevious = { artworkDirection = -1; onPrevious() },
                    cornerRadius = artworkCornerRadius.dp,
                    cropToSquare = cropArtwork,
                    modifier = if (compactWindow) Modifier.size(mediaHeight)
                        else Modifier.fillMaxWidth().aspectRatio(1f),
                )
                Spacer(Modifier.weight(0.5f))
            } else {
                // Artwork hidden: absorb the freed space so the controls stay vertically centred
                // instead of collapsing to the top of the screen.
                Spacer(Modifier.weight(1f))
            }

            // Title/artist left-aligned, download+like grouped as one joined pill on the right -
            // ported from Echo-Music's own download/like button pair (`Player.kt`: shareShape/
            // favShape, asymmetric rounded corners so the two half-pills read as one shape) rather
            // than the previous like-left/title-center/download-right symmetric flanking.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.title.ifBlank { "Nothing playing" },
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth().slowMarquee(),
                    )
                    // One Text per credited artist (rather than a single Text for the whole joined
                    // string) so a multi-artist song's byline sends a tap on one name to *that*
                    // artist's page - a single shared clickable used to always open the first artist
                    // regardless of which name was actually tapped.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .clipToBounds()
                            .slowMarquee(),
                    ) {
                        state.artistCredits.forEachIndexed { index, credit ->
                            Text(
                                text = credit.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier
                                    .clickable {
                                        credit.id?.let(onGoToArtist) ?: onResolveArtistName(credit.name)
                                    }
                                    .testTag("now_playing_artist_name"),
                            )
                            if (index != state.artistCredits.lastIndex) {
                                Text(
                                    text = ", ",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.width(12.dp))

                val shareShape = RoundedCornerShape(topStart = 50.dp, bottomStart = 50.dp, topEnd = 4.dp, bottomEnd = 4.dp)
                val favShape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 50.dp, bottomEnd = 50.dp)
                val pillContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(shareShape)
                            .background(pillContainerColor)
                            .clickable(
                                // "Download" is a meaningless action for a file already on the device -
                                // never fetched through the app, nothing for this button to do.
                                enabled = state.hasMedia && !isLocalDevice && !isDownloaded && downloadProgress == null,
                                onClick = onDownload,
                            )
                            .testTag("now_playing_download"),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            isLocalDevice -> Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = "On this device",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            isDownloaded -> Icon(
                                imageVector = Icons.Default.DownloadDone,
                                contentDescription = "Downloaded",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            downloadProgress != null && downloadProgress >= 0 -> CircularProgressIndicator(
                                progress = { downloadProgress / 100f },
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            )
                            downloadProgress != null -> CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            else -> Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                    LikeButtonWithBurst(
                        isLiked = isLiked,
                        // Liking a local file was landing in Liked with no reliable way back to
                        // playing it from there (see LibraryScreen's own doc on that gap) - disabled
                        // here rather than trying to make that round trip work everywhere it can be
                        // reached from. Still tappable to *un*like one already liked from before this
                        // existed, so that isn't a dead end.
                        enabled = state.hasMedia && (!isLocalDevice || isLiked),
                        shape = favShape,
                        containerColor = pillContainerColor,
                        tint = when {
                            isLiked -> HeartBurstPink
                            isLocalDevice -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.35f)
                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        contentDescription = when {
                            isLiked -> "Remove from Liked"
                            isLocalDevice -> "Liking unavailable for on-device files"
                            else -> "Add to Liked"
                        },
                        onClick = onToggleLike,
                    )
                }
            }

            SeekBar(
                state = state,
                onSeek = onSeek,
                wavySlider = wavySlider,
                slimSlider = slimSlider,
                squigglySlider = squigglySlider,
                showCodecInfo = showCodecInfo,
            )

            TransportControls(
                state = state,
                buttonColor = buttonColor,
                transportStyle = transportStyle,
                onTogglePlayPause = onTogglePlayPause,
                onNext = { artworkDirection = 1; onNext() },
                onPrevious = { artworkDirection = -1; onPrevious() },
            )

            PlayerQuickActionsRow(
                state = state,
                showQueue = showQueue,
                showLyrics = showLyrics,
                sleepTimerRemainingMs = sleepTimerRemainingMs,
                onToggleQueue = {
                    showQueue = !showQueue
                    if (showQueue) onShowLyricsChange(false)
                },
                onToggleLyrics = {
                    onShowLyricsChange(!showLyrics)
                    if (!showLyrics) showQueue = false
                },
                onOpenSleepTimer = { showSleepTimerDialog = true },
                onToggleShuffle = onToggleShuffle,
                onCycleRepeat = onCycleRepeat,
            )

            sleepTimerRemainingMs?.let { remaining ->
                Surface(
                    onClick = { showSleepTimerDialog = true },
                    modifier = Modifier.padding(top = 10.dp),
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.48f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)),
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Bedtime, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                        Text("Sleep timer · ${sleepTimerRemainingLabel(remaining)} left",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    val reducedMotion = com.example.ui.theme.LocalReducedMotion.current
    AnimatedVisibility(
        visible = showQueue,
        enter = if (reducedMotion) fadeIn(tween(0)) else
            slideInVertically(animationSpec = tween(340), initialOffsetY = { it }) + fadeIn(tween(220)),
        exit = if (reducedMotion) fadeOut(tween(0)) else
            slideOutVertically(animationSpec = tween(280), targetOffsetY = { it }) + fadeOut(tween(220)),
    ) {
        PlayerQueueScreen(
            state = state,
            palette = palette,
            onDismiss = { showQueue = false },
            onToggleShuffle = onToggleShuffle,
            onCycleRepeat = onCycleRepeat,
            onPlayQueueItem = { index ->
                artworkDirection = if (index >= state.queue.indexOfFirst { it.isCurrent }) 1 else -1
                onPlayQueueItem(index)
            },
            onMoveQueueItem = onMoveQueueItem,
            onRemoveQueueItem = onRemoveQueueItem,
            onUndoRemoveQueueItem = onUndoRemoveQueueItem,
            onClearUpcomingQueue = onClearUpcomingQueue,
            onAddRadio = onAddRadio,
            onSaveQueueAsPlaylist = onSaveQueueAsPlaylist,
            isLiked = isLiked,
            onToggleLike = onToggleLike,
            onOpenMenu = onOpenMenu,
            onOpenQueueTrackMenu = onOpenQueueTrackMenu,
        )
    }

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            remainingMs = sleepTimerRemainingMs,
            onStart = { minutes ->
                onStartSleepTimer(minutes)
                showSleepTimerDialog = false
            },
            onCancel = {
                onCancelSleepTimer()
                showSleepTimerDialog = false
            },
            onDismiss = { showSleepTimerDialog = false },
        )
    }

    if (showSpeedDialog) {
        SpeedDialog(
            speed = state.speed,
            pitch = state.pitch,
            onSetSpeed = onSetPlaybackSpeed,
            onDismiss = { showSpeedDialog = false },
        )
    }
}

/**
 * The Now Playing like button (background pill, tap handling, icon) with a heart-burst and a
 * bouncy pop the moment the user taps to *like* the track (not to unlike, and not every tap).
 *
 * Triggered directly from the tap itself, not from reactively watching [isLiked] change: an
 * earlier version fired the burst on any false-to-true transition of [isLiked], which included
 * the very first composition after opening Now Playing for a song that was *already* liked -
 * `collectAsState()`'s initial placeholder value briefly reads false until the real, already-true
 * value loads from the repository, and that "false then true" looked identical to a real like
 * action from here, so the burst fired every single time the screen opened for an already-liked
 * song. Driving it from the click instead can't misfire that way, since it only ever runs in
 * response to an actual tap.
 *
 * The burst is a sibling of the clipped pill, not a child of it - the pill itself needs
 * `Modifier.clip(shape)` to keep its rounded background, but that clip would cut the burst's
 * hearts off at the pill's own edge the moment they traveled past it, which defeats a burst radius
 * wider than the button.
 */
@Composable
private fun LikeButtonWithBurst(
    isLiked: Boolean,
    enabled: Boolean,
    shape: Shape,
    containerColor: Color,
    tint: Color,
    contentDescription: String,
    onClick: () -> Unit,
) {
    var showBurst by remember { mutableStateOf(false) }

    val iconScale = remember { Animatable(1f) }
    LaunchedEffect(showBurst) {
        if (!showBurst) return@LaunchedEffect
        iconScale.snapTo(0.6f)
        iconScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessLow))
    }

    Box(contentAlignment = Alignment.Center) {
        if (showBurst) {
            HeartBurstParticles(onFinished = { showBurst = false })
        }
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(shape)
                .background(containerColor)
                .bounceClick(
                    enabled = enabled,
                    onClick = {
                        if (!isLiked) showBurst = true
                        onClick()
                    },
                )
                .testTag("now_playing_like"),
            contentAlignment = Alignment.Center,
        ) {
            if (isLiked) {
                Box(
                    Modifier.size(36.dp)
                        .background(
                            Brush.radialGradient(listOf(
                                HeartBurstBabyPink.copy(alpha = .38f),
                                HeartBurstBabyPink.copy(alpha = .10f),
                                Color.Transparent,
                            )),
                            CircleShape,
                        )
                )
            }
            MotionIcon(
                imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.graphicsLayer { scaleX = iconScale.value; scaleY = iconScale.value },
            )
        }
    }
}

/** Hot pink and baby pink, alternating - fixed regardless of theme/tint, since a "like" burst
 * reads as a pink heart-burst specifically, not whatever the current accent color happens to be. */
private val HeartBurstPink = Color(0xFFFF2D78)
private val HeartBurstBabyPink = Color(0xFFFFB6D5)

/**
 * A handful of hearts flung out from center and faded - a wide travel radius well past the like
 * button's own 42dp background circle, so this reads as a real little celebration rather than a
 * flourish confined to the button. Removes itself (via [onFinished]) once the animation completes,
 * rather than lingering on invisibly forever after.
 */
@Composable
private fun HeartBurstParticles(onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, animationSpec = tween(durationMillis = 550))
        onFinished()
    }
    val radiusPx = with(LocalDensity.current) { 46.dp.toPx() }
    val particleCount = 8
    for (i in 0 until particleCount) {
        val angle = Math.toRadians((360.0 / particleCount) * i)
        Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = null,
            tint = (if (i % 2 == 0) HeartBurstPink else HeartBurstBabyPink)
                .copy(alpha = (1f - progress.value).coerceIn(0f, 1f)),
            modifier = Modifier
                .size(16.dp)
                .graphicsLayer {
                    translationX = (cos(angle) * radiusPx * progress.value).toFloat()
                    translationY = (sin(angle) * radiusPx * progress.value).toFloat()
                    val particleScale = 1f - progress.value * 0.3f
                    scaleX = particleScale
                    scaleY = particleScale
                    alpha = (1f - progress.value).coerceIn(0f, 1f)
                },
        )
    }
}

/**
 * [pitch] independent of [speed] is a real, distinct DSP path (Sonic-style time-stretching, not
 * just resampling) - "Preserve pitch" pins it to 1x while [speed] changes; turning it off lets
 * pitch follow speed for the classic chipmunk/slow-motion effect.
 */
@Composable
private fun SpeedDialog(
    speed: Float,
    pitch: Float,
    onSetSpeed: (Float, Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var sliderSpeed by remember(speed) { mutableFloatStateOf(speed) }
    var preservePitch by remember(speed, pitch) { mutableStateOf(pitch == 1f) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback speed") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "${String.format(androidx.compose.ui.platform.LocalConfiguration.current.locales[0], "%.2f", sliderSpeed)}x",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text("Changes playback speed for this session.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = sliderSpeed,
                    onValueChange = {
                        sliderSpeed = it
                        onSetSpeed(it, if (preservePitch) 1f else it)
                    },
                    valueRange = 0.5f..2f,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0.75f, 1f, 1.25f).forEach { preset ->
                        FilterChip(
                            selected = kotlin.math.abs(sliderSpeed - preset) < 0.01f,
                            onClick = {
                                sliderSpeed = preset
                                onSetSpeed(preset, if (preservePitch) 1f else preset)
                            },
                            label = { Text("${preset}x") },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1.5f, 2f).forEach { preset ->
                        FilterChip(
                            selected = kotlin.math.abs(sliderSpeed - preset) < 0.01f,
                            onClick = {
                                sliderSpeed = preset
                                onSetSpeed(preset, if (preservePitch) 1f else preset)
                            },
                            label = { Text("${preset}x") },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Preserve pitch", modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(
                        checked = preservePitch,
                        onCheckedChange = {
                            preservePitch = it
                            onSetSpeed(sliderSpeed, if (it) 1f else sliderSpeed)
                        },
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = {
                    sliderSpeed = 1f
                    preservePitch = true
                    onSetSpeed(1f, 1f)
                },
            ) { Text("Reset") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

/**
 * While the user is dragging, the thumb follows the finger rather than the player's reported
 * position - otherwise the 500ms position poll would fight the drag and make the thumb stutter.
 * The seek is committed once on release.
 */
@Composable
private fun SeekBar(
    state: NowPlayingState,
    onSeek: (Float) -> Unit,
    wavySlider: Boolean,
    slimSlider: Boolean,
    squigglySlider: Boolean = false,
    showCodecInfo: Boolean = false,
) {
    val trackKey = state.currentPlaybackId
    val reducedMotion = com.example.ui.theme.LocalReducedMotion.current
    val smoothProgress = remember(trackKey) { Animatable(state.progress) }
    var lastRevision by remember(trackKey) { mutableIntStateOf(state.positionRevision) }
    var scrubPosition by remember(trackKey) { mutableStateOf<Float?>(null) }
    var pendingSeek by remember(trackKey) { mutableStateOf<Float?>(null) }

    // The player reports position every 500 ms. Interpolate only those small playback ticks;
    // a seek, song change, pause or reduced-motion setting must land at the real position now.
    LaunchedEffect(trackKey, state.progress, state.positionRevision, state.isPlaying, reducedMotion) {
        val discontinuity = state.positionRevision != lastRevision
        lastRevision = state.positionRevision
        if (discontinuity || !state.isPlaying || reducedMotion) smoothProgress.snapTo(state.progress)
        else smoothProgress.animateTo(state.progress, tween(480, easing = LinearEasing))
    }
    LaunchedEffect(trackKey, state.positionRevision) { pendingSeek = null }
    LaunchedEffect(trackKey, pendingSeek) {
        if (pendingSeek != null) {
            delay(1_500) // If a backend never reports a discontinuity, return to its real value.
            pendingSeek = null
        }
    }
    val displayed = scrubPosition ?: pendingSeek ?: smoothProgress.value
    val commitSeek: (Float) -> Unit = { value ->
        val bounded = value.coerceIn(0f, 1f)
        pendingSeek = bounded
        onSeek(bounded)
    }

    Column(modifier = Modifier.padding(top = 24.dp)) {
        when {
            // A tighter, faster-wiggling wave than Wavy - same component, different tuning.
            squigglySlider -> SquigglySlider(
                progress = displayed,
                onSeek = commitSeek,
                onScrubPreview = { scrubPosition = it },
                playing = state.isPlaying,
                activeColor = MaterialTheme.colorScheme.primary,
                inactiveColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                visibleCycles = 4f,
                phaseDurationMs = 3200,
                pillThumb = true,
            )

            wavySlider -> WavySeekBar(
                progress = displayed,
                onSeek = commitSeek,
                onScrubPreview = { scrubPosition = it },
                playing = state.isPlaying,
                activeColor = MaterialTheme.colorScheme.primary,
                inactiveColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )

            slimSlider -> SlimSeekBar(
                progress = displayed,
                onSeek = commitSeek,
                onScrubPreview = { scrubPosition = it },
                activeColor = MaterialTheme.colorScheme.primary,
                inactiveColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )

            else -> Slider(
                value = displayed,
                onValueChange = { scrubPosition = it },
                onValueChangeFinished = {
                    scrubPosition?.let(commitSeek)
                    scrubPosition = null
                },
                modifier = Modifier.testTag("now_playing_seekbar"),
            )
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = (displayed * state.durationMs).toLong().asPlaybackTime(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = state.durationMs.asPlaybackTime(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (showCodecInfo) {
            val codecLabel by com.example.CurrentCodecInfo.current.collectAsStateWithLifecycle()
            codecLabel?.let { label ->
                Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * A scalloped "cookie" outline - self-rotating (the caller only supplies a target angle, not a
 * pre-rotated path), ported directly from Echo-Music's `WavyShape` (`Player.kt`) for the "Wheel"
 * [PlayerTransportStyle]. [indent] 0 is a plain circle; Echo animates it in/out (0 <-> 0.08) so
 * the shape only actually scallops while playing, flattening to a circle when paused.
 */
private data class WavyShape(
    val sides: Int,
    val indent: Float,
    val rotationDegrees: Float,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val maxRadiusX = size.width / 2f
        val maxRadiusY = size.height / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val steps = 120
        val rotationRad = rotationDegrees * Math.PI / 180.0
        for (i in 0..steps) {
            val angle = i * Math.PI * 2 / steps
            val bumpAngle = angle - rotationRad
            val r = 1f - indent + indent * cos(sides * bumpAngle)
            val x = cx + maxRadiusX * r * cos(angle)
            val y = cy + maxRadiusY * r * sin(angle)
            if (i == 0) path.moveTo(x.toFloat(), y.toFloat()) else path.lineTo(x.toFloat(), y.toFloat())
        }
        path.close()
        return Outline.Generic(path)
    }
}

/**
 * The queue/sleep-timer/lyrics/shuffle/repeat row - five boxed buttons in one continuous strip,
 * pill-rounded on the two outer ends and square-ish in between. Ported directly from Echo-Music's
 * `PlayerQueueButton` row (`Queue.kt`: `queueShape`/`middleShape`/`repeatShape`, and the button's
 * own filled-when-active/outlined-when-inactive treatment), not independently designed - same
 * shapes, same 42dp button size, same 1dp/30%-alpha border on the inactive state.
 */
@Composable
private fun PlayerQuickActionsRow(
    state: NowPlayingState,
    showQueue: Boolean,
    showLyrics: Boolean,
    sleepTimerRemainingMs: Long?,
    onToggleQueue: () -> Unit,
    onToggleLyrics: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
) {
    val buttonSize = 42.dp
    val queueShape = RoundedCornerShape(topStart = 50.dp, bottomStart = 50.dp, topEnd = 3.dp, bottomEnd = 3.dp)
    val middleShape = RoundedCornerShape(3.dp)
    val repeatShape = RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp, topEnd = 50.dp, bottomEnd = 50.dp)

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 20.dp),
    ) {
        PlayerQuickActionButton(
            icon = Icons.AutoMirrored.Filled.QueueMusic,
            contentDescription = if (showQueue) "Hide queue" else "Show queue",
            isActive = showQueue,
            shape = queueShape,
            size = buttonSize,
            onClick = onToggleQueue,
        )
        PlayerQuickActionButton(
            icon = Icons.Default.Bedtime,
            contentDescription = "Sleep timer",
            isActive = sleepTimerRemainingMs != null,
            shape = middleShape,
            size = buttonSize,
            onClick = onOpenSleepTimer,
        )
        PlayerQuickActionButton(
            icon = Icons.Default.Lyrics,
            contentDescription = if (showLyrics) "Hide lyrics" else "Show lyrics",
            isActive = showLyrics,
            shape = middleShape,
            size = buttonSize,
            onClick = onToggleLyrics,
        )
        PlayerQuickActionButton(
            icon = Icons.Default.Shuffle,
            contentDescription = "Shuffle",
            isActive = state.shuffleEnabled,
            shape = middleShape,
            size = buttonSize,
            onClick = onToggleShuffle,
        )
        PlayerQuickActionButton(
            icon = if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
            contentDescription = "Repeat mode",
            isActive = state.repeatMode != Player.REPEAT_MODE_OFF,
            shape = repeatShape,
            size = buttonSize,
            onClick = onCycleRepeat,
        )
    }
}

@Composable
private fun PlayerQuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    isActive: Boolean,
    shape: RoundedCornerShape,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    text: String? = null,
) {
    val base = Modifier
        .size(size)
        .clip(shape)
        .clickable(onClick = onClick)
    val styled = if (isActive) {
        base.background(MaterialTheme.colorScheme.primary)
    } else {
        base.border(width = 1.dp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), shape = shape)
    }
    Box(modifier = styled, contentAlignment = Alignment.Center) {
        if (text != null) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
internal fun TransportControls(
    state: NowPlayingState,
    buttonColor: Color,
    transportStyle: PlayerTransportStyle,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    val resolvedButtonColor = buttonColor.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.primary
    when (transportStyle) {
        PlayerTransportStyle.Wheel -> WheelTransportControls(state, resolvedButtonColor, onTogglePlayPause, onNext, onPrevious)
        PlayerTransportStyle.Pill -> PillTransportControls(state, resolvedButtonColor, onTogglePlayPause, onNext, onPrevious)
        PlayerTransportStyle.Static -> StaticTransportControls(state, resolvedButtonColor, onTogglePlayPause, onNext, onPrevious)
    }
}

/** The original three separate circular buttons, corner-morphing play/pause included - unchanged
 * default behaviour, just extracted out of [TransportControls] so the other two styles could be
 * added alongside it without one giant branching function. */
@Composable
private fun StaticTransportControls(
    state: NowPlayingState,
    buttonColor: Color,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MicroIconButton(onClick = onPrevious, enabled = state.hasPrevious) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "Previous track",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(36.dp),
            )
        }

        Spacer(Modifier.width(24.dp))

        // Corner roundness morphs between a nearly-circular paused state and a more squared-off
        // playing state, echoing Echo-Music's play/pause treatment - a small, purely cosmetic
        // touch, not a shape swap that would affect layout or hit target size.
        val playPauseCornerRadius by animateDpAsState(
            targetValue = if (state.showPauseAction) 24.dp else 36.dp,
            animationSpec = tween(90),
            label = "play_pause_corner_radius",
        )
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(playPauseCornerRadius))
                .background(buttonColor)
                .bounceClick(onClick = onTogglePlayPause)
                .testTag("now_playing_play_pause"),
            contentAlignment = Alignment.Center,
        ) {
            MotionIcon(
                imageVector = if (state.showPauseAction) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (state.showPauseAction) "Pause" else "Play",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(40.dp),
            )
        }

        Spacer(Modifier.width(24.dp))

        MicroIconButton(onClick = onNext, enabled = state.hasNext) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Next track",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

/** "Wheel": play/pause is a plain circle at rest, growing a slowly-rotating scalloped edge while
 * playing - ported from Echo-Music's `cookieIndent`/`WavyShape(9, cookieIndent, rotation)`
 * treatment (`Player.kt`) verbatim, same easing/duration numbers included. Prev/next stay plain
 * circular buttons, matching Echo's own layout (the cookie shape is play/pause-only there too). */
@Composable
private fun WheelTransportControls(
    state: NowPlayingState,
    buttonColor: Color,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MicroIconButton(onClick = onPrevious, enabled = state.hasPrevious) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "Previous track",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(36.dp),
            )
        }

        Spacer(Modifier.width(24.dp))

        val reducedMotion = com.example.ui.theme.LocalReducedMotion.current
        val cookieIndent = animateFloatAsState(
            targetValue = if (state.showPauseAction && !reducedMotion) 0.08f else 0f,
            animationSpec = tween(durationMillis = if (reducedMotion) 0 else 300, easing = LinearEasing),
            label = "cookie_indent",
        )
        val rotation = remember { Animatable(0f) }
        LaunchedEffect(state.isPlaying, reducedMotion) {
            while (state.isPlaying && !reducedMotion) {
                rotation.animateTo(rotation.value + 360f, tween(8000, easing = LinearEasing))
                rotation.snapTo(rotation.value % 360f)
            }
        }
        Box(
            modifier = Modifier
                .size(72.dp)
                .graphicsLayer {
                    // A layer-only shape update avoids relayout/recomposition on every frame.
                    shape = if (cookieIndent.value > 0f)
                        WavyShape(9, cookieIndent.value, rotation.value)
                    else androidx.compose.foundation.shape.CircleShape
                    clip = true
                }
                .background(buttonColor)
                .bounceClick(onClick = onTogglePlayPause)
                .testTag("now_playing_play_pause"),
            contentAlignment = Alignment.Center,
        ) {
            MotionIcon(
                imageVector = if (state.showPauseAction) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (state.showPauseAction) "Pause" else "Play",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(40.dp),
            )
        }

        Spacer(Modifier.width(24.dp))

        MicroIconButton(onClick = onNext, enabled = state.hasNext) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Next track",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

/** "Pill": prev/play-pause/next joined into one continuous pill, no gap between segments -
 * rounded-left on prev, square-ish centre on play/pause, rounded-right on next. Adapted from
 * Echo-Music's `shareShape`/`favShape` asymmetric-corner technique (`Player.kt`, its download/
 * like button pair) - that's a two-segment split; this extends the same idea to three. */
@Composable
private fun PillTransportControls(
    state: NowPlayingState,
    buttonColor: Color,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    val leftShape = RoundedCornerShape(topStart = 32.dp, bottomStart = 32.dp, topEnd = 4.dp, bottomEnd = 4.dp)
    val centerShape = RoundedCornerShape(4.dp)
    val rightShape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 32.dp, bottomEnd = 32.dp)

    // A real gap between the three segments (6dp - the same spacing Echo-Music's own
    // PlayerQueueButton row uses between its buttons), not touching edge-to-edge - a fully fused
    // pill with zero gap read as cramped/congested rather than deliberate. Each segment keeps its
    // own asymmetric corner shape (rounded outer edge, squarer inner edge), so the "one shape,
    // three parts" read is still there, just with breathing room.
    Row(
        modifier = Modifier
            .padding(top = 20.dp)
            .height(64.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 64.dp, height = 64.dp)
                .clip(leftShape)
                .background(buttonColor.copy(alpha = 0.4f))
                .then(if (state.hasPrevious) Modifier.bounceClick(onClick = onPrevious) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "Previous track",
                tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = if (state.hasPrevious) 1f else 0.4f),
                modifier = Modifier.size(30.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(width = 92.dp, height = 64.dp)
                .clip(centerShape)
                .background(buttonColor)
                .bounceClick(onClick = onTogglePlayPause)
                .testTag("now_playing_play_pause"),
            contentAlignment = Alignment.Center,
        ) {
            MotionIcon(
                imageVector = if (state.showPauseAction) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (state.showPauseAction) "Pause" else "Play",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(36.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(width = 64.dp, height = 64.dp)
                .clip(rightShape)
                .background(buttonColor.copy(alpha = 0.4f))
                .then(if (state.hasNext) Modifier.bounceClick(onClick = onNext) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Next track",
                tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = if (state.hasNext) 1f else 0.4f),
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

/** Fixed so a drag can convert pixels travelled into rows travelled without measuring anything. */
private val QueueRowHeight = 60.dp

/** One timeline list keeps every queued song visible, including items before the current song. */
@Composable
private fun PlayerQueueScreen(
    state: NowPlayingState,
    palette: AlbumPalette?,
    onDismiss: () -> Unit,
    isLiked: Boolean,
    onToggleLike: () -> Unit,
    onOpenMenu: () -> Unit,
    onOpenQueueTrackMenu: (QueueItem) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onPlayQueueItem: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onUndoRemoveQueueItem: () -> Unit,
    onClearUpcomingQueue: () -> Unit,
    onAddRadio: ((Int) -> Unit) -> Unit,
    onSaveQueueAsPlaylist: suspend (String) -> Boolean,
) {
    BackHandler(onBack = onDismiss)
    val current = state.queue.firstOrNull { it.isCurrent }
    val queue = state.queue
    val totalMs = queue.sumOf { it.durationMs.coerceAtLeast(0L) }
    var reorderLocked by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showClearDialog by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }
    var savingPlaylist by remember { mutableStateOf(false) }
    var savePlaylistFailed by remember { mutableStateOf(false) }
    val removeWithUndo: (Int) -> Unit = { index ->
        val title = state.queue.firstOrNull { it.index == index }?.title.orEmpty()
        onRemoveQueueItem(index)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            if (snackbar.showSnackbar(
                    message = if (title.isBlank()) "Removed from queue" else "Removed $title",
                    actionLabel = "Undo",
                    withDismissAction = true,
                    duration = SnackbarDuration.Short,
                ) == SnackbarResult.ActionPerformed
            ) onUndoRemoveQueueItem()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize().testTag("player_queue_screen"),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                    .padding(horizontal = 20.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    MicroIconButton(onClick = onDismiss) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Return to player")
                    }
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text("Queue", style = MaterialTheme.typography.headlineSmall)
                    }
                    TextButton(onClick = { showSaveDialog = true }, enabled = current != null) {
                        Text("Save")
                    }
                }

                if (current != null) {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 20.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                            if (current.artworkUrl != null) {
                                AsyncImage(current.artworkUrl, contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(11.dp)))
                            } else {
                                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White)
                                }
                            }
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                Text("NOW PLAYING", style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = .75f))
                                Text(current.title, style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(current.artist, style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = .82f),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            MicroIconButton(onClick = onToggleLike, enabled = !state.isLocalDevice || isLiked,
                                modifier = Modifier.size(36.dp)) {
                                Icon(if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = if (isLiked) "Remove from Liked" else "Add to Liked",
                                    tint = if (isLiked) HeartBurstPink else Color.White)
                            }
                            MicroIconButton(onClick = { reorderLocked = !reorderLocked }, modifier = Modifier.size(36.dp)) {
                                Icon(if (reorderLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = if (reorderLocked) "Unlock queue reordering" else "Lock queue reordering",
                                    tint = Color.White)
                            }
                            MicroIconButton(onClick = onOpenMenu, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Current song options",
                                    tint = Color.White)
                            }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    QueueModeControl(
                        label = "Shuffle",
                        icon = { Icon(Icons.Default.Shuffle, contentDescription = null) },
                        active = state.shuffleEnabled,
                        onClick = onToggleShuffle,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp, topEnd = 5.dp, bottomEnd = 5.dp),
                    )
                    QueueModeControl(
                        label = "Repeat",
                        icon = { Icon(if (state.repeatMode == Player.REPEAT_MODE_ONE)
                            Icons.Default.RepeatOne else Icons.Default.Repeat, contentDescription = null) },
                        active = state.repeatMode != Player.REPEAT_MODE_OFF,
                        onClick = onCycleRepeat,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(5.dp),
                    )
                    QueueModeControl(
                        label = "Radio",
                        icon = { Icon(Icons.Default.Radio, contentDescription = null) },
                        active = false,
                        onClick = {
                            onAddRadio { added ->
                                scope.launch {
                                    snackbar.currentSnackbarData?.dismiss()
                                    snackbar.showSnackbar(when {
                                        added > 0 -> "Added $added radio songs"
                                        added == -1 -> "Queue is full (51 songs)"
                                        added == -2 -> "Radio needs an online YouTube song"
                                        else -> "No radio songs found for this track"
                                    })
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(topStart = 5.dp, bottomStart = 5.dp, topEnd = 28.dp, bottomEnd = 28.dp),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Continue playing", style = MaterialTheme.typography.titleMedium)
                        Text("Next in queue", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${queue.size} songs", style = MaterialTheme.typography.labelLarge)
                        Text(if (queue.isNotEmpty() && queue.all { it.durationMs > 0L }) {
                            queueDurationLabel(totalMs)
                        } else "Calculating duration…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (queue.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("Your queue is empty",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    QueueList(
                        queue = queue,
                        onPlayQueueItem = onPlayQueueItem,
                        onMoveQueueItem = onMoveQueueItem,
                        onRemoveQueueItem = removeWithUndo,
                        onOpenTrackMenu = onOpenQueueTrackMenu,
                        reorderEnabled = !reorderLocked && !state.shuffleEnabled,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }
                TextButton(onClick = { showClearDialog = true }, enabled = queue.size > 1,
                    modifier = Modifier.align(Alignment.End)) { Text("Clear queue") }
            }
            SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter)
                .navigationBarsPadding().padding(bottom = 56.dp))
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear queue?") },
            text = { Text("The current song will keep playing. All ${(state.queue.size - 1).coerceAtLeast(0)} other songs will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    onClearUpcomingQueue()
                    showClearDialog = false
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("Cancel") } },
        )
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { if (!savingPlaylist) showSaveDialog = false },
            title = { Text("Save queue as playlist") },
            text = {
                Column {
                    OutlinedTextField(
                        value = playlistName,
                        onValueChange = {
                            playlistName = it
                            savePlaylistFailed = false
                        },
                        label = { Text("Playlist name") },
                        singleLine = true,
                        isError = savePlaylistFailed,
                    )
                    if (savePlaylistFailed) {
                        Text("Could not save the playlist. Please try again.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = playlistName.trim()
                    scope.launch {
                        savingPlaylist = true
                        val saved = onSaveQueueAsPlaylist(name)
                        savingPlaylist = false
                        if (saved) {
                            showSaveDialog = false
                            snackbar.showSnackbar("Saved as $name")
                        } else {
                            savePlaylistFailed = true
                        }
                    }
                }, enabled = playlistName.isNotBlank() && !savingPlaylist) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSaveDialog = false },
                enabled = !savingPlaylist) { Text("Cancel") } },
        )
    }
}

@Composable
private fun QueueModeControl(
    label: String,
    icon: @Composable () -> Unit,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(14.dp),
) {
    val duration = if (com.example.ui.theme.LocalReducedMotion.current) 0 else 160
    val container by androidx.compose.animation.animateColorAsState(
        if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        tween(duration), label = "queue_mode_container")
    val foreground by androidx.compose.animation.animateColorAsState(
        if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        tween(duration), label = "queue_mode_content")
    Surface(
        modifier = modifier.height(58.dp).clip(shape).feedbackClickable(onClick = onClick).semantics { selected = active },
        shape = shape,
        color = container,
        contentColor = foreground,
    ) {
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            icon()
            Text(label, style = MaterialTheme.typography.labelLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun queueDurationLabel(durationMs: Long): String {
    val seconds = durationMs / 1_000L
    return "${seconds / 3_600}:${((seconds / 60) % 60).toString().padStart(2, '0')}:" +
        (seconds % 60).toString().padStart(2, '0')
}

/**
 * The queue supports direct tap, drag and swipe gestures, plus an explicit row menu for the same
 * actions so they remain discoverable and usable with assistive technology. Reordering is disabled
 * under shuffle because timeline movement cannot promise the next playback position there.
 *
 * The move is committed once, on drag end, rather than continuously as the finger crosses each
 * row boundary. Committing mid-drag would reorder the list under the very composable owning the
 * gesture - and since rows are keyed by their queue position, that item would be disposed and
 * recreated, cancelling the drag halfway through. Holding the change until the end keeps the list
 * stable for the whole gesture; the shifting of the other rows is a visual offset only, so it
 * still looks like a live reorder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueList(
    queue: List<QueueItem>,
    onPlayQueueItem: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onOpenTrackMenu: (QueueItem) -> Unit,
    reorderEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val rowHeightPx = with(LocalDensity.current) { QueueRowHeight.toPx() }
    val listState = rememberLazyListState()

    // The row picked up, and how far it has travelled since. Null index means no drag in progress.
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }

    // Where the dragged row would land if the finger lifted now.
    val targetIndex = draggingIndex?.let { from ->
        (from + (dragOffsetPx / rowHeightPx).roundToInt())
            .coerceIn(queue.firstOrNull()?.index ?: 0, queue.lastOrNull()?.index ?: 0)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        // Keyed on mediaId+index, not index alone - after a removal shifts every later item up
        // one slot, index-only keys would make Compose treat "song B now at slot 2" as the exact
        // same row identity as "song A that used to be at slot 2," reusing its remembered
        // dismissState along with it. That's what made a removed row's red swipe-reveal appear to
        // "stick" onto whatever song took its place instead of the list settling cleanly - the new
        // occupant was silently born already mid-swipe. Including mediaId means a genuinely
        // different song at that slot gets a fresh key (and fresh, Settled dismiss state) instead.
        items(queue, key = { "${it.mediaId}_${it.index}" }) { item ->
            val isDragging = draggingIndex == item.index

            // Rows between the dragged row's origin and its target slide one place to make the
            // gap, so the landing position is visible before the finger lifts.
            val origin = draggingIndex
            val destination = targetIndex
            val shiftPx = when {
                isDragging || origin == null || destination == null -> 0f
                item.index in (origin + 1)..destination -> -rowHeightPx
                item.index in destination..(origin - 1) -> rowHeightPx
                else -> 0f
            }

            // A fresh state per row identity - re-created (not reused) whenever the item's own
            // position in the swipe animation would otherwise carry over onto a different song
            // after removal/reorder, since it's keyed on the composition slot the same as the row
            // itself.
            val dismissState = rememberSwipeToDismissBoxState(
                positionalThreshold = { distance -> distance * .28f },
                confirmValueChange = { value ->
                    if (value == SwipeToDismissBoxValue.StartToEnd) {
                        onRemoveQueueItem(item.index)
                    }
                    // The row disappears through animateItem. Keeping the dismiss state at
                    // Settled prevents its red reveal from returning when Undo reinserts it.
                    false
                },
            )

            SwipeToDismissBox(
                state = dismissState,
                enableDismissFromStartToEnd = true,
                enableDismissFromEndToStart = false,
                // The rows above/below sliding smoothly into a removed row's gap, instead of
                // snapping straight to their new position the instant it's gone.
                modifier = Modifier
                    .animateItem()
                    .testTag("queue_swipe_${item.index}"),
                backgroundContent = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val distance = runCatching { dismissState.requireOffset() }.getOrDefault(0f)
                                alpha = (distance / (size.width * .28f)).coerceIn(0f, 1f)
                            }
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.horizontalGradient(listOf(
                                MaterialTheme.colorScheme.errorContainer,
                                MaterialTheme.colorScheme.error.copy(alpha = .82f),
                            )))
                            .padding(start = 20.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Close, contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer)
                            Text("Remove", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                },
            ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(QueueRowHeight)
                    // The dragged row rides above its neighbours instead of being clipped by them.
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (isDragging) dragOffsetPx else shiftPx
                        alpha = if (isDragging) 0.9f else 1f
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Only the artwork and titles play the track. If the whole row were clickable, a tap
                // that missed the drag handle - or a drag too short to register - would silently jump
                // playback to that item instead of doing nothing.
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onPlayQueueItem(item.index) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (item.artworkUrl != null) {
                            AsyncImage(
                                model = item.artworkUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(44.dp),
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        if (item.isCurrent) {
                            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .45f)),
                                contentAlignment = Alignment.Center) {
                                com.example.ui.component.NowPlayingIndicator()
                            }
                        }
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = item.artist + item.durationMs.takeIf { it > 0L }
                                ?.let { "  ·  ${it.asPlaybackTime()}" }.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                MicroIconButton(onClick = { onOpenTrackMenu(item) }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Options for ${item.title}")
                }

                // A full touch target keeps drag separate from row taps and list scrolling.
                if (reorderEnabled) Box(
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("queue_drag_${item.index}")
                        .pointerInput(item.index, queue.size) {
                            detectDragGestures(
                                onDragStart = {
                                    draggingIndex = item.index
                                    dragOffsetPx = 0f
                                },
                                onDragEnd = {
                                    // Recomputed here from the live state rather than reusing the
                                    // `targetIndex` above: that one is a composition-scope value
                                    // this lambda would capture once, at gesture-setup time, and
                                    // still be reading as null when the finger lifts.
                                    val from = draggingIndex
                                    if (from != null) {
                                        val to = (from + (dragOffsetPx / rowHeightPx).roundToInt())
                                            .coerceIn(queue.first().index, queue.last().index)
                                        if (to != from) onMoveQueueItem(from, to)
                                    }
                                    draggingIndex = null
                                    dragOffsetPx = 0f
                                },
                                onDragCancel = {
                                    draggingIndex = null
                                    dragOffsetPx = 0f
                                },
                            ) { change, dragAmount ->
                                change.consume()
                                dragOffsetPx += dragAmount.y
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.DragHandle, contentDescription = "Drag to reorder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp))
                }
            }
            }
        }
    }
}

@Composable
private fun Artwork(
    imageUrl: String?,
    artworkKey: String,
    isBuffering: Boolean,
    isPlaying: Boolean,
    rotating: Boolean,
    transitionDirection: Int,
    previousImageUrl: String?,
    nextImageUrl: String?,
    canPrevious: Boolean,
    canNext: Boolean,
    swipeEnabled: Boolean,
    onSwipeNext: () -> Unit,
    onSwipePrevious: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: androidx.compose.ui.unit.Dp = 20.dp,
    cropToSquare: Boolean = true,
) {
    val reducedMotion = com.example.ui.theme.LocalReducedMotion.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val imageLoader = context.imageLoader
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val dragDirection by remember { derivedStateOf { when {
        dragOffset < 0f -> -1
        dragOffset > 0f -> 1
        else -> 0
    } } }
    var swipePending by remember { mutableStateOf(false) }
    var settleJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    LaunchedEffect(artworkKey) {
        dragOffset = 0f
        swipePending = false
    }
    val rotation = remember(artworkKey) { Animatable(0f) }
    LaunchedEffect(rotation, rotating, isPlaying, reducedMotion) {
        if (!rotating) rotation.snapTo(0f)
        else if (isPlaying && !reducedMotion) {
            while (true) {
                rotation.animateTo(rotation.value + 360f, tween(20_000, easing = LinearEasing))
            }
        }
    }
    BoxWithConstraints(modifier = modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }.coerceAtLeast(1f)
        val pixels = widthPx.toInt().coerceIn(1, 1536)
        val requests = remember(imageUrl, previousImageUrl, nextImageUrl, pixels) {
            listOfNotNull(imageUrl, previousImageUrl, nextImageUrl).distinct().associateWith { url ->
                coil3.request.ImageRequest.Builder(context).data(url).size(pixels, pixels)
                    .crossfade(false).build()
            }
        }
        DisposableEffect(imageLoader, requests) {
            val warmups = listOfNotNull(previousImageUrl, nextImageUrl).distinct()
                .filter { it != imageUrl }.take(2).mapNotNull { requests[it] }
                .map { imageLoader.enqueue(it) }
            onDispose { warmups.forEach { it.dispose() } }
        }
        val neighborUrl = if (dragDirection < 0) nextImageUrl else previousImageUrl
        val neighborAvailable = if (dragDirection < 0) canNext else canPrevious
        if (dragDirection != 0 && neighborAvailable) {
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val fraction = (abs(dragOffset) / widthPx).coerceIn(0f, 1f)
                    translationX = dragOffset + if (dragOffset < 0f) widthPx else -widthPx
                    scaleX = .92f + .08f * fraction
                    scaleY = scaleX
                }.clip(if (rotating) CircleShape else RoundedCornerShape(cornerRadius))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (neighborUrl != null) AsyncImage(
                    model = requests[neighborUrl], contentDescription = null,
                    contentScale = if (cropToSquare) ContentScale.Crop else ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                ) else Icon(Icons.Default.MusicNote, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(72.dp))
            }
        }
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val fraction = (abs(dragOffset) / widthPx).coerceIn(0f, 1f)
                translationX = dragOffset
                scaleX = 1f - .08f * fraction
                scaleY = scaleX
            }.clip(if (rotating) CircleShape else RoundedCornerShape(cornerRadius))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .artworkSwipeGestures(
                    onHorizontalDrag = { distance ->
                        settleJob?.cancel()
                        val available = if (distance < 0f) canNext else canPrevious
                        dragOffset = (if (available) distance else distance * .18f)
                            .coerceIn(-widthPx, widthPx)
                    },
                    onHorizontalEnd = { distance ->
                        val commit = swipeEnabled && abs(distance) > widthPx * .22f &&
                            (if (distance < 0f) canNext else canPrevious)
                        val target = if (commit) (if (distance < 0f) -widthPx else widthPx) else 0f
                        settleJob = scope.launch {
                            val animation = Animatable(dragOffset)
                            animation.animateTo(target, tween(if (reducedMotion) 0 else 240)) {
                                dragOffset = value
                            }
                            if (commit) {
                                swipePending = true
                                if (distance < 0f) onSwipeNext() else onSwipePrevious()
                                // A failed skip must not strand the neighbor cover on screen.
                                delay(2500)
                                if (swipePending) {
                                    Animatable(dragOffset).animateTo(0f, tween(200)) {
                                        dragOffset = value
                                    }
                                    swipePending = false
                                }
                            }
                        }
                    },
                    swipeToChangeSongEnabled = swipeEnabled,
                ),
            contentAlignment = Alignment.Center,
        ) {
        AnimatedContent(
            targetState = artworkKey to imageUrl,
            transitionSpec = {
                if (reducedMotion || swipePending) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                else if (initialState.second == targetState.second) {
                    fadeIn(tween(220)) togetherWith fadeOut(tween(220))
                }
                else {
                    val direction = transitionDirection
                    (slideInHorizontally(tween(420)) { direction * it } +
                        scaleIn(initialScale = .82f, animationSpec = tween(420)) + fadeIn(tween(300)))
                        .togetherWith(slideOutHorizontally(tween(420)) { -direction * it } +
                            scaleOut(targetScale = .82f, animationSpec = tween(420)) + fadeOut(tween(300)))
                }
            },
            label = "player_artwork_carousel",
        ) { (_, displayedUrl) ->
            val displayedRequest = remember(displayedUrl, pixels) {
                displayedUrl?.let { url ->
                    coil3.request.ImageRequest.Builder(context).data(url).size(pixels, pixels)
                        .crossfade(false).build()
                }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (displayedUrl != null) {
                    AsyncImage(
                        model = displayedRequest,
                        contentDescription = null,
                        contentScale = if (cropToSquare) ContentScale.Crop else ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            if (rotating) {
                                scaleX = 1.42f
                                scaleY = 1.42f
                                rotationZ = rotation.value
                            }
                        },
                    )
                } else {
                    Icon(Icons.Default.MusicNote, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(72.dp))
                }
            }
        }

        PlayerBufferingIndicator(
            isBuffering = isBuffering, recordingKey = artworkKey,
            allowDisplay = dragOffset == 0f && !swipePending,
            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
        )
        }
    }
}
