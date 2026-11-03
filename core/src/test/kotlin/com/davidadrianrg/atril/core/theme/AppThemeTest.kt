package com.davidadrianrg.atril.core.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppThemeTest {

    @Test
    fun `unknown or empty id falls back to warm archive`() {
        assertEquals(AppTheme.WARM_ARCHIVE, AppTheme.fromId(null))
        assertEquals(AppTheme.WARM_ARCHIVE, AppTheme.fromId(""))
        assertEquals(AppTheme.WARM_ARCHIVE, AppTheme.fromId("neon"))
    }

    @Test
    fun `every theme id round-trips so persistence is lossless`() {
        AppTheme.entries.forEach { assertEquals(it, AppTheme.fromId(it.id)) }
        assertEquals(AppTheme.entries.size, AppTheme.entries.map { it.id }.toSet().size)
        assertEquals(AppTheme.MIDNIGHT, AppTheme.fromId("midnight"))
    }

    @Test
    fun `themes have distinct palettes`() {
        assertNotEquals(ThemePalettes.warmArchive, ThemePalettes.midnight)
        assertNotEquals(ThemePalettes.warmArchive.background, ThemePalettes.midnight.background)
    }

    @Test
    fun `midnight is dark blue-indigo`() {
        val bg = ThemePalettes.midnight.background
        val r = ((bg shr 16) and 0xFF).toInt()
        val b = (bg and 0xFF).toInt()
        assertTrue("background should lean blue", b > r)
        assertTrue("background should be very dark", Contrast.luminance(bg) < 0.02)
    }

    @Test
    fun `all text pairs meet WCAG AA 4_5 in every theme`() {
        AppTheme.entries.forEach { theme ->
            ThemePalettes.of(theme).readablePairs().forEach { (name, fg, bg) ->
                val ratio = Contrast.ratio(fg, bg)
                assertTrue("$theme $name contrast $ratio < 4.5", ratio >= 4.5)
            }
        }
    }
}
