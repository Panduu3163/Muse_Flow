package com.example.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VerticalAlignCenter
import androidx.compose.material3.Icon
import com.example.LyricLine
import com.example.LyricWord
import com.example.LyricsResult
import com.example.LyricsTextPosition
import com.example.WordAnimationStyle
import com.example.activeLineIndex
import com.example.ui.theme.LocalReducedMotion
import kotlin.math.sin

/**
 * The scrolling lyrics view.
 *
 * Synced lyrics keep the active line at the vertical centre of the visible lyrics window.
 *
 * Tapping a line seeks to it when [tapToSeek] is on. Plain (unsynced) lyrics render as a static
 * block with no highlight, since there's nothing to sync against.
 */
@Composable
fun LyricsView(
    result: LyricsResult?,
    positionMs: Long,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    playbackSpeed: Float = 1f,
    positionRevision: Int = 0,
    onSeekTo: (Long) -> Unit,
    textSizeSp: Int = 20,
    lineSpacing: Float = 1.2f,
    textPosition: LyricsTextPosition = LyricsTextPosition.Center,
    blurInactive: Boolean = true,
    glow: Boolean = false,
    autoScroll: Boolean = true,
    tapToSeek: Boolean = true,
    wordAnimationStyle: WordAnimationStyle = WordAnimationStyle.Fade,
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
                    color = LocalContentColor.current,
                    textAlign = alignment,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        is LyricsResult.Synced -> SyncedLyrics(
            lines = result.lines,
            positionMs = positionMs,
            isPlaying = isPlaying,
            playbackSpeed = playbackSpeed,
            positionRevision = positionRevision,
            onSeekTo = onSeekTo,
            modifier = modifier,
            textSizeSp = textSizeSp,
            lineSpacing = lineSpacing,
            alignment = alignment,
            blurInactive = blurInactive,
            glow = glow,
            autoScroll = autoScroll,
            tapToSeek = tapToSeek,
            wordAnimationStyle = wordAnimationStyle,
        )
    }
}

