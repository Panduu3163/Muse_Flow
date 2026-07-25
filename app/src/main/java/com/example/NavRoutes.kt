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

    const val NOW_PLAYING = "now_playing"
    const val THEME_SETTINGS = "settings/theme"
    const val EQUALIZER = "settings/equalizer"

    private const val PLAYLIST_BASE = "playlist"
    const val PLAYLIST_ARG = "playlistId"
    const val PLAYLIST = "$PLAYLIST_BASE/{$PLAYLIST_ARG}"
    fun playlist(playlistId: Long) = "$PLAYLIST_BASE/$playlistId"
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
