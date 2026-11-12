package com.example

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One expiry deadline; second-by-second work exists only while a visible UI observes it. */
internal class SleepTimerEngine(
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val expire: suspend (() -> Unit) -> Unit,
) {
    private val deadline = MutableStateFlow<Long?>(null)
    private val remaining = MutableStateFlow<Long?>(null)
    val remainingMs: StateFlow<Long?> = remaining.asStateFlow()
    private var expiryJob: Job? = null
    private val wakeExpiry = Channel<Unit>(Channel.CONFLATED)
    private var generation = 0L

    init {
        scope.launch {
            combine(deadline, remaining.subscriptionCount) { end, count -> if (count > 0) end else null }
                .collectLatest { end ->
                    if (end != null) while (true) {
                        val left = (end - now()).coerceAtLeast(0L)
                        remaining.value = left
                        if (left == 0L) { wakeExpiry.trySend(Unit); break }
                        delay(minOf(1000L, left))
                    }
                }
        }
    }

    fun start(durationMs: Long, onExpire: () -> Unit) {
        expiryJob?.cancel()
        val ticket = ++generation
        val duration = durationMs.coerceAtLeast(0L)
        val end = now() + duration
        remaining.value = duration
        deadline.value = end
        expiryJob = scope.launch {
            while (generation == ticket) {
                val left = (end - now()).coerceAtLeast(0L)
                if (left == 0L) break
                withTimeoutOrNull(left) { wakeExpiry.receive() }
            }
            if (generation == ticket) {
                deadline.value = null
                remaining.value = null
                expire(onExpire)
            }
        }
    }

    fun recheck() { if (deadline.value != null) wakeExpiry.trySend(Unit) }

    fun cancel() {
        generation++
        expiryJob?.cancel()
        expiryJob = null
        deadline.value = null
        remaining.value = null
    }
}

object SleepTimer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engine = SleepTimerEngine(scope, SystemClock::elapsedRealtime) { callback ->
        withContext(Dispatchers.Main.immediate) { callback() }
    }
    val remainingMs: StateFlow<Long?> = engine.remainingMs
    fun start(durationMs: Long, onExpire: () -> Unit) = engine.start(durationMs, onExpire)
    fun cancel() = engine.cancel()
    fun onPlaybackResumed() = engine.recheck()
}
