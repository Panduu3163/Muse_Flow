package com.example

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.example.ui.component.PlayerBufferingIndicator
import com.example.ui.theme.LocalReducedMotion
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlayerBufferingIndicatorTest {
    @get:Rule val compose = createComposeRule()
    @Test fun shortBufferDoesNotFlashButSustainedBufferingIsVisible() {
        compose.setContent { CompositionLocalProvider(LocalReducedMotion provides true) {
            PlayerBufferingIndicator(true, "recording")
        } }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(160)
        compose.onNodeWithText("Buffering").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Buffering").assertExists()
    }
    @Test fun playbackReadyCancelsPendingIndicator() {
        val buffering = mutableStateOf(true)
        compose.setContent { CompositionLocalProvider(LocalReducedMotion provides true) {
            PlayerBufferingIndicator(buffering.value, "recording")
        } }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(100)
        compose.runOnIdle { buffering.value = false; Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("Buffering").assertDoesNotExist()
    }
    @Test fun nextRecordingDoesNotInheritAnOldLoadingIndicator() {
        val recording = mutableStateOf("first")
        compose.setContent { CompositionLocalProvider(LocalReducedMotion provides true) {
            PlayerBufferingIndicator(true, recording.value)
        } }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("Buffering").assertExists()
        compose.runOnIdle { recording.value = "second"; Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeBy(48)
        compose.onNodeWithText("Buffering").assertDoesNotExist()
    }
    @Test fun coverGestureCanSuppressTheBadgeWithoutFalsifyingPlaybackState() {
        val allow = mutableStateOf(false)
        compose.setContent { CompositionLocalProvider(LocalReducedMotion provides true) {
            PlayerBufferingIndicator(true, "recording", allowDisplay = allow.value)
        } }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("Buffering").assertDoesNotExist()
        compose.runOnIdle { allow.value = true; Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeBy(48)
        compose.onNodeWithText("Buffering").assertExists()
    }
}
