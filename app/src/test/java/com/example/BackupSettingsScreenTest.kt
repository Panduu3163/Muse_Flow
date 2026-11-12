package com.example

import android.app.Application
import android.net.Uri
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.BackupSettingsScreen
import com.example.ui.theme.MuseFlowTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
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
class BackupSettingsScreenTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    private var db: MuseFlowDatabase? = null
    @After fun tearDown() { store.clear(); db?.close(); RuntimeEnvironment.setFontScale(1f) }

    @Test fun externalCopyAndRecoveryActionsRemainReachableAtTwoHundredPercentText() {
        RuntimeEnvironment.setFontScale(2f)
        val app = ApplicationProvider.getApplicationContext<Application>()
        val database = Room.inMemoryDatabaseBuilder(app, MuseFlowDatabase::class.java).allowMainThreadQueries().build().also { db = it }
        var copies = 0
        val destination = object : ExternalBackupDestination {
            override suspend fun retain(tree: Uri) = "My backup folder"
            override suspend fun release(tree: Uri) {}
            override suspend fun writeVerified(tree: Uri, json: String): Uri { copies++; return tree }
        }
        val repository = BackupRepository(app, database, externalDestination = destination)
        runBlocking {
            repository.setIncludeHistoryEnabled(false); repository.setIncludeSettingsEnabled(false)
            repository.setAutomaticDestination(Uri.parse("content://backups/tree/user"))
        }
        val model = BackupViewModel(app, repository).also { store.put("backup", it) }
        compose.setContent { MuseFlowTheme(darkTheme = true) { Surface {
            BackupSettingsScreen(onBack = {}, backupViewModel = model)
        } } }
        compose.onNodeWithText("Automatic backup folder").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Back up now").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertTrue("The row must invoke the backup action", model.busy.value || model.outcome.value != null) }
        compose.waitUntil(10_000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            model.outcome.value != null
        }
        compose.onNodeWithText("Backup saved").assertIsDisplayed()
        compose.onNodeWithTag("backup_outcome_ok").performClick()
        compose.runOnIdle { assertEquals(1, copies) }
        compose.onNodeWithText("Restore internal recovery copy").performScrollTo().assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/18-backup-destination-large-text.png")
    }
}
