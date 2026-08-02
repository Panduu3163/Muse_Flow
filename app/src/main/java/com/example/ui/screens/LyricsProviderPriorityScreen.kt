package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.AppSettingsViewModel
import com.example.LyricsProviderId
import kotlin.math.roundToInt

private val ProviderRowHeight = 64.dp

/**
 * Drag-to-reorder list for [LyricsProviderId] - the fallback order [com.example.LyricsViewModel]
 * walks through when fetching lyrics. Reuses the exact drag interaction from the Now Playing
 * queue list (`QueueList` in [NowPlayingScreen]): a dedicated drag handle (not a long-press on the
 * row, so it can never be mistaken for a scroll), other rows sliding to preview the landing gap,
 * and the reorder committed once on drag end rather than continuously - see that composable's doc
 * comment for why. Every change is persisted immediately via [AppSettingsViewModel], the same
 * "live" persistence [LyricsSettingsScreen]'s other preferences use.
 */
@Composable
fun LyricsProviderPriorityScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settingsViewModel: AppSettingsViewModel = viewModel()
    val settings by settingsViewModel.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .testTag("lyrics_provider_order_screen"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("lyrics_provider_order_back")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = "Lyrics provider order",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = "Sources are tried top to bottom until one has lyrics. Drag the handle to " +
                "reorder.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )

        ProviderOrderList(
            order = settings.lyricsProviderOrder,
            onMove = { from, to ->
                val mutable = settings.lyricsProviderOrder.toMutableList()
                val item = mutable.removeAt(from)
                mutable.add(to, item)
                settingsViewModel.setLyricsProviderOrder(mutable)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/** Same drag-to-reorder shape as `NowPlayingScreen`'s `QueueList`, adapted to a flat list of
 * [LyricsProviderId] instead of queue items (there's no dismiss-to-remove here - every provider
 * stays in the list, only its position changes). */
@Composable
private fun ProviderOrderList(
    order: List<LyricsProviderId>,
    onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowHeightPx = with(LocalDensity.current) { ProviderRowHeight.toPx() }

    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }

    val targetIndex = draggingIndex?.let { from ->
        (from + (dragOffsetPx / rowHeightPx).roundToInt()).coerceIn(0, order.lastIndex)
    }

    LazyColumn(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(order, key = { it.name }) { providerId ->
            val index = order.indexOf(providerId)
            val isDragging = draggingIndex == index

            val origin = draggingIndex
            val destination = targetIndex
            val shiftPx = when {
                isDragging || origin == null || destination == null -> 0f
                index in (origin + 1)..destination -> -rowHeightPx
                index in destination..(origin - 1) -> rowHeightPx
                else -> 0f
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ProviderRowHeight)
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (isDragging) dragOffsetPx else shiftPx
                        alpha = if (isDragging) 0.9f else 1f
                    }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = (index + 1).toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Text(
                    text = providerId.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp),
                )
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Reorder ${providerId.label}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("provider_drag_${providerId.name}")
                        .pointerInput(providerId, order.size) {
                            detectDragGestures(
                                onDragStart = {
                                    draggingIndex = order.indexOf(providerId)
                                    dragOffsetPx = 0f
                                },
                                onDragEnd = {
                                    val from = draggingIndex
                                    if (from != null) {
                                        val to = (from + (dragOffsetPx / rowHeightPx).roundToInt())
                                            .coerceIn(0, order.lastIndex)
                                        if (to != from) onMove(from, to)
                                    }
                                    draggingIndex = null
                                    dragOffsetPx = 0f
                                },
                                onDragCancel = {
                                    draggingIndex = null
                                    dragOffsetPx = 0f
                                },
                            ) { change, dragAmount ->
                                change.consume()
                                dragOffsetPx += dragAmount.y
                            }
                        },
                )
            }
        }
    }
}
