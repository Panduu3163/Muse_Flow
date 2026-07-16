package com.example

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// --- Appearance sub-screen row groups ---

@Composable
fun AppearanceMiniPlayerSection(state: AppSettingsState, viewModel: AppSettingsViewModel) {
    SettingsGroupHeader(title = "Mini-player")
    SettingsDropdownRow(
        icon = Icons.Default.ViewAgenda,
        title = "Mini-player background style",
        options = BackgroundStyle.entries,
        selected = state.miniPlayerBackgroundStyle,
        labelFor = { it.label },
        onSelect = viewModel::setMiniPlayerBackgroundStyle,
        tag = "dropdown_mini_player_background_style"
    )
}

@Composable
fun AppearancePlayerSection(state: AppSettingsState, viewModel: AppSettingsViewModel) {
    SettingsGroupHeader(title = "Player")
    SettingsDropdownRow(
        icon = Icons.Default.Wallpaper,
        title = "Player background style",
        options = BackgroundStyle.entries,
        selected = state.playerBackgroundStyle,
        labelFor = { it.label },
        onSelect = viewModel::setPlayerBackgroundStyle,
        tag = "dropdown_player_background_style"
    )
    SettingsToggleRow(
        icon = Icons.Default.HideImage,
        title = "Hide Player Thumbnail",
        subtitle = "Replace album art with the app logo",
        checked = state.hidePlayerThumbnail,
        onCheckedChange = viewModel::setHidePlayerThumbnail,
        tag = "toggle_hide_player_thumbnail"
    )
    SettingsSliderRow(
        icon = Icons.Default.RoundedCorner,
        title = "Thumbnail Corner Radius",
        valueLabel = "${state.thumbnailCornerRadius}dp",
        value = state.thumbnailCornerRadius.toFloat(),
        onValueChange = { viewModel.setThumbnailCornerRadius(it.toInt()) },
        valueRange = 0f..24f,
        steps = 23,
        tag = "slider_thumbnail_corner_radius"
    )
    SettingsToggleRow(
        icon = Icons.Default.Crop,
        title = "Crop Album Art",
        subtitle = "Force a square aspect ratio",
        checked = state.cropAlbumArt,
        onCheckedChange = viewModel::setCropAlbumArt,
        tag = "toggle_crop_album_art"
    )
    PlayerButtonColorRow(
        title = "Player button colors",
        selected = state.playerButtonColor,
        onSelect = viewModel::setPlayerButtonColor,
        tag = "picker_player_button_color"
    )
    SettingsDropdownRow(
        icon = Icons.Default.LinearScale,
        title = "Player slider style",
        options = PlayerSliderStyle.entries,
        selected = state.playerSliderStyle,
        labelFor = { it.label },
        onSelect = viewModel::setPlayerSliderStyle,
        tag = "dropdown_player_slider_style"
    )
}

@Composable
fun AppearanceLayoutSection(state: AppSettingsState, viewModel: AppSettingsViewModel) {
    SettingsGroupHeader(title = "Layout")
    SettingsSegmentedRow(
        icon = Icons.Default.DensityMedium,
        title = "Display density",
        options = DisplayDensity.entries,
        selected = state.displayDensity,
        labelFor = { it.label },
        onSelect = viewModel::setDisplayDensity,
        tag = "segmented_display_density"
    )
    SettingsSegmentedRow(
        icon = Icons.Default.GridView,
        title = "Grid cell size",
        options = GridCellSize.entries,
        selected = state.gridCellSize,
        labelFor = { it.label },
        onSelect = viewModel::setGridCellSize,
        tag = "segmented_grid_cell_size"
    )
}

// --- Player & Audio sub-screen row groups ---

@Composable
fun PlayerAudioNowPlayingSection(state: AppSettingsState, viewModel: AppSettingsViewModel) {
    SettingsGroupHeader(title = "Now Playing")
    SettingsToggleRow(
        icon = Icons.Default.SwipeLeft,
        title = "Enable swipe to change song",
        subtitle = "Swipe the player to skip tracks",
        checked = state.swipeToChangeSong,
        onCheckedChange = viewModel::setSwipeToChangeSong,
        tag = "toggle_swipe_to_change_song"
    )
    SettingsToggleRow(
        icon = Icons.Default.Animation,
        title = "Canvas",
        subtitle = "Show animated album covers when available",
        checked = state.showAnimatedCanvas,
        onCheckedChange = viewModel::setShowAnimatedCanvas,
        tag = "toggle_show_animated_canvas"
    )
    SettingsToggleRow(
        icon = Icons.Default.Autorenew,
        title = "Rotating thumbnail animation",
        subtitle = "Spin the album art like a vinyl record",
        checked = state.rotatingThumbnailAnimation,
        onCheckedChange = viewModel::setRotatingThumbnailAnimation,
        tag = "toggle_rotating_thumbnail_animation"
    )
    SettingsToggleRow(
        icon = Icons.Default.Comment,
        title = "Show comment button",
        subtitle = "Display a comment action on the player",
        checked = state.showCommentButton,
        onCheckedChange = viewModel::setShowCommentButton,
        tag = "toggle_show_comment_button"
    )
    SettingsToggleRow(
        icon = Icons.Default.Info,
        title = "Show codec info on player",
        subtitle = "Display audio format and bitrate details",
        checked = state.showCodecInfo,
        onCheckedChange = viewModel::setShowCodecInfo,
        tag = "toggle_show_codec_info"
    )
}

