package com.example.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Long-press/overflow actions for an album - the menu the audit's "album menu: entirely missing"
 * gap was tracking. Album-scoped (shuffle/radio/download-all operate on every track at once), same
 * split from [TrackActionsSheet] that [PlaylistActionsSheet] already draws.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumActionsSheet(
    title: String,
    artist: String?,
    imageUrl: String?,
    songCount: Int,
    onShuffle: () -> Unit,
    onStartRadio: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onDownloadAll: () -> Unit,
    onDismiss: () -> Unit,
    /** Absent when the album has no artist browseId to navigate to. */
    onGoToArtist: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(
            topStart = com.example.ui.theme.MuseFlowShapes.sheet,
            topEnd = com.example.ui.theme.MuseFlowShapes.sheet,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
                .testTag("album_actions_sheet"),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    if (imageUrl != null) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(52.dp),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Album,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "$songCount ${if (songCount == 1) "song" else "songs"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SheetDividerAlbum()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                AlbumActionPill(
                    icon = Icons.Default.Shuffle,
                    label = "Shuffle",
                    onClick = { onShuffle(); onDismiss() },
                    testTag = "album_action_shuffle",
                )
            }

            SheetDividerAlbum()

            AlbumSheetAction(
                icon = Icons.Default.Radio,
                label = "Start radio",
                supporting = "Create a station based on this album",
                onClick = { onStartRadio(); onDismiss() },
                testTag = "album_action_radio",
            )
            AlbumSheetAction(
                icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                label = "Play next",
                supporting = "Add to the top of your queue",
                onClick = { onPlayNext(); onDismiss() },
                testTag = "album_action_play_next",
            )
            AlbumSheetAction(
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                label = "Add to queue",
                supporting = "Add to the bottom of your queue",
                onClick = { onAddToQueue(); onDismiss() },
                testTag = "album_action_add_to_queue",
            )
            AlbumSheetAction(
                icon = Icons.Default.Download,
                label = "Download",
                supporting = "Make every song available offline",
                onClick = { onDownloadAll(); onDismiss() },
                testTag = "album_action_download",
            )
            onGoToArtist?.let { goToArtist ->
                AlbumSheetAction(
                    icon = Icons.Default.Person,
                    label = "View artist",
                    supporting = artist,
                    onClick = { goToArtist(); onDismiss() },
                    testTag = "album_action_view_artist",
                )
            }
            onShare?.let { share ->
                AlbumSheetAction(
                    icon = Icons.Default.Share,
                    label = "Share",
                    onClick = { share(); onDismiss() },
                    testTag = "album_action_share",
                )
            }
        }
    }
}

@Composable
private fun SheetDividerAlbum() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun AlbumActionPill(icon: ImageVector, label: String, onClick: () -> Unit, testTag: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun AlbumSheetAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String,
    supporting: String? = null,
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
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.padding(start = 18.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
