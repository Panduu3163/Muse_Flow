package com.example

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.collection.LruCache
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.materialkolor.score.Score

/** Dominant/muted/vibrant colour triple extracted from a track's artwork via
 * [androidx.palette.graphics.Palette] - used to tint the Now Playing background for tracks that
 * have real artwork, instead of always falling back to [MusicData.Gradients]' fixed palette.
 * [vibrant] falls back to [dominant] when Palette finds no vibrant swatch (common for muted/
 * grayscale artwork) rather than leaving it nullable - every caller wants three colours to work
 * with, not two-and-sometimes-a-third. */
data class AlbumPalette(
    val dominant: Color,
    val muted: Color,
    val vibrant: Color = dominant,
    val accent: Color = vibrant,
)

/** In-memory cache of [AlbumPalette] by artwork URL - avoids re-running Palette (a real, if
 * small, CPU cost) every time the same track's Now Playing screen is revisited within a process
 * lifetime. Not persisted; recomputed fresh on a cold app start. */
private object AlbumPaletteCache {
    private val cache = LruCache<String, AlbumPalette>(50)
    fun get(key: String): AlbumPalette? = cache[key]
    fun put(key: String, value: AlbumPalette) {
        cache.put(key, value)
    }
}

/** Downsamples [imageUrl]'s artwork and extracts a dominant/muted swatch pair via
 * [androidx.palette.graphics.Palette], caching the result by URL. Returns null if the image can't
 * be loaded or Palette finds nothing usable - callers should fall back to their existing static
 * gradient in that case. Runs entirely off the main thread. */
suspend fun extractAlbumPalette(context: Context, imageUrl: String): AlbumPalette? {
    AlbumPaletteCache.get(imageUrl)?.let { return it }
    return withContext(Dispatchers.IO) {
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .size(100, 100)
            // Palette needs to read pixels directly off the bitmap; hardware bitmaps can't be
            // read that way.
            .allowHardware(false)
            .build()
        val result = runCatching { context.imageLoader.execute(request) }.getOrNull() ?: return@withContext null
        val bitmap = (result.drawable as? BitmapDrawable)?.bitmap ?: return@withContext null

        val palette = withContext(Dispatchers.Default) { Palette.from(bitmap).generate() }
        val dominantArgb = palette.getDominantColor(android.graphics.Color.TRANSPARENT)
        if (dominantArgb == android.graphics.Color.TRANSPARENT) return@withContext null
        val mutedArgb = palette.getMutedColor(dominantArgb)
        val vibrantArgb = palette.getVibrantColor(dominantArgb)
        val rankedAccent = runCatching {
            Score.score(palette.swatches.associate { it.rgb to it.population }).first()
        }.getOrDefault(vibrantArgb)

        AlbumPalette(dominant = Color(dominantArgb), muted = Color(mutedArgb),
            vibrant = Color(vibrantArgb), accent = Color(rankedAccent))
            .also { AlbumPaletteCache.put(imageUrl, it) }
    }
}
