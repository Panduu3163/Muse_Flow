package com.example

import org.junit.Assert.*
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
class LibraryViewPreferencesTest {
    @Test fun independentlyRoundTripsSections() {
        val views = mapOf(
            LibrarySection.Downloads to LibraryViewPreference(TrackSortOption.ARTIST, ascending = false, grid = true, query = "demo"),
            LibrarySection.Liked to LibraryViewPreference(TrackSortOption.TITLE),
        )
        assertEquals(views, decodeLibraryViews(encodeLibraryViews(views)))
    }

    @Test fun damagedAndUnknownValuesHaveSafeDefaults() {
        assertTrue(decodeLibraryViews("bad json").isEmpty())
        val decoded = decodeLibraryViews("""{"Liked":{"sort":"FUTURE","playlistSort":"FUTURE"},"FutureSection":{}}""")
        assertEquals(mapOf(LibrarySection.Liked to LibraryViewPreference()), decoded)
    }

    @Test fun savedViewsSurviveRepositoryRecreation() = kotlinx.coroutines.test.runTest {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val expected = mapOf(LibrarySection.Downloads to LibraryViewPreference(TrackSortOption.DURATION, ascending = false, query = "demo"))
        LibraryViewPreferences(context).write(expected)
        assertEquals(expected, LibraryViewPreferences(context).read())
    }

    @Test fun boundsPersistedQuerySize() {
        val decoded = decodeLibraryViews(encodeLibraryViews(mapOf(LibrarySection.Liked to LibraryViewPreference(query = "x".repeat(1000)))))
        assertEquals(256, decoded.getValue(LibrarySection.Liked).query.length)
    }
}
