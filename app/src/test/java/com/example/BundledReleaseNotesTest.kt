package com.example

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BundledReleaseNotesTest {
    @Test fun currentNotesDescribeIncludedWorkAndDiscloseDevelopmentStatus() {
        val notes = BundledReleaseNotes.load(ApplicationProvider.getApplicationContext())
        assertTrue(notes.contains("1.8.0"))
        assertTrue(notes.contains("Development checkpoint"))
        assertTrue(notes.contains("external folder"))
        assertTrue(notes.contains("Restarting MuseFlow does not automatically start"))
        assertTrue(notes.contains("This is not the completed 1.8 release"))
    }
    @Test fun priorLongNotesRemainARegressionFixtureForTheCompleteReader() {
        val notes = javaClass.getResourceAsStream("/release_notes_1_7.md")!!.bufferedReader().use { it.readText() }
        assertTrue(notes.contains("After finishing the 1.6.0 work"))
        assertTrue(notes.contains("All completed 1.6.0 improvements are included"))
        assertTrue(notes.contains("Static, Wheel and Pill"))
        assertTrue(notes.contains("## 🎤 Lyrics"))
        assertTrue(notes.contains("## 🧠 Recommendations"))
        assertTrue(notes.contains("## ✅ Verification"))
        assertTrue(notes.count { it == '\n' } > 150)
        assertTrue(parseReleaseNotes(notes).count { it.kind == ReleaseNoteKind.Bullet } > 100)
    }
    @Test fun everyNonemptyReleaseNoteLineRemainsReachableInTheParsedDocument() {
        val notes = BundledReleaseNotes.load(ApplicationProvider.getApplicationContext())
        val blocks = parseReleaseNotes(notes)
        assertEquals(notes.lineSequence().count { it.isNotBlank() && it.trim() != "---" }, blocks.size)
        assertTrue(blocks.last().text.startsWith("Release status:"))
    }
    @Test fun parserKeepsMultilingualContentAndRecognizesTheHierarchy() {
        val blocks = parseReleaseNotes("# Release\n\n## 🎤 Lyrics\n- **বাংলা** timing\n> Why skip 1.6.0?\n---")
        assertEquals(listOf(ReleaseNoteKind.Title, ReleaseNoteKind.Heading, ReleaseNoteKind.Bullet, ReleaseNoteKind.Paragraph), blocks.map { it.kind })
        assertEquals("বাংলা timing", blocks[2].text)
        assertEquals("Why skip 1.6.0?", blocks[3].text)
    }
    @Test fun remoteMarkupIsDisplayedAsDataRatherThanRunAsHtml() {
        val payload = "<script>untrusted()</script>\n- visible `text`"
        val blocks = parseReleaseNotes(payload)
        assertEquals("<script>untrusted()</script>", blocks[0].text)
        assertEquals("visible text", blocks[1].text)
    }
    @Test fun emptyAndSeparatorOnlyNotesProduceNoFakeEntries() {
        assertTrue(parseReleaseNotes("\n\n---\n  ").isEmpty())
    }
}
