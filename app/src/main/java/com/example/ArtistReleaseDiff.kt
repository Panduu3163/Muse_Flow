package com.example

/**
 * Pure diff logic for [ArtistReleaseCheckWorker] - which of [currentTrackIds] are new relative to
 * [knownTrackIds] - extracted so it's directly unit-testable without a real database/network call.
 * Returns an empty set (rather than every current id) when [knownTrackIds] is empty, since an
 * empty baseline means "never checked before", not "artist has zero tracks" - notifying about
 * every existing track the first time an artist is followed would be spam, not a genuine new
 * release. Order-independent by design (a reshuffled tracklist isn't a "new release").
 */
fun newReleaseTrackIds(knownTrackIds: Set<String>, currentTrackIds: List<String>): Set<String> {
    if (knownTrackIds.isEmpty()) return emptySet()
    return currentTrackIds.filterNot { it in knownTrackIds }.toSet()
}
