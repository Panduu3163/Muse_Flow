package com.example

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.ui.component.*
import com.example.ui.theme.MuseFlowTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class BrowseComponentsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lightThemeLargeTextKeepsRetryVisibleAndActionable() {
        var retried = false
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                MuseFlowTheme(darkTheme = false) {
                    Surface {
                        Column(Modifier.width(360.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            CollectionIdentityHeader("Your music, your collection", "Your playlist")
                            MediaGridCard("A longer collection title", "Artist · Album", null, Icons.Default.MusicNote, {})
                            FeedbackMessage("Showing saved content. Connection unavailable.", onRetry = { retried = true })
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Retry").assertIsDisplayed().performClick()
        assertTrue(retried)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/browse-light-large-text.png")
    }

    @Test fun pausedTrackHasAccessibleStateAndSeparateMenu() {
        compose.setContent {
            MuseFlowTheme {
                Surface(Modifier.width(360.dp)) {
                    TrackRow("Evening session", "Demo artist", null, "3:24", {}, isPlaying = true, onOpenMenu = {})
                }
            }
        }
        compose.onNode(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Paused")).assertExists()
        compose.onNodeWithText("Evening session").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/browse-dark-paused-track.png")
    }
}
