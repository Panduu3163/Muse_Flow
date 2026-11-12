package com.example

import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.example.ui.screens.BackupRestorePreviewDialog
import com.example.ui.theme.MuseFlowTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xxhdpi")
class BackupRestorePreviewDialogTest {
    @get:Rule val compose = createComposeRule()
    @After fun resetFontScale() { RuntimeEnvironment.setFontScale(1f) }

    @Test fun largeTextCanReadCoverageWithoutScrollingTheConfirmAndCancelActionsAway() {
        var restored = 0
        RuntimeEnvironment.setFontScale(2f)
        compose.setContent {
            MuseFlowTheme(darkTheme = false) { Surface {
                BackupRestorePreviewDialog(BackupRestorePreview(12, 3, 2, 4, 5, 1, true, "My saved library with a long backup filename.json")
                    .copy(includesHistory = true, newHistoryTracks = 9, newHistoryEvents = 11, newListeningSessions = 3,
                        includesSettings = true, settingsCount = 12),
                    onRestore = { restored++ }, onCancel = {})
            } }
        }
        compose.onNodeWithTag("backup_restore_confirm").assertIsDisplayed()
        compose.onNodeWithTag("backup_restore_cancel").assertIsDisplayed()
        compose.onNodeWithText("These settings replace", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Not included:", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("backup_restore_confirm").assertIsDisplayed()
        compose.onNodeWithTag("backup_restore_preview").captureRoboImage("build/outputs/roborazzi/18-backup-settings-preview-large-text.png")
        compose.onNodeWithTag("backup_restore_confirm").performClick()
        compose.runOnIdle { assertEquals(1, restored) }
    }

    @Test fun olderBackupDisclosesMissingArtistCoverageAndCancelDoesNotConfirm() {
        var cancelled = 0
        var restored = 0
        compose.setContent { MuseFlowTheme(darkTheme = true) { Surface {
            BackupRestorePreviewDialog(BackupRestorePreview(1, 0, 0, 0, 0, 0, false, "Old backup.json"),
                onRestore = { restored++ }, onCancel = { cancelled++ })
        } } }
        compose.onNodeWithText("Followed artists are not included", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("backup_restore_preview").captureRoboImage("build/outputs/roborazzi/18-backup-history-preview-legacy.png")
        compose.onNodeWithTag("backup_restore_cancel").performClick()
        compose.runOnIdle { assertEquals(1, cancelled); assertEquals(0, restored) }
    }
}
