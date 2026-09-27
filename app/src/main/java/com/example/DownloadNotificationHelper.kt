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
 * Posts one real system notification for every download currently in flight, combined - a
 * determinate progress bar showing the *whole batch's* aggregate progress, not one notification
 * per track. Downloading several songs at once used to post one of these per track, which read as
 * cluttered (a 5-song bulk download meant 5 stacked notifications). [DownloadRepository] calls
 * [updateProgress] with a fresh snapshot of every active track any time any one of them changes
 * progress, rather than each track owning and posting its own.
 *
 * No separate "download complete" notification either: once the last active track finishes, the
 * caller's snapshot is empty and this just cancels the notification outright, so it disappears on
 * its own instead of turning into a persistent message the user has to dismiss by hand.
 */
object DownloadNotificationHelper {
    // Shared with DownloadService, which posts the single foreground-service notification that
    // keeps downloads alive on this same channel rather than a separate one.
    const val CHANNEL_ID = "downloads"

    // One fixed id for the whole batch, not per-track - there is only ever at most one of these
    // showing at a time now.
    private const val NOTIFICATION_ID = 20_000_000

    fun ensureChannel(context: Context) {
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

    /**
     * [active] is every track currently downloading or waiting for a download slot, keyed by
     * [Track.downloadKey], each paired with its own percent (0-100, or -1 while the server hasn't
     * reported a size yet). Called with the full current set on every change - an empty map
     * cancels the notification rather than posting one with nothing to show.
     */
    fun updateProgress(context: Context, active: Map<String, Pair<Track, Int>>) {
        if (active.isEmpty()) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            return
        }
        if (!hasPermission(context)) return
        ensureChannel(context)

        val count = active.size
        val first = active.values.first().first
        val title = if (count == 1) "Downloading \"${first.title}\"" else "Downloading $count songs"
        val text = if (count == 1) first.artist else "${first.title} and ${count - 1} more"

        // Unknown-size tracks (-1) count as 0 progress toward the batch average rather than being
        // excluded outright - one huge unstarted download shouldn't let the bar read "almost done"
        // just because the others happen to be nearly finished.
        val percents = active.values.map { (_, percent) -> percent.coerceAtLeast(0) }
        val allUnknown = active.values.all { (_, percent) -> percent < 0 }
        val overallPercent = percents.sum() / count

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (allUnknown) {
            builder.setProgress(0, 0, true)
        } else {
            builder.setProgress(100, overallPercent, false)
        }
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }

    /** Cancels the batch notification outright - used on a full-stop (e.g. every download
     * cancelled at once) where there's no updated [updateProgress] snapshot coming right after. */
    fun clear(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
