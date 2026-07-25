package com.example

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** App-wide entry point: installs [CrashHandler], warms up the shared HTTP client
 * ([YtHttpClients]), and re-arms [AutoBackupWorker] if it was left enabled - before anything
 * else in the app runs. */
class MuseFlowApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
        YtHttpClients.init(this)

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
}
