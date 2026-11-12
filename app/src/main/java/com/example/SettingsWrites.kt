package com.example

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** A failed preference/reset write stays retryable instead of escaping the UI's coroutine. */
internal fun CoroutineScope.launchSettingsWrite(
    report: (String) -> Unit = ActionFeedback::post,
    write: suspend () -> Unit,
) = launch {
    try { write() }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { report("Couldn't save settings. Check free storage and retry.") }
}
