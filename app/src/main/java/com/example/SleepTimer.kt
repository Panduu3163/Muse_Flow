package com.example

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * A countdown that pauses playback when it reaches zero.
 *
 * A process-wide singleton with its own [CoroutineScope] - not [PlayerViewModel]'s
 * `viewModelScope] - because the timer has to keep counting whether or not a Now Playing screen is
 * currently attached to observe it; a ViewModel-scoped timer would die the moment the screen
 * backing it was torn down. Deliberately in-memory only, not persisted to disk: this app has no
 * background playback across a full process death, so a timer surviving one would have nothing
 * left to act on anyway.
 */
object SleepTimer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val _remainingMs = MutableStateFlow<Long?>(null)
    /** Null when no timer is running. */
    val remainingMs: StateFlow<Long?> = _remainingMs.asStateFlow()

    fun start(durationMs: Long, onExpire: () -> Unit) {
        job?.cancel()
        _remainingMs.value = durationMs
        job = scope.launch {
            var remaining = durationMs
            while (remaining > 0) {
                delay(1000)
                remaining -= 1000
                _remainingMs.value = remaining.coerceAtLeast(0)
            }
            _remainingMs.value = null
            onExpire()
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _remainingMs.value = null
    }
}
