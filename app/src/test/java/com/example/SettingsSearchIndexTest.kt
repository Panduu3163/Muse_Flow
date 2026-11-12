package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SettingsSearchIndexTest {
    @Test fun duplicateTitlesRemainDistinctByDestination() {
        val results = SettingsSearchIndex.search("background style")
        assertEquals(
            setOf(Routes.SETTINGS_PLAYER, Routes.SETTINGS_MINI_PLAYER),
            results.map { it.route }.toSet(),
        )
    }

    @Test fun allIndexedControlsHaveUniqueTargets() {
        val targets = SettingsSearchIndex.entries.map { it.route to it.title }
        assertEquals(targets.size, targets.distinct().size)
        val ids = SettingsSearchIndex.entries.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        assertTrue(SettingsSearchIndex.search("lyrics provider").any {
            it.route == Routes.SETTINGS_LYRICS && it.title == "Lyrics provider order"
        })
    }

    @Test fun commonSearchWordsFindTheOwningControl() {
        assertTrue(SettingsSearchIndex.search("color").any { it.title == "Accent colour" })
        assertTrue(SettingsSearchIndex.search("seek bar").any { it.title == "Progress bar style" })
        assertTrue(SettingsSearchIndex.search("follow system").any { it.id == "appearance.system" })
    }

    /** A new visible preference must not silently disappear from Settings search. */
    @Test fun everyVisiblePreferenceHasASearchTarget() {
        val screens = mapOf(
            "AppearanceSettingsScreen.kt" to Routes.SETTINGS_APPEARANCE,
            "MiniPlayerSettingsScreen.kt" to Routes.SETTINGS_MINI_PLAYER,
            "PlayerSettingsScreen.kt" to Routes.SETTINGS_PLAYER,
            "LyricsSettingsScreen.kt" to Routes.SETTINGS_LYRICS,
            "AudioSettingsScreen.kt" to Routes.SETTINGS_AUDIO,
            "PlaybackSettingsScreen.kt" to Routes.SETTINGS_PLAYBACK,
            "GeneralSettingsScreen.kt" to Routes.SETTINGS_GENERAL,
            "LibrarySectionsSettingsScreen.kt" to Routes.SETTINGS_LIBRARY_SECTIONS,
            "PrivacySettingsScreen.kt" to Routes.SETTINGS_PRIVACY,
            "BackupSettingsScreen.kt" to Routes.BACKUP,
            "StorageSettingsScreen.kt" to Routes.STORAGE,
        )
        val control = Regex("(?:SwitchPreference|ListPreference|SliderPreference|ActionPreference|NavigationPreference)\\(\\s*title\\s*=\\s*\"([^\"]+)\"")
        screens.forEach { (name, route) ->
            val path = File("src/main/java/com/example/ui/screens/$name")
                .takeIf(File::exists) ?: File("app/src/main/java/com/example/ui/screens/$name")
            val titles = control.findAll(path.readText()).map { it.groupValues[1] }.toList()
            val missing = titles.filter { SettingsSearchIndex.idFor(route, it) == null }
            assertTrue("$name has unsearchable controls: $missing", missing.isEmpty())
        }
    }
}
