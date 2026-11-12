package com.example

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
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
class PortableSettingsRestoreTest {
    private lateinit var context: Context
    private lateinit var db: MuseFlowDatabase
    private lateinit var access: DataStorePortableSettings
    private lateinit var repository: BackupRepository

    @Before fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, MuseFlowDatabase::class.java).allowMainThreadQueries().build()
        access = DataStorePortableSettings(context)
        repository = BackupRepository(context, db, access)
        context.appSettingsDataStore.edit { it.clear() }
        context.themeDataStore.edit { it.clear() }
        context.equalizerDataStore.edit { it.clear() }
        context.libraryViewsDataStore.edit { it.clear() }
        repository.setIncludeSettingsEnabled(false)
    }

    @After fun tearDown() = runBlocking {
        context.appSettingsDataStore.edit { it.clear() }
        context.themeDataStore.edit { it.clear() }
        context.equalizerDataStore.edit { it.clear() }
        context.libraryViewsDataStore.edit { it.clear() }
        repository.setIncludeSettingsEnabled(false)
        db.close()
    }

    @Test fun settingsExportIsOptInAndNeverCopiesPrivateKeysOrQueries() = runBlocking {
        context.appSettingsDataStore.edit { it[stringPreferencesKey("font_style")] = "Serif"; it[stringPreferencesKey("auth_token")] = "DO_NOT_EXPORT" }
        context.libraryViewsDataStore.edit { it[stringPreferencesKey("sections")] = encodeLibraryViews(mapOf(
            LibrarySection.entries.first() to LibraryViewPreference(grid = true, query = "PRIVATE_QUERY"))) }
        assertFalse(JSONObject(repository.buildBackupJson()).getBoolean("settingsIncluded"))
        repository.setIncludeSettingsEnabled(true)
        val copy = repository.buildBackupJson()
        assertFalse(copy.contains("DO_NOT_EXPORT"))
        assertFalse(copy.contains("PRIVATE_QUERY"))
        val parsed = parseBackupDocument(copy).settings!!
        assertEquals("Serif", parsed.stores.getValue("app")["font_style"])
        assertEquals(4, parsed.stores.size)
    }

    @Test fun settingsRestoreReplacesPortableValuesButPreservesPrivacyAndBackupChoices() = runBlocking {
        context.appSettingsDataStore.edit { it[booleanPreferencesKey("disable_screenshots")] = true }
        val result = repository.restoreFromJson(document().toString()) as RestoreResult.Success
        assertTrue(result.settingsRestored)
        assertFalse(result.settingsPending)
        assertNull(db.settingsRestoreJournalDao().pending())
        assertEquals("Serif", context.appSettingsDataStore.data.first()[stringPreferencesKey("font_style")])
        assertEquals(true, context.appSettingsDataStore.data.first()[booleanPreferencesKey("disable_screenshots")])
        assertEquals("Light", context.themeDataStore.data.first()[stringPreferencesKey("theme_mode")])
        assertEquals(true, context.equalizerDataStore.data.first()[booleanPreferencesKey("eq_enabled")])
        assertFalse(repository.includeSettingsEnabled.first())
    }

    @Test fun missingPortableValuesResetToDefaultsWhileExcludedKeysRemain() = runBlocking {
        context.appSettingsDataStore.edit { it[stringPreferencesKey("font_style")] = "Serif"; it[booleanPreferencesKey("disable_screenshots")] = true }
        val empty = PortableSettings(PortableSettingsFormat.rules.mapValues { emptyMap() })
        repository.restoreFromJson(document(empty).toString())
        assertNull(context.appSettingsDataStore.data.first()[stringPreferencesKey("font_style")])
        assertEquals(true, context.appSettingsDataStore.data.first()[booleanPreferencesKey("disable_screenshots")])
        assertEquals(AppFontStyle.System, AppSettingsRepository(context).state.first().fontStyle)
    }

    @Test fun failureAfterOneStoreCommitsLibraryAndFreshRepositoryResumesOnlyRemainingStores() = runBlocking {
        val applied = mutableListOf<String>()
        val broken = object : PortableSettingsAccess by access {
            override suspend fun apply(store: String, values: Map<String, Any>) {
                if (store == "theme") throw IOException("injected storage failure")
                access.apply(store, values); applied.add(store)
            }
        }
        val result = BackupRepository(context, db, broken).restoreFromJson(document().toString()) as RestoreResult.Success
        assertTrue(result.settingsPending)
        assertFalse(result.settingsRestored)
        assertEquals(listOf("app"), applied)
        assertEquals("app", db.settingsRestoreJournalDao().pending()!!.completedStores)
        assertEquals(1, db.likedSongDao().backupSnapshot().size)
        context.appSettingsDataStore.edit { it[stringPreferencesKey("font_style")] = "Monospace" }
        val resumed = mutableListOf<String>()
        val fresh = BackupRepository(context, db, object : PortableSettingsAccess by access {
            override suspend fun apply(store: String, values: Map<String, Any>) { access.apply(store, values); resumed.add(store) }
        })
        assertTrue(fresh.recoverPendingSettings())
        assertEquals(listOf("theme", "equalizer", "library"), resumed)
        assertEquals("Monospace", context.appSettingsDataStore.data.first()[stringPreferencesKey("font_style")])
        assertNull(db.settingsRestoreJournalDao().pending())
        assertEquals(1, db.likedSongDao().backupSnapshot().size)
    }

    @Test fun interruptionBetweenStoreWriteAndCheckpointReplaysIdempotently() = runBlocking {
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_checkpoint BEFORE UPDATE ON settings_restore_journal BEGIN SELECT RAISE(ABORT, 'injected crash boundary'); END")
        val result = repository.restoreFromJson(document().toString()) as RestoreResult.Success
        assertTrue(result.settingsPending)
        assertEquals("", db.settingsRestoreJournalDao().pending()!!.completedStores)
        assertEquals("Serif", context.appSettingsDataStore.data.first()[stringPreferencesKey("font_style")])
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_checkpoint")
        assertTrue(BackupRepository(context, db, access).recoverPendingSettings())
        assertEquals(1, db.likedSongDao().backupSnapshot().size)
        assertNull(db.settingsRestoreJournalDao().pending())
    }

    @Test fun journalInsertFailureRollsBackLibraryBeforeAnySettingsWrite() = runBlocking {
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_journal BEFORE INSERT ON settings_restore_journal BEGIN SELECT RAISE(ABORT, 'injected failure'); END")
        assertTrue(repository.restoreFromJson(document().toString()) is RestoreResult.Failure)
        assertTrue(db.likedSongDao().backupSnapshot().isEmpty())
        assertNull(context.appSettingsDataStore.data.first()[stringPreferencesKey("font_style")])
        assertNull(db.settingsRestoreJournalDao().pending())
    }

    @Test fun cancellationAfterCommitLeavesRecoverableSettingsWithoutReimportingLibrary() = runBlocking {
        val interrupted = BackupRepository(context, db, object : PortableSettingsAccess by access {
            override suspend fun apply(store: String, values: Map<String, Any>) {
                if (store == "theme") throw CancellationException("simulated process interruption")
                access.apply(store, values)
            }
        })
        try { interrupted.restoreFromJson(document().toString()); fail("Expected cancellation") }
        catch (_: CancellationException) { }
        assertEquals(1, db.likedSongDao().backupSnapshot().size)
        assertEquals("app", db.settingsRestoreJournalDao().pending()!!.completedStores)
        assertFalse(interrupted.settingsRestoreWorking.value)
        assertTrue(repository.recoverPendingSettings())
        assertEquals(1, db.likedSongDao().backupSnapshot().size)
    }

    @Test fun explicitlyKeepingCurrentSettingsDoesNotDeleteLibraryOrUndoAppliedStores() = runBlocking {
        val broken = BackupRepository(context, db, object : PortableSettingsAccess by access {
            override suspend fun apply(store: String, values: Map<String, Any>) {
                if (store != "app") throw IOException("failure")
                access.apply(store, values)
            }
        })
        broken.restoreFromJson(document().toString())
        assertTrue(broken.keepCurrentSettings())
        assertNull(db.settingsRestoreJournalDao().pending())
        assertEquals("Serif", context.appSettingsDataStore.data.first()[stringPreferencesKey("font_style")])
        assertNull(context.themeDataStore.data.first()[stringPreferencesKey("theme_mode")])
        assertEquals(1, db.likedSongDao().backupSnapshot().size)
    }

    @Test fun exportWaitsForTheWholeRestoreAndCannotCaptureAnIntermediateSettingsMix() = runBlocking {
        val reached = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val slow = BackupRepository(context, db, object : PortableSettingsAccess by access {
            override suspend fun apply(store: String, values: Map<String, Any>) {
                access.apply(store, values)
                if (store == "app") { reached.complete(Unit); release.await() }
            }
        })
        val restoring = async { slow.restoreFromJson(document().toString()) }
        reached.await()
        val export = async(start = CoroutineStart.UNDISPATCHED) { slow.buildBackupJson(includeSettings = true) }
        try { assertFalse(export.isCompleted) } finally { release.complete(Unit) }
        restoring.await()
        val copy = parseBackupDocument(export.await()).settings!!
        assertEquals("Serif", copy.stores.getValue("app")["font_style"])
        assertEquals("Light", copy.stores.getValue("theme")["theme_mode"])
    }

    @Test fun malformedOrPrivateSettingsAreRejectedBeforeLibraryChanges() = runBlocking {
        val invalid = listOf(
            document().apply { getJSONObject("settings").getJSONObject("stores").getJSONObject("app").put("disable_screenshots", false) },
            document().apply { getJSONObject("settings").getJSONObject("stores").getJSONObject("app").put("font_style", "Unknown") },
            document().apply { getJSONObject("settings").getJSONObject("stores").getJSONObject("app").put("lyrics_text_size", 100000) },
            document().apply { getJSONObject("settings").getJSONObject("stores").remove("theme") },
        )
        invalid.forEach { assertTrue(repository.restoreFromJson(it.toString()) is RestoreResult.Failure) }
        assertTrue(db.likedSongDao().backupSnapshot().isEmpty())
        assertNull(db.settingsRestoreJournalDao().pending())
    }

    @Test fun oldBackupsDoNotResetSettingsAndPreviewDoesNotApplyThem() = runBlocking {
        context.appSettingsDataStore.edit { it[stringPreferencesKey("font_style")] = "Monospace" }
        repository.restoreFromJson(JSONObject().put("schemaVersion", 1).put("likedSongs", JSONArray()).put("playlists", JSONArray()).toString())
        val preview = repository.prepareRestore(document().toString()).preview
        assertTrue(preview.includesSettings)
        assertEquals("Monospace", context.appSettingsDataStore.data.first()[stringPreferencesKey("font_style")])
        assertNull(db.settingsRestoreJournalDao().pending())
    }

    private fun settings() = PortableSettings(linkedMapOf(
        "app" to mapOf("font_style" to "Serif", "lyrics_text_size" to 24),
        "theme" to mapOf("theme_mode" to "Light"),
        "equalizer" to mapOf("eq_enabled" to true, "eq_band_levels" to "100,0,-100"),
        "library" to emptyMap(),
    ))
    private fun document(settings: PortableSettings = settings()) = JSONObject()
        .put("schemaVersion", 4).put("historyIncluded", false).put("settingsIncluded", true).put("settings", settings.toJson())
        .put("likedSongs", JSONArray().put(JSONObject().put("key", "local:kept").put("title", "Kept")))
        .put("playlists", JSONArray()).put("followedArtists", JSONArray())
}
