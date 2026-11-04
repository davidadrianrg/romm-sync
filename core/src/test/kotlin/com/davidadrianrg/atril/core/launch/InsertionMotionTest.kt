package com.davidadrianrg.atril.core.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InsertionMotionTest {

    private fun steps() = (0..100).map { it / 100f }

    @Test
    fun `starts hidden and idle`() {
        val p = InsertionMotion.pose(0f)
        assertEquals(0f, p.appear, 0f)
        assertEquals(0f, p.console, 0f)
        assertEquals(0f, p.drop, 0f)
        assertEquals(0f, p.seat, 0f)
        assertEquals(0f, p.glow, 0f)
    }

    @Test
    fun `ends fully seated with the slot lit`() {
        val p = InsertionMotion.pose(1f)
        assertEquals(1f, p.drop, 1e-4f)
        assertEquals(1f, p.seat, 1e-4f)
        assertEquals(1f, p.glow, 1e-4f)
        assertEquals(1f, p.console, 1e-4f)
        assertEquals(0f, p.impact, 1e-4f)
    }

    @Test
    fun `motion is monotonic and never goes backwards`() {
        var last = floatArrayOf(-1f, -1f, -1f, -1f)
        for (t in steps()) {
            val p = InsertionMotion.pose(t)
            val cur = floatArrayOf(p.console, p.drop, p.seat, p.glow)
            cur.forEachIndexed { i, v -> assertTrue("channel $i at $t", v >= last[i] - 1e-5f) }
            last = cur
        }
    }

    @Test
    fun `it lands on the slot before pushing in`() {
        // El empuje final no empieza hasta que el medio ha llegado a la ranura.
        for (t in steps()) {
            val p = InsertionMotion.pose(t)
            if (p.seat > 0f) assertEquals("drop at $t", 1f, p.drop, 1e-4f)
        }
    }

    @Test
    fun `values stay in sane ranges`() {
        for (t in steps()) {
            val p = InsertionMotion.pose(t)
            assertTrue(p.appear in 0f..1f)
            assertTrue(p.console in 0f..1f)
            assertTrue(p.drop in 0f..1f)
            assertTrue(p.seat in 0f..1f)
            assertTrue(p.shadowAlpha in 0f..1f)
            assertTrue(p.glow in 0f..1f)
            assertTrue(p.impact in 0f..1f)
            assertTrue(p.scale in 0.5f..1f)
        }
    }

    @Test
    fun `impact pulses near the end`() {
        assertTrue(InsertionMotion.pose(0.93f).impact > 0.9f)
        assertEquals(0f, InsertionMotion.pose(0.5f).impact, 0f)
    }

    @Test
    fun `duration is between one and one and a half seconds`() {
        assertTrue(InsertionMotion.DURATION_MS in 1000..1500)
    }

    @Test
    fun `progress outside range is clamped`() {
        assertEquals(InsertionMotion.pose(0f), InsertionMotion.pose(-3f))
        assertEquals(InsertionMotion.pose(1f), InsertionMotion.pose(7f))
    }
}
