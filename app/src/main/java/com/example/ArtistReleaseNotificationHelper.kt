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

/** Posts a "new release from a followed artist" notification - same channel-setup/permission-check
 * shape as [DownloadNotificationHelper], its own channel since this is a discovery notification,
 * not a download-progress one (different importance, different mute preference makes sense). */
object ArtistReleaseNotificationHelper {
    const val CHANNEL_ID = "artist_releases"

    private fun notificationId(artistId: String) = 30_000_000 + (artistId.hashCode() and 0x0FFFFFFF)

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "New releases",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "New releases from artists you follow" }
        )
    }

    /** [newTrackTitles] is expected non-empty - callers should simply not post at all otherwise. */
    fun showNewReleases(context: Context, artistId: String, artistName: String, newTrackTitles: List<String>) {
        if (!hasPermission(context) || newTrackTitles.isEmpty()) return
        ensureChannel(context)
        val contentText = if (newTrackTitles.size == 1) {
            newTrackTitles.first()
        } else {
            "${newTrackTitles.first()} and ${newTrackTitles.size - 1} more"
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("New from $artistName")
            .setContentText(contentText)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId(artistId), notification)
    }

    private fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
