package com.example

/** Route constants for the app's single [androidx.navigation.compose.NavHost]. */
object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val NOW_PLAYING = "now_playing"

    const val SETTINGS_ACCOUNT = "settings/account"
    const val SETTINGS_ACCOUNT_EDIT = "settings/account/edit"
    const val SETTINGS_APPEARANCE = "settings/appearance"
    const val SETTINGS_PLAYER_AUDIO = "settings/player_audio"
    const val SETTINGS_LYRICS = "settings/lyrics"
    const val SETTINGS_LIBRARY_PLAYLISTS = "settings/library_playlists"
    const val SETTINGS_LISTEN_TOGETHER = "settings/listen_together"
    const val SETTINGS_STORAGE = "settings/storage"
    const val SETTINGS_UPTIME = "settings/uptime"
    const val SETTINGS_ABOUT = "settings/about"
    const val SETTINGS_THEME = "settings/theme"
    const val SETTINGS_PALETTE = "settings/palette"
    const val SETTINGS_PROVIDER_TEST = "settings/provider_test"
}

/** Bottom navigation tabs; [route] must be one of the top-level [Routes]. */
enum class MuseTab(val route: String) {
    Home(Routes.HOME),
    Search(Routes.SEARCH),
    Library(Routes.LIBRARY),
    Settings(Routes.SETTINGS)
}
