package com.example

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Records every track that actually starts playing, so Home's "Recently Played" shelf can be
 * real playback history instead of a fixed mock list. A process-wide singleton (see
 * [getInstance]), same pattern as [DownloadRepository], since playback (and thus history
 * recording) happens from [PlaybackService]/[PlayerViewModel] regardless of which screen is
 * currently visible.
 */
class PlaybackHistoryRepository private constructor(context: Context) {

    private val dao = MuseFlowDatabase.getInstance(context.applicationContext).playbackHistoryDao()
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun observeRecent(limit: Int): Flow<List<PlaybackHistoryEntity>> = dao.observeRecent(limit)

    /** For Library's "My Top 50" tile - real play counts, not just recency. */
    fun observeTopPlayed(limit: Int): Flow<List<PlaybackHistoryEntity>> = dao.observeTopPlayed(limit)

    fun recordPlayed(track: Track) {
        repositoryScope.launch {
            val key = track.downloadKey()
            // Increments the existing row's playCount rather than a plain REPLACE overwrite, so
            // "My Top 50" reflects how often a track has actually been played, not just whether
            // it's ever been played once.
            val existingCount = dao.getByKey(key)?.playCount ?: 0
            dao.upsert(
                PlaybackHistoryEntity(
                    key = key,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    duration = track.duration,
                    gradientIndex = track.gradientIndex,
                    imageUrl = track.imageUrl,
                    streamUrl = track.streamUrl,
                    playedAt = System.currentTimeMillis(),
                    sourceId = track.sourceId,
                    sourceType = track.sourceType?.name,
                    playCount = existingCount + 1
                )
            )
        }
    }

    companion object {
        @Volatile private var instance: PlaybackHistoryRepository? = null

        fun getInstance(context: Context): PlaybackHistoryRepository =
            instance ?: synchronized(this) {
                instance ?: PlaybackHistoryRepository(context.applicationContext).also { instance = it }
            }
    }
}

fun PlaybackHistoryEntity.toTrack(): Track = Track(
    title = title,
    artist = artist,
    album = album,
    duration = duration,
    plays = "",
    gradientIndex = gradientIndex,
    imageUrl = imageUrl,
    streamUrl = streamUrl,
    sourceType = sourceType?.let { runCatching { MusicSource.valueOf(it) }.getOrNull() },
    sourceId = sourceId
)
