package com.example

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val Context.appSettingsDataStore by preferencesDataStore(name = "app_settings_prefs")

private object AppSettingsKeys {
    val MINI_PLAYER_BACKGROUND_STYLE = stringPreferencesKey("mini_player_background_style")

    val PLAYER_BACKGROUND_STYLE = stringPreferencesKey("player_background_style")
    val HIDE_PLAYER_THUMBNAIL = booleanPreferencesKey("hide_player_thumbnail")
    val THUMBNAIL_CORNER_RADIUS = intPreferencesKey("thumbnail_corner_radius")
    val CROP_ALBUM_ART = booleanPreferencesKey("crop_album_art")
    val PLAYER_BUTTON_COLOR = stringPreferencesKey("player_button_color")
    val PLAYER_SLIDER_STYLE = stringPreferencesKey("player_slider_style")
    val SWIPE_TO_CHANGE_SONG = booleanPreferencesKey("swipe_to_change_song")
    val SHOW_ANIMATED_CANVAS = booleanPreferencesKey("show_animated_canvas")
    val ROTATING_THUMBNAIL_ANIMATION = booleanPreferencesKey("rotating_thumbnail_animation")
    val SHOW_COMMENT_BUTTON = booleanPreferencesKey("show_comment_button")
    val SHOW_CODEC_INFO = booleanPreferencesKey("show_codec_info")
    val MINI_PLAYER_SWIPE_SENSITIVITY = intPreferencesKey("mini_player_swipe_sensitivity")

    val LYRICS_TEXT_POSITION = stringPreferencesKey("lyrics_text_position")
    val WORD_ANIMATION_STYLE = stringPreferencesKey("word_animation_style")
    val GLOWING_LYRICS_EFFECT = booleanPreferencesKey("glowing_lyrics_effect")
    val BLUR_INACTIVE_LINES = booleanPreferencesKey("blur_inactive_lines")
    val LYRICS_TEXT_SIZE = intPreferencesKey("lyrics_text_size")
    val LYRICS_LINE_SPACING = floatPreferencesKey("lyrics_line_spacing")
    val CHANGE_LYRICS_ON_CLICK = booleanPreferencesKey("change_lyrics_on_click")
    val AUTO_SCROLL_LYRICS = booleanPreferencesKey("auto_scroll_lyrics")
    val SWIPE_SONG_IN_FULLSCREEN_LYRICS = booleanPreferencesKey("swipe_song_in_fullscreen_lyrics")
    val SHOW_PLAY_PAUSE_OVERLAY_ON_THUMBNAIL = booleanPreferencesKey("show_play_pause_overlay_on_thumbnail")
    val HIDE_STATUS_BAR_IN_FULLSCREEN_LYRICS = booleanPreferencesKey("hide_status_bar_in_fullscreen_lyrics")

    val DEFAULT_OPEN_TAB = stringPreferencesKey("default_open_tab")
    val DEFAULT_LIBRARY_CHIP = stringPreferencesKey("default_library_chip")
    val SWIPE_SONG_TO_QUEUE = booleanPreferencesKey("swipe_song_to_queue")
    val ENABLE_HAPTICS = booleanPreferencesKey("enable_haptics")
    val SWIPE_SONG_TO_REMOVE_FROM_PLAYLIST = booleanPreferencesKey("swipe_song_to_remove_from_playlist")
    val GRID_CELL_SIZE = stringPreferencesKey("grid_cell_size")
    val DISPLAY_DENSITY = stringPreferencesKey("display_density")

    val SHOW_LIKED_PLAYLIST = booleanPreferencesKey("show_liked_playlist")
    val SHOW_DOWNLOADED_PLAYLIST = booleanPreferencesKey("show_downloaded_playlist")
    val SHOW_EXPORTED_PLAYLIST = booleanPreferencesKey("show_exported_playlist")
    val SHOW_TOP_PLAYLIST = booleanPreferencesKey("show_top_playlist")
    val SHOW_CACHED_PLAYLIST = booleanPreferencesKey("show_cached_playlist")
}

