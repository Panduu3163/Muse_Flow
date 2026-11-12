package com.example

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CurrentCodecInfoTest {
    @After fun clear() { CurrentCodecInfo.clear() }

    @Test fun labelTracksDecoderMimeAndBitrateRatherThanAssumingOpus() {
        CurrentCodecInfo.update("audio/opus", 160_000)
        assertEquals("OPUS · 160kbps", CurrentCodecInfo.current.value)
        CurrentCodecInfo.update("audio/mp4a-latm", 128_000)
        assertEquals("MP4A-LATM · 128kbps", CurrentCodecInfo.current.value)
        CurrentCodecInfo.update("audio/flac", -1)
        assertEquals("FLAC", CurrentCodecInfo.current.value)
    }

    @Test fun missingCodecDoesNotPretendToKnowTheFormat() {
        CurrentCodecInfo.update(null, -1)
        assertNull(CurrentCodecInfo.current.value)
    }

    @Test fun encodedAverageIsMarkedAndKnownDecoderBitrateTakesPriority() {
        assertEquals(128_000, averageEncodedBitrate(3_840_000, 240_000))
        CurrentCodecInfo.update("audio/opus", -1, averageEncodedBitrate(3_840_000, 240_000))
        assertEquals("OPUS · ~128kbps", CurrentCodecInfo.current.value)
        CurrentCodecInfo.update("audio/opus", 160_000, 128_000)
        assertEquals("OPUS · 160kbps", CurrentCodecInfo.current.value)
    }

    @Test fun unknownLengthsDurationsAndOverflowRemainUnknown() {
        assertNull(averageEncodedBitrate(-1, 240_000))
        assertNull(averageEncodedBitrate(3_840_000, -9223372036854775807L))
        assertNull(averageEncodedBitrate(3_840_000, 0))
        assertNull(averageEncodedBitrate(Long.MAX_VALUE, 1))
        CurrentCodecInfo.update("audio/opus", -1, null)
        assertEquals("OPUS", CurrentCodecInfo.current.value)
    }
}
