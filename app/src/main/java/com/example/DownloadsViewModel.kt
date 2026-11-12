package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class DownloadsUiState(val loading: Boolean = true, val requests: List<DownloadRequestEntity> = emptyList(), val error: String? = null)

class DownloadsViewModel internal constructor(application: Application, private val repository: DownloadRepository) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, DownloadRepository.getInstance(application))
    val state = repository.requests.map { DownloadsUiState(false, it) }
        .catch { emit(DownloadsUiState(false, error = "Could not read download status. Reopen MuseFlow and try again.")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadsUiState())
    val active = repository.inProgress
    private val _busy = MutableStateFlow<Set<String>>(emptySet())
    val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    init { viewModelScope.launch {
        try { repository.initialize() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { _message.value = "Could not finish local download recovery. Check free storage, then reopen MuseFlow." }
    } }
    fun retry(request: DownloadRequestEntity) {
        if (request.key !in _busy.value && !repository.startDownload(request.toTrack()))
            _message.value = "Download cleanup is still running. Retry when it finishes."
    }
    fun cancel(request: DownloadRequestEntity) { repository.cancelDownload(request.toTrack()) }
    fun remove(request: DownloadRequestEntity) {
        if (request.key in _busy.value) return
        _busy.update { it + request.key }
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { repository.deleteDownload(request.toTrack()) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _message.value = "Could not remove every file. Check storage and retry. Your likes and playlists are kept." }
            finally { _busy.update { it - request.key } }
        }
    }
    fun dismissMessage() { _message.value = null }
}
