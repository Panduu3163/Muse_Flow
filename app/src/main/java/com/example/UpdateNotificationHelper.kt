package com.example

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Posts an "app update available" notification when [UpdateChecker] finds a newer GitHub release
 * than what's installed. Its own channel ("updates") - deliberately separate from the playback
 * service's media-session channel and from [ArtistReleaseNotificationHelper]'s "artist_releases"
 * channel, since this is a different kind of interruption with its own mute preference. Same
 * channel-setup/permission-check shape as [ArtistReleaseNotificationHelper]/
 * [DownloadNotificationHelper] - reuses whatever `POST_NOTIFICATIONS` grant
 * `RequestNotificationPermissionOnce` (in [MainActivity]) already obtained rather than prompting
 * again from here.
 */
object UpdateNotificationHelper {
    const val CHANNEL_ID = "updates"

    // Single fixed id: only one "an update is available" notification ever needs to be showing at
    // once - a newer tag posting again should just replace it, not stack up another one.
    private const val NOTIFICATION_ID = 40_000_001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "App updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Notifies when a new MuseFlow version is available" }
        )
    }

    /** [versionTag] is the raw GitHub tag (e.g. "v1.2.0") - shown as-is, since that's the form the
     * user will recognize from the release page [releaseUrl] opens. */
    fun showUpdateAvailable(context: Context, versionTag: String, releaseUrl: String) {
        if (!hasPermission(context)) return
        ensureChannel(context)

        val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl))
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            viewIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("MuseFlow update available")
            .setContentText("Version $versionTag is now available")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // The update remains available in Settings if permission was revoked.
        }
    }

    private fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
}
