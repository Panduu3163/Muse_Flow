package com.example

import android.widget.Toast
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MusePrimary

/**
 * Tap to download [track] for offline playback; tap again to cancel mid-download, or to remove
 * it once complete. Used from Now Playing, Search results, and Album/Artist/Playlist tracklists -
 * everywhere a [Track] carries (or can resolve) a real stream to download from.
 */
@Composable
fun DownloadButton(
    track: Track,
    modifier: Modifier = Modifier,
    tint: Color = Color(0xFFE6E1E5).copy(alpha = 0.8f),
    downloadViewModel: DownloadViewModel = viewModel()
) {
    val downloadedTracks by downloadViewModel.downloadedTracks.collectAsState()
    val downloadingKeys by downloadViewModel.downloadingKeys.collectAsState()
    val downloadingProgress by downloadViewModel.downloadingProgress.collectAsState()
    val failures by downloadViewModel.failures.collectAsState()

    val key = track.downloadKey()
    val isDownloaded = downloadedTracks.any { it.downloadKey() == key }
    val isDownloading = downloadingKeys.contains(key)
    // -1 (or a missing entry, e.g. the instant between tapping and the first progress update)
    // means the total size isn't known yet - an indeterminate spinner, distinct from a real
    // 0-99% that visibly moves and so is distinguishable from one that's stuck.
    val percent = downloadingProgress[key] ?: -1

    // Otherwise a failed download just silently reverts to "not downloaded" with no explanation
    // - overwhelmingly because the connection dropped mid-download.
    val context = LocalContext.current
    LaunchedEffect(failures[key]) {
        if (failures[key] != null) {
            Toast.makeText(context, "Download failed. Check your connection and try again.", Toast.LENGTH_SHORT).show()
        }
    }

    IconButton(
        onClick = {
            when {
                isDownloading -> downloadViewModel.cancel(track)
                isDownloaded -> downloadViewModel.delete(track)
                else -> downloadViewModel.download(track)
            }
        },
        modifier = modifier
    ) {
        when {
            isDownloading && percent in 0..100 -> CircularProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier.size(20.dp),
                color = tint,
                strokeWidth = 2.dp
            )
            isDownloading -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = tint,
                strokeWidth = 2.dp
            )
            isDownloaded -> Icon(
                imageVector = Icons.Default.DownloadDone,
                contentDescription = "Downloaded - tap to remove",
                tint = MusePrimary
            )
            else -> Icon(
                imageVector = Icons.Default.Download,
                contentDescription = "Download for offline playback",
                tint = tint
            )
        }
    }
}
