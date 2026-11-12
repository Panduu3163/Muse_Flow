package com.example

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeHeroSourceTest {
    @Test
    fun `returning listener with likes never gets generic startup recommendations`() {
        assertEquals(HeroSeedSource.LIKED, heroSeedSource(likedCount = 4, recentCount = 7))
    }

    @Test
    fun `recent listening can personalize a listener without likes`() {
        assertEquals(HeroSeedSource.RECENT, heroSeedSource(likedCount = 0, recentCount = 3))
    }

    @Test
    fun `only a confirmed empty library uses the generic hero`() {
        assertEquals(HeroSeedSource.GENERIC, heroSeedSource(likedCount = 0, recentCount = 0))
    }
}
