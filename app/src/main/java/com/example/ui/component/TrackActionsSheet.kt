package com.example.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Long-press/overflow actions for a track: like, download, add to a playlist.
 *
 * Presented as a modal sheet rather than a dropdown so the same surface works from any list and
 * has room for the track's identity at the top - useful when several search results share a title.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackActionsSheet(
    title: String,
    artist: String,
    isLiked: Boolean,
    isDownloaded: Boolean,
    downloadProgress: Int?,
    onToggleLike: () -> Unit,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
                .testTag("track_actions_sheet"),
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            SheetAction(
                icon = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                label = if (isLiked) "Remove from Liked" else "Add to Liked",
                tint = if (isLiked) MaterialTheme.colorScheme.primary else null,
                onClick = { onToggleLike(); onDismiss() },
                testTag = "action_like",
            )

            when {
                isDownloaded -> SheetAction(
                    icon = Icons.Default.DownloadDone,
                    label = "Downloaded",
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = onDismiss,
                    testTag = "action_downloaded",
                )
                downloadProgress != null -> SheetAction(
                    icon = Icons.Default.Downloading,
                    // -1 is the repository's "started, no percentage yet" sentinel.
                    label = if (downloadProgress >= 0) "Downloading $downloadProgress% - tap to cancel"
                    else "Starting download - tap to cancel",
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = { onCancelDownload(); onDismiss() },
                    testTag = "action_cancel_download",
                )
                else -> SheetAction(
                    icon = Icons.Default.Download,
                    label = "Download",
                    onClick = { onDownload(); onDismiss() },
                    testTag = "action_download",
                )
            }

            SheetAction(
                icon = Icons.Default.PlaylistAdd,
                label = "Add to playlist",
                onClick = { onAddToPlaylist(); onDismiss() },
                testTag = "action_add_to_playlist",
            )
        }
    }
}

@Composable
private fun SheetAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String,
    tint: androidx.compose.ui.graphics.Color? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 18.dp),
        )
    }
}
