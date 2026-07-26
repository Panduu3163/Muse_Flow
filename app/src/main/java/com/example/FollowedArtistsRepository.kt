package com.example

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.models.upgradeThumbnailSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Process-wide singleton (same pattern as [SearchHistoryRepository]/[PlaybackHistoryRepository])
 * over [FollowedArtistDao] - followed artists get periodically checked for new releases by
 * [ArtistReleaseCheckWorker]. */
class FollowedArtistsRepository private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val dao = MuseFlowDatabase.getInstance(appContext).followedArtistDao()
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun observeFollowedIds(): Flow<Set<String>> =
        dao.observeAll().map { entities -> entities.map { it.artistId }.toSet() }

    /** The full rows, newest-followed first - backs the followed-artists list in Library. Kept
     * separate from [observeFollowedIds] rather than deriving one from the other, since most
     * callers only need the id set and shouldn't pay for name/image/timestamp they don't use.
     * [FollowedArtistEntity.imageUrl] is upgraded at read time - see
     * `PlaybackHistoryEntity.toTrack`'s comment on why. */
    fun observeAll(): Flow<List<FollowedArtistEntity>> = dao.observeAll().map { artists ->
        artists.map { it.copy(imageUrl = it.imageUrl?.let(::upgradeThumbnailSize)) }
    }

    suspend fun getAll(): List<FollowedArtistEntity> = dao.getAll()

    suspend fun updateKnownTrackIds(artistId: String, trackIds: Set<String>) {
        dao.updateKnownTrackIds(artistId, trackIds.joinToString(","))
    }

    fun follow(artistId: String, name: String, imageUrl: String?, sourceType: MusicSource) {
        repositoryScope.launch {
            dao.upsert(
                FollowedArtistEntity(
                    artistId = artistId,
                    name = name,
                    imageUrl = imageUrl,
                    sourceType = sourceType.name,
                    followedAt = System.currentTimeMillis()
                )
            )
            // Re-arm the periodic worker (idempotent - KEEP policy no-ops if it's already
            // scheduled) so following the first-ever artist doesn't require a separate toggle.
            ArtistReleaseCheckWorker.schedule(appContext)
        }
    }

    fun unfollow(artistId: String) {
        repositoryScope.launch { dao.unfollow(artistId) }
    }

    companion object {
        @Volatile private var instance: FollowedArtistsRepository? = null

        fun getInstance(context: Context): FollowedArtistsRepository =
            instance ?: synchronized(this) {
                instance ?: FollowedArtistsRepository(context.applicationContext).also { instance = it }
            }
    }
}
