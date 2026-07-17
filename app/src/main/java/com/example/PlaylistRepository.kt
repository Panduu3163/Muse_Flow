package com.example

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * Playlists the user has created, for Library's real "Playlists" tab. A process-wide singleton,
 * same pattern as [DownloadRepository].
 */
class PlaylistRepository private constructor(context: Context) {

    private val dao = MuseFlowDatabase.getInstance(context.applicationContext).playlistDao()

    fun observeAll(): Flow<List<PlaylistEntity>> = dao.observeAll()

    suspend fun create(name: String) {
        dao.insert(PlaylistEntity(name = name, createdAt = System.currentTimeMillis()))
    }

    companion object {
        @Volatile private var instance: PlaylistRepository? = null

        fun getInstance(context: Context): PlaylistRepository =
            instance ?: synchronized(this) {
                instance ?: PlaylistRepository(context.applicationContext).also { instance = it }
            }
    }
}
