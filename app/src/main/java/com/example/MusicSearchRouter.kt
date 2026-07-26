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

    /**
     * The tracks behind an album/artist/playlist search result.
     *
     * Routed for the same reason the searches are: the browseId in an [AlbumResult] came from
     * whichever backend produced it, so it has to be expanded by that same backend.
     */
    suspend fun getAlbumTracks(albumId: String): List<TrackResult> =
        when (StreamResolverRouter.activeBackend(context)) {
            ExtractorBackend.INNERTUBE -> InnerTubeMusicProvider(context).getAlbumTracks(albumId)
            ExtractorBackend.LEGACY -> YouTubeMusicProvider(context).getAlbumTracks(albumId)
        }

    /** The Album screen's own fetch - header details plus tracklist in one call, since it
     * navigates by id alone with no cached [AlbumResult] to render a title from meanwhile. */
    suspend fun getAlbumDetails(albumId: String): AlbumDetails =
        when (StreamResolverRouter.activeBackend(context)) {
            ExtractorBackend.INNERTUBE -> InnerTubeMusicProvider(context).getAlbumDetails(albumId)
            ExtractorBackend.LEGACY -> YouTubeMusicProvider(context).getAlbumDetails(albumId)
        }

    suspend fun getArtistTracklist(artistId: String): ArtistTracklist =
        when (StreamResolverRouter.activeBackend(context)) {
            ExtractorBackend.INNERTUBE -> InnerTubeMusicProvider(context).getArtistTracklist(artistId)
            ExtractorBackend.LEGACY -> YouTubeMusicProvider(context).getArtistTracklist(artistId)
        }

    suspend fun getPlaylistTracks(playlistId: String): List<TrackResult> =
        when (StreamResolverRouter.activeBackend(context)) {
            ExtractorBackend.INNERTUBE -> InnerTubeMusicProvider(context).getPlaylistTracks(playlistId)
            ExtractorBackend.LEGACY -> YouTubeMusicProvider(context).getPlaylistTracks(playlistId)
        }

    /**
     * A radio mix seeded from a track.
     *
     * Only InnerTube exposes this. The legacy provider returns none rather than faking a mix out
     * of the seed alone - an empty result lets the caller say "couldn't start radio", where a
     * one-track "radio" would look like a working feature that stops after one song.
     */
    suspend fun getRadioTracks(videoId: String): List<TrackResult> =
        when (StreamResolverRouter.activeBackend(context)) {
            ExtractorBackend.INNERTUBE -> InnerTubeMusicProvider(context).getRadioTracks(videoId)
            ExtractorBackend.LEGACY -> emptyList()
        }

    /** YouTube Music's own lyrics tab, plain text only. Only InnerTube exposes this. */
    suspend fun getLyricsText(videoId: String): String? =
        when (StreamResolverRouter.activeBackend(context)) {
            ExtractorBackend.INNERTUBE -> InnerTubeMusicProvider(context).getLyricsText(videoId)
            ExtractorBackend.LEGACY -> null
        }

    /** The real YouTube Music charts feed - both backends implement this directly, so no
     * per-backend fallback branch is needed the way [getRadioTracks] needs one. */
    suspend fun getChartsTracks(): List<TrackResult> =
        when (StreamResolverRouter.activeBackend(context)) {
            ExtractorBackend.INNERTUBE -> InnerTubeMusicProvider(context).getChartsTracks()
            ExtractorBackend.LEGACY -> YouTubeMusicProvider(context).getChartsTracks()
        }

    /** Type-ahead suggestions. Only InnerTube exposes these; the legacy provider returns none. */
    suspend fun suggestions(query: String): List<String> =
        when (StreamResolverRouter.activeBackend(context)) {
            ExtractorBackend.INNERTUBE -> InnerTubeMusicProvider(context).suggestions(query)
            ExtractorBackend.LEGACY -> emptyList()
        }

    /** New releases. Only InnerTube exposes these; the legacy provider returns none. */
    suspend fun getNewReleases(): List<AlbumResult> =
        when (StreamResolverRouter.activeBackend(context)) {
            ExtractorBackend.INNERTUBE -> InnerTubeMusicProvider(context).getNewReleases()
            ExtractorBackend.LEGACY -> emptyList()
        }

    /** A generic mood/genre browse page. Only InnerTube exposes this. */
    suspend fun browse(browseId: String, params: String?): BrowsePage =
        when (StreamResolverRouter.activeBackend(context)) {
            ExtractorBackend.INNERTUBE -> InnerTubeMusicProvider(context).browse(browseId, params)
            ExtractorBackend.LEGACY -> BrowsePage(title = null, sections = emptyList())
        }

    /** The Explore screen's mood/genre categories. Only InnerTube exposes these. */
    suspend fun getMoodAndGenres(): List<MoodGenreCategory> =
        when (StreamResolverRouter.activeBackend(context)) {
            ExtractorBackend.INNERTUBE -> InnerTubeMusicProvider(context).getMoodAndGenres()
            ExtractorBackend.LEGACY -> emptyList()
        }
}
