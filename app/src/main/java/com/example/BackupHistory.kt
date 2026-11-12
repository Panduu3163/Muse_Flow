package com.example

import org.json.JSONArray
import org.json.JSONObject

internal const val BACKUP_SESSION_LIMIT = 2000

internal data class BackupHistory(
    val tracks: List<PlaybackHistoryEntity>,
    val events: List<PlaybackEventEntity>,
    val sessions: List<ListeningSessionEntity>,
)

internal fun PlaybackHistoryEntity.toBackupJson(): JSONObject = LikedSongEntity(
    key, title, artist, album, duration, gradientIndex, imageUrl, streamUrl, 0,
    sourceId, sourceType, albumId, artistId,
).toBackupJson().apply {
    remove("likedAt")
    put("playedAt", playedAt)
    put("playCount", playCount)
}

internal fun JSONObject.putBackupHistory(history: BackupHistory) {
    put("playbackHistory", JSONArray().apply { history.tracks.forEach { put(it.toBackupJson()) } })
    put("playbackEvents", JSONArray().apply { history.events.forEach { event ->
        put(JSONObject().put("trackKey", event.trackKey).put("playedAt", event.playedAt))
    } })
    put("listeningSessions", JSONArray().apply { history.sessions.forEach { session ->
        put(JSONObject().apply {
            put("id", session.id); put("trackKey", session.trackKey)
            put("sourceId", session.sourceId ?: JSONObject.NULL); put("artist", session.artist)
            put("startedAt", session.startedAt); put("listenedMs", session.listenedMs)
            put("durationMs", session.durationMs); put("outcome", session.outcome)
        })
    } })
}

internal fun parseBackupHistory(root: JSONObject): BackupHistory {
    val aliases = mutableMapOf<String, String>()
    val rows = root.getJSONArray("playbackHistory")
    val history = (0 until rows.length()).map { index ->
        val row = rows.getJSONObject(index)
        row.validateTrack()
        row.requireTime("playedAt")
        val count = row.opt("playCount")
        require(count is Int && count > 0) { "Invalid history play count" }
        val track = requireNotNull(row.toLikedSongEntity())
        val originalKey = row.getString("key")
        require(aliases[originalKey] == null || aliases[originalKey] == track.key) { "Ambiguous history recording identity" }
        require(aliases[track.key] == null || aliases[track.key] == track.key) { "Conflicting history recording identity" }
        aliases[originalKey] = track.key
        aliases[track.key] = track.key
        PlaybackHistoryEntity(track.key, track.title, track.artist, track.album, track.duration,
            track.gradientIndex, track.imageUrl, track.streamUrl, row.getLong("playedAt"),
            track.sourceId, track.sourceType, count, track.albumId, track.artistId)
    }.groupBy { it.key }.values.map { versions ->
        versions.maxBy { it.playedAt }.copy(playCount = versions.maxOf { it.playCount })
    }
    val events = root.getJSONArray("playbackEvents")
    val parsedEvents = (0 until events.length()).map { index ->
        val event = events.getJSONObject(index)
        event.validateStrings("trackKey")
        event.requireTime("playedAt")
        PlaybackEventEntity(trackKey = aliases[event.optString("trackKey")]
            ?: throw IllegalArgumentException("An event has no matching history track"), playedAt = event.getLong("playedAt"))
    }
    val sessions = root.getJSONArray("listeningSessions")
    val historyByKey = history.associateBy { it.key }
    val parsedSessions = (0 until sessions.length()).map { index ->
        val session = sessions.getJSONObject(index)
        session.validateStrings("id", "trackKey", "sourceId", "artist", "outcome")
        require(!session.isNull("id") && session.optString("id").isNotBlank()) { "A listening session has no identity" }
        session.requireTime("startedAt"); session.requireTime("listenedMs"); session.requireTime("durationMs")
        require(ListeningOutcome.entries.any { it.name == session.optString("outcome") }) { "Invalid listening outcome" }
        val trackKey = aliases[session.optString("trackKey")]
            ?: throw IllegalArgumentException("A session has no matching history track")
        val sourceId = if (session.isNull("sourceId")) null else session.getString("sourceId")
        val expectedSource = historyByKey[trackKey]?.sourceId
        require(expectedSource == null || sourceId == null || expectedSource == sourceId) { "Conflicting session recording identity" }
        ListeningSessionEntity(session.getString("id"), trackKey, sourceId,
            if (session.isNull("artist")) "" else session.getString("artist"),
            session.getLong("startedAt"), session.getLong("listenedMs"), session.getLong("durationMs"), session.getString("outcome"))
    }
    require(parsedSessions.distinctBy { it.id }.size == parsedSessions.size) { "Duplicate listening session identity" }
    parsedSessions.fold(0L) { total, session -> Math.addExact(total, session.listenedMs) }
    return BackupHistory(history, parsedEvents, parsedSessions)
}

