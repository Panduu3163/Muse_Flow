package com.example

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/** Every navigable destination in the app. Kept as plain string constants (rather than a sealed
 * hierarchy with argument builders) because destinations with arguments are few - the ones that
 * take an id build their route through the helper below. */
object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"

    // Library stays one screen with a chip row (reverted from a brief tile-grid + sub-screens
    // split); Stats is its one real sub-screen since it summarizes across every chip section
    // rather than belonging to any single one.
    const val LIBRARY_STATS = "library/stats"

    const val NOW_PLAYING = "now_playing"
    const val HISTORY = "history"
    const val THEME_SETTINGS = "settings/theme"
    const val EQUALIZER = "settings/equalizer"
    const val BACKUP = "settings/backup"
    const val CRASH_LOGS = "settings/crash_logs"
    const val STORAGE = "settings/storage"
    const val ABOUT = "settings/about"
    const val SETTINGS_APPEARANCE = "settings/appearance"
    const val SETTINGS_MINI_PLAYER = "settings/mini_player"
    const val SETTINGS_PLAYER = "settings/player"
    const val SETTINGS_LYRICS = "settings/lyrics"
    const val SETTINGS_LYRICS_PROVIDER_ORDER = "settings/lyrics/provider_order"
    const val SETTINGS_AUDIO = "settings/audio"
    const val SETTINGS_PLAYBACK = "settings/playback"
    const val SETTINGS_GENERAL = "settings/general"
    const val SETTINGS_PRIVACY = "settings/privacy"
    const val SETTINGS_LIBRARY_SECTIONS = "settings/library_sections"
    const val CHARTS = "charts"
    const val NEW_RELEASES = "new_releases"
    const val EXPLORE = "explore"

    private const val BROWSE_BASE = "browse"
    const val BROWSE_ID_ARG = "browseId"
    const val BROWSE_PARAMS_ARG = "params"
    const val BROWSE = "$BROWSE_BASE/{$BROWSE_ID_ARG}?$BROWSE_PARAMS_ARG={$BROWSE_PARAMS_ARG}"
    fun browse(browseId: String, params: String?): String {
        val encodedId = java.net.URLEncoder.encode(browseId, "UTF-8")
        val encodedParams = params?.let { java.net.URLEncoder.encode(it, "UTF-8") }.orEmpty()
        return "$BROWSE_BASE/$encodedId?$BROWSE_PARAMS_ARG=$encodedParams"
    }

    private const val PLAYLIST_BASE = "playlist"
    const val PLAYLIST_ARG = "playlistId"
    const val PLAYLIST = "$PLAYLIST_BASE/{$PLAYLIST_ARG}"
    fun playlist(playlistId: Long) = "$PLAYLIST_BASE/$playlistId"

    private const val ARTIST_BASE = "artist"
    const val ARTIST_ARG = "artistId"
    const val ARTIST = "$ARTIST_BASE/{$ARTIST_ARG}"
    // Browse ids can contain characters (e.g. "/") that would otherwise be read as extra path
    // segments, so the id travels encoded and the screen decodes it back on the way out.
    fun artist(artistId: String) = "$ARTIST_BASE/${java.net.URLEncoder.encode(artistId, "UTF-8")}"

    private const val ALBUM_BASE = "album"
    const val ALBUM_ARG = "albumId"
    const val ALBUM = "$ALBUM_BASE/{$ALBUM_ARG}"
    fun album(albumId: String) = "$ALBUM_BASE/${java.net.URLEncoder.encode(albumId, "UTF-8")}"

    // A playlist reached from Search/Browse rather than Library - identified by a YouTube browseId,
    // not a local Room id, so it can't reuse Routes.PLAYLIST. There's no endpoint that returns a
    // remote playlist's own header (title/cover/subtitle) by id alone, so the caller - which already
    // has that from the search/browse result row - passes it along as query args instead of this
    // screen re-fetching it.
    private const val REMOTE_PLAYLIST_BASE = "remote_playlist"
    const val REMOTE_PLAYLIST_ARG = "playlistId"
    const val REMOTE_PLAYLIST_TITLE_ARG = "title"
    const val REMOTE_PLAYLIST_SUBTITLE_ARG = "subtitle"
    const val REMOTE_PLAYLIST_IMAGE_ARG = "imageUrl"
    const val REMOTE_PLAYLIST = "$REMOTE_PLAYLIST_BASE/{$REMOTE_PLAYLIST_ARG}?" +
        "$REMOTE_PLAYLIST_TITLE_ARG={$REMOTE_PLAYLIST_TITLE_ARG}&" +
        "$REMOTE_PLAYLIST_SUBTITLE_ARG={$REMOTE_PLAYLIST_SUBTITLE_ARG}&" +
        "$REMOTE_PLAYLIST_IMAGE_ARG={$REMOTE_PLAYLIST_IMAGE_ARG}"
    fun remotePlaylist(playlistId: String, title: String, subtitle: String, imageUrl: String?): String {
        fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
        return "$REMOTE_PLAYLIST_BASE/${enc(playlistId)}?" +
            "$REMOTE_PLAYLIST_TITLE_ARG=${enc(title)}&" +
            "$REMOTE_PLAYLIST_SUBTITLE_ARG=${enc(subtitle)}&" +
            "$REMOTE_PLAYLIST_IMAGE_ARG=${imageUrl?.let(::enc).orEmpty()}"
    }
}

/**
 * The four top-level tabs shown in the bottom bar. [filledIcon] is used for the selected tab and
 * [outlinedIcon] for the rest - the standard Material 3 navigation-bar treatment, which reads far
 * more clearly than tint alone.
 */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val filledIcon: ImageVector,
    val outlinedIcon: ImageVector,
) {
    Home(Routes.HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    Search(Routes.SEARCH, "Search", Icons.Filled.Search, Icons.Outlined.Search),
    Library(Routes.LIBRARY, "Library", Icons.Filled.LibraryMusic, Icons.Outlined.LibraryMusic),
    Settings(Routes.SETTINGS, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings);

    companion object {
        /** The tab that owns [route], or null for a non-tab destination (so the bar can hide). */
        fun forRoute(route: String?): TopLevelDestination? = entries.find { it.route == route }
    }
}

/** Maps the user's "default tab" preference onto the route the nav graph starts at. */
fun DefaultTab.toRoute(): String = when (this) {
    DefaultTab.Home -> Routes.HOME
    DefaultTab.Search -> Routes.SEARCH
    DefaultTab.Library -> Routes.LIBRARY
    DefaultTab.Settings -> Routes.SETTINGS
}
