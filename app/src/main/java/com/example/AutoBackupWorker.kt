package com.example

import android.content.Context
import kotlinx.coroutines.flow.first
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Daily internal recovery snapshot plus a verified external copy when a folder is selected.
 * Internal files are deleted by clear-data/uninstall; user-selected external copies survive. */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!BackupRepository.getInstance(applicationContext).autoBackupEnabled.first()) return Result.success()
        val succeeded = BackupRepository.getInstance(applicationContext).writeAutoBackup()
        return if (succeeded) Result.success() else if (runAttemptCount < 3) Result.retry() else Result.failure()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "auto_backup"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS)
                .setConstraints(androidx.work.Constraints.Builder().setRequiresStorageNotLow(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
