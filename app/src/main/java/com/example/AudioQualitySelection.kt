package com.example

/** Prefer a usable low-bitrate stream for Data saver, while keeping a fallback when a source
 * exposes only higher-bitrate audio. Unknown bitrates are used only if no known ones exist. */
internal fun <T> selectByBitrate(
    candidates: List<T>,
    quality: AudioQualityMode,
    bitrate: (T) -> Int,
): T? {
    val known = candidates.filter { bitrate(it) > 0 }
    if (known.isEmpty()) return candidates.firstOrNull()
    if (quality == AudioQualityMode.BestAvailable) return known.maxByOrNull(bitrate)
    return known.filter { bitrate(it) <= 96_000 }.maxByOrNull(bitrate)
        ?: known.minByOrNull(bitrate)
}
