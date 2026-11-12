package com.example

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.example.ui.component.MuseFlowNavBar
import com.example.ui.theme.MuseFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TabNavigationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var controller: NavHostController

    private fun start(route: String) {
        compose.setContent {
            val nav = rememberNavController()
            controller = nav
            val entry by nav.currentBackStackEntryAsState()
            MuseFlowTheme {
                Column {
                    NavHost(nav, route, Modifier.weight(1f)) {
                        TopLevelDestination.entries.forEach { destination -> composable(destination.route) {
                            val remembered = rememberSaveable { "${destination.route} saved" }
                            var count by rememberSaveable { mutableIntStateOf(0) }
                            Column {
                                Text("$remembered count=$count")
                                Button(onClick = { count++ }) { Text("Increment ${destination.label}") }
                            }
                        } }
                    }
                    MuseFlowNavBar(TopLevelDestination.entries, entry?.destination?.route, { nav.navigateToTab(it.route) })
                }
            }
        }
    }

    @Test fun directSettingsShortcutCannotTrapHomeOrReselection() {
        start(Routes.HOME)
        repeat(3) {
            compose.runOnIdle { controller.navigate(Routes.SETTINGS) }
            compose.onNodeWithTag("nav_item_${Routes.HOME}").performClick()
            compose.onNodeWithText("${Routes.HOME} saved count=0").assertIsDisplayed()
            compose.runOnIdle { assertEquals(Routes.HOME, controller.currentDestination?.route) }
            compose.onNodeWithTag("nav_item_${Routes.HOME}").performClick()
            compose.onNodeWithText("${Routes.HOME} saved count=0").assertIsDisplayed()
        }
    }

    @Test fun defaultSettingsTabAlsoReturnsToItsActualStartEntry() {
        start(Routes.SETTINGS)
        compose.runOnIdle { controller.navigate(Routes.HOME) }
        compose.onNodeWithTag("nav_item_${Routes.SETTINGS}").performClick()
        compose.onNodeWithText("${Routes.SETTINGS} saved count=0").assertIsDisplayed()
    }

    @Test fun switchingTabsPreservesTheirOwnSavedContent() {
        start(Routes.HOME)
        compose.onNodeWithTag("nav_item_${Routes.SEARCH}").performClick()
        compose.onNodeWithText("Increment Search").performClick()
        for (route in listOf(Routes.LIBRARY, Routes.SETTINGS, Routes.HOME, Routes.SEARCH)) {
            compose.onNodeWithTag("nav_item_$route").performClick()
            compose.onNodeWithTag("nav_item_$route").assertIsSelected()
        }
        compose.onNodeWithText("${Routes.SEARCH} saved count=1").assertIsDisplayed()
    }
}
