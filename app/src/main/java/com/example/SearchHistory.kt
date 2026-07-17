package com.example

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One past search query, keyed by the query text itself - re-searching something already in
 * history just bumps its [searchedAt] via REPLACE rather than growing a duplicate entry. */
@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val searchedAt: Long
)

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun record(entity: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun delete(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearAll()
}

/** Process-wide singleton (same pattern as [PlaybackHistoryRepository]/[DownloadRepository]) over
 * [SearchHistoryDao], so a search recorded from any screen is visible everywhere search history
 * is shown. */
class SearchHistoryRepository private constructor(context: Context) {

    private val dao = MuseFlowDatabase.getInstance(context.applicationContext).searchHistoryDao()
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Most-recent-first, capped at 20 - plenty for a chip row/list, and keeps the table from
     * growing unbounded (older entries past the cap are still in the table until [clearAll], just
     * never surfaced). */
    fun observeRecent(): Flow<List<String>> = dao.observeRecent(20).map { entities -> entities.map { it.query } }

    fun record(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        repositoryScope.launch { dao.record(SearchHistoryEntity(query = trimmed, searchedAt = System.currentTimeMillis())) }
    }

    fun delete(query: String) {
        repositoryScope.launch { dao.delete(query) }
    }

    fun clearAll() {
        repositoryScope.launch { dao.clearAll() }
    }

    companion object {
        @Volatile private var instance: SearchHistoryRepository? = null

        fun getInstance(context: Context): SearchHistoryRepository =
            instance ?: synchronized(this) {
                instance ?: SearchHistoryRepository(context.applicationContext).also { instance = it }
            }
    }
}

/** Thin ViewModel wrapper over [SearchHistoryRepository] for [SearchScreen]. */
class SearchHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SearchHistoryRepository.getInstance(application)

    val recentQueries: StateFlow<List<String>> = repository.observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun record(query: String) = repository.record(query)
    fun delete(query: String) = repository.delete(query)
    fun clearAll() = repository.clearAll()
}
