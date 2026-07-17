package com.example

import com.example.ui.theme.MusePrimary
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun HomeScreen(
    onPlayTrack: (Track, List<Track>) -> Unit,
    modifier: Modifier = Modifier
) {
    val homeViewModel: HomeViewModel = viewModel()
    val recentlyPlayed by homeViewModel.recentlyPlayed.collectAsState()
    val moodShelves by homeViewModel.moodShelves.collectAsState()

    ThemedBackground(
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 90.dp) // Leave space for Bottom Bar + Player Bar
        ) {
            // Header
            item {
                HomeHeader()
            }

            // Shelf: Recently Played - real playback history from Room, not a fixed mock list.
            item {
                HomeShelf(title = "Recently Played") {
                    ShelfContent(
                        tracks = recentlyPlayed,
                        emptyMessage = "Nothing played yet - your history will show up here.",
                        onPlayTrack = onPlayTrack
                    )
                }
            }

            // Every other shelf: a canned genre/mood search standing in for real
            // recommendations, until this app has an actual recommendation system.
            homeViewModel.shelfTitles.forEach { title ->
                item {
                    HomeShelf(title = title) {
                        ShelfContent(
                            tracks = moodShelves[title],
                            emptyMessage = "Couldn't load \"$title\" right now.",
                            onPlayTrack = onPlayTrack
                        )
                    }
                }
            }
        }
    }
}

/** Shared body for every Home shelf: a shimmer placeholder while [tracks] is null (still
 * loading), a friendly message if it loaded to empty (no results, likely offline), or the real
 * track row otherwise. */
@Composable
private fun ShelfContent(
    tracks: List<Track>?,
    emptyMessage: String,
    onPlayTrack: (Track, List<Track>) -> Unit
) {
    when {
        tracks == null -> ShimmerTrackRow()
        tracks.isEmpty() -> Text(
            text = emptyMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        else -> LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(tracks) { track ->
                TrackCard(track = track, onClick = { onPlayTrack(track, tracks) })
            }
        }
    }
}

/** A row of pulsing placeholder cards, shown in place of a shelf's real content while it loads. */
@Composable
private fun ShimmerTrackRow() {
    val infiniteTransition = rememberInfiniteTransition(label = "shelf_shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)

    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("shelf_shimmer")
    ) {
        repeat(3) {
            Column(modifier = Modifier.width(140.dp)) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(placeholderColor)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(placeholderColor)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(placeholderColor)
                )
            }
        }
    }
}

@Composable
fun HomeHeader() {
    val userProfileViewModel: UserProfileViewModel = viewModel()
    val profile by userProfileViewModel.state.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "MuseFlow",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Discover your rhythm",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {},
                modifier = Modifier.testTag("notification_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = "Notifications",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            // Profile circular avatar
            UserAvatar(
                photoUri = profile.photoUri,
                initials = profile.initials,
                size = 40.dp,
                modifier = Modifier
                    .clickable { }
                    .testTag("profile_avatar")
            )
        }
    }
}

@Composable
fun HomeShelf(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)
        )
        content()
    }
}

@Composable
fun TrackCard(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gradientColors = MusicData.Gradients[track.gradientIndex % MusicData.Gradients.size]

    Column(
        modifier = modifier
            .width(140.dp)
            .clickable { onClick() }
            .testTag("track_card_${track.title.lowercase().replace(" ", "_")}")
    ) {
        // Rounded Album Cover Art, with a play-icon overlay in the bottom-right corner
        Box(modifier = Modifier.size(140.dp)) {
            TrackArtwork(
                imageUrl = track.imageUrl,
                gradientColors = gradientColors,
                modifier = Modifier.fillMaxSize()
            ) {}
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = MusePrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = track.title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = track.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
