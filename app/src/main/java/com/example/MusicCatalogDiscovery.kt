package com.example

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Discovery uses the music catalog. General-video search remains an explicit Search feature. */
internal suspend fun musicCatalogDiscovery(
    songs: suspend () -> List<TrackResult>,
    musicVideos: suspend () -> List<TrackResult>,
): List<TrackResult> = coroutineScope {
    suspend fun load(block: suspend () -> List<TrackResult>): Result<List<TrackResult>> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }
    val songRequest = async { load(songs) }
    val videoRequest = async { load(musicVideos) }
    val songResult = songRequest.await()
    val videoResult = videoRequest.await()
    if (songResult.isFailure && videoResult.isFailure) songResult.getOrThrow()
    (songResult.getOrNull().orEmpty() + videoResult.getOrNull().orEmpty()).distinctBy { it.id }
}
