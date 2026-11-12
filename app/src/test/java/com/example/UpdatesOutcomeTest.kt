package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.example.ui.screens.UpdatesScreen
import com.example.ui.theme.MuseFlowTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xxhdpi")
class UpdatesOutcomeTest {
    @get:Rule val compose = createComposeRule()
    private val update = AvailableUpdate("1.9.0", "https://example.invalid/release", "A future test release", "https://example.invalid/app.apk")

    @Before fun cachedNotesAvoidAnyNetworkInThisTest() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("installed_release_notes", Context.MODE_PRIVATE).edit()
            .putString("version", BuildConfig.VERSION_NAME).putString("body", "Cached test notes").commit()
    }

    @Test fun failedDownloadHasVisibleRetryFeedback() {
        compose.setContent { MuseFlowTheme { UpdatesScreen({},
            checkForUpdate = { UpdateChecker.UpdateCheckResult.UpdateAvailable(update) }, downloadUpdate = { _, _ -> UpdateInstallOutcome.Failed(UpdateInstallStage.DOWNLOAD, "Test download failed. Retry.") }) } }
        compose.onNodeWithTag("updates_check_button").performScrollTo().performClick()
        compose.onNodeWithTag("updates_download_now").performScrollTo().performClick()
        compose.onNodeWithTag("updates_download_result").performScrollTo().assertTextEquals("Test download failed. Retry.")
        compose.onNodeWithTag("updates_download_now").assertIsEnabled()
    }

    @Test fun successfulHandoffDoesNotClaimInstallationCompleted() {
        compose.setContent { MuseFlowTheme { UpdatesScreen({},
            checkForUpdate = { UpdateChecker.UpdateCheckResult.UpdateAvailable(update) }, downloadUpdate = { _, _ -> UpdateInstallOutcome.InstallerOpened }) } }
        compose.onNodeWithTag("updates_check_button").performScrollTo().performClick()
        compose.onNodeWithTag("updates_download_now").performScrollTo().performClick()
        compose.onNodeWithTag("updates_download_result").performScrollTo().assertTextEquals("Installer opened. Finish installation in Android.")
    }

    @Test fun cancelledRequestCannotClearBusyStateOrReplaceTheNextRequestResult() {
        val old = CompletableDeferred<Unit>(); val current = CompletableDeferred<Unit>()
        var calls = 0
        compose.setContent { MuseFlowTheme { UpdatesScreen({},
            checkForUpdate = { UpdateChecker.UpdateCheckResult.UpdateAvailable(update) }, downloadUpdate = { _, _ ->
                if (++calls == 1) withContext(NonCancellable) { old.await() } else current.await()
                UpdateInstallOutcome.InstallerOpened
            }) } }
        compose.onNodeWithTag("updates_check_button").performScrollTo().performClick()
        compose.onNodeWithTag("updates_download_now").performScrollTo().performClick()
        compose.onNodeWithTag("updates_cancel_download").performScrollTo().performClick()
        compose.onNodeWithTag("updates_download_result").assertTextEquals("Update download cancelled.")
        compose.onNodeWithTag("updates_download_now").performScrollTo().performClick()
        compose.runOnIdle { old.complete(Unit) }
        compose.waitForIdle()
        compose.onNodeWithTag("updates_download_now").assertIsNotEnabled()
        compose.onNodeWithTag("updates_check_button").assertIsNotEnabled()
        compose.runOnIdle { current.complete(Unit) }
        compose.waitForIdle()
        compose.onNodeWithTag("updates_download_result").performScrollTo().assertTextEquals("Installer opened. Finish installation in Android.")
    }
}
