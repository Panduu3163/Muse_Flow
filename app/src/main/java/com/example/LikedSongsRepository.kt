package com.example

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * The user's liked songs, for Library's real "Liked Songs" tab and the heart button on Now
 * Playing - both read/write the same Room table so a like made from either place is instantly
 * visible in the other. A process-wide singleton, same pattern as [DownloadRepository].
 */
class LikedSongsRepository private constructor(context: Context) {

    private val dao = MuseFlowDatabase.getInstance(context.applicationContext).likedSongDao()

    fun observeAll(): Flow<List<LikedSongEntity>> = dao.observeAll()

    fun observeIsLiked(track: Track): Flow<Boolean> = dao.observeIsLiked(track.downloadKey())

    suspend fun like(track: Track) {
        dao.like(
            LikedSongEntity(
                key = track.downloadKey(),
                title = track.title,
                artist = track.artist,
                album = track.album,
                duration = track.duration,
                gradientIndex = track.gradientIndex,
                imageUrl = track.imageUrl,
                streamUrl = track.streamUrl,
                likedAt = System.currentTimeMillis(),
                sourceId = track.sourceId,
                sourceType = track.sourceType?.name
            )
        )
    }

    suspend fun unlike(track: Track) {
        dao.unlike(track.downloadKey())
    }

    companion object {
        @Volatile private var instance: LikedSongsRepository? = null

        fun getInstance(context: Context): LikedSongsRepository =
            instance ?: synchronized(this) {
                instance ?: LikedSongsRepository(context.applicationContext).also { instance = it }
            }
    }
}

fun LikedSongEntity.toTrack(): Track = Track(
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
