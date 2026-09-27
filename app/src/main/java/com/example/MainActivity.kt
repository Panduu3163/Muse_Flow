package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.core.net.toUri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
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
import com.example.ui.theme.Motion
import com.example.ui.component.MiniPlayer
import com.example.ui.component.MuseFlowNavBar
import com.example.ui.component.TrackActionsHost
import com.example.ui.screens.AboutScreen
import com.example.ui.screens.ImportSharedPlaylistScreen
import com.example.ui.screens.UpdatesScreen
import com.example.ui.screens.AlbumScreen
import com.example.ui.screens.AppearanceSettingsScreen
import com.example.ui.screens.ArtistScreen
import com.example.ui.screens.AudioSettingsScreen
import com.example.ui.screens.BackupSettingsScreen
import com.example.ui.screens.ChartsScreen
import com.example.ui.screens.CrashLogsScreen
import com.example.ui.screens.BrowseScreen
import com.example.ui.screens.EqualizerScreen
import com.example.ui.screens.ExploreScreen
import com.example.ui.screens.GeneralSettingsScreen
import com.example.ui.screens.LibrarySectionsSettingsScreen
import com.example.ui.screens.LyricsProviderPriorityScreen
import com.example.ui.screens.LyricsSettingsScreen
import com.example.ui.screens.MiniPlayerSettingsScreen
import com.example.ui.screens.NewReleasesScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.asTrackResult
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.PlaybackSettingsScreen
import com.example.ui.screens.PlayerSettingsScreen
import com.example.ui.screens.PrivacySettingsScreen
import com.example.ui.screens.StatsScreen
import com.example.ui.screens.NowPlayingScreen
import com.example.ui.screens.OnboardingDialog
import com.example.ui.screens.PlaylistDetailScreen
import com.example.ui.screens.RemotePlaylistScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StorageSettingsScreen
import com.example.ui.theme.MuseFlowTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No-arg enableEdgeToEdge() draws AndroidX's own default theme-aware scrim over the
        // navigation-bar area (for legibility against arbitrary content) - a fixed translucent
        // overlay independent of whatever this app itself draws underneath. Against a colourful,
        // continuously-animated background there (Live Mesh/Blur mini-player), that scrim reads as
        // a visible seam/colour mismatch right at the nav-bar boundary. This app already handles
        // its own bottom-bar contrast (MiniPlayer/MuseFlowNavBar are opaque floating cards with
        // their own scrims), so the system's extra scrim is redundant - making both bar styles
        // fully transparent lets this app's own Compose background paint through uninterrupted.
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = androidx.activity.SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
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

    // DataStore's first emission is asynchronous - for one frame `theme` is the StateFlow's
    // `initialValue`, not the user's real saved seed colour. Without this gate, that one frame
    // rendered the *un-seeded* default (a generic blue) with real content (including the mini
    // player / Home's "Continue playing" card) already visible on top of it, then repainted into
    // the correct colours a moment later - the "wrong theme flash" this guards against. A plain
    // background for one frame is imperceptible; painting real content in the wrong colour isn't.
    if (!theme.isLoaded) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black))
        return
    }

    val appSettingsViewModel: AppSettingsViewModel = viewModel()
    val appSettings by appSettingsViewModel.state.collectAsState()

    // Applied reactively (not just read once at startup), so toggling "Disable screenshots" in
    // Settings takes effect on the window immediately - no relaunch needed either way.
    val activity = LocalContext.current as? android.app.Activity
    LaunchedEffect(appSettings.disableScreenshots, activity) {
        val window = activity?.window ?: return@LaunchedEffect
        if (appSettings.disableScreenshots) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

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
        val appContext = LocalContext.current.applicationContext
        // Surfaces a playback failure as a toast wherever the user is - previously silent, which
        // made "I tapped a song and nothing happened" indistinguishable from "nothing happened
        // because it's still loading" or a genuine bug elsewhere.
        LaunchedEffect(playerViewModel) {
            playerViewModel.playbackErrors.collect { message ->
                android.widget.Toast.makeText(appContext, message, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        val online by remember(appContext) { observeOnline(appContext) }
            .collectAsState(initial = isOnline(appContext))
        // A transient notice, not a persistent one: it used to stay on screen for as long as the
        // device remained offline, which meant it never went away on a longer offline stretch.
        // Re-armed on every false->... transition (LaunchedEffect's key), so going offline again
        // later shows it again rather than only ever once.
        var showOfflineBanner by remember { mutableStateOf(false) }
        // The reverse notice - only fires on an actual offline->online transition *during this
        // session* (hasBeenOffline), not on a cold start that was already online, which would
        // otherwise pop up on every normal launch for no reason.
        var showBackOnlineBanner by remember { mutableStateOf(false) }
        var hasBeenOffline by remember { mutableStateOf(false) }
        LaunchedEffect(online) {
            if (!online) {
                hasBeenOffline = true
                showOfflineBanner = true
                kotlinx.coroutines.delay(3000)
                showOfflineBanner = false
            } else {
                showOfflineBanner = false
                if (hasBeenOffline) {
                    hasBeenOffline = false
                    showBackOnlineBanner = true
                    kotlinx.coroutines.delay(3000)
                    showBackOnlineBanner = false
                }
            }
        }

        LaunchedEffect(nowPlaying.artworkUrl) { paletteViewModel.load(nowPlaying.artworkUrl) }

        // A full-bleed content Box with MiniPlayer/MuseFlowNavBar overlaid on top as a floating
        // layer, not Scaffold's docked `bottomBar` - a docked bottomBar reserves its own measured
        // height as permanent content inset, so content stops exactly above it with nothing ever
        // visible underneath. Both bars are already rounded pills with their own margins and
        // shadow (see MuseFlowNavBar's own doc comment), so overlaying them lets a screen's content
        // genuinely scroll behind their floating edges instead of being walled off by a flush dock.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .navigationBarsPadding(),
        ) {
            MuseFlowNavHost(
                navController = navController,
                onPlayTrack = playerViewModel::play,
                playerViewModel = playerViewModel,
                appSettings = appSettings,
                albumPalette = albumPalette,
                modifier = Modifier.fillMaxSize(),
            )

            // Connectivity is app state, not an error discovered only after a spinner times out.
            // Shown briefly on the transition to offline (see showOfflineBanner above) rather than
            // for as long as the device stays offline.
            if (showOfflineBanner) {
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 8.dp)
                        .testTag("offline_status"),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Offline · downloads and local music",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }

            if (showBackOnlineBanner) {
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 8.dp)
                        .testTag("back_online_status"),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "Online · enjoy limitless music",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }

            // The system-nav-bar inset is applied once on the outer Box above (so content itself
            // never draws into the gesture-nav zone), plus a small extra gap here so the floating
            // bar sits just above it rather than flush against it.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp),
            ) {
                // Shown wherever something is loaded, independent of whether this route is a
                // tab - a playlist detail, History or Backup screen previously hid the
                // mini-player entirely because it was wired to the same condition as the nav
                // bar below. Excluded only on Now Playing itself, where it would sit behind
                // the full player it mirrors.
                if (nowPlaying.hasMedia && currentRoute != Routes.NOW_PLAYING) {
                    MiniPlayer(
                        state = nowPlaying,
                        onTogglePlayPause = playerViewModel::togglePlayPause,
                        onPrevious = { playerViewModel.previous() },
                        onNext = { playerViewModel.next() },
                        onClick = { navController.navigate(Routes.NOW_PLAYING) },
                        backgroundStyle = appSettings.miniPlayerBackgroundStyle,
                        palette = albumPalette,
                    )
                }
                // Hidden on destinations that aren't one of the four tabs (a playlist detail,
                // Now Playing), so those screens get the full height rather than a bar that
                // highlights nothing.
                if (TopLevelDestination.forRoute(currentRoute) != null) {
                    MuseFlowNavBar(
                        destinations = TopLevelDestination.entries,
                        currentRoute = currentRoute,
                        onNavigate = { destination ->
                            navController.navigateToTab(destination.route)
                        },
                    )
                }
            }
        }

        val onboardingViewModel: OnboardingViewModel = viewModel()
        val onboardingKind by onboardingViewModel.onboardingKind.collectAsState()
        when (onboardingKind) {
            OnboardingKind.FreshInstall -> OnboardingDialog(onContinue = onboardingViewModel::markSeen)
            OnboardingKind.Updated -> com.example.ui.screens.ChangelogDialog(onContinue = onboardingViewModel::markSeen)
            OnboardingKind.None -> Unit
        }

        // Independent of the two dialogs above (see UpdateChecker's own doc) - a newer GitHub
        // release existing at all, not this build's own first run. Held off while either of those
        // is showing so a fresh install/update never sees two stacked dialogs on the very first
        // frame.
        val availableUpdate by UpdateChecker.availableUpdate.collectAsState()
        if (availableUpdate != null && onboardingKind == OnboardingKind.None) {
            val update = availableUpdate!!
            var isDownloadingUpdate by remember { mutableStateOf(false) }
            val updateScope = rememberCoroutineScope()
            val updateContext = LocalContext.current
            com.example.ui.screens.UpdateAvailableDialog(
                update = update,
                isDownloading = isDownloadingUpdate,
                onContinue = {
                    if (update.apkDownloadUrl != null) {
                        isDownloadingUpdate = true
                        updateScope.launch {
                            val started = InAppUpdater.downloadAndInstall(updateContext, update)
                            isDownloadingUpdate = false
                            if (started) UpdateChecker.dismissForNow() else {
                                android.widget.Toast.makeText(
                                    updateContext,
                                    "Couldn't download the update. Try again later.",
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    } else {
                        runCatching {
                            updateContext.startActivity(
                                Intent(Intent.ACTION_VIEW, android.net.Uri.parse(update.releaseUrl))
                            )
                        }
                        UpdateChecker.dismissForNow()
                    }
                },
                onDismiss = { if (!isDownloadingUpdate) UpdateChecker.dismissForNow() },
            )
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
    modifier: Modifier = Modifier,
) {
    // Read once, when the graph is first built: changing the preference shouldn't yank the user
    // to a different tab mid-session, it should apply from the next launch.
    val startDestination = remember { appSettings.defaultOpenTab.toRoute() }

    // Threaded into every screen that can open a track's actions sheet, so "View artist"/"View
    // album" reaches the same NavHostController every other navigation action here uses. The ids
    // travel URL-encoded (see [Routes.artist]/[Routes.album]) since a browseId can itself contain
    // "/"-like characters that would otherwise be read as extra path segments.
    val onGoToArtist: (String) -> Unit = { navController.navigate(Routes.artist(it)) }
    val onGoToAlbum: (String) -> Unit = { navController.navigate(Routes.album(it)) }
    val onGoToRemotePlaylist: (String, String, String, String?) -> Unit = { id, title, subtitle, imageUrl ->
        navController.navigate(Routes.remotePlaylist(id, title, subtitle, imageUrl))
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        // Direction-aware slide+fade for the whole NavHost, keyed on each tab's position in
        // TopLevelDestination - the same left/right-of-current-tab comparison Echo-Music's own
        // MainActivity.kt uses (verified against its source, not approximated). A push destination
        // (Artist/Album/Playlist/...) has no entry in TopLevelDestination, so indexOfFirst returns
        // -1 for it - the "not a tab" branch below - which naturally reads as "slide in from the
        // right" for a push and "slide out to the right" when popped back out of, with no separate
        // tab-vs-push branch needed. Now Playing is the one deliberate exception: it isn't a tab and
        // has no meaningful left/right position, so it always gets a vertical slide instead, reading
        // as a sheet coming up over whatever's beneath it.
        enterTransition = {
            val currentRouteIndex = TopLevelDestination.entries.indexOfFirst { it.route == targetState.destination.route }
            val previousRouteIndex = TopLevelDestination.entries.indexOfFirst { it.route == initialState.destination.route }
            when {
                targetState.destination.route == Routes.NOW_PLAYING ->
                    slideInVertically(animationSpec = Motion.quickFade(200)) { it } + fadeIn(Motion.quickFade(200))
                currentRouteIndex == -1 || currentRouteIndex > previousRouteIndex ->
                    slideInHorizontally(animationSpec = Motion.quickFade(200)) { it / 8 } + fadeIn(Motion.quickFade(200))
                else ->
                    slideInHorizontally(animationSpec = Motion.quickFade(200)) { -it / 8 } + fadeIn(Motion.quickFade(200))
            }
        },
        exitTransition = {
            val currentRouteIndex = TopLevelDestination.entries.indexOfFirst { it.route == initialState.destination.route }
            val targetRouteIndex = TopLevelDestination.entries.indexOfFirst { it.route == targetState.destination.route }
            when {
                initialState.destination.route == Routes.NOW_PLAYING ->
                    slideOutVertically(animationSpec = Motion.quickFade(180)) { it } + fadeOut(Motion.quickFade(180))
                targetRouteIndex == -1 || targetRouteIndex > currentRouteIndex ->
                    slideOutHorizontally(animationSpec = Motion.quickFade(200)) { -it / 8 } + fadeOut(Motion.quickFade(180))
                else ->
                    slideOutHorizontally(animationSpec = Motion.quickFade(200)) { it / 8 } + fadeOut(Motion.quickFade(180))
            }
        },
        popEnterTransition = {
            val currentRouteIndex = TopLevelDestination.entries.indexOfFirst { it.route == targetState.destination.route }
            val previousRouteIndex = TopLevelDestination.entries.indexOfFirst { it.route == initialState.destination.route }
            when {
                targetState.destination.route == Routes.NOW_PLAYING ->
                    slideInVertically(animationSpec = Motion.quickFade(200)) { it } + fadeIn(Motion.quickFade(200))
                previousRouteIndex != -1 && previousRouteIndex < currentRouteIndex ->
                    slideInHorizontally(animationSpec = Motion.quickFade(200)) { it / 8 } + fadeIn(Motion.quickFade(200))
                else ->
                    slideInHorizontally(animationSpec = Motion.quickFade(200)) { -it / 8 } + fadeIn(Motion.quickFade(200))
            }
        },
        popExitTransition = {
            val currentRouteIndex = TopLevelDestination.entries.indexOfFirst { it.route == initialState.destination.route }
            val targetRouteIndex = TopLevelDestination.entries.indexOfFirst { it.route == targetState.destination.route }
            when {
                initialState.destination.route == Routes.NOW_PLAYING ->
                    slideOutVertically(animationSpec = Motion.quickFade(180)) { it } + fadeOut(Motion.quickFade(180))
                currentRouteIndex != -1 && currentRouteIndex < targetRouteIndex ->
                    slideOutHorizontally(animationSpec = Motion.quickFade(200)) { -it / 8 } + fadeOut(Motion.quickFade(180))
                else ->
                    slideOutHorizontally(animationSpec = Motion.quickFade(200)) { it / 8 } + fadeOut(Motion.quickFade(180))
            }
        },
    ) {
        topLevelGraph(
            onOpenBrowse = { id, params -> navController.navigate(Routes.browse(id, params)) },
            onOpenTogether = { navController.navigate(Routes.LISTEN_TOGETHER) },
            onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            onPlayTrack = onPlayTrack,
            playerViewModel = playerViewModel,
            onOpenPlaylist = { navController.navigate(Routes.playlist(it)) },
            onOpenLibraryCollection = { navController.navigate(Routes.libraryCollection(it)) },
            onOpenLibraryStats = { navController.navigate(Routes.LIBRARY_STATS) },
            onOpenEqualizer = { navController.navigate(Routes.EQUALIZER) },
            onOpenHistory = { navController.navigate(Routes.HISTORY) },
            onOpenBackup = { navController.navigate(Routes.BACKUP) },
            onOpenCrashLogs = { navController.navigate(Routes.CRASH_LOGS) },
            onOpenStorage = { navController.navigate(Routes.STORAGE) },
            onOpenAbout = { navController.navigate(Routes.ABOUT) },
            onOpenUpdates = { navController.navigate(Routes.UPDATES) },
            onImportSharedPlaylist = { navController.navigate(Routes.IMPORT_SHARED_PLAYLIST) },
            onOpenSettingsAppearance = { navController.navigate(Routes.SETTINGS_APPEARANCE) },
            onOpenSettingsMiniPlayer = { navController.navigate(Routes.SETTINGS_MINI_PLAYER) },
            onOpenSettingsPlayer = { navController.navigate(Routes.SETTINGS_PLAYER) },
            onOpenSettingsLyrics = { navController.navigate(Routes.SETTINGS_LYRICS) },
            onOpenSettingsAudio = { navController.navigate(Routes.SETTINGS_AUDIO) },
            onOpenSettingsPlayback = { navController.navigate(Routes.SETTINGS_PLAYBACK) },
            onOpenSettingsGeneral = { navController.navigate(Routes.SETTINGS_GENERAL) },
            onOpenSettingsPrivacy = { navController.navigate(Routes.SETTINGS_PRIVACY) },
            onOpenSettingsLibrarySections = { navController.navigate(Routes.SETTINGS_LIBRARY_SECTIONS) },
            onOpenCharts = { navController.navigate(Routes.CHARTS) },
            onOpenNewReleases = { navController.navigate(Routes.NEW_RELEASES) },
            onOpenExplore = { navController.navigate(Routes.EXPLORE) },
            onGoToArtist = onGoToArtist,
            onGoToAlbum = onGoToAlbum,
            onGoToRemotePlaylist = onGoToRemotePlaylist,
        )
        composable(Routes.BACKUP) {
            BackupSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.CRASH_LOGS) {
            CrashLogsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.STORAGE) {
            StorageSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.UPDATES) {
            UpdatesScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.IMPORT_SHARED_PLAYLIST) {
            ImportSharedPlaylistScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS_APPEARANCE) {
            AppearanceSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS_MINI_PLAYER) {
            MiniPlayerSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS_PLAYER) {
            PlayerSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS_LYRICS) {
            LyricsSettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenProviderOrder = { navController.navigate(Routes.SETTINGS_LYRICS_PROVIDER_ORDER) },
            )
        }
        composable(Routes.SETTINGS_LYRICS_PROVIDER_ORDER) {
            LyricsProviderPriorityScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS_AUDIO) {
            AudioSettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenEqualizer = { navController.navigate(Routes.EQUALIZER) },
            )
        }
        composable(Routes.SETTINGS_PLAYBACK) {
            PlaybackSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS_GENERAL) {
            GeneralSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS_PRIVACY) {
            PrivacySettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS_LIBRARY_SECTIONS) {
            LibrarySectionsSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.CHARTS) {
            ChartsScreen(
                onPlayTrack = onPlayTrack,
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onGoToArtist = onGoToArtist,
                onGoToAlbum = onGoToAlbum,
            )
        }
        composable(Routes.NEW_RELEASES) {
            NewReleasesScreen(
                onPlayTrack = onPlayTrack,
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onGoToArtist = onGoToArtist,
                onGoToAlbum = onGoToAlbum,
            )
        }
        composable(Routes.EXPLORE) {
            ExploreScreen(
                onOpenBrowse = { browseId, params ->
                    navController.navigate(Routes.browse(browseId, params))
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.BROWSE,
            arguments = listOf(
                navArgument(Routes.BROWSE_ID_ARG) { type = NavType.StringType },
                navArgument(Routes.BROWSE_PARAMS_ARG) {
                    type = NavType.StringType
                    nullable = true
                },
            ),
        ) { entry ->
            val browseId = entry.arguments?.getString(Routes.BROWSE_ID_ARG)?.let {
                java.net.URLDecoder.decode(it, "UTF-8")
            } ?: return@composable
            val params = entry.arguments?.getString(Routes.BROWSE_PARAMS_ARG)
                ?.takeIf { it.isNotEmpty() }
                ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
            BrowseScreen(
                browseId = browseId,
                params = params,
                onPlayTrack = onPlayTrack,
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onGoToArtist = onGoToArtist,
                onGoToAlbum = onGoToAlbum,
            )
        }
        composable(Routes.EQUALIZER) {
            EqualizerScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                onPlayTrack = onPlayTrack,
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onGoToArtist = onGoToArtist,
                onGoToAlbum = onGoToAlbum,
            )
        }
        composable(Routes.LIBRARY_STATS) {
            StatsScreen(
                onBack = { navController.popBackStack() },
                onGoToArtist = onGoToArtist,
                onPlayTrack = onPlayTrack,
            )
        }
        composable(
            route = Routes.LIBRARY_COLLECTION,
            arguments = listOf(navArgument(Routes.LIBRARY_COLLECTION_ARG) { type = NavType.StringType }),
        ) { entry ->
            val section = entry.arguments?.getString(Routes.LIBRARY_COLLECTION_ARG)
                ?.let { runCatching { LibrarySection.valueOf(it) }.getOrNull() }
                ?: return@composable
            LibraryScreen(
                onPlayTrack = onPlayTrack,
                playerViewModel = playerViewModel,
                onOpenPlaylist = { navController.navigate(Routes.playlist(it)) },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onOpenStats = { navController.navigate(Routes.LIBRARY_STATS) },
                onOpenStorage = { navController.navigate(Routes.STORAGE) },
                onGoToArtist = onGoToArtist,
                onGoToAlbum = onGoToAlbum,
                detailSection = section,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.LISTEN_TOGETHER) {
            com.example.ui.screens.ListenTogetherComingSoonScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.PLAYLIST,
            arguments = listOf(navArgument(Routes.PLAYLIST_ARG) { type = NavType.LongType }),
        ) { entry ->
            PlaylistDetailScreen(
                playlistId = entry.arguments?.getLong(Routes.PLAYLIST_ARG) ?: 0L,
                onPlayTrack = onPlayTrack,
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onGoToArtist = onGoToArtist,
                onGoToAlbum = onGoToAlbum,
            )
        }
        composable(
            route = Routes.ARTIST,
            arguments = listOf(navArgument(Routes.ARTIST_ARG) { type = NavType.StringType }),
        ) { entry ->
            val artistId = entry.arguments?.getString(Routes.ARTIST_ARG)?.let {
                java.net.URLDecoder.decode(it, "UTF-8")
            } ?: return@composable
            ArtistScreen(
                artistId = artistId,
                onPlayTrack = onPlayTrack,
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onGoToArtist = onGoToArtist,
                onGoToAlbum = onGoToAlbum,
            )
        }
        composable(
            route = Routes.ALBUM,
            arguments = listOf(navArgument(Routes.ALBUM_ARG) { type = NavType.StringType }),
        ) { entry ->
            val albumId = entry.arguments?.getString(Routes.ALBUM_ARG)?.let {
                java.net.URLDecoder.decode(it, "UTF-8")
            } ?: return@composable
            AlbumScreen(
                albumId = albumId,
                onPlayTrack = onPlayTrack,
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onGoToArtist = onGoToArtist,
                onGoToAlbum = onGoToAlbum,
            )
        }
        composable(
            route = Routes.REMOTE_PLAYLIST,
            arguments = listOf(
                navArgument(Routes.REMOTE_PLAYLIST_ARG) { type = NavType.StringType },
                navArgument(Routes.REMOTE_PLAYLIST_TITLE_ARG) { type = NavType.StringType },
                navArgument(Routes.REMOTE_PLAYLIST_SUBTITLE_ARG) { type = NavType.StringType },
                navArgument(Routes.REMOTE_PLAYLIST_IMAGE_ARG) {
                    type = NavType.StringType
                    nullable = true
                },
            ),
        ) { entry ->
            fun arg(name: String) = entry.arguments?.getString(name)?.let {
                java.net.URLDecoder.decode(it, "UTF-8")
            }
            val playlistId = arg(Routes.REMOTE_PLAYLIST_ARG) ?: return@composable
            RemotePlaylistScreen(
                playlistId = playlistId,
                title = arg(Routes.REMOTE_PLAYLIST_TITLE_ARG) ?: "Playlist",
                subtitle = arg(Routes.REMOTE_PLAYLIST_SUBTITLE_ARG).orEmpty(),
                imageUrl = arg(Routes.REMOTE_PLAYLIST_IMAGE_ARG)?.takeIf { it.isNotEmpty() },
                onPlayTrack = onPlayTrack,
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() },
                onGoToArtist = onGoToArtist,
                onGoToAlbum = onGoToAlbum,
            )
        }
        playerGraph(
            onOpenEqualizer = { navController.navigate(Routes.EQUALIZER) },
            playerViewModel = playerViewModel,
            appSettings = appSettings,
            albumPalette = albumPalette,
            onCollapse = { navController.popBackStack() },
            onGoToArtist = onGoToArtist,
            onGoToAlbum = onGoToAlbum,
        )
    }
}

/** The full-screen player, kept out of [topLevelGraph] because it isn't a tab - the bottom bar
 * hides while it's open (see [TopLevelDestination.forRoute]). */
private fun NavGraphBuilder.playerGraph(
    onOpenEqualizer: () -> Unit,
    playerViewModel: PlayerViewModel,
    appSettings: AppSettingsState,
    albumPalette: AlbumPalette?,
    onCollapse: () -> Unit,
    onGoToArtist: (String) -> Unit,
    onGoToAlbum: (String) -> Unit,
) {
    composable(Routes.NOW_PLAYING) {
        val context = LocalContext.current
        val state by playerViewModel.state.collectAsState()
        val actionsViewModel: TrackActionsViewModel = viewModel()
        val likedKeys by actionsViewModel.likedKeys.collectAsState()
        val downloadedKeys by actionsViewModel.downloadedKeys.collectAsState()
        val downloadsInProgress by actionsViewModel.downloadsInProgress.collectAsState()

        // The controller only exposes metadata, so the track is reconstructed from it to reach
        // the same title/artist download key the repositories index by. Remembered on the same
        // (title, artist) key the LaunchedEffect below already uses to mean "the track changed" -
        // without this, `currentTrackForActions()` re-runs (and hands NowPlayingScreen a new Track
        // instance) on every recomposition of this route, which defeats every callback below that's
        // itself remembered on `currentTrack` for stability.
        val currentTrack = remember(state.title, state.artist) { playerViewModel.currentTrackForActions() }
        val key = remember(currentTrack) { currentTrack?.downloadKey() }
        val sleepTimerRemainingMs by playerViewModel.sleepTimerRemainingMs.collectAsState()
        var menuTrack by remember { mutableStateOf<TrackResult?>(null) }

        // Hoisted out of the lyrics slot (rather than kept lazy behind "lyrics panel open") so the
        // main player menu's "Copy lyrics"/"Search lyrics online" - folded in from the lyrics
        // panel's own former menu - can offer them without requiring the panel to have been opened
        // first.
        val lyricsViewModel: LyricsViewModel = viewModel()
        val lyrics by lyricsViewModel.state.collectAsState()
        val lyricsPositionMs by playerViewModel.lyricsPositionMs.collectAsState()
        val clipboard = LocalClipboardManager.current

        // Every callback below is remembered on the same key its closure actually captures, so
        // NowPlayingScreen (and everything it passes these on to, e.g. transport buttons wrapped in
        // bounceClick) sees a stable reference across recompositions instead of a fresh lambda every
        // time - a fresh lambda identity is indistinguishable from "this callback actually changed"
        // to Compose's skip check, which was silently defeating recomposition-skipping on this
        // screen's own most frequently-recomposing subtree (transport controls, tracked in
        // `docs/nowplaying-jank-investigation.md`).
        val onTogglePlayPause = remember(playerViewModel) { playerViewModel::togglePlayPause }
        val onNext = remember(playerViewModel) { { playerViewModel.next(); Unit } }
        val onPrevious = remember(playerViewModel) { { playerViewModel.previous(); Unit } }
        val onSeek = remember(playerViewModel) { playerViewModel::seekTo }
        val onToggleShuffle = remember(playerViewModel) { playerViewModel::toggleShuffle }
        val onCycleRepeat = remember(playerViewModel) { playerViewModel::cycleRepeatMode }
        val onPlayQueueItem = remember(playerViewModel) { playerViewModel::playQueueItem }
        val onMoveQueueItem = remember(playerViewModel) { playerViewModel::moveQueueItem }
        val onRemoveQueueItem = remember(playerViewModel) { playerViewModel::removeQueueItem }
        val onToggleLike = remember(currentTrack, actionsViewModel) {
            { currentTrack?.let(actionsViewModel::toggleLike); Unit }
        }
        val onDownload = remember(currentTrack, actionsViewModel) {
            { currentTrack?.let(actionsViewModel::download); Unit }
        }
        val onStartSleepTimer = remember(playerViewModel) { playerViewModel::startSleepTimer }
        val onCancelSleepTimer = remember(playerViewModel) { playerViewModel::cancelSleepTimer }
        val onSetPlaybackSpeed = remember(playerViewModel) { playerViewModel::setPlaybackSpeed }
        val onOpenMenu = remember(currentTrack) { { menuTrack = currentTrack?.asTrackResult() } }
        val onLyricsSeekTo = remember(playerViewModel) { { timestampMs: Long -> playerViewModel.seekToMs(timestampMs) } }
        val buttonColor = when (appSettings.playerButtonColor) {
            PlayerButtonColorOption.Primary -> MaterialTheme.colorScheme.primary
            PlayerButtonColorOption.Secondary -> MaterialTheme.colorScheme.secondary
            PlayerButtonColorOption.Tertiary -> MaterialTheme.colorScheme.tertiary
        }

        LaunchedEffect(state.title, state.artist) {
            // Only a confirmed real YouTube id, never the "title|artist" stand-in a stored track
            // without one reports itself as - see hasRealVideoId's own reasoning. A fabricated id
            // here would just make the YouTube-tab fallback fail instead of being skipped.
            val videoId = currentTrack?.asTrackResult()
                ?.takeIf { it.hasRealVideoId() }
                ?.id
            lyricsViewModel.load(
                title = state.title,
                artist = state.artist,
                durationSeconds = (state.durationMs / 1000).toInt().takeIf { it > 0 },
                videoId = videoId,
            )
        }

        NowPlayingScreen(
            state = state,
            onTogglePlayPause = onTogglePlayPause,
            onNext = onNext,
            onPrevious = onPrevious,
            onSeek = onSeek,
            onToggleShuffle = onToggleShuffle,
            onCycleRepeat = onCycleRepeat,
            onPlayQueueItem = onPlayQueueItem,
            onMoveQueueItem = onMoveQueueItem,
            onRemoveQueueItem = onRemoveQueueItem,
            onCollapse = onCollapse,
            isLiked = key != null && likedKeys.contains(key),
            isDownloaded = key != null && downloadedKeys.contains(key),
            downloadProgress = key?.let { downloadsInProgress[it] },
            onToggleLike = onToggleLike,
            onDownload = onDownload,
            hideArtwork = appSettings.hidePlayerThumbnail,
            artworkCornerRadius = appSettings.thumbnailCornerRadius,
            cropArtwork = appSettings.cropAlbumArt,
            wavySlider = appSettings.playerSliderStyle == PlayerSliderStyle.Wavy,
            slimSlider = appSettings.playerSliderStyle == PlayerSliderStyle.Slim,
            squigglySlider = appSettings.playerSliderStyle == PlayerSliderStyle.Squiggly,
            transportStyle = appSettings.playerTransportStyle,
            swipeToChangeSongEnabled = appSettings.swipeToChangeSongEnabled,
            showCodecInfo = appSettings.showCodecInfo,
            backgroundStyle = appSettings.playerBackgroundStyle,
            palette = albumPalette,
            buttonColor = buttonColor,
            sleepTimerRemainingMs = sleepTimerRemainingMs,
            onStartSleepTimer = onStartSleepTimer,
            onCancelSleepTimer = onCancelSleepTimer,
            onSetPlaybackSpeed = onSetPlaybackSpeed,
            onOpenMenu = onOpenMenu,
            onGoToArtist = onGoToArtist,
            lyricsContent = { slotModifier ->
                LyricsView(
                    result = lyrics,
                    positionMs = lyricsPositionMs,
                    onSeekTo = onLyricsSeekTo,
                    modifier = slotModifier,
                    textSizeSp = appSettings.lyricsTextSize,
                    lineSpacing = appSettings.lyricsLineSpacing,
                    textPosition = appSettings.lyricsTextPosition,
                    blurInactive = appSettings.blurInactiveLines,
                    glow = appSettings.glowingLyricsEffect,
                    autoScroll = appSettings.autoScrollLyrics,
                    tapToSeek = appSettings.changeLyricsOnClick,
                    wordAnimationStyle = appSettings.wordAnimationStyle,
                )
            },
        )

        // Always mounted - NOT wrapped in `if (menuTrack != null)`. TrackActionsHost owns its own
        // "Details"/"Add to playlist" dialog state internally via `remember`, entered only *after*
        // the sheet itself dismisses (see its doc). Wrapping the whole call in an `if` keyed on
        // `menuTrack` tore that state down the instant the sheet's onDismiss ran (menuTrack = null
        // happens on every sheet action, "Details" included) - the dialog was asked to open and
        // destroyed in the same frame, which is why it never appeared. Passing `track` straight
        // through as a nullable param instead - the same pattern every other screen already uses -
        // keeps TrackActionsHost itself permanently composed, so its dialogs outlive the sheet.
        TrackActionsHost(
            track = menuTrack,
            onDismiss = { menuTrack = null },
            playerViewModel = playerViewModel,
            actionsViewModel = actionsViewModel,
            onGoToArtist = onGoToArtist,
            onGoToAlbum = onGoToAlbum,
            // Now Playing already has its own dedicated queue/like/download controls elsewhere on
            // screen - repeating them here for the track that's already playing was confusing more
            // than useful (some read as broken since acting on "the currently playing track"
            // doesn't do anything visibly different).
            showQueueActions = false,
            showLikeAction = false,
            showDownloadAction = true,
            onEqualizer = onOpenEqualizer,
            // Folds the lyrics panel's own former "⋮" menu into this one - one menu button on the
            // whole screen instead of two stacked on top of each other. Always present (not gated
            // on lyrics already being loaded) - "Search lyrics online" never needed lyrics text at
            // all, and "Copy lyrics" checks the live state at tap time rather than a value snapshotted
            // when the menu happened to open, so it works the first time Now Playing is opened
            // rather than only after the lyrics panel has been shown once.
            onCopyLyrics = {
                val text = lyrics.asCopyableText()
                if (text != null) {
                    clipboard.setText(AnnotatedString(text))
                    Toast.makeText(context, "Lyrics copied", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "No lyrics to copy yet", Toast.LENGTH_SHORT).show()
                }
            },
            onSearchLyricsOnline = {
                val query = java.net.URLEncoder.encode("${state.title} ${state.artist} lyrics", "UTF-8")
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, "https://www.google.com/search?q=$query".toUri()))
                }
            },
        )
    }
}

