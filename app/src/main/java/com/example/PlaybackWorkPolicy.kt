package com.example

/** Preserve the existing fade envelope, but wake only during an audible fade or at its boundary. */
internal data class PlaybackFadeStep(val volume: Float, val nextUpdateMs: Long)

internal fun playbackNeedsNetworkWake(scheme: String?): Boolean = when (scheme) {
    "file", "content", "asset" -> false
    else -> true
}

internal fun playbackFadeStep(positionMs: Long, durationMs: Long, requestedFadeMs: Long, speed: Float): PlaybackFadeStep {
    if (durationMs <= 0L) return PlaybackFadeStep(1f, 30_000L)
    val position = positionMs.coerceIn(0L, durationMs)
    val fade = requestedFadeMs.coerceAtMost(durationMs / 2).coerceAtLeast(1L)
    val volume = minOf((position.toFloat() / fade).coerceIn(0f, 1f),
        ((durationMs - position).toFloat() / fade).coerceIn(0f, 1f))
    val next = if (position < fade || position >= durationMs - fade) 50L else {
        ((durationMs - fade - position) / speed.coerceAtLeast(0.1f)).toLong().coerceIn(50L, 30_000L)
    }
    return PlaybackFadeStep(volume, next)
}
