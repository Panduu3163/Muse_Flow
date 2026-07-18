package com.example

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    track: Track,
    isPlaying: Boolean,
    progress: Float,
    positionMs: Long,
    onProgressChange: (Float) -> Unit,
    onPlayPauseToggle: () -> Unit,
    onClose: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier,
    /** Real codec/bitrate of the audio actually decoding right now (see
     * [PlayerViewModel.PlayerUiState.audioFormatLabel]), null until the first format is known. */
    audioFormatLabel: String? = null,
    isShuffleEnabled: Boolean = false,
    @androidx.media3.common.Player.RepeatMode repeatMode: Int = androidx.media3.common.Player.REPEAT_MODE_ALL,
    onToggleShuffle: () -> Unit = {},
    onCycleRepeat: () -> Unit = {},
    sleepTimerEndAtMs: Long? = null,
    onStartSleepTimer: (Int) -> Unit = {},
    onCancelSleepTimer: () -> Unit = {},
    queue: List<Track> = emptyList(),
    onJumpToQueueIndex: (Int) -> Unit = {}
) {
    // Interactive states
    var showLyrics by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showQueueDialog by remember { mutableStateOf(false) }

    // Real, Room-backed liked state - shared with Library's Liked Songs tab
    val likedSongsViewModel: LikedSongsViewModel = viewModel()
    val likedKeys by likedSongsViewModel.likedKeys.collectAsState()
    val isLiked = likedKeys.contains(track.downloadKey())

    // Persisted, defaults OFF - see AppSettingsModels/AppSettingsViewModel and the "Swipe to
    // change song" toggle on the Appearance settings screen.
    val appSettingsViewModel: AppSettingsViewModel = viewModel()
    val appSettings by appSettingsViewModel.state.collectAsState()

    val themeViewModel: ThemeViewModel = viewModel()
    val themeState by themeViewModel.themeState.collectAsState()
    val plateModifier = when (themeState.backgroundMode) {
        BackgroundMode.Amoled -> Modifier.background(Color.Black)
        BackgroundMode.Gradient -> Modifier.background(Brush.verticalGradient(themeState.palette.colors))
    }

    // Real synced lyrics from LRCLib, keyed to this track; re-fetched whenever the track changes.
    val lyricsViewModel: LyricsViewModel = viewModel()
    val lyricsResult by lyricsViewModel.lyricsResult.collectAsState()
    LaunchedEffect(track) { lyricsViewModel.loadLyricsFor(track) }

    // Parse track duration to seconds
    val totalSeconds = remember(track.duration) { parseDurationToSeconds(track.duration) }

    // Helper to format elapsed/remaining time
    fun formatTime(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return String.format("%d:%02d", m, s)
    }

    val elapsedSeconds = (progress * totalSeconds).toInt()
    val remainingSeconds = totalSeconds - elapsedSeconds

    // Animated Ambient Canvas / Floating Glow
    val infiniteTransition = rememberInfiniteTransition(label = "ambient_glow")
    val angle1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_1_angle"
    )
    val angle2 by infiniteTransition.animateFloat(
        initialValue = 180f,
        targetValue = 540f,
        animationSpec = infiniteRepeatable(
            animation = tween(24000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_2_angle"
    )

    val gradientColors = MusicData.Gradients[track.gradientIndex % MusicData.Gradients.size]

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(plateModifier)
            .testTag("now_playing_screen")
    ) {
        // Floating Ambient Liquid Orbs Background
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.35f)
        ) {
            val centerWidth = size.width / 2
            val centerHeight = size.height / 3 // Float primarily behind/around the album art
            val radius = size.width * 0.7f

            val x1 = centerWidth + (size.width * 0.25f * cos(Math.toRadians(angle1.toDouble())).toFloat())
            val y1 = centerHeight + (size.height * 0.15f * sin(Math.toRadians(angle1.toDouble())).toFloat())
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(gradientColors.first().copy(alpha = 0.8f), Color.Transparent),
                    center = Offset(x1, y1),
                    radius = radius
                ),
                center = Offset(x1, y1),
                radius = radius
            )

            val x2 = centerWidth + (size.width * 0.25f * cos(Math.toRadians(angle2.toDouble())).toFloat())
            val y2 = centerHeight + (size.height * 0.15f * sin(Math.toRadians(angle2.toDouble())).toFloat())
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        (if (gradientColors.size > 1) gradientColors[1] else gradientColors.first()).copy(alpha = 0.8f),
                        Color.Transparent
                    ),
                    center = Offset(x2, y2),
                    radius = radius
                ),
                center = Offset(x2, y2),
                radius = radius
            )
        }

        // Main Layout Container
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            // 1. Header Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("now_playing_back_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse Player",
                        tint = Color(0xFFE6E1E5),
                        modifier = Modifier.size(32.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "PLAYING FROM ALBUM",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.5.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color(0xFFCAC4D0).copy(alpha = 0.7f)
                    )
                    Text(
                        text = track.album,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFE6E1E5),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 200.dp)
                    )
                }

                IconButton(onClick = { /* Settings context option */ }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Track Options",
                        tint = Color(0xFFE6E1E5)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.1f))

            // 2. Large Album Art Container (with interactive lyrics flip overlay)
            var swipeDragAccumulator by remember { mutableFloatStateOf(0f) }
            val swipeThresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) { 80.dp.toPx() }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(gradientColors))
                    .then(
                        if (appSettings.swipeToChangeSong) {
                            Modifier.pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onDragStart = { swipeDragAccumulator = 0f },
                                    onDragEnd = {
                                        when {
                                            swipeDragAccumulator <= -swipeThresholdPx -> onNext()
                                            swipeDragAccumulator >= swipeThresholdPx -> onPrevious()
                                        }
                                        swipeDragAccumulator = 0f
                                    },
                                    onHorizontalDrag = { change, dragAmount ->
                                        change.consume()
                                        swipeDragAccumulator += dragAmount
                                    }
                                )
                            }
                        } else {
                            Modifier
                        }
                    )
                    .testTag("album_art_container"),
                contentAlignment = Alignment.Center
            ) {
                // Real cover art (JioSaavn search results), if this track has one. Mock-catalog
                // tracks have no imageUrl and keep the emoji placeholder below unchanged.
                if (track.imageUrl != null) {
                    coil.compose.AsyncImage(
                        model = track.imageUrl,
                        contentDescription = "${track.title} cover art",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                }

                // If lyrics overlay is requested, show them on top of the cover art beautifully
                if (showLyrics) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.85f))
                            .padding(20.dp)
                    ) {
                        LyricsContent(
                            lyricsResult = lyricsResult,
                            positionMs = positionMs,
                            modifier = Modifier.fillMaxSize()
                        )
                        // Pill overlay to collapse lyrics
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF4F378B))
                                .clickable { showLyrics = false }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Show Cover", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (track.imageUrl == null) {
                    // Huge Vinyl/Note Icon representation - only when there's no real art to show
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🎵", fontSize = 64.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(0.1f))

            // 3. Track Title & Artist Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFE6E1E5),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("now_playing_title")
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFCAC4D0),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("now_playing_artist")
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Download the currently playing track for offline playback
                    DownloadButton(
                        track = track,
                        tint = Color(0xFFE6E1E5),
                        modifier = Modifier.testTag("now_playing_download_button")
                    )

                    // Heart Icon
                    IconButton(
                        onClick = { likedSongsViewModel.toggle(track) },
                        modifier = Modifier.testTag("now_playing_heart_button")
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = if (isLiked) "Remove from Liked" else "Add to Liked",
                            tint = if (isLiked) Color.Red else Color(0xFFE6E1E5),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 4. Progress Seek Bar & Labels
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = progress,
                    onValueChange = onProgressChange,
                    colors = SliderDefaults.colors(
                        activeTrackColor = Color(0xFFD0BCFF),
                        inactiveTrackColor = Color(0xFFE6E1E5).copy(alpha = 0.2f),
                        thumbColor = Color(0xFFD0BCFF)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("now_playing_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(elapsedSeconds),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFCAC4D0)
                    )
                    Text(
                        text = "-${formatTime(remainingSeconds)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFCAC4D0)
                    )
                }

                // Real codec/bitrate of the audio actually decoding right now - absent (not a
                // placeholder value) until ExoPlayer reports the first selected format.
                if (audioFormatLabel != null) {
                    Text(
                        text = audioFormatLabel,
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.5.sp),
                        color = Color(0xFFCAC4D0).copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .testTag("now_playing_codec_label")
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.1f))

            // 5. Main Controls Row (Prev, Play/Pause, Next)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous Button
                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier.testTag("now_playing_prev")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous Track",
                        tint = Color(0xFFE6E1E5),
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Play / Pause Circle
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFD0BCFF))
                        .clickable { onPlayPauseToggle() }
                        .testTag("now_playing_play_pause"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color(0xFF0C0C0E),
                        modifier = Modifier.size(40.dp)
                    )
                }

                // Next Button
                IconButton(
                    onClick = onNext,
                    modifier = Modifier.testTag("now_playing_next")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next Track",
                        tint = Color(0xFFE6E1E5),
                        modifier = Modifier.size(36.dp)
                    )
                }

            }

            Spacer(modifier = Modifier.weight(0.1f))

            // 6. Secondary Action Row (Queue, Sleep Timer, Lyrics, Shuffle, Repeat)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Queue
                IconButton(
                    onClick = { showQueueDialog = true },
                    modifier = Modifier.testTag("now_playing_queue")
                ) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = "Queue",
                        tint = Color(0xFFE6E1E5).copy(alpha = 0.8f),
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Sleep Timer - shows the real remaining time beside the icon while active,
                // ticking once a second so it stays accurate rather than a static snapshot.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { showSleepTimerDialog = true }
                        .testTag("now_playing_sleep_timer")
                        .padding(vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = if (sleepTimerEndAtMs != null) Icons.Default.Bedtime else Icons.Default.BedtimeOff,
                        contentDescription = "Sleep Timer",
                        tint = if (sleepTimerEndAtMs != null) Color(0xFFD0BCFF) else Color(0xFFE6E1E5).copy(alpha = 0.8f),
                        modifier = Modifier.size(22.dp)
                    )
                    if (sleepTimerEndAtMs != null) {
                        var remainingMs by remember(sleepTimerEndAtMs) { mutableStateOf(sleepTimerEndAtMs - System.currentTimeMillis()) }
                        LaunchedEffect(sleepTimerEndAtMs) {
                            while (true) {
                                remainingMs = sleepTimerEndAtMs - System.currentTimeMillis()
                                if (remainingMs <= 0) break
                                delay(1000)
                            }
                        }
                        val totalSeconds = (remainingMs / 1000).coerceAtLeast(0)
                        Text(
                            text = "%d:%02d".format(totalSeconds / 60, totalSeconds % 60),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFD0BCFF),
                            modifier = Modifier.testTag("now_playing_sleep_timer_remaining")
                        )
                    }
                }

                // Lyrics
                IconButton(
                    onClick = { showLyrics = !showLyrics },
                    modifier = Modifier.testTag("now_playing_lyrics_chip")
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Lyrics",
                        tint = if (showLyrics) Color(0xFFD0BCFF) else Color(0xFFE6E1E5).copy(alpha = 0.8f),
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Shuffle - wired to real ExoPlayer shuffle mode, not a cosmetic toggle
                IconButton(
                    onClick = onToggleShuffle,
                    modifier = Modifier.testTag("now_playing_shuffle")
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffleEnabled) Color(0xFFD0BCFF) else Color(0xFFE6E1E5).copy(alpha = 0.6f),
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Repeat - cycles OFF/ALL/ONE against the real Player.repeatMode
                IconButton(
                    onClick = onCycleRepeat,
                    modifier = Modifier.testTag("now_playing_repeat")
                ) {
                    Icon(
                        imageVector = if (repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = "Repeat",
                        tint = if (repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) Color(0xFFD0BCFF) else Color(0xFFE6E1E5).copy(alpha = 0.6f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        if (showSleepTimerDialog) {
            SleepTimerDialog(
                activeEndAtMs = sleepTimerEndAtMs,
                onDismiss = { showSleepTimerDialog = false },
                onSelectMinutes = { minutes ->
                    onStartSleepTimer(minutes)
                    showSleepTimerDialog = false
                },
                onCancelTimer = {
                    onCancelSleepTimer()
                    showSleepTimerDialog = false
                }
            )
        }

        if (showQueueDialog) {
            QueueDialog(
                queue = queue,
                currentTrack = track,
                onDismiss = { showQueueDialog = false },
                onSelect = { index ->
                    onJumpToQueueIndex(index)
                    showQueueDialog = false
                }
            )
        }
    }
}

@Composable
private fun SleepTimerDialog(
    activeEndAtMs: Long?,
    onDismiss: () -> Unit,
    onSelectMinutes: (Int) -> Unit,
    onCancelTimer: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1E),
        title = { Text("Sleep Timer", color = Color(0xFFE6E1E5), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                listOf(15, 30, 45, 60).forEach { minutes ->
                    Text(
                        text = "$minutes minutes",
                        color = Color(0xFFE6E1E5),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectMinutes(minutes) }
                            .padding(vertical = 12.dp)
                            .testTag("sleep_timer_option_$minutes")
                    )
                }
            }
        },
        confirmButton = {
            if (activeEndAtMs != null) {
                TextButton(onClick = onCancelTimer) {
                    Text("Turn Off", color = Color(0xFFD0BCFF), fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFFCAC4D0))
            }
        }
    )
}

