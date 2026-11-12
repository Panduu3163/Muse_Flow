package com.example

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupHistoryRestoreTest {
    private lateinit var context: Context
    private lateinit var db: MuseFlowDatabase
    private lateinit var repository: BackupRepository

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, MuseFlowDatabase::class.java).allowMainThreadQueries().build()
        repository = BackupRepository(context, db)
    }

    @After fun tearDown() {
        runBlocking { repository.setIncludeHistoryEnabled(false) }
        db.close()
    }

    @Test fun historyIsExcludedByDefaultAndTheExplicitChoiceControlsExports() = runBlocking {
        repository.setIncludeHistoryEnabled(false)
        db.playbackHistoryDao().upsert(track())
        db.playbackHistoryDao().insertEvent(event(100))
        val privateCopy = JSONObject(repository.buildBackupJson())
        assertFalse(privateCopy.getBoolean("historyIncluded"))
        assertFalse(privateCopy.has("playbackHistory"))
        repository.setIncludeHistoryEnabled(true)
        assertTrue(repository.includeHistoryEnabled.first())
        val fullCopy = JSONObject(repository.buildBackupJson())
        assertTrue(fullCopy.getBoolean("historyIncluded"))
        assertEquals(1, fullCopy.getJSONArray("playbackHistory").length())
        assertEquals(1, fullCopy.getJSONArray("playbackEvents").length())
        assertFalse(JSONObject(repository.buildBackupJson(includeHistory = false)).getBoolean("historyIncluded"))
    }

    @Test fun exportRoundTripKeepsObservedEventsAndSessionsWithoutLocalDatabaseIds() = runBlocking {
        val dao = db.playbackHistoryDao()
        dao.upsert(track(count = 5))
        dao.insertEvent(event(100).copy(id = 40))
        dao.insertEvent(event(100).copy(id = 41))
        dao.insertEvent(event(200).copy(id = 42))
        val observed = session(heard = 60_000).copy(durationMs = 30_000)
        dao.saveListeningSession(observed)
        val copy = repository.buildBackupJson(includeHistory = true)
        assertFalse(JSONObject(copy).getJSONArray("playbackEvents").getJSONObject(0).has("id"))
        val target = Room.inMemoryDatabaseBuilder(context, MuseFlowDatabase::class.java).allowMainThreadQueries().build()
        try {
            val importer = BackupRepository(context, target)
            val first = importer.restoreFromJson(copy) as RestoreResult.Success
            assertEquals(3, first.historyEventsAdded)
            assertEquals(1, first.listeningSessionsMerged)
            assertEquals(track(count = 5), target.playbackHistoryDao().backupHistory().single())
            assertEquals(listOf(100L, 100L, 200L), target.playbackHistoryDao().backupEvents().map { it.playedAt })
            assertEquals(observed, target.playbackHistoryDao().backupSessions().single())
            assertTrue(target.playbackHistoryDao().backupEvents().all { it.id < 40 })
            val repeat = importer.restoreFromJson(copy) as RestoreResult.Success
            assertEquals(0, repeat.historyEventsAdded)
            assertEquals(0, repeat.listeningSessionsMerged)
            assertEquals(5, target.playbackHistoryDao().backupHistory().single().playCount)
            assertEquals(60_000L, target.playbackHistoryDao().backupSessions().single().listenedMs)
            assertFalse(importer.includeHistoryEnabled.first())
        } finally { target.close() }
    }

    @Test fun overlappingSnapshotsUseEventMultiplicityAndMaximumCountersRatherThanSums() = runBlocking {
        val dao = db.playbackHistoryDao()
        dao.upsert(track(count = 8).copy(title = "New local metadata", playedAt = 300))
        dao.insertEvent(event(100)); dao.insertEvent(event(300))
        dao.saveListeningSession(session(heard = 40_000, outcome = "ENDED"))
        val backup = document(BackupHistory(listOf(track(count = 5)), listOf(event(100), event(100), event(200)),
            listOf(session(heard = 30_000)))).toString()
        val preview = repository.prepareRestore(backup).preview
        assertEquals(2, preview.newHistoryEvents)
        assertEquals(1, preview.skippedHistoryEvents)
        assertEquals(1, preview.skippedListeningSessions)
        assertEquals(2, dao.backupEvents().size)
        repeat(2) { assertTrue(repository.restoreFromJson(backup) is RestoreResult.Success) }
        assertEquals(8, dao.backupHistory().single().playCount)
        assertEquals("New local metadata", dao.backupHistory().single().title)
        assertEquals(listOf(100L, 100L, 200L, 300L), dao.backupEvents().map { it.playedAt })
        assertEquals(40_000L, dao.backupSessions().single().listenedMs)
        assertEquals("ENDED", dao.backupSessions().single().outcome)
    }

    @Test fun independentObservedEventsAreALowerBoundForTheMergedAggregateCount() = runBlocking {
        val dao = db.playbackHistoryDao()
        dao.upsert(track(count = 1)); dao.insertEvent(event(100))
        val backup = document(BackupHistory(listOf(track(count = 1)), listOf(event(200)), emptyList())).toString()
        repeat(2) { repository.restoreFromJson(backup) }
        assertEquals(2, dao.backupHistory().single().playCount)
        assertEquals(2, dao.backupEvents().size)
    }

    @Test fun longerOrFinalSessionVersionsMergeWithoutAddingHeardTime() = runBlocking {
        val dao = db.playbackHistoryDao()
        dao.upsert(track()); dao.saveListeningSession(session(heard = 30_000))
        val closing = document(BackupHistory(listOf(track()), emptyList(), listOf(session(heard = 30_000, outcome = "ENDED")))).toString()
        assertEquals(1, (repository.restoreFromJson(closing) as RestoreResult.Success).listeningSessionsMerged)
        val longer = document(BackupHistory(listOf(track()), emptyList(), listOf(session(heard = 50_000, outcome = "ENDED")))).toString()
        repository.restoreFromJson(longer); repository.restoreFromJson(closing); repository.restoreFromJson(longer)
        assertEquals(50_000L, dao.backupSessions().single().listenedMs)
        assertEquals("ENDED", dao.backupSessions().single().outcome)
    }

    @Test fun historicalStartsNeverCreateInventedCompletedListeningSessions() = runBlocking {
        val backup = document(BackupHistory(listOf(track(count = 100)), listOf(event(100)), emptyList()))
        repository.restoreFromJson(backup.toString())
        assertEquals(100, db.playbackHistoryDao().backupHistory().single().playCount)
        assertTrue(db.playbackHistoryDao().backupSessions().isEmpty())
    }

    @Test fun conflictingSessionIdentityRejectsTheWholeImport() = runBlocking {
        val dao = db.playbackHistoryDao()
        dao.upsert(track()); dao.saveListeningSession(session())
        val backup = document(BackupHistory(listOf(track()), listOf(event(100)), listOf(session().copy(startedAt = 999))))
            .put("likedSongs", JSONArray().put(JSONObject().put("key", "local:new").put("title", "New")))
        assertTrue(repository.restoreFromJson(backup.toString()) is RestoreResult.Failure)
        assertTrue(db.likedSongDao().backupSnapshot().isEmpty())
        assertTrue(dao.backupEvents().isEmpty())
        assertEquals(session(), dao.backupSessions().single())
    }

    @Test fun historyWriteFailureRollsBackLibraryChangesAndPriorCounters() = runBlocking {
        val dao = db.playbackHistoryDao()
        val original = track(count = 4).copy(title = "Original")
        dao.upsert(original)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_event BEFORE INSERT ON playback_events BEGIN SELECT RAISE(ABORT, 'injected failure'); END")
        val backup = document(BackupHistory(listOf(track(count = 9)), listOf(event(100)), listOf(session())))
            .put("likedSongs", JSONArray().put(JSONObject().put("key", "local:new").put("title", "New")))
        assertTrue(repository.restoreFromJson(backup.toString()) is RestoreResult.Failure)
        assertTrue(db.likedSongDao().backupSnapshot().isEmpty())
        assertEquals(original, dao.backupHistory().single())
        assertTrue(dao.backupSessions().isEmpty())
        assertTrue(dao.backupEvents().isEmpty())
    }

    @Test fun sessionRetentionUsesTheSameDeterministicMostRecentPolicyAndStaysIdempotent() = runBlocking {
        val dao = db.playbackHistoryDao()
        dao.upsert(track())
        db.withTransaction {
            repeat(BACKUP_SESSION_LIMIT) { index -> dao.saveListeningSession(session().copy(id = "old-$index", startedAt = index.toLong())) }
        }
        val fresh = session().copy(id = "fresh", startedAt = 3000)
        val backup = document(BackupHistory(listOf(track()), emptyList(), listOf(fresh))).toString()
        assertEquals(1, repository.prepareRestore(backup).preview.newListeningSessions)
        repository.restoreFromJson(backup)
        assertEquals(BACKUP_SESSION_LIMIT, dao.backupSessions().size)
        assertNull(dao.listeningSession("old-0"))
        assertEquals(fresh, dao.listeningSession("fresh"))
        assertEquals(0, (repository.restoreFromJson(backup) as RestoreResult.Success).listeningSessionsMerged)
    }

    @Test fun oldAndHistoryExcludedBackupsLeaveExistingHistoryUntouched() = runBlocking {
        val dao = db.playbackHistoryDao()
        dao.upsert(track()); dao.insertEvent(event(100)); dao.saveListeningSession(session())
        val excluded = JSONObject(repository.buildBackupJson(includeHistory = false))
        // Even stray unknown arrays must not silently opt the user into history restoration.
        excluded.put("playbackHistory", JSONArray().put(track(count = 99).toBackupJson()))
        repository.restoreFromJson(excluded.toString())
        for (version in 1..2) {
            repository.restoreFromJson(JSONObject().put("schemaVersion", version).put("likedSongs", JSONArray())
                .put("playlists", JSONArray()).put("followedArtists", JSONArray()).toString())
        }
        assertEquals(track(), dao.backupHistory().single())
        assertEquals(1, dao.backupEvents().size)
        assertEquals(session(), dao.backupSessions().single())
    }

    @Test fun legacyRecordingKeysNormalizeTogetherAcrossHistoryEventsAndSessions() = runBlocking {
        val row = track().copy(key = "old::artist", sourceId = "audio000001", sourceType = "YOUTUBE_MUSIC")
        val backup = document(BackupHistory(listOf(row), listOf(event(100).copy(trackKey = row.key)),
            listOf(session().copy(trackKey = row.key, sourceId = row.sourceId))))
        repository.restoreFromJson(backup.toString())
        assertEquals("yt:audio000001", db.playbackHistoryDao().backupHistory().single().key)
        assertEquals("yt:audio000001", db.playbackHistoryDao().backupEvents().single().trackKey)
        assertEquals("yt:audio000001", db.playbackHistoryDao().backupSessions().single().trackKey)
    }

    @Test fun malformedHistoryCannotPartiallyImportOrOverflowListeningTotals() = runBlocking {
        val valid = document(BackupHistory(listOf(track()), listOf(event(100)), listOf(session())))
        val invalid = listOf(
            JSONObject(valid.toString()).apply { getJSONArray("playbackHistory").getJSONObject(0).put("playCount", -1) },
            JSONObject(valid.toString()).apply { getJSONArray("playbackEvents").getJSONObject(0).put("trackKey", "missing") },
            JSONObject(valid.toString()).apply { getJSONArray("listeningSessions").getJSONObject(0).put("listenedMs", -1) },
            document(BackupHistory(listOf(track()), emptyList(), listOf(session(), session()))),
            document(BackupHistory(listOf(track()), emptyList(), listOf(session(heard = Long.MAX_VALUE), session(heard = Long.MAX_VALUE).copy(id = "other")))),
            document(BackupHistory(listOf(
                track().copy(key = "old", sourceId = "audio000001", sourceType = "YOUTUBE_MUSIC"),
                track().copy(key = "old", sourceId = "video000001", sourceType = "YOUTUBE_MUSIC")), emptyList(), emptyList())),
            document(BackupHistory(listOf(track().copy(sourceId = "audio000001", sourceType = "YOUTUBE_MUSIC")),
                emptyList(), listOf(session().copy(sourceId = "video000001")))),
        )
        invalid.forEach { assertTrue(repository.restoreFromJson(it.toString()) is RestoreResult.Failure) }
        assertTrue(db.playbackHistoryDao().backupHistory().isEmpty())
        assertTrue(db.playbackHistoryDao().backupEvents().isEmpty())
        assertTrue(db.playbackHistoryDao().backupSessions().isEmpty())
    }

    private fun track(count: Int = 1) = PlaybackHistoryEntity("local:a", "Song", "Artist", "Album", "3:00", 2, null, null, 200, playCount = count)
    private fun event(at: Long) = PlaybackEventEntity(trackKey = "local:a", playedAt = at)
    private fun session(heard: Long = 30_000, outcome: String = "OPEN") = ListeningSessionEntity("session-a", "local:a", null, "Artist", 100, heard, 180_000, outcome)
    private fun document(history: BackupHistory) = JSONObject().put("schemaVersion", 3).put("historyIncluded", true)
        .put("likedSongs", JSONArray()).put("playlists", JSONArray()).put("followedArtists", JSONArray())
        .apply { putBackupHistory(history) }
}
