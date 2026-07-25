package com.example

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Silently writes a fresh backup (see [BackupRepository.writeAutoBackup]) to internal storage
 * roughly once a day, while [BackupRepository.autoBackupEnabled] is on - a safety net against
 * losing liked songs/playlists to an accidental clear-data or uninstall, on top of (not instead
 * of) the manual export/import on the Storage settings screen, which is the only way to get a
 * backup onto another device (this worker's output never leaves the device on its own). */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val succeeded = BackupRepository.getInstance(applicationContext).writeAutoBackup()
        return if (succeeded) Result.success() else Result.retry()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "auto_backup"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
