package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Disk usage for Storage settings. [loading] covers the very first measurement only - refreshes
 * after a clear keep showing the previous numbers until the new ones are ready, rather than
 * flashing back to an empty state. */
data class StorageUsage(
    val downloadsBytes: Long = 0L,
    val streamCacheBytes: Long = 0L,
    val imageCacheBytes: Long = 0L,
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

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val (downloads, streamCache, imageCache) = withContext(Dispatchers.IO) {
                Triple(
                    dirSizeBytes(DownloadRepository.downloadsDir(context)),
                    runCatching { StreamCache.get(context).cacheSpace }.getOrDefault(0L),
                    runCatching { context.imageLoader.diskCache?.size }.getOrNull() ?: 0L,
                )
            }
            _usage.value = StorageUsage(
                downloadsBytes = downloads,
                streamCacheBytes = streamCache,
                imageCacheBytes = imageCache,
                loading = false,
            )
        }
    }

    fun clearDownloads() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                DownloadRepository.getInstance(getApplication()).deleteAllDownloads()
            }
            refresh()
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            withContext(Dispatchers.IO) {
                runCatching { StreamCache.clear(context) }
                runCatching { context.imageLoader.diskCache?.clear() }
            }
            context.imageLoader.memoryCache?.clear()
            refresh()
        }
    }

    private fun dirSizeBytes(dir: java.io.File): Long =
        dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}
