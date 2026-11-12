package com.example

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A short, human-readable label for whatever ExoPlayer is actually decoding right now (e.g.
 * "OPUS · 160kbps"), for the optional codec-info line under Now Playing's timeline.
 *
 * A standalone singleton rather than a [PlayerViewModel]/[NowPlayingState] field on purpose: this
 * is diagnostic-only UI chrome, not real playback state, and [PlaybackService] is where the real
 * `Format` data is available (via `AnalyticsListener.onAudioInputFormatChanged`) - piping it
 * through the MediaSession/controller boundary into `NowPlayingState` would mean touching the
 * core playback state model for a text label. The UI collects this directly instead.
 */
object CurrentCodecInfo {
    private val _current = MutableStateFlow<String?>(null)
    val current = _current.asStateFlow()

    fun update(mimeType: String?, bitrate: Int, estimatedAverageBitrate: Int? = null) {
        val codec = mimeType?.substringAfter('/')?.uppercase()
        _current.value = when {
            codec == null -> null
            bitrate > 0 -> "$codec · ${bitrate / 1000}kbps"
            estimatedAverageBitrate != null && estimatedAverageBitrate > 0 ->
                "$codec · ~${kotlin.math.round(estimatedAverageBitrate / 1000.0).toInt().coerceAtLeast(1)}kbps"
            else -> codec
        }
    }

    fun clear() {
        _current.value = null
    }
}

/** Average encoded-file rate, including container overhead, not network speed. */
internal fun averageEncodedBitrate(contentLength: Long, durationMs: Long): Int? {
    if (contentLength <= 0 || durationMs <= 0) return null
    val rate = contentLength.toDouble() * 8_000.0 / durationMs
    return rate.takeIf { it.isFinite() && it >= 1 && it <= Int.MAX_VALUE }?.toInt()
}
