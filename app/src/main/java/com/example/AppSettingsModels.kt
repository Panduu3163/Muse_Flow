package com.example

/** Visual treatment for a player surface's background. */
enum class BackgroundStyle(val label: String) {
    Solid("Solid"),
    /** Vertical gradient built from the artwork's dominant and muted colours. */
    Gradient("Album gradient"),
    /** The artwork itself, blurred and dimmed behind the content. */
    Blur("Blurred artwork"),
    /** Three blurred, saturated copies of the artwork rotating independently behind the content. */
    LiveMesh("Live mesh"),
    /** Slowly moving, artwork-coloured radial light behind player controls. */
    GlowAnimated("Glow animated"),
    /** Clear cover fading into a blurred artwork backdrop. */
    AppleMusic("Apple Music inspired")
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
    /** A gentle, wide-period travelling sine wave. */
    Wavy("Wavy"),
    /** A thinner track for a more restrained player. */
    Slim("Slim"),
    /** A tighter, faster-wiggling wave than [Wavy] - more playful, less smooth. */
    Squiggly("Squiggly")
}

/** Visual style of Now Playing's prev/play-pause/next transport row - ported from Echo-Music's
 * own player customisation (`useNewPlayerDesign`'s cookie/wavy play-pause shape, and its Apple
 * Music-style joined-pill row), not independently designed. */
enum class PlayerTransportStyle(val label: String) {
    /** The original three separate circular buttons - unchanged default. */
    Static("Static"),
    /** Play/pause gets a slowly-rotating scalloped "cookie" edge while playing, flattening to a
     * plain circle when paused - Echo-Music's `WavyShape`/`cookieIndent` treatment. */
    Wheel("Wheel"),
    /** Prev/play/next joined into one continuous pill - rounded left edge on prev, rounded right
     * edge on next, square-ish centre for play/pause - Echo-Music's `shareShape`/`favShape`
     * asymmetric-corner technique, extended to three segments. */
    Pill("Pill")
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
    Bounce("Bounce"),
    Scale("Scale"),
    Wave("Wave"),
    Karaoke("Karaoke sweep"),
    Metro("Metro"),
    Fluid("Fluid (V2)"),
    ViviMusic("Vivi Music"),
    AppleMusic("Apple Music"),
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
enum class GridCellSize(val label: String, val homeSizeDp: Int) {
    Small("Small", 112),
    Medium("Medium", 140),
    Large("Large", 172)
}

/** Overall UI density/spacing of the app. */
enum class DisplayDensity(val label: String, val scale: Float) {
    Compact("Compact", 0.88f),
    Native("Native", 1.0f),
    Comfortable("Comfortable", 1.08f)
}

/** App typography choice. Android's system font scale is always preserved. */
enum class AppFontStyle(val label: String) {
    System("System"),
    Serif("Serif"),
    Monospace("Monospace"),
    DotMatrix("Dot matrix"),
}

/**
 * Identifies one of MuseFlow's lyrics sources - see [LyricsViewModel] for how they're tried.
 * [label] is what the drag-reorder settings screen shows; the enum name itself is what's
 * persisted (comma-joined) in [AppSettingsState.lyricsProviderOrder], so renaming a `label` is
 * safe but renaming an entry is not (it would silently reset any saved custom order back to
 * default for existing users, since [AppSettingsRepository] falls back to the default order for
 * any unrecognized name).
 */
enum class LyricsProviderId(val label: String) {
    YouLyPlus("YouLyPlus"),
    PaxSenix("PaxSenix"),
    BetterLyrics("Better Lyrics"),
    SimpMusic("SimpMusic"),
    LrcLib("LRCLib"),
    Kugou("Kugou");

