package com.example

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Small, independent lookup policy. Sources run together so a slow provider doesn't make all
 * later sources wait. Results are ranked by timing precision, then by the user's source order. */
internal data class LyricsLookup(val result: LyricsResult, val source: String?)

internal data class LyricsSourceRequest(
    val name: String,
    val fetch: suspend () -> LyricsResult,
)

internal fun lyricResultRank(result: LyricsResult): Int = when (result) {
    is LyricsResult.Synced -> when {
        result.lines.isEmpty() -> 0
        syncedWordCoverage(result) >= 0.5 -> 4
        else -> 3
    }
    is LyricsResult.PlainOnly -> if (result.text.isNotBlank()) 2 else 0
    is LyricsResult.Instrumental -> 1
    else -> 0
}

/** One timed word in an otherwise line-timed document should not win as full word sync. */
internal fun syncedWordCoverage(result: LyricsResult.Synced): Double {
    val total = result.lines.sumOf { it.text.length }.coerceAtLeast(1)
    val timed = result.lines.sumOf { line ->
        if (line.words.orEmpty().any { it.endMs > it.startMs }) line.text.length else 0
    }
    return timed.toDouble() / total
}

internal suspend fun fetchBestLyrics(
    sources: List<LyricsSourceRequest>,
    timeoutMs: Long = 14_000L,
): LyricsLookup {
    if (sources.isEmpty()) return LyricsLookup(LyricsResult.NotFound, null)
    return coroutineScope {
        val channel = Channel<Pair<Int, LyricsResult>>(sources.size)
        val jobs = sources.mapIndexed { index, source ->
            launch {
                val fetched = try {
                    source.fetch()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    LyricsResult.Error(error.message ?: "Lyrics source failed")
                }
                val result = if (fetched is LyricsResult.Synced) normalizeSyncedLyrics(fetched) else fetched
                channel.send(index to result)
            }
        }
        var best: Pair<Int, LyricsResult>? = null
        var completed = 0
        var failures = 0
        val responded = BooleanArray(sources.size)
        withTimeoutOrNull(timeoutMs) {
            while (completed < sources.size) {
                val candidate = channel.receive()
                completed++
                responded[candidate.first] = true
                if (candidate.second is LyricsResult.Error) failures++
                val current = best
                val newRank = lyricResultRank(candidate.second)
                val oldRank = lyricResultRank(current?.second ?: LyricsResult.NotFound)
                if (newRank > oldRank || (newRank == oldRank && newRank > 0 &&
                            (current == null || candidate.first < current.first))) {
                    best = candidate
                }
                // A word-timed result is final only after every higher-priority source
                // has answered. This keeps parallel lookup fast without changing the
                // user's configured source order when two sources have equal precision.
                val winner = best
                if (winner != null && lyricResultRank(winner.second) == 4 &&
                    (0 until winner.first).all { responded[it] }) break
            }
        }
        jobs.forEach { it.cancel() }
        channel.close()
        val picked = best
        when {
            picked != null -> LyricsLookup(picked.second, sources[picked.first].name)
            completed == sources.size && failures == sources.size ->
                LyricsLookup(LyricsResult.Error("Lyrics sources are unavailable. Retry in a moment."), null)
            completed < sources.size && failures == completed ->
                LyricsLookup(LyricsResult.Error("Lyrics search timed out. Retry to search again."), null)
            completed < sources.size || failures > 0 ->
                LyricsLookup(LyricsResult.Error("Some lyrics sources could not be checked. Retry or choose another source."), null)
            else -> LyricsLookup(LyricsResult.NotFound, null)
        }
    }
}
