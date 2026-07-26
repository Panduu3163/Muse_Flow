package com.example

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the screen should tell the user about the last export or restore it ran. */
sealed interface BackupOutcome {
    data class Exported(val location: String) : BackupOutcome
    data class Restored(val likedSongs: Int, val playlists: Int, val skipped: Int) : BackupOutcome
    data class Failed(val message: String) : BackupOutcome
}

/**
 * Export/restore over the Storage Access Framework.
 *
 * The file is written through a `content://` URI the user picked, not to a path this app invents:
 * on Android 10+ an app can't write to arbitrary shared storage anyway, and a backup the user
 * can't find in their own Files app is not much of a backup.
 */
class BackupViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BackupRepository.getInstance(application)

    val autoBackupEnabled: StateFlow<Boolean> = repository.autoBackupEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val lastBackupAt: StateFlow<Long?> = repository.lastBackupAt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** True while an export or restore is in flight, so the buttons can't be fired twice. */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _outcome = MutableStateFlow<BackupOutcome?>(null)
    val outcome: StateFlow<BackupOutcome?> = _outcome.asStateFlow()

    fun setAutoBackupEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setAutoBackupEnabled(enabled) }
    }

    fun exportTo(uri: Uri) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _outcome.value = runCatching {
                val json = repository.buildBackupJson()
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver
                        .openOutputStream(uri, "wt")
                        ?.use { it.write(json.toByteArray()) }
                        ?: error("Couldn't open the chosen file for writing")
                }
                displayNameOf(uri)
            }.fold(
                onSuccess = { BackupOutcome.Exported(it) },
                onFailure = { BackupOutcome.Failed(it.message ?: "Export failed") },
            )
            _busy.value = false
        }
    }

    fun restoreFrom(uri: Uri) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _outcome.value = runCatching {
                val json = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver
                        .openInputStream(uri)
                        ?.use { it.readBytes().decodeToString() }
                        ?: error("Couldn't open the chosen file")
                }
                repository.restoreFromJson(json)
            }.fold(
                onSuccess = { result ->
                    when (result) {
                        is RestoreResult.Success -> BackupOutcome.Restored(
                            result.likedSongsRestored,
                            result.playlistsRestored,
                            result.playlistsSkipped,
                        )
                        is RestoreResult.Failure -> BackupOutcome.Failed(result.message)
                    }
                },
                onFailure = { BackupOutcome.Failed(it.message ?: "Restore failed") },
            )
            _busy.value = false
        }
    }

    fun dismissOutcome() {
        _outcome.value = null
    }

    /**
     * The file name a `content://` URI actually shows to the user.
     *
     * Not [Uri.lastPathSegment] - for a Downloads document that's the numeric row id, so the
     * confirmation read "Written to 2353". The display name has to be queried from the provider.
     */
    private suspend fun displayNameOf(uri: Uri): String = withContext(Dispatchers.IO) {
        runCatching {
            getApplication<Application>().contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: "the chosen file"
    }
}
