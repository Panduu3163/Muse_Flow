package com.example

/** Small session cache for network discovery pages. Refresh failures keep useful results visible.
 * No artwork bitmaps or playback URLs are retained here. */
internal object DiscoveryPageCache {
    private val entries = LinkedHashMap<String, List<TrackResult>>()
    fun get(key: String): List<TrackResult>? = entries[key]
    fun put(key: String, tracks: List<TrackResult>) {
        entries.remove(key)
        entries[key] = tracks.toList()
        while (entries.size > 4) entries.remove(entries.keys.first())
    }
}
