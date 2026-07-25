package com.example

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

private const val TAG = "ArtistReleaseCheck"

/** Periodically re-fetches each followed artist's tracklist and notifies (see
 * [ArtistReleaseNotificationHelper]) about anything new relative to the stored baseline (see
 * [newReleaseTrackIds]'s doc for why an empty baseline never notifies) - same
 * `CoroutineWorker`/`PeriodicWorkRequestBuilder`/`enqueueUniquePeriodicWork` shape as
 * [AutoBackupWorker]. One artist failing to fetch (network blip, provider hiccup) doesn't stop the
 * rest from being checked. */
class ArtistReleaseCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = FollowedArtistsRepository.getInstance(applicationContext)
        val youTubeProvider = YouTubeMusicProvider(applicationContext)

        for (artist in repository.getAll()) {
            runCatching {
                val tracklist = youTubeProvider.getArtistTracklist(artist.artistId)
                val currentIds = tracklist.tracks.map { it.id }
                val knownIds = artist.knownTrackIds.split(",").filter { it.isNotBlank() }.toSet()
                val newIds = newReleaseTrackIds(knownIds, currentIds)

                if (newIds.isNotEmpty()) {
                    val newTitles = tracklist.tracks.filter { it.id in newIds }.map { it.title }
                    ArtistReleaseNotificationHelper.showNewReleases(
                        applicationContext, artist.artistId, artist.name, newTitles
                    )
                }
                // Always advance the baseline to the full current set (not just newIds) - a track
                // that later disappears from the tracklist (removed/region-locked) shouldn't be
                // treated as "new" again if it reappears.
                if (currentIds.isNotEmpty()) {
                    repository.updateKnownTrackIds(artist.artistId, currentIds.toSet())
                }
            }.onFailure { Log.w(TAG, "release check failed for ${artist.name}", it) }
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "artist_release_check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ArtistReleaseCheckWorker>(12, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