private fun JSONObject.requireTime(field: String) {
    require(has(field)) { "Missing history value: $field" }
    validateTimestamp(field)
}

internal data class BackupHistoryPlan(
    val tracks: List<PlaybackHistoryEntity>,
    val events: List<PlaybackEventEntity>,
    val sessions: List<ListeningSessionEntity>,
    val newTracks: Int,
    val existingTracks: Int,
    val skippedEvents: Int,
    val newSessions: Int,
    val updatedSessions: Int,
    val skippedSessions: Int,
)

/** Conservative snapshot merge: never add aggregate counters or add the same session's heard time. */
internal fun planBackupHistory(
    incoming: BackupHistory,
    existingTracks: List<PlaybackHistoryEntity>,
    existingEvents: List<PlaybackEventEntity>,
    existingSessions: List<ListeningSessionEntity>,
): BackupHistoryPlan {
    val tracksByKey = existingTracks.associateBy { it.key }
    val eventCopies = existingEvents.groupingBy { it.copy(id = 0) }.eachCount().toMutableMap()
    val newEvents = incoming.events.filter { event ->
        val identity = event.copy(id = 0)
        val remaining = eventCopies[identity] ?: 0
        if (remaining > 0) { eventCopies[identity] = remaining - 1; false } else true
    }
    // Preserve genuine same-millisecond multiplicity, and use observed events as a lower bound.
    val observedCounts = (existingEvents + newEvents).groupingBy { it.trackKey }.eachCount()
    val mergedTracks = incoming.tracks.map { track ->
        val old = tracksByKey[track.key]
        val metadata = if (old != null && old.playedAt >= track.playedAt) old else track
        metadata.copy(playCount = maxOf(track.playCount, old?.playCount ?: 0, observedCounts[track.key] ?: 0))
    }
    val sessionMap = existingSessions.associateBy { it.id }.toMutableMap()
    incoming.sessions.forEach { session ->
        val old = sessionMap[session.id]
        require(old == null || (old.trackKey == session.trackKey && old.startedAt == session.startedAt && old.sourceId == session.sourceId)) {
            "Conflicting listening session identity"
        }
        val merged = when {
            old == null || session.listenedMs > old.listenedMs -> session
            session.listenedMs == old.listenedMs && old.outcome == ListeningOutcome.OPEN.name -> session
            else -> old
        }
        sessionMap[session.id] = merged
    }
    val retained = sessionMap.values.sortedWith(compareByDescending<ListeningSessionEntity> { it.startedAt }.thenBy { it.id })
        .take(BACKUP_SESSION_LIMIT)
    retained.fold(0L) { total, session -> Math.addExact(total, session.listenedMs) }
    val oldSessions = existingSessions.associateBy { it.id }
    val importedIds = incoming.sessions.map { it.id }.toSet()
    val changes = retained.filter { it.id in importedIds && oldSessions[it.id] != it }
    val newSessions = changes.count { it.id !in oldSessions }
    return BackupHistoryPlan(mergedTracks, newEvents, changes,
        incoming.tracks.count { it.key !in tracksByKey }, incoming.tracks.count { it.key in tracksByKey },
        incoming.events.size - newEvents.size, newSessions, changes.size - newSessions, incoming.sessions.size - changes.size)
}