@Composable
fun PlayerAudioMiniPlayerSection(state: AppSettingsState, viewModel: AppSettingsViewModel) {
    SettingsGroupHeader(title = "Mini-player")
    SettingsSliderRow(
        icon = Icons.Default.SwipeVertical,
        title = "Mini player swipe sensitivity",
        valueLabel = "${state.miniPlayerSwipeSensitivity}%",
        value = state.miniPlayerSwipeSensitivity.toFloat(),
        onValueChange = { viewModel.setMiniPlayerSwipeSensitivity(it.toInt()) },
        valueRange = 0f..100f,
        tag = "slider_mini_player_swipe_sensitivity"
    )
}

// --- Lyrics sub-screen ---

@Composable
fun LyricsSection(state: AppSettingsState, viewModel: AppSettingsViewModel) {
    SettingsGroupHeader(title = "Lyrics")
    SettingsSegmentedRow(
        icon = Icons.Default.FormatAlignCenter,
        title = "Lyrics text position",
        options = LyricsTextPosition.entries,
        selected = state.lyricsTextPosition,
        labelFor = { it.label },
        onSelect = viewModel::setLyricsTextPosition,
        tag = "segmented_lyrics_text_position"
    )
    SettingsDropdownRow(
        icon = Icons.Default.TextFields,
        title = "Word-by-word animation style",
        options = WordAnimationStyle.entries,
        selected = state.wordAnimationStyle,
        labelFor = { it.label },
        onSelect = viewModel::setWordAnimationStyle,
        tag = "dropdown_word_animation_style"
    )
    SettingsToggleRow(
        icon = Icons.Default.Lightbulb,
        title = "Enable glowing lyrics effect",
        subtitle = "Add a soft glow to the active lyric line",
        checked = state.glowingLyricsEffect,
        onCheckedChange = viewModel::setGlowingLyricsEffect,
        tag = "toggle_glowing_lyrics_effect"
    )
    SettingsToggleRow(
        icon = Icons.Default.BlurOn,
        title = "Standard lyrics blur on inactive lines",
        subtitle = "Blur lines that aren't currently playing",
        checked = state.blurInactiveLines,
        onCheckedChange = viewModel::setBlurInactiveLines,
        tag = "toggle_blur_inactive_lines"
    )
    SettingsSliderRow(
        icon = Icons.Default.FormatSize,
        title = "Lyrics text size",
        valueLabel = "${state.lyricsTextSize}sp",
        value = state.lyricsTextSize.toFloat(),
        onValueChange = { viewModel.setLyricsTextSize(it.toInt()) },
        valueRange = 14f..32f,
        steps = 17,
        tag = "slider_lyrics_text_size"
    )
    SettingsSliderRow(
        icon = Icons.Default.FormatLineSpacing,
        title = "Lyrics line spacing",
        valueLabel = "${"%.1f".format(state.lyricsLineSpacing)}x",
        value = state.lyricsLineSpacing,
        onValueChange = { viewModel.setLyricsLineSpacing((it * 10).toInt() / 10f) },
        valueRange = 1.0f..2.0f,
        steps = 9,
        tag = "slider_lyrics_line_spacing"
    )
    SettingsToggleRow(
        icon = Icons.Default.TouchApp,
        title = "Change lyrics on click",
        subtitle = "Tap a lyric line to seek to that moment",
        checked = state.changeLyricsOnClick,
        onCheckedChange = viewModel::setChangeLyricsOnClick,
        tag = "toggle_change_lyrics_on_click"
    )
    SettingsToggleRow(
        icon = Icons.Default.VerticalAlignCenter,
        title = "Auto scroll lyrics",
        subtitle = "Automatically follow along as the song plays",
        checked = state.autoScrollLyrics,
        onCheckedChange = viewModel::setAutoScrollLyrics,
        tag = "toggle_auto_scroll_lyrics"
    )
    SettingsToggleRow(
        icon = Icons.Default.SwipeLeft,
        title = "Swipe to change song in fullscreen lyrics",
        subtitle = "Swipe the lyrics view to skip tracks",
        checked = state.swipeSongInFullscreenLyrics,
        onCheckedChange = viewModel::setSwipeSongInFullscreenLyrics,
        tag = "toggle_swipe_song_fullscreen_lyrics"
    )
    SettingsToggleRow(
        icon = Icons.Default.PlayCircle,
        title = "Show play/pause overlay on thumbnail",
        subtitle = "Overlay playback controls in fullscreen lyrics",
        checked = state.showPlayPauseOverlayOnThumbnail,
        onCheckedChange = viewModel::setShowPlayPauseOverlayOnThumbnail,
        tag = "toggle_show_play_pause_overlay"
    )
    SettingsToggleRow(
        icon = Icons.Default.VisibilityOff,
        title = "Hide status bar in fullscreen lyrics mode",
        subtitle = "Use the full screen height for lyrics",
        checked = state.hideStatusBarInFullscreenLyrics,
        onCheckedChange = viewModel::setHideStatusBarInFullscreenLyrics,
        tag = "toggle_hide_status_bar_fullscreen_lyrics"
    )
}

