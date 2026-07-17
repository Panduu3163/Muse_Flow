package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        setContent {
            MyApplicationTheme {
                MainLayout()
            }
        }
    }

    // Android 13+ requires runtime consent to show any notification, including the playback
    // MediaStyle one - without it the foreground service still runs, but silently.
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

// Settings sub-screens slide horizontally, like a classic drill-down list.
private val settingsEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300))
}
private val settingsExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
}
private val settingsPopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300))
}
private val settingsPopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
}

// Bottom-nav tabs simply cross-fade into each other.
private val tabEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    fadeIn(animationSpec = tween(200))
}
private val tabExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    fadeOut(animationSpec = tween(200))
}

// Now Playing always slides up/down regardless of push vs. pop direction.
private val nowPlayingEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInVertically(initialOffsetY = { it }, animationSpec = tween(400)) + fadeIn(animationSpec = tween(400))
}
private val nowPlayingExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutVertically(targetOffsetY = { it }, animationSpec = tween(350)) + fadeOut(animationSpec = tween(350))
}

/**
 * Root composable: shows onboarding on first launch (gated until the persisted
 * [UserProfileState] has actually loaded, so returning users never see a flash of onboarding
 * before their `hasSeenOnboarding = true` is read from DataStore), otherwise the real app.
 */
@Composable
fun MainLayout() {
    val userProfileViewModel: UserProfileViewModel = viewModel()
    val userProfileState by userProfileViewModel.state.collectAsState()

    when {
        !userProfileState.isLoaded -> {
            // Briefly blank while DataStore loads (typically a single frame) rather than
            // flashing the onboarding UI for a returning user.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            )
        }
        !userProfileState.hasSeenOnboarding -> {
            OnboardingFlow(
                onComplete = { displayName, photoUri ->
                    userProfileViewModel.completeOnboarding(displayName, photoUri)
                }
            )
        }
        else -> {
            MainApp()
        }
    }
}

