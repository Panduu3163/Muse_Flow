package com.example

import androidx.media3.common.MediaItem
import kotlinx.coroutines.*

internal data class PlaybackResumeSnapshot(val items: List<MediaItem>, val index: Int, val positionMs: Long)

/** Checks only the selected local source. A missing download may re-resolve its exact recording. */
internal suspend fun validatedResumeSnapshot(
    snapshot: PlaybackResumeSnapshot,
    canRead: suspend (android.net.Uri) -> Boolean,
): PlaybackResumeSnapshot {
    val item = snapshot.items[snapshot.index]
    val uri = item.localConfiguration?.uri ?: return snapshot
    if (uri.scheme !in setOf("file", "content") || canRead(uri)) return snapshot
    check(item.mediaId.looksLikeYouTubeVideoId()) { "This local song is unavailable. Open MuseFlow and choose it again." }
    val items = snapshot.items.toMutableList()
    items[snapshot.index] = item.buildUpon().setUri(youTubeResolvePlaceholderUri(item.mediaId)).build()
    return snapshot.copy(items = items)
}

/** One paused startup restore, shared by user controls and Media3 resumption. Never issues Play. */
internal class PlaybackResumptionCoordinator(
    scope: CoroutineScope,
    restorePaused: suspend () -> Unit,
    private val generation: () -> Long,
    private val readSnapshot: () -> PlaybackResumeSnapshot?,
) {
    private val prepared = scope.async(start = CoroutineStart.LAZY) {
        try { restorePaused(); Result.success(Unit) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { Result.failure(error) }
    }
    private var invalidated = false
    fun start() { prepared.start() }
    fun invalidate() { invalidated = true; prepared.cancel() }
    suspend fun snapshot(): PlaybackResumeSnapshot? {
        check(!invalidated) { "Playback session has stopped" }
        val requestedGeneration = generation()
        prepared.await().getOrThrow()
        currentCoroutineContext().ensureActive()
        check(!invalidated) { "Playback session has stopped" }
        check(requestedGeneration == generation()) { "A newer queue replaced this resumption request" }
        return readSnapshot()
    }
}