// --- Library & Playlists sub-screen row groups ---

@Composable
fun LibraryPlaylistsBrowseSection(state: AppSettingsState, viewModel: AppSettingsViewModel) {
    SettingsGroupHeader(title = "Library")
    SettingsDropdownRow(
        icon = Icons.Default.Home,
        title = "Default open tab",
        options = DefaultTab.entries,
        selected = state.defaultOpenTab,
        labelFor = { it.label },
        onSelect = viewModel::setDefaultOpenTab,
        tag = "dropdown_default_open_tab"
    )
    SettingsDropdownRow(
        icon = Icons.Default.LibraryMusic,
        title = "Default library chip",
        options = DefaultLibraryChip.entries,
        selected = state.defaultLibraryChip,
        labelFor = { it.label },
        onSelect = viewModel::setDefaultLibraryChip,
        tag = "dropdown_default_library_chip"
    )
}

@Composable
fun LibraryPlaylistsInteractionsSection(state: AppSettingsState, viewModel: AppSettingsViewModel) {
    SettingsGroupHeader(title = "Interactions")
    SettingsToggleRow(
        icon = Icons.Default.QueueMusic,
        title = "Swipe song left/right to queue/play-next",
        subtitle = "Swipe a track row to queue it or play it next",
        checked = state.swipeSongToQueue,
        onCheckedChange = viewModel::setSwipeSongToQueue,
        tag = "toggle_swipe_song_to_queue"
    )
    SettingsToggleRow(
        icon = Icons.Default.PlaylistRemove,
        title = "Swipe song to remove from playlist",
        subtitle = "Swipe a track row within a playlist to remove it",
        checked = state.swipeSongToRemoveFromPlaylist,
        onCheckedChange = viewModel::setSwipeSongToRemoveFromPlaylist,
        tag = "toggle_swipe_song_to_remove"
    )
    SettingsToggleRow(
        icon = Icons.Default.Vibration,
        title = "Enable haptics",
        subtitle = "Vibrate on taps, swipes, and toggles",
        checked = state.enableHaptics,
        onCheckedChange = viewModel::setEnableHaptics,
        tag = "toggle_enable_haptics"
    )
}

@Composable
fun AutoPlaylistsSection(state: AppSettingsState, viewModel: AppSettingsViewModel) {
    SettingsGroupHeader(title = "Auto playlists")
    SettingsToggleRow(
        icon = Icons.Default.Favorite,
        title = "Show \"Liked\" playlist",
        subtitle = "Surface your liked songs as an auto playlist",
        checked = state.showLikedPlaylist,
        onCheckedChange = viewModel::setShowLikedPlaylist,
        tag = "toggle_show_liked_playlist"
    )
    SettingsToggleRow(
        icon = Icons.Default.DownloadDone,
        title = "Show \"Downloaded\" playlist",
        subtitle = "Surface downloaded tracks as an auto playlist",
        checked = state.showDownloadedPlaylist,
        onCheckedChange = viewModel::setShowDownloadedPlaylist,
        tag = "toggle_show_downloaded_playlist"
    )
    SettingsToggleRow(
        icon = Icons.Default.IosShare,
        title = "Show \"Exported\" playlist",
        subtitle = "Surface exported tracks as an auto playlist",
        checked = state.showExportedPlaylist,
        onCheckedChange = viewModel::setShowExportedPlaylist,
        tag = "toggle_show_exported_playlist"
    )
    SettingsToggleRow(
        icon = Icons.Default.TrendingUp,
        title = "Show \"Top\" playlist",
        subtitle = "Surface your most played tracks as an auto playlist",
        checked = state.showTopPlaylist,
        onCheckedChange = viewModel::setShowTopPlaylist,
        tag = "toggle_show_top_playlist"
    )
    SettingsToggleRow(
        icon = Icons.Default.Storage,
        title = "Show \"Cached\" playlist",
        subtitle = "Surface locally cached tracks as an auto playlist",
        checked = state.showCachedPlaylist,
        onCheckedChange = viewModel::setShowCachedPlaylist,
        tag = "toggle_show_cached_playlist"
    )
}
