package com.example

import android.content.Context

/**
 * Routes search to whichever [ExtractorBackend] is selected, mirroring [StreamResolverRouter]'s
 * role for stream resolution - so both halves of a track's journey (finding it, then playing it)
 * are served by the same backend rather than a mix of the two.
 *
 * Strict, like the stream router: a failing backend surfaces as an error rather than quietly
 * borrowing results from the other one.
 */
class MusicSearchRouter(private val context: Context) {

    suspend fun searchTracks(query: String): List<TrackResult> =
        when (StreamResolverRouter.activeBackend(context)) {
            ExtractorBackend.INNERTUBE -> InnerTubeMusicProvider(context).search(query)
            ExtractorBackend.LEGACY -> YouTubeMusicProvider(context).search(query)
        }

    suspend fun searchAlbums(query: String): List<AlbumResult> =
        when (StreamResolverRouter.activeBackend(context)) {
            ExtractorBackend.INNERTUBE -> InnerTubeMusicProvider(context).searchAlbums(query)
            ExtractorBackend.LEGACY -> YouTubeMusicProvider(context).searchAlbums(query)
        }

    suspend fun searchArtists(query: String): List<ArtistResult> =
        when (StreamResolverRouter.activeBackend(context)) {
            ExtractorBackend.INNERTUBE -> InnerTubeMusicProvider(context).searchArtists(query)
            ExtractorBackend.LEGACY -> YouTubeMusicProvider(context).searchArtists(query)
        }

    suspend fun searchPlaylists(query: String): List<PlaylistResult> =
        when (StreamResolverRouter.activeBackend(context)) {
            ExtractorBackend.INNERTUBE -> InnerTubeMusicProvider(context).searchPlaylists(query)
            ExtractorBackend.LEGACY -> YouTubeMusicProvider(context).searchPlaylists(query)
        }

    /** Type-ahead suggestions. Only InnerTube exposes these; the legacy provider returns none. */
    suspend fun suggestions(query: String): List<String> =
        when (StreamResolverRouter.activeBackend(context)) {
            ExtractorBackend.INNERTUBE -> InnerTubeMusicProvider(context).suggestions(query)
            ExtractorBackend.LEGACY -> emptyList()
        }
}