    companion object {
        /** YouLyPlus, PaxSenix, BetterLyrics, SimpMusic, LrcLib, Kugou - the order this feature
         * shipped with. */
        val DEFAULT_ORDER = listOf(YouLyPlus, PaxSenix, BetterLyrics, SimpMusic, LrcLib, Kugou)

        /** Parses the comma-joined DataStore value back into an order, falling back to
         * [DEFAULT_ORDER] wholesale if it's blank. Unrecognized names (e.g. from a future
         * downgrade) are dropped rather than crashing; any provider missing from the saved value
         * (e.g. a newly added one after an update) is appended at the end so it's still reachable
         * instead of silently never tried. */
        fun deserialize(value: String): List<LyricsProviderId> {
            if (value.isBlank()) return DEFAULT_ORDER
            val saved = value.split(",").mapNotNull { name ->
                runCatching { valueOf(name.trim()) }.getOrNull()
            }
            val missing = entries.filter { it !in saved }
            return (saved + missing).ifEmpty { DEFAULT_ORDER }
        }

        fun serialize(order: List<LyricsProviderId>): String = order.joinToString(",") { it.name }
    }
}

/**
 * Persisted app preferences used by the player, lyrics, playback service and UI.
 * Some historical fields remain stored for compatibility even though their controls are hidden.
 */
data class AppSettingsState(
    val isLoaded: Boolean = false,
    // Mini-player
    val miniPlayerBackgroundStyle: BackgroundStyle = BackgroundStyle.Solid,

    // Player
    val playerBackgroundStyle: BackgroundStyle = BackgroundStyle.Solid,
    val hidePlayerThumbnail: Boolean = false,
    val thumbnailCornerRadius: Int = 12,
    val cropAlbumArt: Boolean = true,
    val playerButtonColor: PlayerButtonColorOption = PlayerButtonColorOption.Primary,
    val playerSliderStyle: PlayerSliderStyle = PlayerSliderStyle.Default,
    val playerTransportStyle: PlayerTransportStyle = PlayerTransportStyle.Static,
    /** Horizontal swipe-to-skip on the Now Playing artwork. On by default to match the gesture's
     * existing always-on behaviour; vertical swipe-to-adjust-volume is independent and unaffected
     * by this setting - see [com.example.ui.screens.artworkSwipeGestures]. */
    val swipeToChangeSongEnabled: Boolean = true,
    val showAnimatedCanvas: Boolean = false,
    val rotatingThumbnailAnimation: Boolean = false,
    val showCommentButton: Boolean = false,
    val showCodecInfo: Boolean = false,
    val miniPlayerSwipeSensitivity: Int = 50,

    // Lyrics
    /** User-configurable fallback order for lyrics sources - see [LyricsViewModel]. */
    val lyricsProviderOrder: List<LyricsProviderId> = LyricsProviderId.DEFAULT_ORDER,
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

    // Playback quality
    /** Drops silent passages, so gapless-mastered albums and padded uploads run tighter. */
    val skipSilence: Boolean = false,
    /** Restores the queue and position after the app is killed. */
    val persistentQueue: Boolean = true,
    /** Resolves the next track's stream URL ahead of time, removing the gap between tracks. */
    val preloadNextTrack: Boolean = true,
    /** Smooths loudness differences between tracks - a dynamic-range compressor in the audio
     * pipeline, not a per-track precomputed gain (this app has no loudness database to draw one
     * from). See [com.example.audio.NormalizerAudioProcessor]. */
    val audioNormalizationEnabled: Boolean = false,
    /** Fades the outgoing track out and the incoming one in over [crossfadeDurationMs], rather
     * than true overlapping playback of two simultaneous decoders - the same fade-based approach
     * most mobile players use under this name. See [PlayerViewModel.applyCrossfadeVolume]. */
    val crossfadeEnabled: Boolean = false,
    val crossfadeDurationMs: Int = 4000,
    /** A low-shelf boost distinct from the equalizer's own 60Hz band - see
     * [com.example.audio.BassBoostAudioProcessor]'s doc for why they're kept separate. */
    val bassBoostEnabled: Boolean = false,
    val bassBoostIntensity: Int = 50,
    /** Headphone crossfeed (blends a dulled portion of each channel into the other) - see
     * [com.example.audio.CrossfeedAudioProcessor]. Not a full spatial/HRTF virtualizer. */
    val crossfeedEnabled: Boolean = false,
    val crossfeedIntensity: Int = 30,

    // Misc
    val defaultOpenTab: DefaultTab = DefaultTab.Home,
    val defaultLibraryChip: DefaultLibraryChip = DefaultLibraryChip.Playlists,
    val swipeSongToQueue: Boolean = false,
    val enableHaptics: Boolean = true,
    val swipeSongToRemoveFromPlaylist: Boolean = false,
    val gridCellSize: GridCellSize = GridCellSize.Medium,
    val displayDensity: DisplayDensity = DisplayDensity.Comfortable,
    val fontStyle: AppFontStyle = AppFontStyle.System,

    // Auto playlists
    val showLikedPlaylist: Boolean = true,
    val showDownloadedPlaylist: Boolean = true,
    val showExportedPlaylist: Boolean = false,
    val showTopPlaylist: Boolean = true,
    val showCachedPlaylist: Boolean = false,

    /** The header shortcut on Library (next to search/stats) that opens a sheet of recently
     * played tracks - independent of [showCachedPlaylist], which is the "Recent" chip *section*
     * further down the same screen. Defaults on: unlike the chip row, this costs no extra screen
     * real estate when collapsed, so there's no reason to hide it by default. */
    val showRecentlyPlayedShortcut: Boolean = true,

    // Privacy
    /** Sets `FLAG_SECURE` on the activity window (applied reactively in `MuseFlowApp`), so the
     * app is blocked from screenshots/screen recording and hidden from the recents-app switcher
     * thumbnail. */
    val disableScreenshots: Boolean = false
)
