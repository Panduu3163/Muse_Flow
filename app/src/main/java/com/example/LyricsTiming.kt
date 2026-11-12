package com.example

/** Some rich-sync feeds mark their last syllable with end=start. Repair only impossible windows;
 * leave valid source timing alone, including overlapping vocals and intentional gaps. */
internal fun normalizeSyncedLyrics(result: LyricsResult.Synced): LyricsResult.Synced {
    val ordered = result.lines.sortedBy { it.timeMs }
    return LyricsResult.Synced(ordered.mapIndexed { lineIndex, line ->
        val rawWords = line.words.orEmpty()
        if (rawWords.isEmpty()) return@mapIndexed line
        val nextLineStart = ordered.getOrNull(lineIndex + 1)?.timeMs
        val words = rawWords.mapIndexed { wordIndex, word ->
            val start = word.startMs.coerceAtLeast(0L)
            val nextWordStart = rawWords.getOrNull(wordIndex + 1)?.startMs
            val end = if (word.endMs > start) word.endMs else {
                sequenceOf(nextWordStart, line.endMs, nextLineStart, start + 500L)
                    .filterNotNull().first { it > start }
            }
            word.copy(startMs = start, endMs = end)
        }
        val sameText = sequenceOf("", " ").any { separator ->
            words.joinToString(separator) { it.text }.trim() == line.text.trim()
        }
        line.copy(words = words.takeIf { sameText })
    })
}
