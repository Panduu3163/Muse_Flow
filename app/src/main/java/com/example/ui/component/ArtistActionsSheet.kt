package com.example.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Share
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
 * Long-press/overflow actions for an artist - the "entirely missing" menu the gap audit flagged.
 * Follow already has its own prominent header button on [com.example.ui.screens.ArtistScreen]; this
 * menu is for the less common actions that don't deserve permanent screen space.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistActionsSheet(
    name: String,
    imageUrl: String?,
    isFollowed: Boolean,
    onToggleFollow: () -> Unit,
    onStartRadio: () -> Unit,
    onDismiss: () -> Unit,
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
                .testTag("artist_actions_sheet"),
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
                        .clip(RoundedCornerShape(26.dp))
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
                    }
                }
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            ArtistSheetAction(
                icon = Icons.Default.Radio,
                label = "Start radio",
                supporting = "Create a station based on this artist",
                onClick = { onStartRadio(); onDismiss() },
                testTag = "artist_action_radio",
            )
            ArtistSheetAction(
                icon = if (isFollowed) Icons.Default.PersonRemove else Icons.Default.PersonAdd,
                label = if (isFollowed) "Unfollow" else "Follow",
                supporting = "Get notified about new releases",
                onClick = { onToggleFollow(); onDismiss() },
                testTag = "artist_action_toggle_follow",
            )
            onShare?.let { share ->
                ArtistSheetAction(
                    icon = Icons.Default.Share,
                    label = "Share",
                    onClick = { share(); onDismiss() },
                    testTag = "artist_action_share",
                )
            }
        }
    }
}

@Composable
private fun ArtistSheetAction(
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
