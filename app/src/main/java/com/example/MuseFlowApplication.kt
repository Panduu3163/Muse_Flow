package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.music.innertube.YouTube
import com.music.innertube.models.YouTubeLocale
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** App-wide entry point: installs [CrashHandler], warms up the shared HTTP client
 * ([YtHttpClients]), initializes the InnerTube client's locale/guest session, supplies a cached
 * [ImageLoader], and re-arms [AutoBackupWorker] if it was left enabled - before anything else in
 * the app runs. */
class MuseFlowApplication : Application(), ImageLoaderFactory {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
        YtHttpClients.init(this)

        // Never set before this session: every InnerTube request went out with the module's own
        // default locale (`hl` built from `toLanguageTag()`, e.g. "en-US" - InnerTube expects a
        // bare language code like "en", not a BCP-47 tag) and no visitor id at all. Region-locked
        // surfaces degrade silently rather than erroring on either mistake - Charts came back
        // empty rather than failing, which is what actually surfaced this. Mirrors the fallback
        // Echo's `App.onCreate` uses: the device's own country/language when set, "US"/"en"
        // otherwise, since YouTube Music's catalog and charts are sparsest for anything else.
        val deviceLocale = Locale.getDefault()
        YouTube.locale = YouTubeLocale(
            gl = deviceLocale.country.takeIf { it.length == 2 } ?: "US",
            hl = deviceLocale.language.takeIf { it.isNotBlank() } ?: "en",
        )
        appScope.launch {
            runCatching { YouTube.refreshVisitorData() }
        }

        // WorkManager's own scheduling is persisted separately from our DataStore flag, but
        // re-asserting it here (off the main thread, not blocking startup) is cheap and makes
        // sure a periodic job that was somehow lost (e.g. after a device restore) comes back.
        appScope.launch {
            if (BackupRepository.getInstance(this@MuseFlowApplication).autoBackupEnabled.first()) {
                AutoBackupWorker.schedule(this@MuseFlowApplication)
            }
            // Only worth re-arming if there's actually at least one followed artist - unlike
            // auto-backup, following an artist already schedules this worker itself
            // (FollowedArtistsRepository.follow), so this is purely the "worker somehow got lost"
            // recovery path, same reasoning as the auto-backup re-arm above.
            if (FollowedArtistsRepository.getInstance(this@MuseFlowApplication).getAll().isNotEmpty()) {
                ArtistReleaseCheckWorker.schedule(this@MuseFlowApplication)
            }
        }
    }

    /**
     * Every `AsyncImage` in the app was drawing through Coil's bare defaults - no disk cache, no
     * crossfade - so cover art popped in abruptly and had to be re-fetched from the network on
     * every screen visit, never just read back off disk. Sizes/settings mirror Echo's own
     * `newImageLoader` (`App.kt`), minus the DataStore-configurable cache-size preference this
     * app doesn't have a settings UI for - 250MB is a fixed, reasonable default instead.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .crossfade(true)
        .allowHardware(true)
        .memoryCache {
            MemoryCache.Builder(this)
                .maxSizePercent(0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache"))
                .maxSizeBytes(250L * 1024 * 1024)
                .build()
        }
        .build()
}
