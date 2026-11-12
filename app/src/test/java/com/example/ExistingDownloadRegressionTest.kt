package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.room.Room
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.*
import org.junit.Before
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExistingDownloadRegressionTest {
    private lateinit var context: Context
    private lateinit var database: MuseFlowDatabase
    private lateinit var repository: DownloadRepository
    private lateinit var scope: CoroutineScope
    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, MuseFlowDatabase::class.java).allowMainThreadQueries().build()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        repository = DownloadRepository(context, database, repositoryScope = scope)
    }
    @After fun tearDown() = runBlocking { scope.coroutineContext.job.cancelAndJoin(); database.close() }

    @Test fun alreadyDownloadedAudioDoesNotStartTransferServiceOrRewriteFile() = runBlocking {
        val track = Track("Same title", "Same artist", "Album", "3:00", "", 0,
            sourceId = "audio000001", sourceType = MusicSource.YOUTUBE_MUSIC)
        val file = File(DownloadRepository.downloadsDir(context).apply { mkdirs() }, "saved-preflight.audio")
            .apply { writeBytes(byteArrayOf(4, 3, 2, 1)) }
        val bytes = file.readBytes()
        val timestamp = file.lastModified()
        val dao = database.downloadedTrackDao()
        dao.upsert(DownloadedTrackEntity(track.downloadKey(), track.title, track.artist, track.album, track.duration, 0,
            null, file.absolutePath, "COMPLETED", 100, track.sourceId, "YOUTUBE_MUSIC"))
        repository.startDownload(track)
        val deadline = System.currentTimeMillis() + 5_000
        while (repository.isDownloading(track) && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertFalse(repository.isDownloading(track))
        assertTrue(repository.inProgress.value.isEmpty())
        assertNull(repository.failures.value[track.downloadKey()])
        assertNull(shadowOf(context as Application).nextStartedService)
        assertArrayEquals(bytes, file.readBytes())
        assertEquals(timestamp, file.lastModified())
        assertNotNull(repository.getByKey(track.downloadKey()))
        assertNull(repository.getByKey(track.copy(sourceId = "video000001").downloadKey()))
        dao.deleteByKey(track.downloadKey())
        file.delete()
        Unit
    }

    @Test fun missingOrEmptyFilesAreNotAdvertisedAsDownloaded() = runBlocking {
        val file = File(DownloadRepository.downloadsDir(context).apply { mkdirs() }, "empty.audio").apply { writeBytes(byteArrayOf()) }
        val key = "yt:empty000001"
        val dao = database.downloadedTrackDao()
        dao.upsert(DownloadedTrackEntity(key, "Missing", "Artist", "", "3:00", 0, null, file.path, "COMPLETED", 1, "empty000001", "YOUTUBE_MUSIC"))
        assertNull(repository.getByKey(key))
        assertTrue(repository.completedDownloads.first().none { it.key == key })
        file.delete()
        assertNull(repository.getByKey(key))
        dao.deleteByKey(key)
        Unit
    }
}