@Composable
private fun SyncedLyrics(
    lines: List<LyricLine>,
    positionMs: Long,
    isPlaying: Boolean,
    playbackSpeed: Float,
    positionRevision: Int,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier,
    textSizeSp: Int,
    lineSpacing: Float,
    alignment: TextAlign,
    blurInactive: Boolean,
    glow: Boolean,
    autoScroll: Boolean,
    tapToSeek: Boolean,
    wordAnimationStyle: WordAnimationStyle,
) {
    val listState = rememberLazyListState()
    val activeIndex = remember(lines, positionMs) { lines.activeLineIndex(positionMs) }
    val reducedMotion = LocalReducedMotion.current
    var followLyrics by remember(lines) { mutableStateOf(true) }
    val manualScrollConnection = remember(lines) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) followLyrics = false
                return Offset.Zero
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
    val viewportHeight = with(LocalDensity.current) { maxHeight.roundToPx() }
    LaunchedEffect(lines, activeIndex, positionRevision, autoScroll, followLyrics, reducedMotion,
        viewportHeight) {
        if (autoScroll && followLyrics && activeIndex >= 0) {
            // Adjacent lines are already laid out. Animate their measured centre directly;
            // animateScrollToItem followed by a second correction caused the down-then-up hop.
            val wasVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == activeIndex }
            if (!wasVisible) {
                // A long seek is different: jump to the sought line once, then position it
                // exactly. No intermediate motion through unrelated verses.
                listState.scrollToItem(activeIndex)
                withFrameNanos { }
            }
            val layout = listState.layoutInfo
            val item = layout.visibleItemsInfo.firstOrNull { it.index == activeIndex }
            if (item != null) {
                // LazyColumn item offsets use the *content* coordinate system. With half-height
                // top/bottom padding, viewportStartOffset is negative and viewportEndOffset is
                // correspondingly shorter; viewportSize/2 put the active line below the lyrics
                // area's visual centre. The midpoint of the viewport offsets is the right target.
                val centre = (layout.viewportStartOffset + layout.viewportEndOffset) / 2f
                val correction = item.offset + item.size / 2f - centre
                if (kotlin.math.abs(correction) > 1f) {
                    if (reducedMotion || !wasVisible) listState.scrollBy(correction)
                    else listState.animateScrollBy(correction, spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessLow,
                    ))
                }
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(manualScrollConnection)
            .testTag("lyrics_synced"),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = maxHeight / 2),
    ) {
        itemsIndexed(lines, key = { index, line -> "$index:${line.timeMs}" }) { index, line ->
            val isActive = index == activeIndex
            // Word animation only applies to the line actually playing right now - animating
            // every line at once would be both pointless (nothing to sync them against) and a
            // lot of wasted per-frame text layout in a list that already redraws several times a
            // second.
            //
            // Use source-provided word/syllable timestamps when available. For line-timed
            // providers, estimated word pacing supplies a visual effect without claiming exact
            // word sync; the source label above the lyrics makes that distinction visible.
            val words = remember(line, lines, index) {
                displayLyricWords(line, lines.getOrNull(index + 1))
            }
            val wordSeparator = remember(line, words) { lyricWordSeparator(line, words) }
            val karaoke = isActive

            // The in-progress word's sweep is driven by this Animatable, not by re-reading
            // positionMs on every tick - that was still only 60ms-resolution (see
            // PlayerViewModel.lyricsPositionMs), which is fine for line/word *boundaries* but
            // visibly stepped for the sweep *within* a word. Animatable.animateTo runs on Compose's
            // own frame clock (matches display refresh rate), so once a word starts, its fraction
            // advances every frame regardless of how often positionMs itself updates - positionMs
            // only decides *which* word is active and seeds the animation's starting point.
            val activeWordIndex = if (karaoke) {
                remember(words, positionMs) { words.indexOfLast { positionMs >= it.startMs }.coerceAtLeast(0) }
            } else 0
            val activeWord = words.getOrNull(activeWordIndex)
            val wordProgress = remember(line) { Animatable(0f) }
            if (karaoke && activeWord != null) {
                // Position ticks select a word, while Compose's frame clock fills the space
                // between them. Pause/buffer, speed changes and seeks restart from the sampled
                // player position; a seek within the same word is caught by positionRevision.
                LaunchedEffect(activeWord, isPlaying, playbackSpeed, positionRevision, reducedMotion) {
                    val duration = (activeWord.endMs - activeWord.startMs).coerceAtLeast(1)
                    val elapsed = (positionMs - activeWord.startMs).coerceIn(0, duration)
                    wordProgress.snapTo(elapsed.toFloat() / duration)
                    val remaining = duration - elapsed
                    if (isPlaying && !reducedMotion && remaining > 0) {
                        wordProgress.animateTo(1f, tween(
                            (remaining / playbackSpeed.coerceAtLeast(0.25f)).toInt().coerceAtLeast(1),
                            easing = LinearEasing,
                        ))
                    }
                }
            }

            val sampledProgress = activeWord?.let { word ->
                ((positionMs - word.startMs).toFloat() / (word.endMs - word.startMs).coerceAtLeast(1))
                    .coerceIn(0f, 1f)
            } ?: 0f

            // Fixed white/dimmed-white rather than the app's dynamic theme colours - a
            // per-track/seed-driven accent can land close in hue or lightness to the inactive
            // colour (the same low-contrast failure mode the Stats/Search screens had), which
            // makes the sung-vs-upcoming word transition barely perceptible and leaves only the
            // coarser line-level cues (bold weight, the active-line scale bump) visibly "doing"
            // anything - reading as the whole line reacting at once instead of each word. A fixed
            // white scale guarantees the per-word contrast regardless of theme/seed, and is what
            // actually sells styles like Metro's tile/Vivi Music's gradient sweep.
            val color by animateColorAsState(
                targetValue = if (isActive) Color.White else Color.White.copy(alpha = 0.48f),
                animationSpec = tween(if (reducedMotion) 0 else 320, easing = FastOutSlowInEasing),
                label = "lyric_colour",
            )
            val scale by animateFloatAsState(
                targetValue = if (reducedMotion || isActive) 1f else 0.98f,
                animationSpec = tween(if (reducedMotion) 0 else 320, easing = FastOutSlowInEasing),
                label = "lyric_scale",
            )

            val activeTextColor = Color.White
            val inactiveTextColor = Color.White.copy(alpha = 0.48f)
            // Dark, not theme-driven either: Metro's "sung tile" is a solid block in sungColor
            // (now white) with the character punched out in this colour - it needs to stay dark
            // for the punched-out text to read against a white tile regardless of theme.
            val metroTileTextColor = Color.Black

            Text(
                text = if (karaoke) {
                    buildKaraokeText(
                        words = words,
                        separator = wordSeparator,
                        activeWordIndex = activeWordIndex,
                        activeWordProgress = if (reducedMotion) sampledProgress else wordProgress.value,
                        sungColor = activeTextColor,
                        upcomingColor = inactiveTextColor,
                        style = if (reducedMotion) WordAnimationStyle.Fade else wordAnimationStyle,
                        glow = glow && !reducedMotion,
                        metroTileTextColor = metroTileTextColor,
                    )
                } else {
                    AnnotatedString(line.text)
                },
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = (textSizeSp * scale).sp,
                    lineHeight = (textSizeSp * lineSpacing * 1.3f).sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    // karaoke (real word data or the synthetic per-line stand-in) covers every
                    // active line now, and buildKaraokeText already applies glow per span - so
                    // there's no "no word timing at all" case left for this outer style to cover.
                ),
                color = if (karaoke) Color.Unspecified else color,
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
    if (autoScroll && !followLyrics) {
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)
                .clickable { followLyrics = true },
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.48f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.20f)),
            shadowElevation = 3.dp,
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.VerticalAlignCenter, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface)
                Text("Follow lyrics", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
    }
}

