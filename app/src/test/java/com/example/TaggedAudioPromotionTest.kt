package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 36])
class TaggedAudioPromotionTest {
    @Test fun completedTagCopyReplacesTheOriginalWithoutAnInPlaceCopy() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val target = File(context.cacheDir, "original-audio.part").apply { writeText("original audio") }
        val working = File(context.cacheDir, "tagged-audio.part.m4a").apply { writeText("tagged audio") }
        promoteTaggedAudio(working, target)
        assertEquals("tagged audio", target.readText())
        assertFalse(working.exists())
    }
    @Test fun missingOrEmptyTagOutputLeavesOriginalAudioIntact() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val target = File(context.cacheDir, "keep-audio.part").apply { writeText("original audio") }
        val working = File(context.cacheDir, "empty-tag.part.m4a").apply { writeText("") }
        try { promoteTaggedAudio(working, target); fail("Empty tag output must fail") } catch (_: IllegalStateException) { }
        assertEquals("original audio", target.readText())
        working.delete()
        try { promoteTaggedAudio(working, target); fail("Missing tag output must fail") } catch (_: IllegalStateException) { }
        assertEquals("original audio", target.readText())
    }
}
