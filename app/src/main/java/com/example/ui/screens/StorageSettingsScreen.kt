package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.StorageViewModel
import com.example.ui.component.ActionPreference
import com.example.ui.component.PreferenceGroup
import kotlin.math.ln
import kotlin.math.pow

/**
 * Shows what MuseFlow's downloads and caches take up on disk, and lets the user reclaim it.
 * Downloads and caches are kept as separate figures and separate actions - a download is user
 * data the user chose to keep offline (deleting it loses something), a cache is purely a
 * performance shortcut the app will just rebuild (deleting it loses nothing but a little re-fetch
 * time) - see [StorageViewModel]'s own doc.
 */
@Composable
fun StorageSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: StorageViewModel = viewModel()
    val usage by viewModel.usage.collectAsState()

    var confirmingClearDownloads by remember { mutableStateOf(false) }
    var confirmingClearCache by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .testTag("storage_screen"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("storage_back")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = "Storage",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
        }

        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 200.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PreferenceGroup(title = "Downloads") {
                UsageRow(label = "Downloaded songs", bytes = usage.downloadsBytes, loading = usage.loading)
                ActionPreference(
                    title = "Clear downloads",
                    subtitle = "Deletes every downloaded song's audio file from this device.",
                    icon = Icons.Default.Download,
                    destructive = true,
                    enabled = !usage.loading && usage.downloadsBytes > 0,
                    onClick = { confirmingClearDownloads = true },
                )
            }

            PreferenceGroup(title = "Cache") {
                UsageRow(label = "Streamed audio cache", bytes = usage.streamCacheBytes, loading = usage.loading)
                UsageRow(label = "Artwork cache", bytes = usage.imageCacheBytes, loading = usage.loading)
                ActionPreference(
                    title = "Clear cache",
                    subtitle = "Frees space by clearing cached audio and artwork. Nothing " +
                        "downloaded is affected - everything here is rebuilt automatically as " +
                        "you keep listening.",
                    icon = Icons.Default.CleaningServices,
                    destructive = true,
                    enabled = !usage.loading && usage.cacheBytes > 0,
                    onClick = { confirmingClearCache = true },
                )
            }

            Text(
                text = "Cached audio and artwork speed up playback and browsing you've already " +
                    "done - clearing them just means the next time costs a normal network " +
                    "fetch again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }

    if (confirmingClearDownloads) {
        AlertDialog(
            onDismissRequest = { confirmingClearDownloads = false },
            title = { Text("Clear downloads?") },
            text = {
                Text(
                    "Every downloaded song's audio file is deleted from this device. Your " +
                        "liked songs and playlists aren't affected - you can re-download any " +
                        "track later."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearDownloads()
                        confirmingClearDownloads = false
                    },
                    modifier = Modifier.testTag("storage_clear_downloads_confirm"),
                ) { Text("Clear downloads") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingClearDownloads = false }) { Text("Cancel") }
            },
        )
    }

    if (confirmingClearCache) {
        AlertDialog(
            onDismissRequest = { confirmingClearCache = false },
            title = { Text("Clear cache?") },
            text = {
                Text(
                    "Cached audio and artwork are deleted. Nothing you've downloaded, liked, " +
                        "or saved is affected - the cache just rebuilds itself as you use the app."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearCache()
                        confirmingClearCache = false
                    },
                    modifier = Modifier.testTag("storage_clear_cache_confirm"),
                ) { Text("Clear cache") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingClearCache = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun UsageRow(label: String, bytes: Long, loading: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(
            imageVector = Icons.Default.Storage,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (loading) "…" else formatBytes(bytes),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** "1.2 GB"-style formatting - matches how Android's own Settings app sizes storage. */
private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    val exp = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(1, units.size)
    val value = bytes / 1024.0.pow(exp.toDouble())
    return "%.1f %s".format(value, units[exp - 1])
}