@Composable
private fun QueueDialog(
    queue: List<Track>,
    currentTrack: Track,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A1E),
        title = { Text("Queue", color = Color(0xFFE6E1E5), fontWeight = FontWeight.Bold) },
        text = {
            if (queue.isEmpty()) {
                Text("Nothing queued.", color = Color(0xFFCAC4D0))
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    itemsIndexed(queue) { index, queuedTrack ->
                        val isCurrent = queuedTrack.downloadKey() == currentTrack.downloadKey()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(index) }
                                .padding(vertical = 10.dp)
                                .testTag("queue_item_$index"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = queuedTrack.title,
                                    color = if (isCurrent) Color(0xFFD0BCFF) else Color(0xFFE6E1E5),
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = queuedTrack.artist,
                                    color = Color(0xFFCAC4D0),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color(0xFFD0BCFF), fontWeight = FontWeight.Bold)
            }
        }
    )
}

/**
 * Renders whatever [LrcLibProvider.fetchLyrics] came back with. Null means still loading. Synced
 * lyrics get a scrolling, karaoke-style list that highlights and auto-centers the line whose
 * timestamp [positionMs] has most recently passed - everything else (plain-only lyrics,
 * instrumental tracks, no match, a fetch error) gets a plain, non-highlighted message instead,
 * since none of those have real timing data to sync against.
 */
