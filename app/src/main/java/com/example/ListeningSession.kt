package com.example

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID
import java.util.Locale

/** New observations only: historical starts must never be backfilled as completed listens. */
@Entity(tableName = "listening_sessions", indices = [Index("startedAt"), Index("trackKey")])
data class ListeningSessionEntity(
    @PrimaryKey val id: String,
    val trackKey: String,
    val sourceId: String?,
    val artist: String,
    val startedAt: Long,
    val listenedMs: Long,
    val durationMs: Long,
    val outcome: String,
)

internal enum class ListeningOutcome { OPEN, ENDED, SKIPPED, INTERRUPTED, ERROR }

/** Event-driven: no heartbeat, no seek-position-as-listened-time, no buffering/pause time. */
internal class ListeningSessionTracker(
    private val elapsedMs: () -> Long,
    private val wallMs: () -> Long,
    private val generation: () -> Long,
    private val save: (ListeningSessionEntity, Long) -> Unit,
) {
    private var session: ListeningSessionEntity? = null
    private var epoch = 0L
    private var runningSince: Long? = null

    fun ensureStarted(track: Track, durationMs: Long) {
        if (session == null) start(track, durationMs, false)
    }

    fun start(track: Track, durationMs: Long, playing: Boolean) {
        finish(ListeningOutcome.INTERRUPTED)
        epoch = generation()
        session = ListeningSessionEntity(UUID.randomUUID().toString(), track.downloadKey(),
            track.sourceId, track.artist, wallMs(), 0, durationMs.coerceAtLeast(0), ListeningOutcome.OPEN.name)
        runningSince = if (playing) elapsedMs() else null
    }

    fun playingChanged(playing: Boolean, durationMs: Long) {
        accumulate()
        session = session?.let { it.copy(durationMs = durationMs.takeIf { d -> d > 0 } ?: it.durationMs) }
        runningSince = if (playing && session != null) elapsedMs() else null
        if (!playing) persist()
    }

    fun finish(outcome: ListeningOutcome) {
        val finalOutcome = if (outcome == ListeningOutcome.SKIPPED && runningSince == null)
            ListeningOutcome.INTERRUPTED else outcome
        accumulate()
        session = session?.copy(outcome = finalOutcome.name)
        persist()
        session = null
        runningSince = null
    }

    private fun accumulate() {
        val start = runningSince ?: return
        session = session?.let { it.copy(listenedMs = it.listenedMs + (elapsedMs() - start).coerceAtLeast(0)) }
        runningSince = null
    }

    private fun persist() {
        session?.takeIf { it.listenedMs > 0 }?.let { save(it, epoch) }
    }
}

internal fun ListeningSessionEntity.isPositiveListen(): Boolean =
    listenedMs >= 30_000 || (durationMs in 1..30_000 && listenedMs >= durationMs * 0.8)

/** A bounded Bayesian preference estimate, not a pretrained neural model. Neutral outcomes
 * cannot train a dislike; one accidental skip cannot dominate the provider's relevance. */
internal fun learnedRecordingPreferences(sessions: List<ListeningSessionEntity>, now: Long): Map<String, Double> =
    sessions.filter { it.startedAt in (now - 30L * 86_400_000)..now && it.sourceId != null }
        .groupBy { it.sourceId!! }.mapValues { (_, events) ->
            val positive = events.count { it.isPositiveListen() }.toDouble()
            val negative = events.count { it.outcome == ListeningOutcome.SKIPPED.name &&
                it.listenedMs in 3_000 until 30_000 && it.durationMs > 30_000 }.toDouble()
            ((positive + 2.0) / (positive + negative + 4.0) - 0.5) * 0.6
        }

internal fun learnedArtistPreferences(sessions: List<ListeningSessionEntity>, now: Long): Map<String, Double> =
    sessions.filter { it.startedAt in (now - 30L * 86_400_000)..now && it.artist.isNotBlank() }
        .groupBy { it.artist.trim().lowercase(Locale.ROOT) }.mapValues { (_, events) ->
            val positive = events.count { it.isPositiveListen() }.toDouble()
            val negative = events.count { it.outcome == ListeningOutcome.SKIPPED.name &&
                it.listenedMs in 3_000 until 30_000 && it.durationMs > 30_000 }.toDouble()
            ((positive + 4.0) / (positive + negative + 8.0) - 0.5) * 0.3
        }

internal fun recentFavourites(history: List<PlaybackHistoryEntity>, sessions: List<ListeningSessionEntity>, now: Long): List<Track> {
    val time = sessions.filter { it.startedAt in (now - 7L * 86_400_000)..now && it.isPositiveListen() }
        .groupBy { it.trackKey }.mapValues { (_, rows) -> rows.sumOf { it.listenedMs } }
    return history.filter { it.key in time }.sortedWith(compareByDescending<PlaybackHistoryEntity> { time[it.key] }
        .thenByDescending { it.playedAt }).take(12).map { it.toTrack() }
}
