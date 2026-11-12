package com.example

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalReducedMotion
import com.example.ui.utils.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MicroMotionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tapDispatchesOnceWithoutWaitingForReleaseAnimation() {
        var clicks = 0
        compose.setContent { Box(Modifier.size(80.dp).testTag("target").feedbackClickable { clicks++ }) }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("target").performTouchInput { down(center); advanceEventTime(80); up() }
        compose.runOnIdle { assertEquals(1, clicks) }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun longPressDoesNotAlsoDispatchTap() {
        var clicks = 0
        var holds = 0
        compose.setContent { Box(Modifier.size(80.dp).testTag("target")
            .feedbackClickable(onLongClick = { holds++ }) { clicks++ }) }
        compose.onNodeWithTag("target").performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(1, holds); assertEquals(0, clicks) }
    }

    @Test fun cancelledPressDoesNotTriggerAnAction() {
        var clicks = 0
        compose.setContent { Box(Modifier.size(80.dp).testTag("target").feedbackClickable { clicks++ }) }
        compose.onNodeWithTag("target").performTouchInput { down(center); advanceEventTime(50); cancel() }
        compose.runOnIdle { assertEquals(0, clicks) }
    }

    @Test fun disabledNativeButtonKeepsItsDisabledSemantics() {
        var clicks = 0
        compose.setContent { MicroIconButton({ clicks++ }, Modifier.testTag("target"), enabled = false) { Text("Play") } }
        compose.onNodeWithTag("target").assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, clicks) }
    }

    @Test fun iconHasOnlyCurrentAccessibleActionDuringCrossfade() {
        val playing = mutableStateOf(false)
        compose.setContent { MotionIcon(if (playing.value) Icons.Default.Pause else Icons.Default.PlayArrow,
            if (playing.value) "Pause" else "Play") }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { playing.value = true; androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeBy(48)
        compose.onNodeWithContentDescription("Pause").assertExists()
        compose.onNodeWithContentDescription("Play").assertDoesNotExist()
    }

    @Test fun countExposesOnlyCurrentValueDuringCrossfade() {
        val count = mutableStateOf("1 selected")
        compose.setContent { MotionLabel(count.value, Modifier.testTag("count")) }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { count.value = "2 selected"; androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeBy(48)
        compose.onNodeWithTag("count").assertTextEquals("2 selected")
        compose.onNodeWithText("1 selected").assertDoesNotExist()
    }

    @Test fun changingSectionRetainsOnlyTheCurrentContentTree() {
        val section = mutableStateOf("Playlists")
        compose.setContent { Box(Modifier.contentMotion(section.value)) { Text(section.value) } }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { section.value = "Following"; androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications() }
        compose.mainClock.advanceTimeBy(48)
        compose.onNodeWithText("Following").assertExists()
        compose.onNodeWithText("Playlists").assertDoesNotExist()
    }

    @Test fun reducedMotionStillAllowsImmediateRepeatedActions() {
        var clicks = 0
        compose.setContent { CompositionLocalProvider(LocalReducedMotion provides true) {
            Box(Modifier.size(80.dp).testTag("target").feedbackClickable { clicks++ })
        } }
        repeat(3) { compose.onNodeWithTag("target").performTouchInput { click() } }
        compose.runOnIdle { assertEquals(3, clicks) }
    }

    @Test fun settingsSwitchHasOneActionInsteadOfNestedDuplicateToggles() {
        var calls = 0
        val checked = mutableStateOf(false)
        compose.setContent { com.example.ui.component.SwitchPreference(
            title = "Motion setting", subtitle = "Shared settings row", checked = checked.value,
            onCheckedChange = { calls++; checked.value = it }) }
        compose.onNodeWithText("Motion setting").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, calls); assertEquals(true, checked.value) }
    }
}
