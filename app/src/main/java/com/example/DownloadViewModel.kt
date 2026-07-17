package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Activity-scoped facade over [DownloadRepository] for the UI: which tracks are downloaded (for
 * Library's Downloads tab and the Offline mode filter), which are downloading right now (for a
 * per-row spinner), and the actions to start/cancel/remove a download.
 */
class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DownloadRepository.getInstance(application)

    val downloadedTracks: StateFlow<List<Track>> = repository.completedDownloads
        .map { entities -> entities.map { it.toTrack() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloadingKeys: StateFlow<Set<String>> = repository.inProgress
        .map { it.keys }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /** Key -> error message, for the most recent failed download attempt per track. */
    val failures: StateFlow<Map<String, String>> = repository.failures

    fun isDownloaded(track: Track): Boolean =
        downloadedTracks.value.any { it.downloadKey() == track.downloadKey() }

    fun isDownloading(track: Track): Boolean = downloadingKeys.value.contains(track.downloadKey())

    fun download(track: Track) = repository.startDownload(track)

    fun cancel(track: Track) = repository.cancelDownload(track)

    fun delete(track: Track) {
        viewModelScope.launch { repository.deleteDownload(track) }
    }
}

private fun DownloadedTrackEntity.toTrack(): Track = Track(
    title = title,
    artist = artist,
    album = album,
    duration = duration,
    plays = "",
    gradientIndex = gradientIndex,
    imageUrl = imageUrl,
    // Playback for a downloaded track normally resolves to its local file (looked up separately
    // by downloadKey() - see PlayerViewModel's downloadedFilePathsByKey), never this streamUrl.
    // sourceId/sourceType are still reconstructed so a YouTube-sourced download whose local file
    // has gone missing falls back to a real re-resolve via YouTubeStreamResolver instead of the
    // mock catalog.
    streamUrl = null,
    sourceType = sourceType?.let { runCatching { MusicSource.valueOf(it) }.getOrNull() },
    sourceId = sourceId
)