@Composable
private fun MainApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // Every viewModel() call inside NavHost's composable{} blocks is, by default, scoped to
    // that back stack entry (destroyed when the entry is popped) - not the Activity. Several
    // screens (e.g. ThemedBackground) rely on ThemeViewModel/AppSettingsViewModel being a single
    // Activity-wide instance so the whole app reacts together to a theme change. Capturing the
    // Activity-level owner here (outside NavHost) and re-providing it around NavHost's content
    // restores that sharing without threading the ViewModels through every screen's parameters.
    val activityViewModelStoreOwner = requireNotNull(LocalViewModelStoreOwner.current) {
        "MainLayout must be composed within a ViewModelStoreOwner (e.g. a ComponentActivity)"
    }

    // Global player state, backed by a MediaController talking to the always-running
    // PlaybackService - so playback (and this state) keeps going in the background, not just
    // while MainApp is in composition.
    val playerViewModel: PlayerViewModel = viewModel()
    val playerState by playerViewModel.uiState.collectAsState()
    val activeTrack = playerState.track
    val isPlaying = playerState.isPlaying
    val playbackProgress = playerState.progress

    // Local, unpersisted Settings toggles. Hoisted here (rather than inside SettingsScreen)
    // because each settings sub-screen is now its own NavHost destination - a separate
    // composable() entry - so state that needs to survive navigating between them can't live
    // inside any single one.
    var offlineMode by remember { mutableStateOf(false) }
    var crossfade by remember { mutableStateOf(true) }
    var gaplessPlayback by remember { mutableStateOf(true) }
    var pauseOnMute by remember { mutableStateOf(false) }
    var resumeOnBluetooth by remember { mutableStateOf(true) }
    var hideVideoContent by remember { mutableStateOf(false) }

    val onPlayTrack: (Track) -> Unit = { playerViewModel.playTrack(it) }
    // Search results form their own queue (the current results list), rather than the mock
    // catalog Home/Library browse - so next/previous cycle through what was actually searched.
    val onPlaySearchResult: (Track, List<Track>) -> Unit = { track, queue -> playerViewModel.playTrack(track, queue) }

    // Now Playing is a full destination, not a bottom-bar tab; hide the nav bar/mini-player
    // while it's showing, same as the previous overlay-based design.
    val showBottomBar = currentRoute != Routes.NOW_PLAYING
    val selectedTab = when {
        currentRoute == null -> MuseTab.Home
        currentRoute.startsWith("settings") -> MuseTab.Settings
        else -> MuseTab.entries.find { it.route == currentRoute } ?: MuseTab.Home
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                Column {
                    // Persistent Compact Music Player (floating above navigation)
                    activeTrack?.let { track ->
                        CompactPlayer(
                            track = track,
                            isPlaying = isPlaying,
                            progress = playbackProgress,
                            onPlayPauseToggle = { playerViewModel.togglePlayPause() },
                            onClose = { playerViewModel.stopPlayback() },
                            onClick = { navController.navigate(Routes.NOW_PLAYING) }
                        )
                    }

                    // Navigation Bar
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp,
                        modifier = Modifier.testTag("bottom_nav_bar")
                    ) {
                        fun navigateToTab(tab: MuseTab) {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }

                        NavigationBarItem(
                            selected = selectedTab == MuseTab.Home,
                            onClick = { navigateToTab(MuseTab.Home) },
                            icon = {
                                Icon(
                                    imageVector = if (selectedTab == MuseTab.Home) Icons.Default.Home else Icons.Outlined.Home,
                                    contentDescription = "Home"
                                )
                            },
                            label = { Text("Home") },
                            modifier = Modifier.testTag("nav_tab_home")
                        )
                        NavigationBarItem(
                            selected = selectedTab == MuseTab.Search,
                            onClick = { navigateToTab(MuseTab.Search) },
                            icon = {
                                Icon(
                                    imageVector = if (selectedTab == MuseTab.Search) Icons.Default.Search else Icons.Outlined.Search,
                                    contentDescription = "Search"
                                )
                            },
                            label = { Text("Search") },
                            modifier = Modifier.testTag("nav_tab_search")
                        )
                        NavigationBarItem(
                            selected = selectedTab == MuseTab.Library,
                            onClick = { navigateToTab(MuseTab.Library) },
                            icon = {
                                Icon(
                                    imageVector = if (selectedTab == MuseTab.Library) Icons.Default.LibraryMusic else Icons.Outlined.LibraryMusic,
                                    contentDescription = "Library"
                                )
                            },
                            label = { Text("Library") },
                            modifier = Modifier.testTag("nav_tab_library")
                        )
                        NavigationBarItem(
                            selected = selectedTab == MuseTab.Settings,
                            onClick = { navigateToTab(MuseTab.Settings) },
                            icon = {
                                Icon(
                                    imageVector = if (selectedTab == MuseTab.Settings) Icons.Default.Settings else Icons.Outlined.Settings,
                                    contentDescription = "Settings"
                                )
                            },
                            label = { Text("Settings") },
                            modifier = Modifier.testTag("nav_tab_settings")
                        )
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        CompositionLocalProvider(LocalViewModelStoreOwner provides activityViewModelStoreOwner) {
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(Routes.HOME, enterTransition = tabEnter, exitTransition = tabExit) {
                    Box(Modifier.padding(innerPadding)) {
                        HomeScreen(onPlayTrack = onPlayTrack)
                    }
                }
                composable(Routes.SEARCH, enterTransition = tabEnter, exitTransition = tabExit) {
                    Box(Modifier.padding(innerPadding)) {
                        SearchScreen(onPlayTrack = onPlaySearchResult)
                    }
                }
                composable(Routes.LIBRARY, enterTransition = tabEnter, exitTransition = tabExit) {
                    Box(Modifier.padding(innerPadding)) {
                        LibraryScreen(onPlayTrack = onPlayTrack)
                    }
                }
                composable(Routes.SETTINGS, enterTransition = tabEnter, exitTransition = tabExit) {
                    Box(Modifier.padding(innerPadding)) {
                        SettingsScreen(navController = navController)
                    }
                }

                composable(
                    Routes.SETTINGS_ACCOUNT,
                    enterTransition = settingsEnter,
                    exitTransition = settingsExit,
                    popEnterTransition = settingsPopEnter,
                    popExitTransition = settingsPopExit
                ) {
                    Box(Modifier.padding(innerPadding)) {
                        AccountScreen(
                            onEditProfile = { navController.navigate(Routes.SETTINGS_ACCOUNT_EDIT) },
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
                composable(
                    Routes.SETTINGS_ACCOUNT_EDIT,
                    enterTransition = settingsEnter,
                    exitTransition = settingsExit,
                    popEnterTransition = settingsPopEnter,
                    popExitTransition = settingsPopExit
                ) {
                    Box(Modifier.padding(innerPadding)) {
                        EditProfileScreen(onBack = { navController.popBackStack() })
                    }
                }
                composable(
                    Routes.SETTINGS_APPEARANCE,
                    enterTransition = settingsEnter,
                    exitTransition = settingsExit,
                    popEnterTransition = settingsPopEnter,
                    popExitTransition = settingsPopExit
                ) {
                    val themeViewModel: ThemeViewModel = viewModel()
                    val themeState by themeViewModel.themeState.collectAsState()
                    val appSettingsViewModel: AppSettingsViewModel = viewModel()
                    val appSettings by appSettingsViewModel.state.collectAsState()
                    Box(Modifier.padding(innerPadding)) {
                        AppearanceSettingsScreen(
                            themeState = themeState,
                            onOpenTheme = { navController.navigate(Routes.SETTINGS_THEME) },
                            appSettings = appSettings,
                            appSettingsViewModel = appSettingsViewModel,
                            hideVideoContent = hideVideoContent,
                            onHideVideoContentChange = { hideVideoContent = it },
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
                composable(
                    Routes.SETTINGS_PLAYER_AUDIO,
                    enterTransition = settingsEnter,
                    exitTransition = settingsExit,
                    popEnterTransition = settingsPopEnter,
                    popExitTransition = settingsPopExit
                ) {
                    val appSettingsViewModel: AppSettingsViewModel = viewModel()
                    val appSettings by appSettingsViewModel.state.collectAsState()
                    Box(Modifier.padding(innerPadding)) {
                        PlayerAudioSettingsScreen(
                            offlineMode = offlineMode,
                            onOfflineModeChange = { offlineMode = it },
                            crossfade = crossfade,
                            onCrossfadeChange = { crossfade = it },
                            gaplessPlayback = gaplessPlayback,
                            onGaplessPlaybackChange = { gaplessPlayback = it },
                            pauseOnMute = pauseOnMute,
                            onPauseOnMuteChange = { pauseOnMute = it },
                            resumeOnBluetooth = resumeOnBluetooth,
                            onResumeOnBluetoothChange = { resumeOnBluetooth = it },
                            appSettings = appSettings,
                            appSettingsViewModel = appSettingsViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
                composable(
                    Routes.SETTINGS_LYRICS,
                    enterTransition = settingsEnter,
                    exitTransition = settingsExit,
                    popEnterTransition = settingsPopEnter,
                    popExitTransition = settingsPopExit
                ) {
                    val appSettingsViewModel: AppSettingsViewModel = viewModel()
                    val appSettings by appSettingsViewModel.state.collectAsState()
                    Box(Modifier.padding(innerPadding)) {
                        LyricsSettingsScreen(
                            appSettings = appSettings,
                            appSettingsViewModel = appSettingsViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
                composable(
                    Routes.SETTINGS_LIBRARY_PLAYLISTS,
                    enterTransition = settingsEnter,
                    exitTransition = settingsExit,
                    popEnterTransition = settingsPopEnter,
                    popExitTransition = settingsPopExit
                ) {
                    val appSettingsViewModel: AppSettingsViewModel = viewModel()
                    val appSettings by appSettingsViewModel.state.collectAsState()
                    Box(Modifier.padding(innerPadding)) {
                        LibraryPlaylistsSettingsScreen(
                            appSettings = appSettings,
                            appSettingsViewModel = appSettingsViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
                composable(
                    Routes.SETTINGS_LISTEN_TOGETHER,
                    enterTransition = settingsEnter,
                    exitTransition = settingsExit,
                    popEnterTransition = settingsPopEnter,
                    popExitTransition = settingsPopExit
                ) {
                    Box(Modifier.padding(innerPadding)) {
                        ListenTogetherScreen(onBack = { navController.popBackStack() })
                    }
                }
                composable(
                    Routes.SETTINGS_STORAGE,
                    enterTransition = settingsEnter,
                    exitTransition = settingsExit,
                    popEnterTransition = settingsPopEnter,
                    popExitTransition = settingsPopExit
                ) {
                    Box(Modifier.padding(innerPadding)) {
                        StorageScreen(onBack = { navController.popBackStack() })
                    }
                }
                composable(
                    Routes.SETTINGS_UPTIME,
                    enterTransition = settingsEnter,
                    exitTransition = settingsExit,
                    popEnterTransition = settingsPopEnter,
                    popExitTransition = settingsPopExit
                ) {
                    Box(Modifier.padding(innerPadding)) {
                        ServiceUptimeScreen(onBack = { navController.popBackStack() })
                    }
                }
                composable(
                    Routes.SETTINGS_ABOUT,
                    enterTransition = settingsEnter,
                    exitTransition = settingsExit,
                    popEnterTransition = settingsPopEnter,
                    popExitTransition = settingsPopExit
                ) {
                    Box(Modifier.padding(innerPadding)) {
                        AboutScreen(onBack = { navController.popBackStack() })
                    }
                }
                composable(
                    Routes.SETTINGS_THEME,
                    enterTransition = settingsEnter,
                    exitTransition = settingsExit,
                    popEnterTransition = settingsPopEnter,
                    popExitTransition = settingsPopExit
                ) {
                    val themeViewModel: ThemeViewModel = viewModel()
                    val themeState by themeViewModel.themeState.collectAsState()
                    Box(Modifier.padding(innerPadding)) {
                        ThemeSettingsScreen(
                            themeState = themeState,
                            onSelectMode = { themeViewModel.setBackgroundMode(it) },
                            onOpenPalette = { navController.navigate(Routes.SETTINGS_PALETTE) },
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
                composable(
                    Routes.SETTINGS_PALETTE,
                    enterTransition = settingsEnter,
                    exitTransition = settingsExit,
                    popEnterTransition = settingsPopEnter,
                    popExitTransition = settingsPopExit
                ) {
                    val themeViewModel: ThemeViewModel = viewModel()
                    val themeState by themeViewModel.themeState.collectAsState()
                    Box(Modifier.padding(innerPadding)) {
                        PalettePickerScreen(
                            themeState = themeState,
                            onSelectPalette = { themeViewModel.setPalette(it) },
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
                composable(
                    Routes.SETTINGS_PROVIDER_TEST,
                    enterTransition = settingsEnter,
                    exitTransition = settingsExit,
                    popEnterTransition = settingsPopEnter,
                    popExitTransition = settingsPopExit
                ) {
                    Box(Modifier.padding(innerPadding)) {
                        YouTubeMusicTestScreen(onBack = { navController.popBackStack() })
                    }
                }

                composable(
                    Routes.NOW_PLAYING,
                    enterTransition = nowPlayingEnter,
                    exitTransition = nowPlayingExit,
                    popEnterTransition = nowPlayingEnter,
                    popExitTransition = nowPlayingExit
                ) {
                    // Deliberately ignores innerPadding - NowPlayingScreen manages its own
                    // status/navigation bar insets and should be truly edge-to-edge, same as
                    // when it was rendered as a sibling overlay before this NavHost existed.
                    activeTrack?.let { track ->
                        NowPlayingScreen(
                            track = track,
                            isPlaying = isPlaying,
                            progress = playbackProgress,
                            positionMs = playerState.positionMs,
                            onProgressChange = { playerViewModel.seekTo(it) },
                            onPlayPauseToggle = { playerViewModel.togglePlayPause() },
                            onClose = { navController.popBackStack() },
                            onNext = { playerViewModel.skipNext() },
                            onPrevious = { playerViewModel.skipPrevious() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CompactPlayer(
    track: Track,
    isPlaying: Boolean,
    progress: Float,
    onPlayPauseToggle: () -> Unit,
    onClose: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gradientColors = MusicData.Gradients[track.gradientIndex % MusicData.Gradients.size]

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable { onClick() }
            .testTag("compact_player"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Track Small Cover Art
                TrackArtwork(
                    imageUrl = track.imageUrl,
                    gradientColors = gradientColors,
                    modifier = Modifier.size(44.dp)
                ) {
                    Text("🎵", fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))

                // Track title & artist
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Playback Control Buttons
                IconButton(onClick = onPlayPauseToggle) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss Player",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Player Progress Bar, driven by the shared playback position
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                            )
                        )
                )
            }
        }
    }
}
