package com.example
import androidx.room.withTransaction

import android.content.Context
import com.music.innertube.models.upgradeThumbnailSize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Playlists the user has created (or imported from an online source), for Library's real
 * "Playlists" tab, plus the tracks inside each one. A process-wide singleton, same pattern as
 * [DownloadRepository].
 */
class PlaylistRepository private constructor(context: Context) {

    private val database = MuseFlowDatabase.getInstance(context.applicationContext)
    private val dao = database.playlistDao()
    private val trackDao = MuseFlowDatabase.getInstance(context.applicationContext).playlistTrackDao()

    // coverImageUrl is only set for an imported online playlist and only ever read (Library's row,
    // the mosaic cover's fallback) - upgraded here at read time for the same reason every other
    // stored thumbnail is, rather than in the entity itself.
    fun observeAll(): Flow<List<PlaylistEntity>> = dao.observeAll().map { playlists ->
        playlists.map { it.copy(coverImageUrl = it.coverImageUrl?.let(::upgradeThumbnailSize)) }
    }

    fun observeWithDownloads(): Flow<List<PlaylistEntity>> = dao.observeWithDownloads().map { playlists ->
        playlists.map { it.copy(coverImageUrl = it.coverImageUrl?.let(::upgradeThumbnailSize)) }
    }

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
                    addedAt = baseTime + index,
                    albumId = track.albumId,
                    artistId = track.artistId,
                )
            }
        )
    }

    suspend fun removeTrack(playlistId: Long, key: String) {
        trackDao.delete(playlistId, key)
    }

    /** Same one-time backfill as [LikedSongsRepository.backfillMissingArtistIds], for playlist
     * tracks added before artistId/albumId were reliably threaded through - see its doc for the
     * full reasoning (exact-videoId re-match, never a fuzzy guess). */
    suspend fun backfillMissingArtistIds(context: Context) {
        val missing = runCatching { trackDao.getAll() }.getOrNull().orEmpty()
            .filter { it.artistId == null && it.sourceType == MusicSource.YOUTUBE_MUSIC.name && it.sourceId != null }
        if (missing.isEmpty()) return

        val router = MusicSearchRouter(context)
        for (entity in missing) {
            runCatching {
                val results = router.searchTracks("${entity.title} ${entity.artist}")
                val match = results.firstOrNull { it.id == entity.sourceId } ?: return@runCatching
                if (match.artistId != null || match.albumId != null) {
                    trackDao.updateArtistAndAlbumId(entity.key, match.artistId, match.albumId)
                }
            }
        }
    }

    suspend fun setPinned(playlistId: Long, pinned: Boolean) {
        dao.setPinned(playlistId, pinned)
    }

    /** Sets (or, with `uri = null`, clears) a user-picked cover. The caller is responsible for
     * having already taken a persistable read-permission grant on [uri] via
     * `ContentResolver.takePersistableUriPermission` - this just stores the string. */
    suspend fun setCustomCover(playlistId: Long, uri: String?) {
        dao.setCustomCoverUri(playlistId, uri)
    }

    /** Deletes the playlist and its tracks - two statements rather than a foreign-key cascade,
     * since none is declared on [PlaylistTrackEntity] (see [PlaylistTrackDao.deleteAllForPlaylist]). */
    suspend fun delete(playlistId: Long) {
        trackDao.deleteAllForPlaylist(playlistId)
        dao.delete(playlistId)
    }

    /** Creates a new playlist pre-populated with [tracks] and a real [coverImageUrl] in one shot -
     * used by "Add to my library" on an online playlist's detail screen. */
    suspend fun importOnlinePlaylist(name: String, coverImageUrl: String?, tracks: List<Track>, remoteId: String? = null): Long = database.withTransaction {
        val id = remoteId?.let { dao.findRemote(it)?.id } ?: dao.insert(
            PlaylistEntity(name = name, coverImageUrl = coverImageUrl, createdAt = System.currentTimeMillis(), remoteId = remoteId)
        )
        addTracks(id, tracks)
        id
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
    // Upgraded at read time - see PlaybackHistoryEntity.toTrack's comment on why.
    imageUrl = imageUrl?.let(::upgradeThumbnailSize),
    streamUrl = streamUrl,
    sourceType = sourceType?.let { runCatching { MusicSource.valueOf(it) }.getOrNull() },
    sourceId = sourceId,
    albumId = albumId,
    artistId = artistId,
)
