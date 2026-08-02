package com.example

import android.content.Context
import com.music.innertube.models.upgradeThumbnailSize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * The user's liked songs, for Library's real "Liked Songs" tab and the heart button on Now
 * Playing - both read/write the same Room table so a like made from either place is instantly
 * visible in the other. A process-wide singleton, same pattern as [DownloadRepository].
 */
class LikedSongsRepository private constructor(context: Context) {

    private val dao = MuseFlowDatabase.getInstance(context.applicationContext).likedSongDao()

    fun observeAll(): Flow<List<LikedSongEntity>> = dao.observeAll()

    fun observeIsLiked(track: Track): Flow<Boolean> = dao.observeIsLiked(track.downloadKey())

    /** Same as [observeIsLiked], but for callers (the lock-screen/notification like button) that
     * only have a title/artist pair on hand, not a full [Track]. */
    fun observeIsLiked(key: String): Flow<Boolean> = dao.observeIsLiked(key)

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
                sourceType = track.sourceType?.name,
                albumId = track.albumId,
                artistId = track.artistId,
            )
        )
    }

    suspend fun unlike(track: Track) {
        dao.unlike(track.downloadKey())
    }

    /**
     * One-time backfill for likes made before artistId/albumId were reliably threaded through the
     * like-write path - those rows have both permanently null, since nothing else ever rewrites
     * an existing liked-song row's these two fields. Same "re-search and patch just the missing
     * field" shape as [DownloadRepository.backfillMissingCovers].
     *
     * Only attempts a YouTube-sourced entry, matched back by its own [LikedSongEntity.sourceId]
     * (exact video id) rather than a fuzzy title/artist guess - a search result whose id doesn't
     * match the original track is worse than leaving the field null.
     */
    suspend fun backfillMissingArtistIds(context: Context) {
        val missing = runCatching { dao.observeAll().first() }.getOrNull().orEmpty()
            .filter { it.artistId == null && it.sourceType == MusicSource.YOUTUBE_MUSIC.name && it.sourceId != null }
        if (missing.isEmpty()) return

        val router = MusicSearchRouter(context)
        for (entity in missing) {
            runCatching {
                val results = router.searchTracks("${entity.title} ${entity.artist}")
                val match = results.firstOrNull { it.id == entity.sourceId } ?: return@runCatching
                if (match.artistId != null || match.albumId != null) {
                    dao.updateArtistAndAlbumId(entity.key, match.artistId, match.albumId)
                }
            }
        }
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
    // Upgraded at read time - see PlaybackHistoryEntity.toTrack's comment on why.
    imageUrl = imageUrl?.let(::upgradeThumbnailSize),
    streamUrl = streamUrl,
    sourceType = sourceType?.let { runCatching { MusicSource.valueOf(it) }.getOrNull() },
    sourceId = sourceId,
    albumId = albumId,
    artistId = artistId,
)
