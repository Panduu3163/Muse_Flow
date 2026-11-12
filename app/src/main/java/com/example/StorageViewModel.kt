package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import coil3.annotation.ExperimentalCoilApi
import coil3.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

/** Disk usage for Storage settings. [loading] covers the very first measurement only - refreshes
 * after a clear keep showing the previous numbers until the new ones are ready, rather than
 * flashing back to an empty state. */
data class StorageUsage(
    val downloadsBytes: Long = 0L,
    val streamCacheBytes: Long = 0L,
    val imageCacheBytes: Long = 0L,
    val backupsBytes: Long = 0L,
    val loading: Boolean = true,
) {
    val cacheBytes: Long get() = streamCacheBytes + imageCacheBytes
}

/**
 * Backs the Storage settings screen: measures what MuseFlow's downloads and caches actually take
 * up on disk, and performs the "Clear downloads"/"Clear cache" actions.
 *
 * Downloads ([DownloadRepository], `filesDir/downloads`) and caches (audio in [StreamCache],
 * artwork in Coil's disk cache - see [MuseFlowApplication.newImageLoader]) are deliberately kept
 * as separate numbers here even though they're both "storage MuseFlow uses": downloads are user
 * data the user chose to keep offline, caches are OS-reclaimable and invisible until now - the
 * same distinction [StreamCache]'s own doc draws.
 */
@UnstableApi
@OptIn(ExperimentalCoilApi::class)
class StorageViewModel(application: Application) : AndroidViewModel(application) {

    private val _usage = MutableStateFlow(StorageUsage())
    val usage: StateFlow<StorageUsage> = _usage.asStateFlow()
    private val _busy = MutableStateFlow<String?>(null)
    val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val context = getApplication<Application>()
            try {
                val measured = withContext(Dispatchers.IO) {
                    StorageUsage(downloadsBytes = dirSizeBytes(DownloadRepository.downloadsDir(context)),
                        streamCacheBytes = StreamCache.get(context).cacheSpace,
                        imageCacheBytes = context.imageLoader.diskCache?.size ?: 0L,
                        backupsBytes = dirSizeBytes(java.io.File(context.filesDir, "backups")), loading = false)
                }
                _usage.value = measured
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _message.value = "Could not refresh storage sizes. Previous figures may be out of date."; _usage.value = _usage.value.copy(loading = false) }
        }
    }

    fun clearDownloads() {
        cleanup("Removing downloads") {
            DownloadRepository.getInstance(getApplication()).deleteAllDownloads()
        }
    }

    fun clearCache() {
        cleanup("Clearing cache") {
            val context = getApplication<Application>()
            StreamCache.clear(context)
            context.imageLoader.diskCache?.clear()
            context.imageLoader.memoryCache?.clear()
        }
    }

    private fun cleanup(label: String, action: suspend () -> Unit) {
        if (_busy.value != null) return
        _busy.value = label
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { action() } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _message.value = "Cleanup could not finish. Check storage and retry. Likes, playlists and backup copies are kept." }
            finally { _busy.value = null; refresh() }
        }
    }
    fun dismissMessage() { _message.value = null }

    private fun dirSizeBytes(dir: java.io.File): Long =
        dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}
