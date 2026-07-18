package com.example

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Playlists the user has created (or imported from an online source), for Library's real
 * "Playlists" tab, plus the tracks inside each one. A process-wide singleton, same pattern as
 * [DownloadRepository].
 */
class PlaylistRepository private constructor(context: Context) {

    private val dao = MuseFlowDatabase.getInstance(context.applicationContext).playlistDao()
    private val trackDao = MuseFlowDatabase.getInstance(context.applicationContext).playlistTrackDao()

    fun observeAll(): Flow<List<PlaylistEntity>> = dao.observeAll()

    /** Creates an empty playlist and returns its new id, so callers (e.g. the "add songs" flow
     * right after naming a playlist) can immediately add tracks to it. */
    suspend fun create(name: String, coverImageUrl: String? = null): Long =
        dao.insert(PlaylistEntity(name = name, createdAt = System.currentTimeMillis(), coverImageUrl = coverImageUrl))

    fun observeTracks(playlistId: Long): Flow<List<Track>> =
        trackDao.observeForPlaylist(playlistId).map { entities -> entities.map { it.toTrack() } }

    /** Adds [tracks] to [playlistId], deduped against whatever's already there (re-adding an
     * existing track just bumps its position). [addedAt] is offset by list index within this
     * batch so tracks added together keep their relative order despite sharing a millisecond. */
    suspend fun addTracks(playlistId: Long, tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val baseTime = System.currentTimeMillis()
        trackDao.insertAll(
            tracks.mapIndexed { index, track ->
                PlaylistTrackEntity(
                    playlistId = playlistId,
                    key = track.downloadKey(),
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    duration = track.duration,
                    gradientIndex = track.gradientIndex,
                    imageUrl = track.imageUrl,
                    streamUrl = track.streamUrl,
                    sourceId = track.sourceId,
                    sourceType = track.sourceType?.name,
                    addedAt = baseTime + index
                )
            }
        )
    }

    suspend fun removeTrack(playlistId: Long, key: String) {
        trackDao.delete(playlistId, key)
    }

    /** Creates a new playlist pre-populated with [tracks] and a real [coverImageUrl] in one shot -
     * used by "Add to my library" on an online playlist's detail screen. */
    suspend fun importOnlinePlaylist(name: String, coverImageUrl: String?, tracks: List<Track>): Long {
        val id = create(name, coverImageUrl)
        addTracks(id, tracks)
        return id
    }

    companion object {
        @Volatile private var instance: PlaylistRepository? = null

        fun getInstance(context: Context): PlaylistRepository =
            instance ?: synchronized(this) {
                instance ?: PlaylistRepository(context.applicationContext).also { instance = it }
            }
    }
}

fun PlaylistTrackEntity.toTrack(): Track = Track(
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
