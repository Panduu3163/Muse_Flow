package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.graphics.Color

class ThemeModeTest {
    @Test fun legacyChoicesKeepTheirAppearance() {
        assertEquals(ThemeMode.Amoled, resolveThemeMode(null, true, true))
        assertEquals(ThemeMode.Light, resolveThemeMode(null, false, false))
        assertEquals(ThemeMode.Dark, resolveThemeMode(null, true, false))
    }

    @Test fun explicitModeOverridesLegacyChoices() {
        assertEquals(ThemeMode.System, resolveThemeMode("System", false, true))
        assertEquals(ThemeMode.Dark, resolveThemeMode("unknown", true, false))
    }

    @Test fun followSystemTracksBothDeviceModes() {
        assertTrue(ThemeMode.System.isDark(true))
        assertFalse(ThemeMode.System.isDark(false))
        assertFalse(ThemeMode.Light.isDark(true))
        assertTrue(ThemeMode.Amoled.isDark(false))
    }

    @Test fun albumArtColorOnlyWinsWhenEnabledAndAvailable() {
        val saved = Color(0xFF7C5CFF)
        val art = Color(0xFF42A87F)
        assertEquals(art, themeSeedColor(saved, art, true))
        assertEquals(saved, themeSeedColor(saved, null, true))
        assertEquals(saved, themeSeedColor(saved, art, false))
    }
}
