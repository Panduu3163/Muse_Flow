package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioTagFormatTest {

    @Test
    fun `mp4 content type maps to m4a`() {
        assertEquals(".m4a", audioTagFileExtensionFor("audio/mp4"))
    }

    @Test
    fun `m4a content type maps to m4a`() {
        assertEquals(".m4a", audioTagFileExtensionFor("audio/x-m4a"))
    }

    @Test
    fun `mpeg content type maps to mp3`() {
        assertEquals(".mp3", audioTagFileExtensionFor("audio/mpeg"))
    }

    @Test
    fun `a content type with charset parameters is still matched`() {
        assertEquals(".mp3", audioTagFileExtensionFor("audio/mpeg; charset=utf-8"))
    }

    @Test
    fun `matching is case-insensitive`() {
        assertEquals(".m4a", audioTagFileExtensionFor("AUDIO/MP4"))
    }

    @Test
    fun `webm - YouTube's actual common format - is not supported`() {
        assertNull(audioTagFileExtensionFor("audio/webm"))
    }

    @Test
    fun `opus is not supported`() {
        assertNull(audioTagFileExtensionFor("audio/opus"))
    }

    @Test
    fun `a null content type is not supported`() {
        assertNull(audioTagFileExtensionFor(null))
    }

    @Test
    fun `an unrecognized content type is not supported`() {
        assertNull(audioTagFileExtensionFor("application/octet-stream"))
    }
}
