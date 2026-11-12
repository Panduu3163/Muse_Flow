package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LyricsCacheStoreTest {
    @Test fun timedLyricsSurviveStoreRecreation() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteFile("lyrics_cache_v1.json")
        val expected = LyricsLookup(
            LyricsResult.Synced(listOf(LyricLine(1200, "愛してる",
                listOf(LyricWord(1200, 1500, "愛し"), LyricWord(1500, 1900, "てる"))))),
            "BetterLyrics",
        )
        LyricsCacheStore(context).put("song-one", expected)
        assertEquals(expected, LyricsCacheStore(context).get("song-one"))
    }

    @Test fun failuresAreNeverStoredAndRetryRemovalIsDurable() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteFile("lyrics_cache_v1.json")
        val store = LyricsCacheStore(context)
        store.put("error", LyricsLookup(LyricsResult.Error("offline"), null))
        assertNull(LyricsCacheStore(context).get("error"))
        store.put("plain", LyricsLookup(LyricsResult.PlainOnly("One line"), "source"))
        store.remove("plain")
        assertNull(store.get("plain"))
        assertNull(LyricsCacheStore(context).get("plain"))
    }

    @Test fun replacingAndRemovingOneEntryPreservesTheOthers() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteFile("lyrics_cache_v1.json")
        val store = LyricsCacheStore(context)
        store.put("a", LyricsLookup(LyricsResult.PlainOnly("old"), "source"))
        store.put("b", LyricsLookup(LyricsResult.PlainOnly("keep"), "source"))
        store.put("a", LyricsLookup(LyricsResult.PlainOnly("new"), "source"))
        assertEquals(LyricsResult.PlainOnly("new"), LyricsCacheStore(context).get("a")?.result)
        store.remove("a")
        assertNull(LyricsCacheStore(context).get("a"))
        assertEquals(LyricsResult.PlainOnly("keep"), LyricsCacheStore(context).get("b")?.result)
    }
}
