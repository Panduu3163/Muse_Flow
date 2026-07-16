package com.example

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** A single song found via [YouTubeMusicProvider.search]. */
data class YtSearchResult(
    val videoId: String,
    val title: String,
    val artist: String,
    val duration: String?
)

/**
 * Minimal subset of YouTube Music's InnerTube `/player` response, just the fields needed to
 * resolve a playable audio stream URL. Field names/shape verified against a real response
 * (cross-checked with Metrolist's `PlayerResponse.kt`).
 */
@JsonClass(generateAdapter = true)
data class YtPlayerResponse(
    val playabilityStatus: YtPlayabilityStatus?,
    val streamingData: YtStreamingData?,
    val videoDetails: YtVideoDetails?
)

@JsonClass(generateAdapter = true)
data class YtPlayabilityStatus(
    val status: String?,
    val reason: String?
)

@JsonClass(generateAdapter = true)
data class YtStreamingData(
    val formats: List<YtFormat>?,
    val adaptiveFormats: List<YtFormat>?
)

@JsonClass(generateAdapter = true)
data class YtFormat(
    val itag: Int?,
    val url: String?,
    val mimeType: String?,
    val bitrate: Int?,
    val width: Int?,
    val audioQuality: String?,
    val signatureCipher: String?,
    val cipher: String?
) {
    /** Adaptive audio-only formats have no width, per YouTube's format convention. */
    val isAudioOnly: Boolean get() = width == null
}

@JsonClass(generateAdapter = true)
data class YtVideoDetails(
    val videoId: String?,
    val title: String?,
    val author: String?,
    @Json(name = "lengthSeconds") val lengthSeconds: String?
)
