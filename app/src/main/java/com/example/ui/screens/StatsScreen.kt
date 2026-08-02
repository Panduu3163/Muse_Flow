package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ArtistStat
import com.example.CollectionKind
import com.example.StatsViewModel
import com.example.Track
import com.example.downloadKey
import com.example.ui.component.CollectionRow
import com.example.ui.component.TrackRow

/**
 * All-time listening insights, entirely derived from existing playback history - see
 * [StatsViewModel]'s doc for why this is all-time rather than day/week/month like Echo's
 * `StatPeriod`: the history table doesn't keep a timestamped event per play, only a running count
 * and a most-recent timestamp per track.
 */
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    onGoToArtist: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: StatsViewModel = viewModel()
    val totalPlays by viewModel.totalPlays.collectAsState()
    val uniqueTracks by viewModel.uniqueTrackCount.collectAsState()
    val uniqueArtists by viewModel.uniqueArtistCount.collectAsState()
    val uniqueAlbums by viewModel.uniqueAlbumCount.collectAsState()
    val totalMinutes by viewModel.totalListeningMinutes.collectAsState()
    val topArtists by viewModel.topArtists.collectAsState()
    val topTracks by viewModel.topTracks.collectAsState()
    var showSummarySheet by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 200.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Stats",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }

        if (totalPlays == 0) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Play something and your listening stats will show up here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            return@LazyColumn
        }

        item {
            ListeningSummaryButton(
                totalMinutesLabel = totalMinutes.asListeningTimeLabel(),
                onClick = { showSummarySheet = true },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }

        if (topArtists.isNotEmpty()) {
            item {
                SectionHeader("Top artists")
            }
            items(topArtists) { artist ->
                ArtistStatRow(artist, onClick = { artist.artistId?.let(onGoToArtist) })
            }
        }

        if (topTracks.isNotEmpty()) {
            item {
                SectionHeader("Top songs")
            }
            items(topTracks, key = { it.downloadKey() }) { track ->
                TrackRow(
                    title = track.title,
                    artist = track.artist,
                    imageUrl = track.imageUrl,
                    duration = track.duration,
                    onClick = {},
                    onLongClick = {},
                )
            }
        }
    }

    if (showSummarySheet) {
        ListeningSummarySheet(
            onDismiss = { showSummarySheet = false },
            totalMinutes = totalMinutes,
            uniqueSongs = uniqueTracks,
            uniqueArtists = uniqueArtists,
            uniqueAlbums = uniqueAlbums,
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

/** Entry point into [ListeningSummarySheet] - a tappable card rather than the old always-visible
 * stat-tile row, matching Echo's own "Listening Summary" living behind a button rather than
 * permanently on screen. */
@Composable
private fun ListeningSummaryButton(
    totalMinutesLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                Text(
                    text = "Listening Summary",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Text(
                    text = "$totalMinutesLabel total",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
    }
}

/**
 * Echo-Music's `ActivityHistoryBottomSheet` layout (verified against its source), adapted for
 * MuseFlow's all-time-only stats: no per-period breakdown exists here (see [StatsScreen]'s doc),
 * so the hero card and the "All time" row both read the same total rather than a period vs.
 * lifetime split like Echo's own week/month/year picker produces.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListeningSummarySheet(
    onDismiss: () -> Unit,
    totalMinutes: Long,
    uniqueSongs: Int,
    uniqueArtists: Int,
    uniqueAlbums: Int,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Listening Summary",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "All time",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Total Listening Time hero card.
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val durationText = totalMinutes.asListeningTimeLabel()
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "TOTAL LISTENING TIME",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = durationText,
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-1).sp,
                            ),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Box(
                        modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                val segmentBg = MaterialTheme.colorScheme.surfaceContainerHigh
                SummarySegment(
                    icon = Icons.Default.MusicNote,
                    iconTint = MaterialTheme.colorScheme.primary,
                    value = uniqueSongs.toString(),
                    label = "Songs",
                    background = segmentBg,
                    shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp, topEnd = 6.dp, bottomEnd = 6.dp),
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(3.dp))
                SummarySegment(
                    icon = Icons.Default.Person,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    value = uniqueArtists.toString(),
                    label = "Artists",
                    background = segmentBg,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(3.dp))
                SummarySegment(
                    icon = Icons.Default.Album,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    value = uniqueAlbums.toString(),
                    label = "Albums",
                    background = segmentBg,
                    shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp, topStart = 6.dp, bottomStart = 6.dp),
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Column {
                            Text(
                                text = "ALL TIME",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "Total listening time",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = totalMinutes.asListeningTimeLabel(),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SummarySegment(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    value: String,
    label: String,
    background: Color,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.height(112.dp).background(color = background, shape = shape),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 4.dp)) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ArtistStatRow(artist: ArtistStat, onClick: () -> Unit) {
    CollectionRow(
        title = artist.name,
        subtitle = "${artist.plays} play${if (artist.plays == 1) "" else "s"}",
        imageUrl = artist.imageUrl,
        kind = CollectionKind.Artist,
        onClick = onClick,
    )
}

/** "2h 14m"/"14m"/"<1m", same style [totalDurationLabel] uses for a track list's duration sum -
 * kept separate since this sums from a minute count already, not a list of "m:ss" strings. */
private fun Long.asListeningTimeLabel(): String {
    val hours = this / 60
    val minutes = this % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        minutes > 0 -> "${minutes}m"
        else -> "<1m"
    }
}
