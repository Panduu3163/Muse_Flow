package com.example

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xxhdpi")
class SettingsRecoveryGateTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var context: Context
    private lateinit var db: MuseFlowDatabase
    private val settings = PortableSettings(PortableSettingsFormat.rules.mapValues { emptyMap() })

    @Before fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, MuseFlowDatabase::class.java).allowMainThreadQueries().build()
        db.settingsRestoreJournalDao().insert(SettingsRestoreJournal(token = "pending", payload = settings.toJson().toString(), createdAt = 1))
    }
    @After fun tearDown() { RuntimeEnvironment.setFontScale(1f); db.close() }

    @Test fun startupDoesNotExposeContentUntilAllSettingsAreRecovered() {
        val release = CompletableDeferred<Unit>()
        val repository = BackupRepository(context, db, object : PortableSettingsAccess {
            override suspend fun snapshot() = settings
            override suspend fun apply(store: String, values: Map<String, Any>) { release.await() }
        })
        compose.setContent { SettingsRecoveryGate(repository) { Text("Library ready") } }
        compose.onNodeWithText("Preparing MuseFlow").assertIsDisplayed()
        compose.onNodeWithText("Library ready").assertDoesNotExist()
        release.complete(Unit)
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Library ready").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Library ready").assertIsDisplayed()
        runBlocking { assertNull(db.settingsRestoreJournalDao().pending()) }
    }

    @Test fun failedRecoveryRequiresExplicitKeepConfirmationAtLargeText() {
        RuntimeEnvironment.setFontScale(2f)
        val repository = BackupRepository(context, db, object : PortableSettingsAccess {
            override suspend fun snapshot() = settings
            override suspend fun apply(store: String, values: Map<String, Any>) { throw IOException("storage unavailable") }
        })
        compose.setContent { SettingsRecoveryGate(repository) { Text("Library ready") } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Settings recovery needs attention").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Library ready").assertDoesNotExist()
        compose.onNodeWithText("Keep current settings").performScrollTo().performClick()
        compose.onNodeWithTag("settings_recovery_keep_confirm").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/18-settings-recovery-large-text.png")
        compose.onNodeWithText("Cancel").performClick()
        runBlocking { assertNotNull(db.settingsRestoreJournalDao().pending()) }
        compose.onNodeWithText("Keep current settings").performClick()
        compose.onNodeWithTag("settings_recovery_keep_confirm").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Library ready").fetchSemanticsNodes().isNotEmpty() }
        runBlocking { assertNull(db.settingsRestoreJournalDao().pending()) }
    }
}
