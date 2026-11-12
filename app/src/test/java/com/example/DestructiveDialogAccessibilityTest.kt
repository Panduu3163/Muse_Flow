package com.example

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.input.key.Key
import com.example.ui.component.BlurredConfirmDialog
import com.example.ui.theme.MuseFlowTheme
import com.github.takahirom.roborazzi.captureRoboImage
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
class DestructiveDialogAccessibilityTest {
    @get:Rule val compose = createComposeRule()
    @After fun resetScale() { RuntimeEnvironment.setFontScale(1f) }

    @Test fun modalCancelHasKeyboardFocusAndLargeTextKeepsActionsReachable() {
        RuntimeEnvironment.setFontScale(2f)
        val visible = mutableStateOf(false); var deleted = 0
        lateinit var inputMode: InputModeManager
        compose.setContent { MuseFlowTheme(darkTheme = true, pureBlack = true) {
            inputMode = LocalInputModeManager.current
            Button(onClick = { visible.value = true }) { Text("Open deletion") }
            if (visible.value) BlurredConfirmDialog("Delete playlist?", "A long playlist description. ".repeat(60), "Delete playlist",
                { deleted++; visible.value = false }, { visible.value = false })
        } }
        compose.runOnIdle {
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
            inputMode.requestInputMode(InputMode.Keyboard)
        }
        // A pointer tap switches Android back to touch mode, where native buttons do not take
        // keyboard focus. Open with an actual keyboard action for the keyboard-focus contract.
        compose.onNodeWithText("Open deletion").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        compose.onNodeWithText("Open deletion").performKeyInput { pressKey(Key.Enter) }
        // Robolectric hosts the separate dialog window without a real window manager focus
        // event. Deliver the platform event before checking focus inside that window.
        compose.runOnIdle {
            val decor = org.robolectric.shadows.ShadowDialog.getLatestDialog().window!!.decorView
            val root = org.robolectric.util.ReflectionHelpers.callInstanceMethod<Any>(decor, "getViewRootImpl")
            val shadowRoot: org.robolectric.shadows.ShadowViewRootImpl = org.robolectric.shadow.api.Shadow.extract(root)
            shadowRoot.callWindowFocusChanged(true)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("blurred_confirm_cancel").assertIsFocused().assertIsDisplayed()
        compose.onNodeWithTag("blurred_confirm_confirm").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/18-delete-confirm-amoled-large-text.png")
        compose.onNodeWithTag("blurred_confirm_cancel").performClick()
        compose.onNodeWithTag("blurred_confirm_dialog").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, deleted) }
    }
}
