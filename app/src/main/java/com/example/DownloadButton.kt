package com.example

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MusePrimary

/**
 * Tap to download [track] for offline playback; tap again to cancel mid-download, or to remove
 * it once complete. Shared by Now Playing (the currently playing track) and Search results (any
 * searched track) - both the only two places a [Track] is guaranteed to carry a real
 * [Track.streamUrl] to download from.
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

    val key = track.downloadKey()
    val isDownloaded = downloadedTracks.any { it.downloadKey() == key }
    val isDownloading = downloadingKeys.contains(key)

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