/**
 * Splits [line] into its own words with synthesized per-word timing, for a source that only gave
 * a line-level timestamp. The original whitespace stays attached to its preceding token so
 * rendering never changes punctuation, spacing, or line breaks. This is estimated pacing, not
 * source-provided word timing. Scripts without spaces remain one timed segment.
 */
internal fun displayLyricWords(line: LyricLine, nextLine: LyricLine?): List<LyricWord> =
    line.words?.takeIf { it.isNotEmpty() } ?: synthesizeWordTimings(line, nextLine)

internal fun lyricWordSeparator(line: LyricLine, words: List<LyricWord>): String =
    if (words.joinToString("") { it.text }.trim() == line.text.trim()) "" else " "

private fun synthesizeWordTimings(line: LyricLine, nextLine: LyricLine?): List<LyricWord> {
    val lineEnd = line.endMs?.takeIf { it > line.timeMs } ?: nextLine?.timeMs?.takeIf { it > line.timeMs } ?: (line.timeMs + 4000L)
    val duration = (lineEnd - line.timeMs).coerceAtLeast(1L)
    val tokens = Regex("\\S+\\s*").findAll(line.text).map { it.value }.toList().toMutableList()
    if (tokens.isNotEmpty()) {
        val prefix = line.text.substringBefore(tokens.first())
        tokens[0] = prefix + tokens[0]
    }
    if (tokens.size <= 1) return listOf(LyricWord(line.timeMs, lineEnd, line.text))

    val totalChars = tokens.sumOf { token -> token.count { !it.isWhitespace() } }.coerceAtLeast(1)
    var cursor = line.timeMs
    return tokens.mapIndexed { index, token ->
        val start = cursor
        // The last word absorbs any rounding remainder rather than leaving a timing gap before
        // the next line takes over.
        val end = if (index == tokens.lastIndex) {
            lineEnd
        } else {
            (start + (duration * token.count { !it.isWhitespace() } / totalChars).coerceAtLeast(1L)).coerceAtMost(lineEnd)
        }
        cursor = end
        LyricWord(start, end, token)
    }
}

/**
 * Builds the currently-playing line as a karaoke-style [AnnotatedString], one [SpanStyle] per
 * word rather than per-composable - a `Row` of individual `Text`s would need its own wrapping
 * logic to match a lyric line's natural word-wrap, which `Text` + `AnnotatedString` already gets
 * for free.
 *
 * A word before [activeWordIndex] is "sung" (fully lit); one after is "upcoming" (dim); the word
 * *at* [activeWordIndex] is "singing now" and gets [style]'s specific treatment driven by
 * [activeWordProgress] - a per-frame [Animatable] value (see the call site in [SyncedLyrics]), not
 * a value recomputed from the player's polled position. That split is what makes the sweep smooth:
 * this function itself has no notion of *time*, only "which word" and "how far through it," so it
 * renders identically whether it's called once or sixty times a second.
 */
private fun buildKaraokeText(
    words: List<LyricWord>,
    separator: String,
    activeWordIndex: Int,
    activeWordProgress: Float,
    sungColor: Color,
    upcomingColor: Color,
    style: WordAnimationStyle,
    glow: Boolean,
    metroTileTextColor: Color,
): AnnotatedString = buildAnnotatedString {
    words.forEachIndexed { index, word ->
        if (index > 0) append(separator)
        when {
            index < activeWordIndex -> withStyle(sungSpan(sungColor, glow)) { append(word.text) }
            index > activeWordIndex -> withStyle(SpanStyle(color = upcomingColor)) { append(word.text) }
            else -> appendInProgressWord(word.text, activeWordProgress, sungColor, upcomingColor, style, glow, metroTileTextColor)
        }
    }
}

