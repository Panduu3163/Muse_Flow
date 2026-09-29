package com.example.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import com.example.LibrarySection

/**
 * The "..." pill's sheet, opening from the bottom over half the screen rather than a full-screen
 * destination ([ModalBottomSheet], the same floating-half-sheet pattern [ArtistActionsSheet]
 * already uses) - one shared sheet for every Library detail section, whose rows change per
 * [section] instead of one hand-written sheet per section.
 *
 * [onRequestDeleteAllDownloaded] only *asks* to delete - the caller shows its own confirmation
 * (with the red confirm button/blurred backdrop) rather than this sheet deleting anything itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryCollectionActionsSheet(
    section: LibrarySection,
    allDownloaded: Boolean,
    hasDownloadableTracks: Boolean,
    onDownloadAll: () -> Unit,
    onRequestDeleteAllDownloaded: () -> Unit,
    onAddToQueue: () -> Unit,
    onDismiss: () -> Unit,
    onManageDownloads: (() -> Unit)? = null,
    onOpenStats: (() -> Unit)? = null,
    onRescanDevice: (() -> Unit)? = null,
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
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
                .testTag("library_collection_actions_sheet"),
        ) {
            when (section) {
                LibrarySection.Liked, LibrarySection.TopPlayed -> {
                    if (hasDownloadableTracks) {
                        SheetActionRow(
                            icon = if (allDownloaded) Icons.Default.Delete else Icons.Default.Download,
                            label = if (allDownloaded) "Remove all downloads" else "Download all songs",
                            onClick = if (allDownloaded) onRequestDeleteAllDownloaded else onDownloadAll,
                            destructive = allDownloaded,
                        )
                    }
                    SheetActionRow(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to queue", onAddToQueue)
                    if (section == LibrarySection.TopPlayed && onOpenStats != null) {
                        SheetActionRow(Icons.Default.BarChart, "Listening stats", onOpenStats)
                    }
                }
                LibrarySection.Downloads -> {
                    SheetActionRow(Icons.Default.Delete, "Delete all downloads", onRequestDeleteAllDownloaded, destructive = true)
                    SheetActionRow(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to queue", onAddToQueue)
                    if (onManageDownloads != null) {
                        SheetActionRow(Icons.Default.Storage, "Manage downloads", onManageDownloads)
                    }
                }
                LibrarySection.OnDevice -> {
                    SheetActionRow(Icons.AutoMirrored.Filled.PlaylistAdd, "Add to queue", onAddToQueue)
                    if (onRescanDevice != null) {
                        SheetActionRow(Icons.Default.Refresh, "Rescan device", onRescanDevice)
                    }
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun SheetActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .testTag("library_sheet_action_${label.lowercase().replace(" ", "_")}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = color,
            modifier = Modifier.padding(start = 20.dp),
        )
    }
}
