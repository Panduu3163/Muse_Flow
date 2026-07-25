package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.component.LyricsView
import com.example.ui.component.MiniPlayer
import com.example.ui.component.MuseFlowNavBar
import com.example.ui.screens.EqualizerScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.NowPlayingScreen
import com.example.ui.screens.PlaylistDetailScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MuseFlowTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MuseFlowApp() }
    }
}

/**
 * The app's root composable: resolves the user's persisted theme, then hosts the navigation graph
 * inside a [Scaffold] whose bottom slot holds the floating nav bar.
 *
 * [ThemeViewModel] is requested here, at the Activity-scoped root, so every screen below shares
 * the same instance - a theme change made in Settings recomposes the whole tree at once.
 */
@Composable
fun MuseFlowApp() {
    val themeViewModel: ThemeViewModel = viewModel()
    val theme by themeViewModel.themeState.collectAsState()

    val appSettingsViewModel: AppSettingsViewModel = viewModel()
    val appSettings by appSettingsViewModel.state.collectAsState()

    // Hoisted to the root so both the player background and the app-wide accent read one palette.
    val paletteViewModel: AlbumPaletteViewModel = viewModel()
    val albumPalette by paletteViewModel.palette.collectAsState()

    RequestNotificationPermissionOnce()

    // "Colour from album art": the artwork's dominant colour becomes the MaterialKolor seed, so the
    // whole generated palette follows what's playing. Falls back to the user's chosen accent
    // whenever nothing is playing or no palette could be extracted.
    val seedColor = if (theme.dynamicAlbumColor) {
        albumPalette?.dominant ?: theme.seedColor
    } else {
        theme.seedColor
    }

    // Display density scales every dp in the app at once by overriding LocalDensity, rather than
    // each screen having to know about the preference.
    val densityScale = when (appSettings.displayDensity) {
        DisplayDensity.Compact -> 0.88f
        DisplayDensity.Native -> 1.0f
        DisplayDensity.Comfortable -> 1.08f
    }
    val baseDensity = LocalDensity.current

    MuseFlowTheme(
        darkTheme = theme.darkTheme,
        pureBlack = theme.pureBlack,
        themeColor = seedColor,
    ) {
      CompositionLocalProvider(
          LocalDensity provides Density(
              density = baseDensity.density * densityScale,
              // fontScale is left alone: it's the user's accessibility setting, and quietly
              // shrinking text they asked to be larger would be the wrong call.
              fontScale = baseDensity.fontScale,
          )
      ) {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route

        // Activity-scoped, so the mini-player and every screen that triggers playback share one
        // MediaController connection to PlaybackService.
        val playerViewModel: PlayerViewModel = viewModel()
        val nowPlaying by playerViewModel.state.collectAsState()

        LaunchedEffect(nowPlaying.artworkUrl) { paletteViewModel.load(nowPlaying.artworkUrl) }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                // Hidden on destinations that aren't one of the four tabs (a playlist detail,
                // Now Playing), so those screens get the full height rather than a bar that
                // highlights nothing.
                if (TopLevelDestination.forRoute(currentRoute) != null) {
                    Column {
                        MiniPlayer(
                            state = nowPlaying,
                            onTogglePlayPause = playerViewModel::togglePlayPause,
                            onNext = { playerViewModel.next() },
                            onClick = { navController.navigate(Routes.NOW_PLAYING) },
                        )
                        MuseFlowNavBar(
                            destinations = TopLevelDestination.entries,
                            currentRoute = currentRoute,
                            onNavigate = { destination ->
                                navController.navigateToTab(destination.route)
                            },
                        )
                    }
                }
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                MuseFlowNavHost(
                    navController = navController,
                    onPlayTrack = playerViewModel::play,
                    playerViewModel = playerViewModel,
                    appSettings = appSettings,
                    albumPalette = albumPalette,
                    playerHasMedia = nowPlaying.hasMedia,
                )
            }
        }
      }
    }
}

/**
 * Asks for `POST_NOTIFICATIONS` on Android 13+.
 *
 * Both notification helpers already *check* this permission before posting, but nothing ever
 * requested it - so on Android 13+ every download-progress and download-complete notification was
 * being silently dropped. The media notification is posted by the foreground service and is
 * exempt, which is why playback controls appeared while download notifications never did.
 */
@Composable
private fun RequestNotificationPermissionOnce() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Declining is fine - downloads still work, they're just silent. */ }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun MuseFlowNavHost(
    navController: NavHostController,
    onPlayTrack: (TrackResult, List<TrackResult>) -> Unit,
    playerViewModel: PlayerViewModel,
    appSettings: AppSettingsState,
    albumPalette: AlbumPalette?,
    playerHasMedia: Boolean,
    modifier: Modifier = Modifier,
) {
    // Read once, when the graph is first built: changing the preference shouldn't yank the user
    // to a different tab mid-session, it should apply from the next launch.
    val startDestination = remember { appSettings.defaultOpenTab.toRoute() }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        topLevelGraph(
            onPlayTrack = onPlayTrack,
            onOpenPlaylist = { navController.navigate(Routes.playlist(it)) },
            onOpenEqualizer = { navController.navigate(Routes.EQUALIZER) },
            playerHasMedia = playerHasMedia,
        )
        composable(Routes.EQUALIZER) {
            EqualizerScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.PLAYLIST,
            arguments = listOf(navArgument(Routes.PLAYLIST_ARG) { type = NavType.LongType }),
        ) { entry ->
            PlaylistDetailScreen(
                playlistId = entry.arguments?.getLong(Routes.PLAYLIST_ARG) ?: 0L,
                onPlayTrack = onPlayTrack,
                onBack = { navController.popBackStack() },
            )
        }
        playerGraph(
            playerViewModel = playerViewModel,
            appSettings = appSettings,
            albumPalette = albumPalette,
            onCollapse = { navController.popBackStack() },
        )
    }
}

