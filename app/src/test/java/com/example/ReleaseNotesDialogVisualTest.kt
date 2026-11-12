package com.example

import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.example.ui.screens.ReleaseNotesDialog
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
class ReleaseNotesDialogVisualTest {
    @get:Rule val compose = createComposeRule()
    @After fun resetFontScale() { RuntimeEnvironment.setFontScale(1f) }
    @Test fun largeTextCanReadTheLastFullNoteAndContinueWithoutScrollingTheButtonAway() {
        var closed = 0
        RuntimeEnvironment.setFontScale(2f)
        compose.setContent {
                MuseFlowTheme(darkTheme = false) { Surface { ReleaseNotesDialog(onClose = { closed++ }) } }
        }
        compose.onNodeWithTag("changelog_continue").assertIsDisplayed()
        compose.onNodeWithTag("changelog_full_toggle").performClick()
        compose.onNodeWithTag("release_notes_list").performScrollToNode(hasText("Release status:", substring = true))
        compose.onNodeWithTag("changelog_continue").assertIsDisplayed()
        compose.onNodeWithTag("changelog_dialog").captureRoboImage("build/outputs/roborazzi/18-changelog-large-text.png")
        compose.onNodeWithTag("changelog_continue").performClick()
        compose.runOnIdle { assertEquals(1, closed) }
    }
    @Test fun publishedNotesDoNotHideTheCompleteIncludedChangelog() {
        compose.setContent { MuseFlowTheme(darkTheme = true) { Surface {
            ReleaseNotesDialog(onClose = {}, title = "Changelog", actionLabel = "Done",
                startExpanded = true, publishedNotes = "# Published release\n- A cached release note")
        } } }
        compose.onNodeWithText("A cached release note").assertExists()
        compose.onNodeWithTag("changelog_source_toggle").performClick()
        compose.onNodeWithText("Included with this build").assertExists()
        compose.onNodeWithTag("release_notes_list").performScrollToNode(hasText("Development checkpoint", substring = true))
        compose.onNodeWithTag("changelog_continue").assertIsDisplayed()
        compose.onNodeWithTag("changelog_dialog").captureRoboImage("build/outputs/roborazzi/18-changelog-source-and-offline.png")
    }
}
