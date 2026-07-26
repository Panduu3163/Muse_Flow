package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
    val totalMinutes by viewModel.totalListeningMinutes.collectAsState()
    val topArtists by viewModel.topArtists.collectAsState()
    val topTracks by viewModel.topTracks.collectAsState()

    LazyColumn(
        modifier = modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 140.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Stats",
                    style = MaterialTheme.typography.headlineSmall,
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatTile("Plays", totalPlays.toString(), Modifier.weight(1f))
                StatTile("Songs", uniqueTracks.toString(), Modifier.weight(1f))
                StatTile("Artists", uniqueArtists.toString(), Modifier.weight(1f))
                StatTile("Time", totalMinutes.asListeningTimeLabel(), Modifier.weight(1f))
            }
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
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = value, style = MaterialTheme.typography.titleLarge)
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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

/** "2 hr 14 min"/"14 min"/"Less than a minute", same style [totalDurationLabel] uses for a track
 * list's duration sum - kept separate since this sums from a minute count already, not a list of
 * "m:ss" strings. */
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
