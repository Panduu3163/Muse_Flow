package com.example

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioQualitySelectionTest {
    @Test fun dataSaverChoosesBestStreamWithinCap() {
        val bitrates = listOf(48_000, 70_000, 128_000, 160_000)
        assertEquals(70_000, selectByBitrate(bitrates, AudioQualityMode.DataSaver) { it })
        assertEquals(160_000, selectByBitrate(bitrates, AudioQualityMode.BestAvailable) { it })
    }

    @Test fun dataSaverFallsBackWhenOnlyHighBitrateIsAvailable() {
        assertEquals(128_000, selectByBitrate(listOf(256_000, 128_000), AudioQualityMode.DataSaver) { it })
    }
}
