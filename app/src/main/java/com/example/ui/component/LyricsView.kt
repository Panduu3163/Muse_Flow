package com.example.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.LyricLine
import com.example.LyricWord
import com.example.LyricsResult
import com.example.LyricsTextPosition
import com.example.WordAnimationStyle
import com.example.activeLineIndex
import kotlin.math.sin

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
            // Word animation only applies to the line actually playing right now - animating
            // every line at once would be both pointless (nothing to sync them against) and a
            // lot of wasted per-frame text layout in a list that already redraws several times a
            // second.
            //
            // Real per-word timing ([LyricLine.words]) only ever comes from BetterLyrics's Kugou
            // source, which in practice means it exists for close to no tracks outside Chinese-
            // language ones - every *other* synced source (LRCLib included) only has line-level
            // timing. Gating every animation style behind real word data would make all five
            // styles silently do nothing for almost every track, which is worse than the plain
            // line highlight it would otherwise replace. So when there's no word data, the line's
            // own words are still split out and each given a slice of the line's on-screen window
            // proportional to its character length - not one synthetic "word" spanning the whole
            // line, which would make Bounce/Scale/Wave move the entire line as one blob instead of
            // one word at a time. This is the same technique Echo Music's lyrics view uses for its
            // own word-timing-less lines.
            val words = remember(line, lines, index) { synthesizeWordTimings(line, lines.getOrNull(index + 1)) }
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
                // Keyed on the word itself (not positionMs) - restarting on every tick would reset
                // the animation to a stutter instead of letting it free-run between ticks.
                LaunchedEffect(activeWord) {
                    val duration = (activeWord.endMs - activeWord.startMs).coerceAtLeast(1)
                    val elapsed = (positionMs - activeWord.startMs).coerceIn(0, duration)
                    wordProgress.snapTo(elapsed.toFloat() / duration)
                    val remaining = duration - elapsed
                    if (remaining > 0) {
                        wordProgress.animateTo(1f, tween(remaining.toInt(), easing = LinearEasing))
                    }
                }
            }

            // Fixed white/dimmed-white rather than the app's dynamic theme colours - a
            // per-track/seed-driven accent can land close in hue or lightness to the inactive
            // colour (the same low-contrast failure mode the Stats/Search screens had), which
            // makes the sung-vs-upcoming word transition barely perceptible and leaves only the
            // coarser line-level cues (bold weight, the active-line scale bump) visibly "doing"
            // anything - reading as the whole line reacting at once instead of each word. A fixed
            // white scale guarantees the per-word contrast regardless of theme/seed, and is what
            // actually sells styles like Metro's tile/Vivi Music's gradient sweep.
            val color by animateColorAsState(
                targetValue = if (isActive) Color.White else Color.White.copy(alpha = 0.45f),
                label = "lyric_colour",
            )
            val scale by animateFloatAsState(
                targetValue = if (isActive) 1f else 0.94f,
                label = "lyric_scale",
            )

            val activeTextColor = Color.White
            val inactiveTextColor = Color.White.copy(alpha = 0.45f)
            // Dark, not theme-driven either: Metro's "sung tile" is a solid block in sungColor
            // (now white) with the character punched out in this colour - it needs to stay dark
            // for the punched-out text to read against a white tile regardless of theme.
            val metroTileTextColor = Color.Black

            Text(
                text = if (karaoke) {
                    buildKaraokeText(
                        words = words,
                        activeWordIndex = activeWordIndex,
                        activeWordProgress = wordProgress.value,
                        sungColor = activeTextColor,
                        upcomingColor = inactiveTextColor,
                        style = wordAnimationStyle,
                        glow = glow,
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
}

/**
 * Splits [line] into its own words with synthesized per-word timing, for a source that only gave
 * a line-level timestamp - each word gets a slice of the line's on-screen window (from its own
 * timestamp to [nextLine]'s, or a flat 4s guess for the last line) proportional to its character
 * length, matching how longer words plausibly take longer to sing than short ones. A line with no
 * spaces (a single "word", or a script that doesn't tokenize on whitespace) falls back to one
 * word spanning the whole window.
 */
private fun synthesizeWordTimings(line: LyricLine, nextLine: LyricLine?): List<LyricWord> {
    val lineEnd = nextLine?.timeMs ?: (line.timeMs + 4000L)
    val duration = (lineEnd - line.timeMs).coerceAtLeast(1L)
    val tokens = line.text.split(" ").filter { it.isNotEmpty() }
    if (tokens.size <= 1) return listOf(LyricWord(line.timeMs, lineEnd, line.text))

    val totalChars = tokens.sumOf { it.length }.coerceAtLeast(1)
    var cursor = line.timeMs
    return tokens.mapIndexed { index, token ->
        val start = cursor
        // The last word absorbs any rounding remainder rather than leaving a timing gap before
        // the next line takes over.
        val end = if (index == tokens.lastIndex) {
            lineEnd
        } else {
            (start + (duration * token.length / totalChars).coerceAtLeast(1L)).coerceAtMost(lineEnd)
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
    activeWordIndex: Int,
    activeWordProgress: Float,
    sungColor: Color,
    upcomingColor: Color,
    style: WordAnimationStyle,
    glow: Boolean,
    metroTileTextColor: Color,
): AnnotatedString = buildAnnotatedString {
    words.forEachIndexed { index, word ->
        if (index > 0) append(" ")
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

/** Renders the one word actually being sung right now, [fraction] (0f-1f) through its own
 * start/end window - the only place the four [WordAnimationStyle]s actually differ. */
private fun androidx.compose.ui.text.AnnotatedString.Builder.appendInProgressWord(
    text: String,
    fraction: Float,
    sungColor: Color,
    upcomingColor: Color,
    style: WordAnimationStyle,
    glow: Boolean,
    metroTileTextColor: Color,
) {
    when (style) {
        WordAnimationStyle.Karaoke -> {
            // A literal sweep: the fraction of the word already "sung" is split at the character
            // level, not just coloured as a whole - the classic karaoke-bar look.
            val cut = (text.length * fraction).toInt().coerceIn(0, text.length)
            if (cut > 0) withStyle(sungSpan(sungColor, glow)) { append(text.substring(0, cut)) }
            if (cut < text.length) withStyle(SpanStyle(color = upcomingColor)) { append(text.substring(cut)) }
        }

        WordAnimationStyle.Bounce -> {
            // A triangular envelope - rises to the peak at the word's midpoint, settles back down
            // by the time it's fully sung - rather than a size that only ever grows.
            val bounce = 1f - kotlin.math.abs(fraction - 0.5f) * 2f
            withStyle(
                SpanStyle(
                    color = lerp(upcomingColor, sungColor, fraction),
                    fontSize = androidx.compose.ui.unit.TextUnit(1f + bounce * 0.22f, androidx.compose.ui.unit.TextUnitType.Em),
                    shadow = if (glow) Shadow(color = sungColor, blurRadius = 14f) else null,
                )
            ) { append(text) }
        }

        WordAnimationStyle.Scale -> withStyle(
            SpanStyle(
                color = lerp(upcomingColor, sungColor, fraction),
                fontSize = androidx.compose.ui.unit.TextUnit(1f + fraction * 0.18f, androidx.compose.ui.unit.TextUnitType.Em),
                shadow = if (glow) Shadow(color = sungColor, blurRadius = 14f) else null,
            )
        ) { append(text) }

        WordAnimationStyle.Wave -> withStyle(
            SpanStyle(
                color = lerp(upcomingColor, sungColor, fraction),
                baselineShift = BaselineShift(sin(fraction * Math.PI).toFloat() * 0.18f),
                shadow = if (glow) Shadow(color = sungColor, blurRadius = 14f) else null,
            )
        ) { append(text) }

        WordAnimationStyle.Fade -> withStyle(
            SpanStyle(
                color = lerp(upcomingColor, sungColor, fraction),
                shadow = if (glow) Shadow(color = sungColor, blurRadius = 14f * fraction) else null,
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
            val smooth = fraction * fraction * (3f - 2f * fraction) // smoothstep, matches AnimatedWordV2's use of sin-based easing for continuous motion
            val bounce = kotlin.math.sin(fraction * Math.PI).toFloat()
            val cutPos = text.length * fraction
            val edgeChars = 1.5f
            text.forEachIndexed { i, c ->
                val charAlpha = (((cutPos - i) / edgeChars) + 0.5f).coerceIn(0f, 1f)
                withStyle(
                    SpanStyle(
                        color = lerp(upcomingColor, sungColor, charAlpha),
                        fontSize = androidx.compose.ui.unit.TextUnit(1f + bounce * 0.06f, androidx.compose.ui.unit.TextUnitType.Em),
                        baselineShift = BaselineShift(bounce * 0.06f),
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
                    fontWeight = if (fraction >= 0.999f) FontWeight.ExtraBold else FontWeight.SemiBold,
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
