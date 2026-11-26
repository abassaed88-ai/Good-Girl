package com.goodgirl.accounting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MainActivityPaletteTest {

    @Test
    fun eachPaletteSelectsItsOwnLightColorScheme() {
        val ocean = appPaletteFor(isDark = false, paletteId = "ocean")
        val mint = appPaletteFor(isDark = false, paletteId = "mint")
        val rose = appPaletteFor(isDark = false, paletteId = "rose")

        assertNotEquals(ocean.background, mint.background)
        assertNotEquals(ocean.brandBlue, mint.brandBlue)
        assertNotEquals(ocean.background, rose.background)
        assertNotEquals(mint.background, rose.background)
    }

    @Test
    fun selectedPaletteKeepsItsIdentityInDarkMode() {
        val lightMint = appPaletteFor(isDark = false, paletteId = "mint")
        val darkMint = appPaletteFor(isDark = true, paletteId = "mint")
        val lightRose = appPaletteFor(isDark = false, paletteId = "rose")
        val darkRose = appPaletteFor(isDark = true, paletteId = "rose")

        assertNotEquals(lightMint.background, darkMint.background)
        assertNotEquals(lightRose.background, darkRose.background)
        assertNotEquals(darkMint.background, darkRose.background)
    }

    @Test
    fun unknownPaletteFallsBackToOceanInBothModes() {
        assertEquals(
            appPaletteFor(isDark = false, paletteId = "ocean"),
            appPaletteFor(isDark = false, paletteId = "invalid")
        )
        assertEquals(
            appPaletteFor(isDark = true, paletteId = "ocean"),
            appPaletteFor(isDark = true, paletteId = "invalid")
        )
    }
}