@Composable
private fun LyricsContent(
    lyricsResult: LyricsResult?,
    positionMs: Long,
    modifier: Modifier = Modifier
) {
    when (lyricsResult) {
        null -> LyricsMessage("Loading lyrics…", modifier)
        is LyricsResult.Error -> LyricsMessage("Couldn't load lyrics right now.", modifier)
        LyricsResult.NotFound -> LyricsMessage("No lyrics found for this track.", modifier)
        LyricsResult.Instrumental -> LyricsMessage("Instrumental - no lyrics.", modifier)
        is LyricsResult.PlainOnly -> {
            Column(modifier = modifier.verticalScroll(rememberScrollState())) {
                LyricsHeader()
                Text(
                    text = lyricsResult.text,
                    style = MaterialTheme.typography.titleMedium.copy(lineHeight = 26.sp),
                    color = Color(0xFFE6E1E5).copy(alpha = 0.85f)
                )
            }
        }
        is LyricsResult.Synced -> SyncedLyricsList(lyricsResult.lines, positionMs, modifier)
    }
}

@Composable
private fun LyricsMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFFE6E1E5).copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LyricsHeader() {
    Text(
        text = "Lyrics",
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = Color(0xFFD0BCFF),
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
private fun SyncedLyricsList(lines: List<LyricLine>, positionMs: Long, modifier: Modifier = Modifier) {
    val currentIndex = remember(lines, positionMs) { LrcLibProvider.currentLineIndex(lines, positionMs) }
    val listState = rememberLazyListState()

    // +1 to skip past the "Lyrics" header item at index 0; -2 so the current line settles a
    // little below the top of the visible area rather than being pinned to it.
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) {
            listState.animateScrollToItem(max(0, currentIndex + 1 - 2))
        }
    }

    LazyColumn(modifier = modifier, state = listState) {
        item { LyricsHeader() }
        itemsIndexed(lines) { index, line ->
            val isCurrent = index == currentIndex
            // Word-by-word highlighting only makes sense for whichever line is actually
            // playing right now - every other line is either fully sung already or hasn't
            // started, so the plain per-line style below covers those with no per-frame
            // recomputation wasted on lines that aren't animating.
            if (isCurrent && line.words != null) {
                WordSyncedLyricLine(words = line.words, positionMs = positionMs)
            } else {
                Text(
                    text = line.text.ifBlank { "♪" },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Medium,
                        lineHeight = 26.sp
                    ),
                    color = if (isCurrent) Color(0xFFD0BCFF) else Color(0xFFE6E1E5).copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

/** Word-by-word ("karaoke") highlighting for the currently active line, when [LyricLine.words]
 * provides that granularity (see [BetterLyricsProvider], backed by Kugou's KRC timing) - each
 * word lights up as playback reaches its start time, rather than the whole line lighting up at
 * once the way [SyncedLyricsList]'s plain per-line style does for lines without this data. */
@Composable
private fun WordSyncedLyricLine(words: List<LyricWord>, positionMs: Long, modifier: Modifier = Modifier) {
    val annotated = remember(words, positionMs) {
        buildAnnotatedString {
            for (word in words) {
                val sung = positionMs >= word.startMs
                withStyle(
                    SpanStyle(
                        color = if (sung) Color(0xFFD0BCFF) else Color(0xFFE6E1E5).copy(alpha = 0.45f),
                        fontWeight = if (sung) FontWeight.ExtraBold else FontWeight.Medium
                    )
                ) {
                    append(word.text)
                }
            }
        }
    }
    Text(
        text = annotated,
        style = MaterialTheme.typography.titleMedium.copy(lineHeight = 26.sp),
        modifier = modifier.padding(vertical = 4.dp)
    )
}
