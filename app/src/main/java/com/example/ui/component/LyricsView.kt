package com.example.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.LyricLine
import com.example.LyricsResult
import com.example.LyricsTextPosition
import com.example.activeLineIndex

/**
 * The scrolling lyrics view.
 *
 * Synced lyrics track playback position and auto-scroll the active line to roughly a third from the
 * top - not the centre, because the lines a listener wants to read next are the ones *below* the
 * current one, so biasing upward shows more of them.
 *
 * Tapping a line seeks to it when [tapToSeek] is on. Plain (unsynced) lyrics render as a static
 * block with no highlight, since there's nothing to sync against.
 */
@Composable
fun LyricsView(
    result: LyricsResult?,
    positionMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    textSizeSp: Int = 20,
    lineSpacing: Float = 1.2f,
    textPosition: LyricsTextPosition = LyricsTextPosition.Center,
    blurInactive: Boolean = true,
    glow: Boolean = false,
    autoScroll: Boolean = true,
    tapToSeek: Boolean = true,
) {
    val alignment = when (textPosition) {
        LyricsTextPosition.Left -> TextAlign.Start
        LyricsTextPosition.Center -> TextAlign.Center
        LyricsTextPosition.Right -> TextAlign.End
    }

    when (result) {
        null -> CenteredBox(modifier) { CircularProgressIndicator() }

        is LyricsResult.Instrumental -> CenteredMessage(modifier, "♪  Instrumental")
        is LyricsResult.NotFound -> CenteredMessage(modifier, "No lyrics found for this track.")
        is LyricsResult.Error -> CenteredMessage(
            modifier,
            result.message,
            color = MaterialTheme.colorScheme.error,
        )

        // Unsynced: one text blob with no timing, so it renders as a plain scrollable block with
        // no highlight and no tap-to-seek - there's nothing to sync against.
        is LyricsResult.PlainOnly -> LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .testTag("lyrics_plain"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 32.dp),
        ) {
            item {
                Text(
                    text = result.text,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = textSizeSp.sp,
                        lineHeight = (textSizeSp * lineSpacing * 1.3f).sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = alignment,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        is LyricsResult.Synced -> SyncedLyrics(
            lines = result.lines,
            positionMs = positionMs,
            onSeekTo = onSeekTo,
            modifier = modifier,
            textSizeSp = textSizeSp,
            lineSpacing = lineSpacing,
            alignment = alignment,
            blurInactive = blurInactive,
            glow = glow,
            autoScroll = autoScroll,
            tapToSeek = tapToSeek,
        )
    }
}

@Composable
private fun SyncedLyrics(
    lines: List<LyricLine>,
    positionMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier,
    textSizeSp: Int,
    lineSpacing: Float,
    alignment: TextAlign,
    blurInactive: Boolean,
    glow: Boolean,
    autoScroll: Boolean,
    tapToSeek: Boolean,
) {
    val listState = rememberLazyListState()
    val activeIndex = remember(lines, positionMs) { lines.activeLineIndex(positionMs) }

    LaunchedEffect(activeIndex, autoScroll) {
        if (autoScroll && activeIndex >= 0) {
            // Offset so the active line sits ~1/3 down rather than pinned to the very top.
            listState.animateScrollToItem(
                index = activeIndex.coerceAtLeast(0),
                scrollOffset = -(textSizeSp * 6),
            )
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .testTag("lyrics_synced"),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 120.dp),
    ) {
        itemsIndexed(lines, key = { index, line -> "$index:${line.timeMs}" }) { index, line ->
            val isActive = index == activeIndex

            val color by animateColorAsState(
                targetValue = if (isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                label = "lyric_colour",
            )
            val scale by animateFloatAsState(
                targetValue = if (isActive) 1f else 0.94f,
                label = "lyric_scale",
            )

            Text(
                text = line.text,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = (textSizeSp * scale).sp,
                    lineHeight = (textSizeSp * lineSpacing * 1.3f).sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                ),
                color = color,
                textAlign = alignment,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (tapToSeek) {
                            Modifier.clickable { onSeekTo(line.timeMs) }
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    // Blur is applied only to inactive lines, and only when asked - it's a real
                    // render-pass cost on a list that redraws several times a second.
                    .then(
                        if (blurInactive && !isActive) Modifier.blur(2.dp) else Modifier
                    ),
            )
        }
    }
}

@Composable
private fun CenteredBox(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun CenteredMessage(
    modifier: Modifier,
    message: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = color,
            textAlign = TextAlign.Center,
        )
    }
}