private inline fun <reified T : Enum<T>> Preferences.enumOrDefault(
    key: Preferences.Key<String>,
    default: T
): T = this[key]?.let { saved -> runCatching { enumValueOf<T>(saved) }.getOrNull() } ?: default

private class AppSettingsRepository(private val context: Context) {
    val state: Flow<AppSettingsState> = context.appSettingsDataStore.data.map { prefs ->
        AppSettingsState(
            miniPlayerBackgroundStyle = prefs.enumOrDefault(AppSettingsKeys.MINI_PLAYER_BACKGROUND_STYLE, BackgroundStyle.Solid),

            playerBackgroundStyle = prefs.enumOrDefault(AppSettingsKeys.PLAYER_BACKGROUND_STYLE, BackgroundStyle.Solid),
            hidePlayerThumbnail = prefs[AppSettingsKeys.HIDE_PLAYER_THUMBNAIL] ?: false,
            thumbnailCornerRadius = prefs[AppSettingsKeys.THUMBNAIL_CORNER_RADIUS] ?: 12,
            cropAlbumArt = prefs[AppSettingsKeys.CROP_ALBUM_ART] ?: true,
            playerButtonColor = prefs.enumOrDefault(AppSettingsKeys.PLAYER_BUTTON_COLOR, PlayerButtonColorOption.Primary),
            playerSliderStyle = prefs.enumOrDefault(AppSettingsKeys.PLAYER_SLIDER_STYLE, PlayerSliderStyle.Default),
            swipeToChangeSong = prefs[AppSettingsKeys.SWIPE_TO_CHANGE_SONG] ?: true,
            showAnimatedCanvas = prefs[AppSettingsKeys.SHOW_ANIMATED_CANVAS] ?: false,
            rotatingThumbnailAnimation = prefs[AppSettingsKeys.ROTATING_THUMBNAIL_ANIMATION] ?: false,
            showCommentButton = prefs[AppSettingsKeys.SHOW_COMMENT_BUTTON] ?: false,
            showCodecInfo = prefs[AppSettingsKeys.SHOW_CODEC_INFO] ?: false,
            miniPlayerSwipeSensitivity = prefs[AppSettingsKeys.MINI_PLAYER_SWIPE_SENSITIVITY] ?: 50,

            lyricsTextPosition = prefs.enumOrDefault(AppSettingsKeys.LYRICS_TEXT_POSITION, LyricsTextPosition.Center),
            wordAnimationStyle = prefs.enumOrDefault(AppSettingsKeys.WORD_ANIMATION_STYLE, WordAnimationStyle.Fade),
            glowingLyricsEffect = prefs[AppSettingsKeys.GLOWING_LYRICS_EFFECT] ?: false,
            blurInactiveLines = prefs[AppSettingsKeys.BLUR_INACTIVE_LINES] ?: true,
            lyricsTextSize = prefs[AppSettingsKeys.LYRICS_TEXT_SIZE] ?: 20,
            lyricsLineSpacing = prefs[AppSettingsKeys.LYRICS_LINE_SPACING] ?: 1.2f,
            changeLyricsOnClick = prefs[AppSettingsKeys.CHANGE_LYRICS_ON_CLICK] ?: true,
            autoScrollLyrics = prefs[AppSettingsKeys.AUTO_SCROLL_LYRICS] ?: true,
            swipeSongInFullscreenLyrics = prefs[AppSettingsKeys.SWIPE_SONG_IN_FULLSCREEN_LYRICS] ?: true,
            showPlayPauseOverlayOnThumbnail = prefs[AppSettingsKeys.SHOW_PLAY_PAUSE_OVERLAY_ON_THUMBNAIL] ?: true,
            hideStatusBarInFullscreenLyrics = prefs[AppSettingsKeys.HIDE_STATUS_BAR_IN_FULLSCREEN_LYRICS] ?: false,

            defaultOpenTab = prefs.enumOrDefault(AppSettingsKeys.DEFAULT_OPEN_TAB, DefaultTab.Home),
            defaultLibraryChip = prefs.enumOrDefault(AppSettingsKeys.DEFAULT_LIBRARY_CHIP, DefaultLibraryChip.Playlists),
            swipeSongToQueue = prefs[AppSettingsKeys.SWIPE_SONG_TO_QUEUE] ?: false,
            enableHaptics = prefs[AppSettingsKeys.ENABLE_HAPTICS] ?: true,
            swipeSongToRemoveFromPlaylist = prefs[AppSettingsKeys.SWIPE_SONG_TO_REMOVE_FROM_PLAYLIST] ?: false,
            gridCellSize = prefs.enumOrDefault(AppSettingsKeys.GRID_CELL_SIZE, GridCellSize.Medium),
            displayDensity = prefs.enumOrDefault(AppSettingsKeys.DISPLAY_DENSITY, DisplayDensity.Comfortable),

            showLikedPlaylist = prefs[AppSettingsKeys.SHOW_LIKED_PLAYLIST] ?: true,
            showDownloadedPlaylist = prefs[AppSettingsKeys.SHOW_DOWNLOADED_PLAYLIST] ?: true,
            showExportedPlaylist = prefs[AppSettingsKeys.SHOW_EXPORTED_PLAYLIST] ?: false,
            showTopPlaylist = prefs[AppSettingsKeys.SHOW_TOP_PLAYLIST] ?: true,
            showCachedPlaylist = prefs[AppSettingsKeys.SHOW_CACHED_PLAYLIST] ?: false
        )
    }

