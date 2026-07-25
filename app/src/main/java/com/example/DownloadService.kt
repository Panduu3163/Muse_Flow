package com.example

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Keeps a real foreground-service priority (and the process itself) alive while any track is
 * downloading, so the OS doesn't suspend or kill the in-flight transfer once the screen turns off
 * or the app leaves the foreground - [DownloadRepository]'s downloads previously ran in a plain
 * process-wide coroutine scope with nothing telling Android they needed to keep running.
 *
 * A foreground service alone stops the *process* from being killed, but doesn't by itself stop
 * the CPU from sleeping once the screen turns off - confirmed by testing: on real devices this let
 * an in-flight download's socket read stall/time out mid-transfer the moment the screen locked,
 * exactly the "download failed when the screen turns off" symptom. A held [PowerManager.WakeLock]
 * for the duration of any active download is the actual fix - the same reason [PlaybackService]
 * doesn't need one of its own (ExoPlayer's `WAKE_MODE_NETWORK` already does this for playback).
 *
 * This service does no downloading itself - [DownloadRepository] still owns that entirely. It
 * just observes [DownloadRepository.inProgress] and starts/stops itself (and the wake lock) in
 * lockstep with whether anything's actually downloading, via [start].
 */
class DownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        DownloadRepository.getInstance(applicationContext).inProgress
            .onEach { inProgress ->
                if (inProgress.isEmpty()) {
                    releaseWakeLock()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    acquireWakeLock()
                    ServiceCompat.startForeground(
                        this,
                        FOREGROUND_NOTIFICATION_ID,
                        buildNotification(inProgress.size),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                }
            }
            .launchIn(serviceScope)
    }

    /** Idempotent - safe to call on every progress update, not just the empty-to-nonempty
     * transition (acquiring an already-held non-reference-counted lock is a no-op). Capped at 30
     * minutes so a wake lock can never outlive a genuinely stuck transfer indefinitely if
     * [releaseWakeLock] is somehow never reached - re-acquired on the next progress tick if the
     * download is still legitimately running past that.
     */
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(POWER_SERVICE) as? PowerManager ?: return
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MuseFlow:DownloadService")
            .apply { setReferenceCounted(false); acquire(30 * 60 * 1000L) }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun buildNotification(count: Int): Notification {
        DownloadNotificationHelper.ensureChannel(this)
        return NotificationCompat.Builder(this, DownloadNotificationHelper.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(if (count == 1) "Downloading 1 track" else "Downloading $count tracks")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 20_999_999

        /** Idempotent - safe to call for every [DownloadRepository.startDownload], even while the
         * service is already running for another track. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
        }
    }
}