private fun sungSpan(color: Color, glow: Boolean) = SpanStyle(
    color = color,
    shadow = if (glow) Shadow(color = color, blurRadius = 18f) else null,
)

/** Renders the word being sung now, with each style using its own motion treatment. */
private fun androidx.compose.ui.text.AnnotatedString.Builder.appendInProgressWord(
    text: String,
    fraction: Float,
    sungColor: Color,
    upcomingColor: Color,
    style: WordAnimationStyle,
    glow: Boolean,
    metroTileTextColor: Color,
) {
    val eased = fraction * fraction * (3f - 2f * fraction)
    when (style) {
        WordAnimationStyle.Karaoke -> {
            // A narrow colour edge travels continuously across the word. Character-based
            // substring cuts stepped at each glyph and rebuilt text spans during playback.
            val leading = (fraction - .035f).coerceIn(0f, 1f)
            val trailing = (fraction + .035f).coerceIn(0f, 1f)
            withStyle(SpanStyle(brush = Brush.horizontalGradient(
                0f to sungColor, leading to sungColor,
                trailing to upcomingColor, 1f to upcomingColor,
            ))) { append(text) }
        }

        WordAnimationStyle.Bounce -> {
            // A gentle sine envelope peaks at the midpoint and settles without a sharp corner.
            val bounce = kotlin.math.sin(fraction * Math.PI).toFloat()
            withStyle(
                SpanStyle(
                    color = lerp(upcomingColor, sungColor, eased),
                    fontSize = androidx.compose.ui.unit.TextUnit(1f + bounce * 0.025f, androidx.compose.ui.unit.TextUnitType.Em),
                    baselineShift = BaselineShift(bounce * 0.04f),
                    shadow = if (glow) Shadow(color = sungColor.copy(alpha = .45f * bounce), blurRadius = 16f * bounce) else null,
                )
            ) { append(text) }
        }

        WordAnimationStyle.Scale -> withStyle(
            SpanStyle(
                color = lerp(upcomingColor, sungColor, eased),
                fontSize = androidx.compose.ui.unit.TextUnit(1f + eased * 0.025f, androidx.compose.ui.unit.TextUnitType.Em),
                shadow = if (glow) Shadow(color = sungColor.copy(alpha = .4f * eased), blurRadius = 14f * eased) else null,
            )
        ) { append(text) }

        WordAnimationStyle.Wave -> withStyle(
            SpanStyle(
                color = lerp(upcomingColor, sungColor, eased),
                baselineShift = BaselineShift(sin(fraction * Math.PI).toFloat() * 0.055f),
                shadow = if (glow) Shadow(color = sungColor.copy(alpha = .35f * eased), blurRadius = 12f * eased) else null,
            )
        ) { append(text) }

        WordAnimationStyle.Fade -> withStyle(
            SpanStyle(
                color = lerp(upcomingColor, sungColor, eased),
                shadow = if (glow) Shadow(color = sungColor, blurRadius = 14f * eased) else null,
            )
        ) { append(text) }

        // Adapted from Echo Music's METRO_LYRICS style (ui/component/MetroLyrics.kt), which
        // renders lyrics on a raw Canvas with a hard per-character cut and bold, high-contrast
        // glyphs - a blocky "tile" look rather than the smooth colour/size ramps every other
        // style here uses. That canvas approach doesn't translate directly onto this
        // AnnotatedString-based renderer, so the technique is adapted rather than ported
        // verbatim: a hard (non-lerped) snap at the halfway point, a solid background "tile"
        // behind the already-sung portion, and a jump straight to Black weight - no easing
        // anywhere, on purpose.
        WordAnimationStyle.Metro -> {
            val cut = (text.length * fraction).toInt().coerceIn(0, text.length)
            if (cut > 0) {
                withStyle(
                    SpanStyle(
                        color = metroTileTextColor,
                        background = sungColor,
                        fontWeight = FontWeight.Black,
                        letterSpacing = androidx.compose.ui.unit.TextUnit(0.5f, androidx.compose.ui.unit.TextUnitType.Sp),
                    )
                ) { append(text.substring(0, cut)) }
            }
            if (cut < text.length) {
                withStyle(
                    SpanStyle(
                        color = upcomingColor,
                        fontWeight = FontWeight.Black,
                        letterSpacing = androidx.compose.ui.unit.TextUnit(0.5f, androidx.compose.ui.unit.TextUnitType.Sp),
                    )
                ) { append(text.substring(cut)) }
            }
        }

        // Adapted from Echo Music's LYRICS_V2 ("Lyrics V2 (Fluid)") style
        // (ui/component/LyricsV2.kt, AnimatedWordV2) - a continuous sine-driven float/scale
        // bounce on the active word plus a soft-edged wipe (there it's a `drawWithContent`
        // gradient mask; here the same "soft edge instead of a hard cut" idea is reproduced by
        // ramping alpha smoothly over a couple of characters around the cut point instead of
        // Karaoke's binary switch) - continuous motion rather than the discrete per-word jumps
        // the other styles use.
        WordAnimationStyle.Fluid -> {
            val smooth = eased
            val bounce = kotlin.math.sin(fraction * Math.PI).toFloat()
            val cutPos = text.length * fraction
            val edgeChars = 1.5f
            text.forEachIndexed { i, c ->
                val charAlpha = (((cutPos - i) / edgeChars) + 0.5f).coerceIn(0f, 1f)
                withStyle(
                    SpanStyle(
                        color = lerp(upcomingColor, sungColor, charAlpha),
                        baselineShift = BaselineShift(bounce * 0.025f),
                        shadow = if (glow) Shadow(color = sungColor, blurRadius = 10f * smooth) else null,
                    )
                ) { append(c) }
            }
        }

        // Adapted from Echo Music's "Vivi Music (Fluid)" style (echomusic_1 in
        // constants/PreferenceKeys.kt, rendered by ui/component/EchoMusicLyrics.kt) - a
        // horizontal gradient brush swept across the *whole word* as one span (rather than
        // per-character, like Karaoke/Metro/Fluid above) with a glow shadow that intensifies as
        // the sweep progresses, giving a soft "light passing through" look distinct from the
        // hard/discrete styles.
        WordAnimationStyle.ViviMusic -> withStyle(
            SpanStyle(
                brush = Brush.horizontalGradient(
                    0f to sungColor,
                    (fraction - 0.05f).coerceAtLeast(0f) to sungColor,
                    (fraction + 0.05f).coerceAtMost(1f) to sungColor.copy(alpha = 0.45f),
                    1f to upcomingColor.copy(alpha = 0.45f),
                ),
                fontWeight = FontWeight.ExtraBold,
                shadow = Shadow(
                    color = sungColor.copy(alpha = 0.6f * fraction),
                    blurRadius = (12f * fraction).coerceAtLeast(0.1f),
                ),
            )
        ) { append(text) }

        // Adapted from Echo Music's APPLE style (ui/component/Lyrics.kt, the
        // `LyricsAnimationStyle.APPLE` branch) - Apple Music's real lyrics UI ramps each word in
        // with a smoothstep opacity/weight curve rather than a hard colour swap, so the sung
        // word "settles in" instead of snapping - reproduced here as a smoothstep-eased alpha
        // ramp (0.55 -> 1.0, matching Echo's own floor/ceiling) plus a weight step up to
        // ExtraBold and a glow that grows with the smoothed progress squared, same as the
        // source's `glowIntensity = smoothProgress * smoothProgress`.
        WordAnimationStyle.AppleMusic -> {
            val smooth = fraction * fraction * (3f - 2f * fraction)
            val alpha = 0.55f + 0.45f * smooth
            withStyle(
                SpanStyle(
                    color = sungColor.copy(alpha = alpha),
                    fontWeight = FontWeight.SemiBold,
                    shadow = if (glow) {
                        Shadow(
                            color = sungColor.copy(alpha = 0.2f + 0.4f * smooth * smooth),
                            blurRadius = 10f + 12f * smooth * smooth,
                        )
                    } else null,
                )
            ) { append(text) }
        }
    }
}

private fun lerp(start: Color, stop: Color, fraction: Float): Color = androidx.compose.ui.graphics.lerp(start, stop, fraction)

@Composable
private fun CenteredBox(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun CenteredMessage(
    modifier: Modifier,
    message: String,
    color: androidx.compose.ui.graphics.Color = LocalContentColor.current.copy(alpha = .72f),
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
