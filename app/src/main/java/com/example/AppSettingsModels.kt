package com.example

/** Visual treatment for a player surface's background. */
enum class BackgroundStyle(val label: String) {
    Solid("Solid"),
    LiquidGlass("Liquid Glass"),
    Blur("Blur")
}

/** Which theme color drives the Now Playing control buttons. */
enum class PlayerButtonColorOption(val label: String) {
    Primary("Primary"),
    Secondary("Secondary"),
    Tertiary("Tertiary")
}

/** Visual style of the Now Playing progress slider. */
enum class PlayerSliderStyle(val label: String) {
    Default("Default"),
    Wavy("Wavy")
}

/** Horizontal alignment of the lyrics text block. */
enum class LyricsTextPosition(val label: String) {
    Left("Left"),
    Center("Center"),
    Right("Right")
}

/** Named animation styles for the word-by-word lyrics highlight. */
enum class WordAnimationStyle(val label: String) {
    Fade("Fade"),
    Bounce("Bounce")
}

/** Which bottom-nav tab is shown when the app is launched. */
enum class DefaultTab(val label: String) {
    Home("Home"),
    Search("Search"),
    Library("Library"),
    Settings("Settings")
}

/** Which Library tab/chip is selected by default. */
enum class DefaultLibraryChip(val label: String) {
    Playlists("Playlists"),
    LikedSongs("Liked Songs"),
    Downloads("Downloads"),
    RecentlyPlayed("Recently Played")
}

/** Size of grid cells used in grid-style browsing layouts. */
enum class GridCellSize(val label: String) {
    Small("Small"),
    Medium("Medium"),
    Large("Large")
}

/** Overall UI density/spacing of the app. */
enum class DisplayDensity(val label: String) {
    Compact("Compact"),
    Native("Native"),
    Comfortable("Comfortable")
}

/**
 * All Appearance-adjacent app preferences beyond the app-wide background [ThemeState].
 * These are UI-only preferences: persisted via DataStore (see [AppSettingsViewModel]) so
 * choices survive relaunch, but (aside from the theme itself) don't yet drive real
 * playback/lyrics behavior.
 */
data class AppSettingsState(
    // Mini-player
    val miniPlayerBackgroundStyle: BackgroundStyle = BackgroundStyle.Solid,

    // Player
    val playerBackgroundStyle: BackgroundStyle = BackgroundStyle.Solid,
    val hidePlayerThumbnail: Boolean = false,
    val thumbnailCornerRadius: Int = 12,
    val cropAlbumArt: Boolean = true,
    val playerButtonColor: PlayerButtonColorOption = PlayerButtonColorOption.Primary,
    val playerSliderStyle: PlayerSliderStyle = PlayerSliderStyle.Default,
    val swipeToChangeSong: Boolean = true,
    val showAnimatedCanvas: Boolean = false,
    val rotatingThumbnailAnimation: Boolean = false,
    val showCommentButton: Boolean = false,
    val showCodecInfo: Boolean = false,
    val miniPlayerSwipeSensitivity: Int = 50,

    // Lyrics
    val lyricsTextPosition: LyricsTextPosition = LyricsTextPosition.Center,
    val wordAnimationStyle: WordAnimationStyle = WordAnimationStyle.Fade,
    val glowingLyricsEffect: Boolean = false,
    val blurInactiveLines: Boolean = true,
    val lyricsTextSize: Int = 20,
    val lyricsLineSpacing: Float = 1.2f,
    val changeLyricsOnClick: Boolean = true,
    val autoScrollLyrics: Boolean = true,
    val swipeSongInFullscreenLyrics: Boolean = true,
    val showPlayPauseOverlayOnThumbnail: Boolean = true,
    val hideStatusBarInFullscreenLyrics: Boolean = false,

    // Misc
    val defaultOpenTab: DefaultTab = DefaultTab.Home,
    val defaultLibraryChip: DefaultLibraryChip = DefaultLibraryChip.Playlists,
    val swipeSongToQueue: Boolean = false,
    val enableHaptics: Boolean = true,
    val swipeSongToRemoveFromPlaylist: Boolean = false,
    val gridCellSize: GridCellSize = GridCellSize.Medium,
    val displayDensity: DisplayDensity = DisplayDensity.Comfortable,

    // Auto playlists
    val showLikedPlaylist: Boolean = true,
    val showDownloadedPlaylist: Boolean = true,
    val showExportedPlaylist: Boolean = false,
    val showTopPlaylist: Boolean = true,
    val showCachedPlaylist: Boolean = false
)
