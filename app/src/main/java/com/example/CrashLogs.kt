package com.example

import android.content.Context
import java.io.File

/** Read side of what [CrashHandler] writes - listing/reading/deleting the persisted crash
 * reports, for the in-app viewer ([ui.screens.CrashLogsScreen]) that lets a crash actually be
 * retrieved without a debugger or adb attached. */
object CrashLogs {

    private fun logsDir(context: Context): File = File(context.filesDir, "crash_logs")

    /** Newest first - the one someone opening this screen right after a crash almost always
     * wants is whatever just happened. */
    fun list(context: Context): List<File> =
        logsDir(context).listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun delete(file: File) {
        file.delete()
    }

    fun clearAll(context: Context) {
        logsDir(context).listFiles()?.forEach { it.delete() }
    }
}
