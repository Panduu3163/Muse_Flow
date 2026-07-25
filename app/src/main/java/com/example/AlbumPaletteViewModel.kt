package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Extracts a colour palette from the current track's artwork.
 *
 * Feeds two separate things: the Now Playing background (gradient/blur styles) and, when the user
 * enables it, the app-wide accent seed. Both want the same expensive work — decode the image and
 * quantise it — so it happens once here rather than in each consumer.
 *
 * Results are cached per artwork URL, because skipping back and forth between two tracks should
 * not re-decode the same images repeatedly.
 */
class AlbumPaletteViewModel(application: Application) : AndroidViewModel(application) {

    private val _palette = MutableStateFlow<AlbumPalette?>(null)
    val palette: StateFlow<AlbumPalette?> = _palette.asStateFlow()

    private val cache = mutableMapOf<String, AlbumPalette>()
    private var job: Job? = null
    private var loadedUrl: String? = null

    fun load(artworkUrl: String?) {
        if (artworkUrl == null) {
            job?.cancel()
            loadedUrl = null
            _palette.value = null
            return
        }
        if (artworkUrl == loadedUrl) return

        loadedUrl = artworkUrl
        cache[artworkUrl]?.let {
            _palette.value = it
            return
        }

        job?.cancel()
        job = viewModelScope.launch {
            val extracted = runCatching {
                extractAlbumPalette(getApplication(), artworkUrl)
            }.getOrNull()

            if (extracted != null) cache[artworkUrl] = extracted
            // Guard a late result landing after the user already skipped on.
            if (loadedUrl == artworkUrl) _palette.value = extracted
        }
    }
}
