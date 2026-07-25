package com.example

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "CrashHandler"

/**
 * Installs a process-wide [Thread.UncaughtExceptionHandler] that persists a crash log (thread
 * name, stacktrace, device/build info) to `filesDir/crash_logs/` before delegating to whatever
 * handler was previously installed (Android's own default one, unless something else already
 * replaced it) - so the platform's normal crash behavior (system dialog, ANR/crash reporting)
 * still happens exactly as before; this only adds a persisted log alongside it. Mirrors
 * Metrolist's `CrashHandler.kt` (GPL-3.0) shape, without its separate `CrashActivity` UI - not
 * needed here since the system's own crash handling already covers that.
 *
 * Self-guards: if writing the log itself throws (e.g. disk full), that failure is swallowed so
 * the real crash still reaches the delegate handler unmodified.
 */
object CrashHandler {

    private const val MAX_LOG_FILES = 20

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                persistCrashLog(appContext, thread, throwable)
            } catch (loggingFailure: Throwable) {
                Log.e(TAG, "Failed to persist crash log", loggingFailure)
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun persistCrashLog(context: Context, thread: Thread, throwable: Throwable) {
        val logsDir = File(context.filesDir, "crash_logs").apply { mkdirs() }
        pruneOldLogs(logsDir)

        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val stackTraceWriter = StringWriter()
        throwable.printStackTrace(PrintWriter(stackTraceWriter))

        val report = buildString {
            appendLine("Time: ${Date()}")
            appendLine("Thread: ${thread.name}")
            appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine()
            append(stackTraceWriter.toString())
        }

        File(logsDir, "crash_$timestamp.txt").writeText(report)
    }

    /** Keeps at most [MAX_LOG_FILES] previous crash logs so a device that crashes repeatedly
     * doesn't accumulate files forever. */
    private fun pruneOldLogs(logsDir: File) {
        val files = logsDir.listFiles()?.sortedBy { it.lastModified() } ?: return
        val excess = files.size - (MAX_LOG_FILES - 1)
        if (excess > 0) files.take(excess).forEach { it.delete() }
    }
}
