package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController

data class ServiceProvider(
    val name: String,
    val url: String,
    val isOnline: Boolean,
    val latency: String = ""
)

/** The top-level Settings list. Each row navigates to its own [Routes] destination. */
@Composable
fun SettingsScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    val themeViewModel: ThemeViewModel = viewModel()
    val themeState by themeViewModel.themeState.collectAsState()

    ThemedBackground(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 90.dp) // Leave room for bottom compact player and navigation bar
        ) {
            // Header Title
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 16.dp)
            )

            SettingsActionRow(
                icon = Icons.Default.AccountCircle,
                title = "Account",
                subtitle = "Profile and account details",
                onClick = { navController.navigate(Routes.SETTINGS_ACCOUNT) },
                tag = "action_account"
            )
            SettingsActionRow(
                icon = Icons.Default.Palette,
                title = "Appearance",
                subtitle = when (themeState.backgroundMode) {
                    BackgroundMode.Amoled -> "AMOLED Black · player look, layout density"
                    BackgroundMode.Gradient -> "Gradient · ${themeState.palette.label}"
                },
                onClick = { navController.navigate(Routes.SETTINGS_APPEARANCE) },
                tag = "action_appearance"
            )
            SettingsActionRow(
                icon = Icons.Default.PlayCircle,
                title = "Player & Audio",
                subtitle = "Playback behavior and Now Playing controls",
                onClick = { navController.navigate(Routes.SETTINGS_PLAYER_AUDIO) },
                tag = "action_player_audio"
            )
            SettingsActionRow(
                icon = Icons.Default.Lyrics,
                title = "Lyrics",
                subtitle = "Lyrics appearance and scrolling behavior",
                onClick = { navController.navigate(Routes.SETTINGS_LYRICS) },
                tag = "action_lyrics"
            )
            SettingsActionRow(
                icon = Icons.Default.LibraryMusic,
                title = "Library & Playlists",
                subtitle = "Default tabs, swipe actions, auto playlists",
                onClick = { navController.navigate(Routes.SETTINGS_LIBRARY_PLAYLISTS) },
                tag = "action_library_playlists"
            )
            SettingsActionRow(
                icon = Icons.Default.Group,
                title = "Listen Together",
                subtitle = "Synced group listening sessions",
                onClick = { navController.navigate(Routes.SETTINGS_LISTEN_TOGETHER) },
                tag = "action_listen_together"
            )
            SettingsActionRow(
                icon = Icons.Default.Cloud,
                title = "Storage",
                subtitle = "Cache, downloads, and storage usage",
                onClick = { navController.navigate(Routes.SETTINGS_STORAGE) },
                tag = "action_storage"
            )
            SettingsActionRow(
                icon = Icons.Default.Dns,
                title = "Service Uptime",
                subtitle = "Check online status of all API & metadata providers",
                onClick = { navController.navigate(Routes.SETTINGS_UPTIME) },
                tag = "action_service_uptime"
            )
            SettingsActionRow(
                icon = Icons.Default.Info,
                title = "About",
                subtitle = "App version, developer info, and more",
                onClick = { navController.navigate(Routes.SETTINGS_ABOUT) },
                tag = "action_about"
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Temporary dev-only screen for testing Provider implementations. Safe to remove.
            SettingsGroupHeader(title = "Developer")
            SettingsActionRow(
                icon = Icons.Default.BugReport,
                title = "Provider Test (temporary)",
                subtitle = "Search & play a real song via YouTube Music or JioSaavn",
                onClick = { navController.navigate(Routes.SETTINGS_PROVIDER_TEST) },
                tag = "action_yt_music_test"
            )
        }
    }
}

@Composable
fun ServiceUptimeScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val musicProviders = remember {
        listOf(
            ServiceProvider("YouTube Music", "https://music.youtube.com", true, "238ms"),
            ServiceProvider("JioSaavn", "Server 3", true, "223ms"),
            ServiceProvider("Qobuz", "https://qobuz.kennyy.com.br", false)
        )
    }

    val canvasProviders = remember {
        listOf(
            ServiceProvider("Echo Canvas", "https://canvas.echomusic.fun", true, "403ms"),
            ServiceProvider("Tidal Canvas", "https://api.tidal.com/v1/", true, "827ms")
        )
    }

    val lyricsProviders = remember {
        listOf(
            ServiceProvider("LRCLib", "https://lrclib.net", true, "1176ms"),
            ServiceProvider("BetterLyrics", "https://lyrics-api.boidu.dev", true, "265ms"),
            ServiceProvider("Unison", "https://unison.boidu.dev", true, "286ms"),
            ServiceProvider("Paxsenix", "https://lyrics.paxsenix.org", true, "276ms"),
            ServiceProvider("KuGou", "https://lyrics.kugou.com", true, "624ms"),
            ServiceProvider("YouLyPlus", "https://lyricsplus.prjktla.my.id", true, "178ms"),
            ServiceProvider("SimpMusic", "https://api-lyrics.simpmusic.org", true, "664ms")
        )
    }

    val otherServices = remember {
        listOf(
            ServiceProvider("Apple Music API", "https://amp-api.music.apple.com", true, "535ms"),
            ServiceProvider("Echo Find/Shazam", "https://amp.shazam.com", true, "2602ms")
        )
    }

    ThemedBackground(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Header Bar with Back Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 8.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("uptime_back_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back to Settings",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Service Uptime",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 90.dp) // Spacer for compact player
            ) {
                Text(
                    text = "Real-time connection latency and status check of integrated stream scrapers, lyrics sync databases, and audio backends.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 20.dp, top = 8.dp)
                )

                // Music Providers
                UptimeSectionHeader(title = "Music Providers")
                musicProviders.forEach { provider ->
                    ProviderUptimeCard(provider = provider)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Canvas Providers
                UptimeSectionHeader(title = "Canvas Providers")
                canvasProviders.forEach { provider ->
                    ProviderUptimeCard(provider = provider)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Lyrics Providers
                UptimeSectionHeader(title = "Lyrics Providers")
                lyricsProviders.forEach { provider ->
                    ProviderUptimeCard(provider = provider)
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Other Services
                UptimeSectionHeader(title = "Other Services")
                otherServices.forEach { provider ->
                    ProviderUptimeCard(provider = provider)
                }
            }
        }
    }
}

@Composable
fun UptimeSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 0.5.sp
        ),
        modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
    )
}

@Composable
fun SettingsGroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 0.5.sp
        ),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF090A0F),
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.testTag("${tag}_switch")
        )
    }
}

@Composable
fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = "Navigate to item",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
fun ProviderUptimeCard(provider: ServiceProvider) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .testTag("provider_card_${provider.name.lowercase().replace(" ", "_")}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = provider.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = provider.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Status Pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (provider.isOnline) Color(0xFF1B3B2B) else Color(0xFF3C1F1F)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (provider.isOnline) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (provider.isOnline) Color(0xFF81C784) else Color(0xFFE57373),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = if (provider.isOnline) "Online (${provider.latency})" else "Offline",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (provider.isOnline) Color(0xFF81C784) else Color(0xFFE57373)
                )
            }
        }
    }
}
