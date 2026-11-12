package com.example

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.ui.component.HomeHeader
import com.example.ui.component.MuseFlowNavBar
import com.example.ui.theme.LocalReducedMotion
import com.example.ui.theme.MuseFlowTheme
import com.github.takahirom.roborazzi.captureRoboImage
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xxhdpi")
class HomeHeaderNavigationVisualTest {
    @get:Rule val compose = createComposeRule()

    @Test fun homeHasOriginalDirectActionsWithoutSettingsOrOverflow() {
        var tapped = ""
        compose.setContent { MuseFlowTheme { Surface { HomeHeader({ tapped = "history" }, { tapped = "together" }, { tapped = "stats" }) } } }
        compose.onNodeWithContentDescription("Settings").assertDoesNotExist()
        compose.onNodeWithContentDescription("Home options").assertDoesNotExist()
        compose.onNodeWithContentDescription("History").performClick()
        assertEquals("history", tapped)
        compose.onNodeWithContentDescription("Listen together").performClick()
        assertEquals("together", tapped)
        compose.onNodeWithContentDescription("Stats").performClick()
        assertEquals("stats", tapped)
    }

    private fun preview(dark: Boolean, reduced: Boolean, fontScale: Float) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalReducedMotion provides reduced, LocalDensity provides Density(density.density, fontScale)) {
                MuseFlowTheme(darkTheme = dark) {
                    val haze = rememberHazeState()
                    var route by remember { mutableStateOf(Routes.HOME) }
                    Box(Modifier.fillMaxSize()) {
                        Surface(Modifier.fillMaxSize().hazeSource(haze)) {
                            Column {
                                HomeHeader({}, {}, {})
                                Canvas(Modifier.fillMaxWidth().weight(1f)) {
                                    val colors = listOf(Color(0xFF6A4FA3), Color(0xFF346B7B), Color(0xFFBB8060))
                                    repeat(12) { i -> drawRect(colors[i % colors.size], androidx.compose.ui.geometry.Offset(0f, size.height * i / 12), androidx.compose.ui.geometry.Size(size.width, size.height / 12 + 1)) }
                                }
                            }
                        }
                        MuseFlowNavBar(TopLevelDestination.entries, route, { route = it.route }, Modifier.align(Alignment.BottomCenter), haze)
                    }
                }
            }
        }
        compose.onNodeWithTag("nav_item_${Routes.SETTINGS}").performClick().assertIsSelected()
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText("Settings", useUnmergedTree = true)
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        org.junit.Assert.assertTrue(layouts.isNotEmpty())
        layouts.forEach { layout ->
            for (line in 0 until layout.lineCount) org.junit.Assert.assertFalse(layout.isLineEllipsized(line))
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/navigation-settings-${if (dark) "dark" else "light"}-${if (reduced) "reduced" else "motion"}.png")
        compose.onNodeWithTag("nav_item_${Routes.HOME}").performClick().assertIsSelected()
        compose.onNodeWithText("Home").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/navigation-${if (dark) "dark" else "light"}-${if (reduced) "reduced" else "motion"}.png")
    }

    @Test fun glassDarkKeepsSharpLabelsAndAnimatedSelection() = preview(true, false, 1f)
    @Test fun glassLightLargeTextRespectsReducedMotion() = preview(false, true, 1.6f)

    @Test @Config(sdk = [24]) fun android24HasReadableFallback() = preview(true, true, 1f)
}
