package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The failure these mostly guard against is silent: a naive comma split turns
 * `"Shakira, Burna Boy"` into two fields, shifting every later column by one, so the album ends up
 * in the artist slot and every track fails to match for no visible reason.
 */
class PlaylistCsvTest {

    private val exportifyHeader = "\"Track URI\",\"Track Name\",\"Artist Name(s)\",\"Album Name\""

    @Test
    fun `reads an exportify style file`() {
        val csv = """
            $exportifyHeader
            "spotify:track:1","Fix You","Coldplay","X&Y"
            "spotify:track:2","Yellow","Coldplay","Parachutes"
        """.trimIndent()

        val tracks = parsePlaylistCsv(csv)

        assertEquals(2, tracks.size)
        assertEquals("Fix You", tracks[0].title)
        assertEquals("Coldplay", tracks[0].artist)
        assertEquals("X&Y", tracks[0].album)
    }

    @Test
    fun `a comma inside a quoted field stays in that field`() {
        val csv = """
            $exportifyHeader
            "spotify:track:3","Dai Dai","Shakira, Burna Boy","World Cup"
        """.trimIndent()

        val track = parsePlaylistCsv(csv).single()

        assertEquals("Dai Dai", track.title)
        assertEquals("Shakira, Burna Boy", track.artist)
        assertEquals("World Cup", track.album)
    }

    @Test
    fun `a doubled quote is a literal quote`() {
        // Not a raw string: a doubled quote followed by the field's closing quote spells `"""`,
        // which would end the Kotlin literal rather than the CSV field.
        val csv = "\"Track Name\",\"Artist Name(s)\"\n" +
            "\"Say \"\"Hello\"\"\",\"Someone\""

        assertEquals("Say \"Hello\"", parsePlaylistCsv(csv).single().title)
    }

    @Test
    fun `columns are found by name not position`() {
        val csv = """
            "Artist Name(s)","Album Name","Track Name"
            "Radiohead","The Bends","High and Dry"
        """.trimIndent()

        val track = parsePlaylistCsv(csv).single()

        assertEquals("High and Dry", track.title)
        assertEquals("Radiohead", track.artist)
    }

    @Test
    fun `album artist does not steal the artist column`() {
        val csv = """
            "Track Name","Album Artist","Artist Name(s)"
            "Song","Various Artists","The Real Artist"
        """.trimIndent()

        assertEquals("The Real Artist", parsePlaylistCsv(csv).single().artist)
    }

    @Test
    fun `rows missing a title or artist are dropped`() {
        val csv = """
            $exportifyHeader
            "spotify:track:5","","Coldplay","X&Y"
            "spotify:track:6","Clocks","","A Rush of Blood"
            "spotify:track:7","Trouble","Coldplay","Parachutes"
        """.trimIndent()

        assertEquals(listOf("Trouble"), parsePlaylistCsv(csv).map { it.title })
    }

    @Test
    fun `a missing album column is tolerated`() {
        val csv = """
            "Track Name","Artist Name(s)"
            "Creep","Radiohead"
        """.trimIndent()

        assertEquals(null, parsePlaylistCsv(csv).single().album)
    }

    @Test
    fun `a file without the needed columns is rejected with a useful message`() {
        val csv = """
            "Column A","Column B"
            "x","y"
        """.trimIndent()

        val error = assertThrows(CsvFormatException::class.java) { parsePlaylistCsv(csv) }
        assertTrue(error.message!!.contains("Track Name"))
    }

    @Test
    fun `an empty file is rejected`() {
        assertThrows(CsvFormatException::class.java) { parsePlaylistCsv("") }
    }

    @Test
    fun `crlf line endings and a trailing newline are handled`() {
        val csv = "\"Track Name\",\"Artist Name(s)\"\r\n\"Creep\",\"Radiohead\"\r\n"

        assertEquals(1, parsePlaylistCsv(csv).size)
    }

    @Test
    fun `a newline inside a quoted field does not split the row`() {
        val csv = "\"Track Name\",\"Artist Name(s)\"\n\"Two\nLines\",\"Someone\""

        val track = parsePlaylistCsv(csv).single()

        assertEquals("Two\nLines", track.title)
        assertEquals("Someone", track.artist)
    }
}
