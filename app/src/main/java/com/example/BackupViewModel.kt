package com.example

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the screen should tell the user about the last export or restore it ran. */
sealed interface BackupOutcome {
    data class Exported(val location: String) : BackupOutcome
    data class Restored(
        val likedSongs: Int, val playlists: Int, val skipped: Int,
        val artists: Int = 0, val artistsSkipped: Int = 0, val includesArtists: Boolean = false,
        val includesHistory: Boolean = false, val historyTracks: Int = 0, val historyEvents: Int = 0, val listeningSessions: Int = 0,
        val includesSettings: Boolean = false, val settingsPending: Boolean = false,
    ) : BackupOutcome
    data class Failed(val message: String) : BackupOutcome
    data class Notice(val title: String, val message: String) : BackupOutcome
}

/**
 * Export/restore over the Storage Access Framework.
 *
 * The file is written through a `content://` URI the user picked, not to a path this app invents:
 * on Android 10+ an app can't write to arbitrary shared storage anyway, and a backup the user
 * can't find in their own Files app is not much of a backup.
 */
class BackupViewModel internal constructor(
    application: Application,
    private val repository: BackupRepository,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, BackupRepository.getInstance(application))

    val autoBackupEnabled: StateFlow<Boolean> = repository.autoBackupEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val lastBackupAt: StateFlow<Long?> = repository.lastBackupAt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val includeHistory: StateFlow<Boolean> = repository.includeHistoryEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val includeSettings: StateFlow<Boolean> = repository.includeSettingsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val pendingSettings: StateFlow<Boolean> = repository.pendingSettingsRestore
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val automaticDestination = repository.automaticDestination
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AutomaticBackupDestinationState())
    val hasInternalBackup = repository.hasInternalBackup
    val hasPreviousInternalBackup = repository.hasPreviousInternalBackup

    /** True while an export or restore is in flight, so the buttons can't be fired twice. */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _outcome = MutableStateFlow<BackupOutcome?>(null)
    val outcome: StateFlow<BackupOutcome?> = _outcome.asStateFlow()

    private var preparedRestore: PreparedBackupRestore? = null
    private val _restorePreview = MutableStateFlow<BackupRestorePreview?>(null)
    val restorePreview: StateFlow<BackupRestorePreview?> = _restorePreview.asStateFlow()

    fun setAutoBackupEnabled(enabled: Boolean) {
        updateBackupChoice { repository.setAutoBackupEnabled(enabled) }
    }

    fun setAutomaticDestination(tree: Uri?) {
        updateBackupChoice("Could not use that backup folder. Choose a writable folder that supports saved access. Your previous destination is kept.") {
            repository.setAutomaticDestination(tree)
        }
    }

    fun backupNow() {
        if (_busy.value || preparedRestore != null) return
        _busy.value = true
        _outcome.value = null
        viewModelScope.launch {
            try {
                val saved = repository.writeAutoBackup()
                _outcome.value = if (saved) BackupOutcome.Notice("Backup saved",
                    if (repository.automaticDestination.first().treeUri != null) "An internal copy and a verified external copy were saved."
                    else "An internal recovery copy was saved. Choose an external folder or export a file to protect it from uninstall.")
                else BackupOutcome.Failed(repository.automaticDestination.first().error
                    ?: "Could not save the backup. Check available storage and retry. Earlier copies have been kept.")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _outcome.value = BackupOutcome.Failed("Backup could not be completed. Check storage and retry. Earlier copies are kept.") }
            finally { _busy.value = false }
        }
    }

    fun previewInternalBackup(previous: Boolean = false) {
        if (_busy.value || preparedRestore != null) return
        _busy.value = true
        _outcome.value = null
        viewModelScope.launch {
            try {
                val prepared = repository.prepareRestore(repository.readInternalBackup(previous))
                preparedRestore = prepared
                _restorePreview.value = prepared.preview.copy(fileName = if (previous) "Previous internal recovery copy" else "Latest internal recovery copy")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _outcome.value = BackupOutcome.Failed("Could not read the internal recovery copy. Try a previously exported backup instead. Your library was not changed.") }
            finally { _busy.value = false }
        }
    }

    fun setIncludeHistoryEnabled(enabled: Boolean) {
        updateBackupChoice { repository.setIncludeHistoryEnabled(enabled) }
    }

    fun setIncludeSettingsEnabled(enabled: Boolean) {
        updateBackupChoice { repository.setIncludeSettingsEnabled(enabled) }
    }

    private fun updateBackupChoice(message: String = "Could not save your backup choice. Try again before exporting.", update: suspend () -> Unit) {
        if (_busy.value || preparedRestore != null) return
        _busy.value = true
        viewModelScope.launch {
            try {
                update()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _outcome.value = BackupOutcome.Failed(message)
            } finally {
                _busy.value = false
            }
        }
    }

    fun retryPendingSettings() = runSettingsRecovery(false)
    fun keepCurrentSettings() = runSettingsRecovery(true)

    private fun runSettingsRecovery(keep: Boolean) {
        if (_busy.value || preparedRestore != null) return
        _busy.value = true
        viewModelScope.launch {
            try {
                val restored = if (keep) repository.keepCurrentSettings() else repository.recoverPendingSettings()
                _outcome.value = if (restored) BackupOutcome.Notice(
                    if (keep) "Current settings kept" else "Settings restored",
                    if (keep) "Your library and any settings already applied are kept." else "The pending settings restore is complete.")
                else BackupOutcome.Failed("Settings are still pending. Your restored library is saved; retry when storage is available.")
            } catch (cancelled: CancellationException) { throw cancelled }
            finally { _busy.value = false }
        }
    }

    fun exportTo(uri: Uri) {
        if (_busy.value || preparedRestore != null) return
        _busy.value = true
        _outcome.value = null
        viewModelScope.launch {
            try {
                val json = repository.buildBackupJson()
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver
                        .openOutputStream(uri, "wt")
                        ?.use { it.write(json.toByteArray()) }
                        ?: error("Couldn't open the chosen file for writing")
                }
                _outcome.value = BackupOutcome.Exported(displayNameOf(uri))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _outcome.value = BackupOutcome.Failed(error.message ?: "Export failed")
            } finally {
                _busy.value = false
            }
        }
    }

    fun previewRestoreFrom(uri: Uri) {
        if (_busy.value || preparedRestore != null) return
        _busy.value = true
        _outcome.value = null
        viewModelScope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver
                        .openInputStream(uri)
                        ?.use { readBackupDocument(it) }
                        ?: error("Couldn't open the chosen file")
                }
                val prepared = repository.prepareRestore(json)
                val fileName = displayNameOf(uri).take(512)
                preparedRestore = prepared
                _restorePreview.value = prepared.preview.copy(fileName = fileName)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _outcome.value = BackupOutcome.Failed("Could not read a supported MuseFlow backup. Your library was not changed.")
            } finally {
                _busy.value = false
            }
        }
    }

    fun cancelRestorePreview() {
        if (_busy.value) return
        preparedRestore = null
        _restorePreview.value = null
    }

    fun confirmRestore() {
        if (_busy.value) return
        val prepared = preparedRestore ?: return
        // Consume the validated document once. Never reopen a potentially changed provider URI.
        preparedRestore = null
        _restorePreview.value = null
        _busy.value = true
        viewModelScope.launch {
            try {
                _outcome.value = when (val result = repository.restorePrepared(prepared.document)) {
                    is RestoreResult.Success -> BackupOutcome.Restored(
                        result.likedSongsRestored,
                        result.playlistsRestored,
                        result.playlistsSkipped,
                        result.artistsRestored,
                        result.artistsSkipped,
                        prepared.document.includesArtists,
                        prepared.document.history != null,
                        result.historyTracksMerged,
                        result.historyEventsAdded,
                        result.listeningSessionsMerged,
                        prepared.document.settings != null,
                        result.settingsPending,
                    )
                    is RestoreResult.Failure -> BackupOutcome.Failed(result.message)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _outcome.value = BackupOutcome.Failed(error.message ?: "Restore failed")
            } finally {
                _busy.value = false
            }
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
