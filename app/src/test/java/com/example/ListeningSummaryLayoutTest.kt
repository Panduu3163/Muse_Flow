package com.example

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import com.example.ui.screens.ListeningSummarySheet
import com.example.ui.theme.MuseFlowTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xxhdpi")
class ListeningSummaryLayoutTest {
    @get:Rule val compose = createComposeRule()

    private fun check(font: AppFontStyle, dark: Boolean, pureBlack: Boolean = false) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                MuseFlowTheme(darkTheme = dark, pureBlack = pureBlack, fontStyle = font) {
                    ListeningSummarySheet({}, 12345, 65, 125, 45, 30)
                }
            }
        }
        compose.onNodeWithText("RECORDED LISTENING").assertExists()
        compose.onNodeWithText("Estimate from track starts").performScrollTo().assertIsDisplayed()
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithText("Estimate from track starts", useUnmergedTree = true)
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        org.junit.Assert.assertTrue(layouts.isNotEmpty())
        compose.onNode(isRoot() and hasAnyDescendant(hasText("RECORDED LISTENING")))
            .captureRoboImage("build/outputs/roborazzi/summary-${font.name}.png")
        // Paragraph layout may retain unused cached width after a wrapping parent remeasures.
        // Check actual line extents/ellipsis rather than counting that blank width as clipping.
        layouts.forEach { layout ->
            org.junit.Assert.assertTrue(layout.multiParagraph.height <= layout.size.height + 1f)
            for (line in 0 until layout.lineCount) {
                org.junit.Assert.assertFalse(layout.isLineEllipsized(line))
                org.junit.Assert.assertTrue(layout.getLineRight(line) <= layout.size.width + 1f)
            }
        }
        compose.onNodeWithText("205h 45m").performScrollTo().assertIsDisplayed()
        compose.onNode(isRoot() and hasAnyDescendant(hasText("RECORDED LISTENING")))
            .captureRoboImage("build/outputs/roborazzi/summary-${font.name}.png")
    }

    @Test fun systemLightLargeTextScrollsToHistoricalEstimate() = check(AppFontStyle.System, false)
    @Test fun serifDarkLargeTextScrollsToHistoricalEstimate() = check(AppFontStyle.Serif, true)
    @Test fun monoDarkLargeTextScrollsToHistoricalEstimate() = check(AppFontStyle.Monospace, true)
    @Test fun dotMatrixAmoledLargeTextScrollsToHistoricalEstimate() = check(AppFontStyle.DotMatrix, true, true)
}
