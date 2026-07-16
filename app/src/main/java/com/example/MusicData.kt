package com.example

import androidx.compose.ui.graphics.Color

data class Track(
    val title: String,
    val artist: String,
    val album: String,
    val duration: String,
    val plays: String,
    val gradientIndex: Int
)

/** Parses a "m:ss" duration string into total seconds, falling back to 210s (3:30) if malformed. */
fun parseDurationToSeconds(duration: String): Int {
    return try {
        val parts = duration.split(":")
        if (parts.size == 2) {
            parts[0].toInt() * 60 + parts[1].toInt()
        } else 210
    } catch (e: Exception) {
        210
    }
}

data class Album(
    val title: String,
    val artist: String,
    val year: String,
    val trackCount: Int,
    val gradientIndex: Int
)

data class Artist(
    val name: String,
    val followers: String,
    val gradientIndex: Int
)

data class Playlist(
    val title: String,
    val trackCount: Int,
    val creator: String,
    val gradientIndex: Int
)

object MusicData {
    // A palette of premium, vibrant gradients suitable for the Immersive UI design theme
    val Gradients = listOf(
        listOf(Color(0xFFD0BCFF), Color(0xFF381E72)), // Lavender to Dark Violet
        listOf(Color(0xFF625B71), Color(0xFF1C1B1F)), // Muted Slate to Dark Grey
        listOf(Color(0xFF21005D), Color(0xFFEADDFF)), // Indigo to Soft Purple
        listOf(Color(0xFF4F378B), Color(0xFFD0BCFF)), // Medium Violet to Lavender
        listOf(Color(0xFF1C1B1F), Color(0xFFE6E1E5)), // Dark Grey to Soft Off-white
        listOf(Color(0xFF381E72), Color(0xFF4F378B)), // Deep Violet to Medium Violet
        listOf(Color(0xFFD0BCFF), Color(0xFF4F378B), Color(0xFF1C1B1F)), // Triple Gradient Glow
        listOf(Color(0xFF625B71), Color(0xFF381E72)), // Slate to Deep Violet
        listOf(Color(0xFF21005D), Color(0xFF4F378B)), // Indigo to Medium Violet
        listOf(Color(0xFF1C1B1F), Color(0xFF381E72))  // Dark Grey to Deep Violet
    )

    val tracks = listOf(
        Track("Midnight City", "M83", "Hurry Up, We're Dreaming", "4:03", "142M", 0),
        Track("Blinding Lights", "The Weeknd", "After Hours", "3:20", "2.1B", 1),
        Track("Intro", "The xx", "xx", "2:08", "89M", 2),
        Track("Get Lucky", "Daft Punk", "Random Access Memories", "4:08", "450M", 3),
        Track("Starboy", "The Weeknd", "Starboy", "3:50", "1.2B", 4),
        Track("Lose Yourself", "Eminem", "8 Mile", "5:26", "980M", 5),
        Track("Stairway to Heaven", "Led Zeppelin", "Led Zeppelin IV", "8:02", "55M", 6),
        Track("Bohemian Rhapsody", "Queen", "A Night at the Opera", "5:55", "1.1B", 7),
        Track("Instant Destiny", "Tame Impala", "The Slow Rush", "3:13", "34M", 8),
        Track("Bad Guy", "Billie Eilish", "When We All Fall Asleep", "3:14", "1.4B", 9)
    )

    val albums = listOf(
        Album("After Hours", "The Weeknd", "2020", 14, 1),
        Album("Random Access Memories", "Daft Punk", "2013", 13, 3),
        Album("Hurry Up, We're Dreaming", "M83", "2011", 22, 0),
        Album("Fine Line", "Harry Styles", "2019", 12, 4),
        Album("Discovery", "Daft Punk", "2001", 14, 2),
        Album("Currents", "Tame Impala", "2015", 13, 8),
        Album("The Slow Rush", "Tame Impala", "2020", 12, 9),
        Album("When We All Fall Asleep", "Billie Eilish", "2019", 14, 6)
    )

    val artists = listOf(
        Artist("The Weeknd", "72.4M followers", 1),
        Artist("Daft Punk", "15.8M followers", 3),
        Artist("Tame Impala", "8.2M followers", 8),
        Artist("Billie Eilish", "54.1M followers", 9),
        Artist("Coldplay", "45.9M followers", 5),
        Artist("Eminem", "61.3M followers", 2),
        Artist("M83", "3.4M followers", 0),
        Artist("Queen", "38.2M followers", 7)
    )

    val playlists = listOf(
        Playlist("Late Night Vibes", 45, "MuseFlow", 2),
        Playlist("Chill Lo-Fi Beats", 120, "Lofi Girl", 6),
        Playlist("Workout Energy", 62, "MuseFlow", 4),
        Playlist("Focus & Flow", 88, "Deep Work", 5),
        Playlist("Discover Weekly", 30, "MuseFlow", 0),
        Playlist("Coding Session", 145, "Developer", 8)
    )
    
    // Playlists specifically for Liked, Downloads, Recently Played
    val likedSongs = listOf(
        Track("Blinding Lights", "The Weeknd", "After Hours", "3:20", "2.1B", 1),
        Track("Get Lucky", "Daft Punk", "Random Access Memories", "4:08", "450M", 3),
        Track("Intro", "The xx", "xx", "2:08", "89M", 2),
        Track("Bad Guy", "Billie Eilish", "When We All Fall Asleep", "3:14", "1.4B", 9)
    )

    val downloadedSongs = listOf(
        Track("Midnight City", "M83", "Hurry Up, We're Dreaming", "4:03", "142M", 0),
        Track("Starboy", "The Weeknd", "Starboy", "3:50", "1.2B", 4),
        Track("Lose Yourself", "Eminem", "8 Mile", "5:26", "980M", 5)
    )

    val recentlyPlayedSongs = listOf(
        Track("Instant Destiny", "Tame Impala", "The Slow Rush", "3:13", "34M", 8),
        Track("Bohemian Rhapsody", "Queen", "A Night at the Opera", "5:55", "1.1B", 7),
        Track("Stairway to Heaven", "Led Zeppelin", "Led Zeppelin IV", "8:02", "55M", 6),
        Track("Midnight City", "M83", "Hurry Up, We're Dreaming", "4:03", "142M", 0)
    )
}
