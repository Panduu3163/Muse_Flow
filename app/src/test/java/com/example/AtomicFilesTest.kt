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
class AtomicFilesTest {
    private fun target(name: String) = File(ApplicationProvider.getApplicationContext<Context>().filesDir, name)

    @Test fun overwritingAnExistingCopyActuallyPersistsTheNewBytes() {
        val target = target("verified-copy.json")
        writeAtomicFile(target, "first verified copy".toByteArray())
        writeAtomicFile(target, "second different verified copy".toByteArray())
        assertEquals("second different verified copy", readAtomicFile(target).bufferedReader().use { it.readText() })
        assertFalse(File(target.path + ".new").exists())
    }
    @Test fun failureBeforePromotionKeepsTheOriginalAndDoesNotClaimSuccess() {
        val target = target("keep-verified-copy.json").apply { writeText("original") }
        File(target.path + ".new").mkdir()
        try { writeAtomicFile(target, "replacement".toByteArray()); fail("Staging must fail") } catch (_: java.io.IOException) { }
        assertEquals("original", target.readText())
    }
    @Test fun oldInterruptedAtomicFileWriteRecoversBackupAndDiscardsStaging() {
        val target = target("legacy-copy.json").apply { writeText("incomplete data") }
        File(target.path + ".bak").writeText("last verified copy")
        File(target.path + ".new").writeText("unfinished new copy")
        assertEquals("last verified copy", readAtomicFile(target).bufferedReader().use { it.readText() })
        assertFalse(File(target.path + ".bak").exists()); assertFalse(File(target.path + ".new").exists())
    }
}
