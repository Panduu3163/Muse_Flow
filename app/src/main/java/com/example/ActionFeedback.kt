package com.example

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** One app-level snackbar host, so dismissing a menu cannot lose its operation's result. */
internal object ActionFeedback {
    private val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events = messages.asSharedFlow()
    fun post(message: String) { messages.tryEmit(message) }
}

internal data class BatchActionResult(val completed: Int, val failed: Int)

internal suspend fun <T> runTrackBatch(items: List<T>, action: suspend (T) -> Unit): BatchActionResult {
    var completed = 0
    var failed = 0
    for (item in items) {
        try { action(item); completed++ }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed++ }
    }
    return BatchActionResult(completed, failed)
}

internal fun BatchActionResult.message(action: String): String = if (failed == 0) {
    "$action $completed ${if (completed == 1) "song" else "songs"}."
} else {
    "$action $completed of ${completed + failed} songs. $failed failed; please try again."
}