    suspend fun <T> setValue(key: Preferences.Key<T>, value: T) {
        context.appSettingsDataStore.edit { it[key] = value }
    }

    suspend fun <T : Enum<T>> setEnum(key: Preferences.Key<String>, value: T) {
        context.appSettingsDataStore.edit { it[key] = value.name }
    }
}

/**
 * Single source of truth for the extra Appearance preferences (Mini-player, Player, Lyrics,
 * Misc, Auto playlists). Scoped to the hosting Activity via `viewModel()`, same pattern as
 * [ThemeViewModel], and persisted to its own DataStore Preferences file so choices survive
 * relaunch. None of these (aside from the app background theme) drive real playback/lyrics
 * behavior yet - they only hold the user's selection.
 */
class AppSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AppSettingsRepository(application)

    val state: StateFlow<AppSettingsState> = repository.state.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppSettingsState()
    )

    // Mini-player
    fun setMiniPlayerBackgroundStyle(value: BackgroundStyle) = setEnum(AppSettingsKeys.MINI_PLAYER_BACKGROUND_STYLE, value)

    // Player
    fun setPlayerBackgroundStyle(value: BackgroundStyle) = setEnum(AppSettingsKeys.PLAYER_BACKGROUND_STYLE, value)
    fun setHidePlayerThumbnail(value: Boolean) = set(AppSettingsKeys.HIDE_PLAYER_THUMBNAIL, value)
    fun setThumbnailCornerRadius(value: Int) = set(AppSettingsKeys.THUMBNAIL_CORNER_RADIUS, value)
    fun setCropAlbumArt(value: Boolean) = set(AppSettingsKeys.CROP_ALBUM_ART, value)
    fun setPlayerButtonColor(value: PlayerButtonColorOption) = setEnum(AppSettingsKeys.PLAYER_BUTTON_COLOR, value)
    fun setPlayerSliderStyle(value: PlayerSliderStyle) = setEnum(AppSettingsKeys.PLAYER_SLIDER_STYLE, value)
    fun setSwipeToChangeSong(value: Boolean) = set(AppSettingsKeys.SWIPE_TO_CHANGE_SONG, value)
    fun setShowAnimatedCanvas(value: Boolean) = set(AppSettingsKeys.SHOW_ANIMATED_CANVAS, value)
    fun setRotatingThumbnailAnimation(value: Boolean) = set(AppSettingsKeys.ROTATING_THUMBNAIL_ANIMATION, value)
    fun setShowCommentButton(value: Boolean) = set(AppSettingsKeys.SHOW_COMMENT_BUTTON, value)
    fun setShowCodecInfo(value: Boolean) = set(AppSettingsKeys.SHOW_CODEC_INFO, value)
    fun setMiniPlayerSwipeSensitivity(value: Int) = set(AppSettingsKeys.MINI_PLAYER_SWIPE_SENSITIVITY, value)

    // Lyrics
    fun setLyricsTextPosition(value: LyricsTextPosition) = setEnum(AppSettingsKeys.LYRICS_TEXT_POSITION, value)
    fun setWordAnimationStyle(value: WordAnimationStyle) = setEnum(AppSettingsKeys.WORD_ANIMATION_STYLE, value)
    fun setGlowingLyricsEffect(value: Boolean) = set(AppSettingsKeys.GLOWING_LYRICS_EFFECT, value)
    fun setBlurInactiveLines(value: Boolean) = set(AppSettingsKeys.BLUR_INACTIVE_LINES, value)
    fun setLyricsTextSize(value: Int) = set(AppSettingsKeys.LYRICS_TEXT_SIZE, value)
    fun setLyricsLineSpacing(value: Float) = set(AppSettingsKeys.LYRICS_LINE_SPACING, value)
    fun setChangeLyricsOnClick(value: Boolean) = set(AppSettingsKeys.CHANGE_LYRICS_ON_CLICK, value)
    fun setAutoScrollLyrics(value: Boolean) = set(AppSettingsKeys.AUTO_SCROLL_LYRICS, value)
    fun setSwipeSongInFullscreenLyrics(value: Boolean) = set(AppSettingsKeys.SWIPE_SONG_IN_FULLSCREEN_LYRICS, value)
    fun setShowPlayPauseOverlayOnThumbnail(value: Boolean) = set(AppSettingsKeys.SHOW_PLAY_PAUSE_OVERLAY_ON_THUMBNAIL, value)
    fun setHideStatusBarInFullscreenLyrics(value: Boolean) = set(AppSettingsKeys.HIDE_STATUS_BAR_IN_FULLSCREEN_LYRICS, value)

    // Misc
    fun setDefaultOpenTab(value: DefaultTab) = setEnum(AppSettingsKeys.DEFAULT_OPEN_TAB, value)
    fun setDefaultLibraryChip(value: DefaultLibraryChip) = setEnum(AppSettingsKeys.DEFAULT_LIBRARY_CHIP, value)
    fun setSwipeSongToQueue(value: Boolean) = set(AppSettingsKeys.SWIPE_SONG_TO_QUEUE, value)
    fun setEnableHaptics(value: Boolean) = set(AppSettingsKeys.ENABLE_HAPTICS, value)
    fun setSwipeSongToRemoveFromPlaylist(value: Boolean) = set(AppSettingsKeys.SWIPE_SONG_TO_REMOVE_FROM_PLAYLIST, value)
    fun setGridCellSize(value: GridCellSize) = setEnum(AppSettingsKeys.GRID_CELL_SIZE, value)
    fun setDisplayDensity(value: DisplayDensity) = setEnum(AppSettingsKeys.DISPLAY_DENSITY, value)

    // Auto playlists
    fun setShowLikedPlaylist(value: Boolean) = set(AppSettingsKeys.SHOW_LIKED_PLAYLIST, value)
    fun setShowDownloadedPlaylist(value: Boolean) = set(AppSettingsKeys.SHOW_DOWNLOADED_PLAYLIST, value)
    fun setShowExportedPlaylist(value: Boolean) = set(AppSettingsKeys.SHOW_EXPORTED_PLAYLIST, value)
    fun setShowTopPlaylist(value: Boolean) = set(AppSettingsKeys.SHOW_TOP_PLAYLIST, value)
    fun setShowCachedPlaylist(value: Boolean) = set(AppSettingsKeys.SHOW_CACHED_PLAYLIST, value)

    private fun <T> set(key: Preferences.Key<T>, value: T) {
        viewModelScope.launch { repository.setValue(key, value) }
    }

    private fun <T : Enum<T>> setEnum(key: Preferences.Key<String>, value: T) {
        viewModelScope.launch { repository.setEnum(key, value) }
    }
}
