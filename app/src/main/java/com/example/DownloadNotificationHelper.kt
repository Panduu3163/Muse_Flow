package com.example

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Posts a real system notification for each in-flight download - a determinate progress bar (via
 * [NotificationCompat.Builder.setProgress]) that fills in step with actual bytes downloaded, so a
 * slow-but-healthy download is visibly distinguishable - from the notification shade, not just
 * in-app - from one that's genuinely stuck. Replaced with a completion notification once the
 * file is fully written. Used only by [DownloadRepository], which owns the actual download.
 */
object DownloadNotificationHelper {
    private const val CHANNEL_ID = "downloads"

    // Stable per-track notification id derived from downloadKey(), so re-downloading the same
    // track updates (rather than stacks alongside) its own notification.
    private fun notificationId(key: String) = 20_000_000 + (key.hashCode() and 0x0FFFFFFF)

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                // LOW: a routine progress update shouldn't make sound or heads-up-interrupt.
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Offline download progress" }
        )
    }

    /** [percent] in 0..100 for a determinate bar, or null while the server hasn't reported a
     * total size yet (an indeterminate bar - still visibly "working", just not quantified). */
    fun showProgress(context: Context, track: Track, percent: Int?) {
        if (!hasPermission(context)) return
        ensureChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Downloading \"${track.title}\"")
            .setContentText(track.artist)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (percent != null) {
            builder.setProgress(100, percent, false)
        } else {
            builder.setProgress(0, 0, true)
        }
        NotificationManagerCompat.from(context).notify(notificationId(track.downloadKey()), builder.build())
    }

    fun showCompleted(context: Context, track: Track) {
        if (!hasPermission(context)) return
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Download complete")
            .setContentText("\"${track.title}\" is ready to play offline")
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId(track.downloadKey()), notification)
    }

    /** Called on cancel/failure - otherwise a stuck "Downloading..." notification (which
     * [showProgress] marks ongoing) would sit in the shade forever with nothing to resolve it. */
    fun clear(context: Context, key: String) {
        NotificationManagerCompat.from(context).cancel(notificationId(key))
    }

    private fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