/** The four tab destinations. Kept as an extension on [NavGraphBuilder] so further graphs
 * (playlist detail, Now Playing, settings sub-screens) can be added as sibling functions instead
 * of growing one monolithic `NavHost` block. */
private fun NavGraphBuilder.topLevelGraph(
    onOpenBrowse: (String, String?) -> Unit,
    onOpenTogether: () -> Unit,
    onOpenSettings: () -> Unit,
    onPlayTrack: (TrackResult, List<TrackResult>) -> Unit,
    // Passed rather than resolved with viewModel() inside each screen: the track context menu's
    // queue actions have to reach the same activity-scoped controller the mini-player uses, and a
    // viewModel() call inside a composable() would be scoped to that NavBackStackEntry instead -
    // a second MediaController connection queueing into a player nobody can see.
    playerViewModel: PlayerViewModel,
    onOpenPlaylist: (Long) -> Unit,
    onOpenLibraryCollection: (LibrarySection) -> Unit,
    onOpenLibraryStats: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenCrashLogs: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenUpdates: () -> Unit,
    onImportSharedPlaylist: () -> Unit,
    onOpenSettingsAppearance: () -> Unit,
    onOpenSettingsMiniPlayer: () -> Unit,
    onOpenSettingsPlayer: () -> Unit,
    onOpenSettingsLyrics: () -> Unit,
    onOpenSettingsAudio: () -> Unit,
    onOpenSettingsPlayback: () -> Unit,
    onOpenSettingsGeneral: () -> Unit,
    onOpenSettingsPrivacy: () -> Unit,
    onOpenSettingsLibrarySections: () -> Unit,
    onOpenCharts: () -> Unit,
    onOpenNewReleases: () -> Unit,
    onOpenExplore: () -> Unit,
    onGoToArtist: (String) -> Unit,
    onGoToAlbum: (String) -> Unit,
    onGoToRemotePlaylist: (String, String, String, String?) -> Unit,
) {
    composable(Routes.HOME) {
        HomeScreen(
            onOpenTogether = onOpenTogether,
            onOpenSettings = onOpenSettings,
            onOpenStats = onOpenLibraryStats,
            onOpenHistory = onOpenHistory,
            onPlayTrack = onPlayTrack,
            onOpenPlaylist = onOpenPlaylist,
            onOpenRemotePlaylist = onGoToRemotePlaylist,
        )
    }
    composable(Routes.SEARCH) {
        SearchScreen(
            onOpenBrowse = onOpenBrowse,
            onPlayTrack = onPlayTrack,
            playerViewModel = playerViewModel,
            onGoToArtist = onGoToArtist,
            onGoToAlbum = onGoToAlbum,
            onGoToPlaylist = onGoToRemotePlaylist,
            onOpenCharts = onOpenCharts,
            onOpenNewReleases = onOpenNewReleases,
            onOpenExplore = onOpenExplore,
        )
    }
    composable(Routes.LIBRARY) {
        LibraryScreen(
            onPlayTrack = onPlayTrack,
            playerViewModel = playerViewModel,
            onOpenPlaylist = onOpenPlaylist,
            onOpenCollection = onOpenLibraryCollection,
            onOpenHistory = onOpenHistory,
            onOpenStats = onOpenLibraryStats,
            onOpenStorage = onOpenStorage,
            onImportSharedPlaylist = onImportSharedPlaylist,
            onGoToArtist = onGoToArtist,
            onGoToAlbum = onGoToAlbum,
        )
    }
    composable(Routes.SETTINGS) {
        SettingsScreen(
            onOpenAppearance = onOpenSettingsAppearance,
            onOpenMiniPlayer = onOpenSettingsMiniPlayer,
            onOpenPlayer = onOpenSettingsPlayer,
            onOpenLyrics = onOpenSettingsLyrics,
            onOpenAudio = onOpenSettingsAudio,
            onOpenPlayback = onOpenSettingsPlayback,
            onOpenGeneral = onOpenSettingsGeneral,
            onOpenPrivacy = onOpenSettingsPrivacy,
            onOpenLibrarySections = onOpenSettingsLibrarySections,
            onOpenBackup = onOpenBackup,
            onOpenStorage = onOpenStorage,
            onOpenCrashLogs = onOpenCrashLogs,
            onOpenAbout = onOpenAbout,
            onOpenUpdates = onOpenUpdates,
        )
    }
}

/** Flattens whatever lyrics are currently showing into one copyable block of plain text, or null
 * when there's nothing worth copying (still loading, instrumental, not found, or an error) - the
 * lyrics menu's "Copy lyrics" disables itself in exactly those cases rather than copying a blank
 * string or a UI message. */
private fun LyricsResult?.asCopyableText(): String? = when (this) {
    is LyricsResult.PlainOnly -> text
    is LyricsResult.Synced -> lines.joinToString("\n") { it.text }
    else -> null
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