/** The full-screen player, kept out of [topLevelGraph] because it isn't a tab - the bottom bar
 * hides while it's open (see [TopLevelDestination.forRoute]). */
private fun NavGraphBuilder.playerGraph(
    playerViewModel: PlayerViewModel,
    appSettings: AppSettingsState,
    albumPalette: AlbumPalette?,
    onCollapse: () -> Unit,
) {
    composable(Routes.NOW_PLAYING) {
        val state by playerViewModel.state.collectAsState()
        val actionsViewModel: TrackActionsViewModel = viewModel()
        val likedKeys by actionsViewModel.likedKeys.collectAsState()
        val downloadedKeys by actionsViewModel.downloadedKeys.collectAsState()
        val downloadsInProgress by actionsViewModel.downloadsInProgress.collectAsState()

        // The controller only exposes metadata, so the track is reconstructed from it to reach
        // the same title/artist download key the repositories index by.
        val currentTrack = playerViewModel.currentTrackForActions()
        val key = currentTrack?.downloadKey()


        NowPlayingScreen(
            state = state,
            onTogglePlayPause = playerViewModel::togglePlayPause,
            onNext = { playerViewModel.next() },
            onPrevious = { playerViewModel.previous() },
            onSeek = playerViewModel::seekTo,
            onToggleShuffle = playerViewModel::toggleShuffle,
            onCycleRepeat = playerViewModel::cycleRepeatMode,
            onPlayQueueItem = playerViewModel::playQueueItem,
            onCollapse = onCollapse,
            isLiked = key != null && likedKeys.contains(key),
            isDownloaded = key != null && downloadedKeys.contains(key),
            downloadProgress = key?.let { downloadsInProgress[it] },
            onToggleLike = { currentTrack?.let(actionsViewModel::toggleLike) },
            onDownload = { currentTrack?.let(actionsViewModel::download) },
            hideArtwork = appSettings.hidePlayerThumbnail,
            artworkCornerRadius = appSettings.thumbnailCornerRadius,
            cropArtwork = appSettings.cropAlbumArt,
            wavySlider = appSettings.playerSliderStyle == PlayerSliderStyle.Wavy,
            slimSlider = appSettings.playerSliderStyle == PlayerSliderStyle.Slim,
            backgroundStyle = appSettings.playerBackgroundStyle,
            palette = albumPalette,
            buttonColor = when (appSettings.playerButtonColor) {
                PlayerButtonColorOption.Primary -> MaterialTheme.colorScheme.primary
                PlayerButtonColorOption.Secondary -> MaterialTheme.colorScheme.secondary
                PlayerButtonColorOption.Tertiary -> MaterialTheme.colorScheme.tertiary
            },
            lyricsContent = { slotModifier ->
                val lyricsViewModel: LyricsViewModel = viewModel()
                val lyrics by lyricsViewModel.state.collectAsState()

                // Keyed on the track so skipping refetches, and so the fetch only happens once
                // the user actually opens lyrics rather than on every track change.
                LaunchedEffect(state.title, state.artist) {
                    lyricsViewModel.load(
                        title = state.title,
                        artist = state.artist,
                        durationSeconds = (state.durationMs / 1000).toInt().takeIf { it > 0 },
                    )
                }

                LyricsView(
                    result = lyrics,
                    positionMs = state.positionMs,
                    // Named param: `it` here would bind to the enclosing composable() lambda's
                    // NavBackStackEntry, not the timestamp.
                    onSeekTo = { timestampMs -> playerViewModel.seekToMs(timestampMs) },
                    modifier = slotModifier,
                    textSizeSp = appSettings.lyricsTextSize,
                    lineSpacing = appSettings.lyricsLineSpacing,
                    textPosition = appSettings.lyricsTextPosition,
                    blurInactive = appSettings.blurInactiveLines,
                    glow = appSettings.glowingLyricsEffect,
                    autoScroll = appSettings.autoScrollLyrics,
                    tapToSeek = appSettings.changeLyricsOnClick,
                )
            },
        )
    }
}

/** The four tab destinations. Kept as an extension on [NavGraphBuilder] so further graphs
 * (playlist detail, Now Playing, settings sub-screens) can be added as sibling functions instead
 * of growing one monolithic `NavHost` block. */
private fun NavGraphBuilder.topLevelGraph(
    onPlayTrack: (TrackResult, List<TrackResult>) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenEqualizer: () -> Unit,
    playerHasMedia: Boolean,
) {
    composable(Routes.HOME) { HomeScreen(onPlayTrack = onPlayTrack, isPlayerActive = playerHasMedia) }
    composable(Routes.SEARCH) { SearchScreen(onPlayTrack = onPlayTrack) }
    composable(Routes.LIBRARY) {
        LibraryScreen(onPlayTrack = onPlayTrack, onOpenPlaylist = onOpenPlaylist)
    }
    composable(Routes.SETTINGS) { SettingsScreen(onOpenEqualizer = onOpenEqualizer) }
}

/**
 * Switches to a top-level tab the way a bottom bar is expected to behave: pops back to the start
 * destination rather than stacking tabs on top of each other, keeps each tab's own scroll/state
 * across switches, and never creates a second copy of a tab already on top.
 */
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
