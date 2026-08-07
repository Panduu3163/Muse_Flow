package com.example

/**
 * Whether this result is backed by a real YouTube video id.
 *
 * Not every [TrackResult] is. Two produce a stand-in:
 * - a local file, which has no YouTube identity at all;
 * - a stored [Track] converted by `asTrackResult()`, which synthesises `"title|artist"` as the id
 *   when the row has no `sourceId` - *and* falls back to [MusicSource.YOUTUBE_MUSIC] for the
 *   source, so the source field alone cannot tell the two cases apart.
 *
 * Anything that turns the id into a URL or hands it to the API has to ask this first. Sharing a
 * fabricated id produces a dead link to `watch?v=Some Song|Some Artist`, and seeding radio with
 * one produces an error the user reads as "radio is broken".
 */
internal fun TrackResult.hasRealVideoId(): Boolean =
    sourceType != MusicSource.LOCAL_DEVICE && YOUTUBE_VIDEO_ID.matches(id)

/** Same check as [hasRealVideoId], for a bare id string with no [TrackResult] around it to read
 * [MusicSource] from - e.g. [com.example.PlaybackHistoryEntity.sourceId], which a local file or a
 * synthesised "title|artist" stand-in id would otherwise seed a radio/recommendation request with
 * nonsense and just fail. */
internal fun String.looksLikeYouTubeVideoId(): Boolean = YOUTUBE_VIDEO_ID.matches(this)

/** A YouTube video id is exactly 11 URL-safe characters. */
private val YOUTUBE_VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
