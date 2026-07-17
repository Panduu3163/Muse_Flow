package com.example

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/** Default ceiling for how long a network-backed fetch is allowed to hang before it's treated as
 * a genuine failure rather than "still loading" - long enough to cover ordinary slow responses,
 * short enough that a real outage surfaces an error instead of spinning forever. */
const val DEFAULT_LOAD_TIMEOUT_MS = 9000L

/** Three-state result of a network-backed fetch, so "still waiting on a slow response" is never
 * confused with "definitely failed": [Loading] while in flight, [Success] with the fetched data,
 * or [Error] with a user-facing message - reached only via a genuinely thrown exception or an
 * actual timeout, never speculatively. */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

/** Runs [block], mapping the outcome to a [UiState]: a thrown exception or exceeding [timeoutMs]
 * both become [UiState.Error] with [errorMessage] - nothing else does, so callers never need their
 * own ad hoc loading/error booleans. */
suspend fun <T> loadAsUiState(
    errorMessage: String,
    timeoutMs: Long = DEFAULT_LOAD_TIMEOUT_MS,
    block: suspend () -> T
): UiState<T> = try {
    UiState.Success(withTimeout(timeoutMs) { block() })
} catch (e: TimeoutCancellationException) {
    UiState.Error(errorMessage)
} catch (e: Exception) {
    UiState.Error(errorMessage)
}
