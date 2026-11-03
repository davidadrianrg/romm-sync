package com.davidadrianrg.atril.core.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InsertionMotionTest {

    private fun steps() = (0..100).map { it / 100f }

    @Test
    fun `starts hidden and idle`() {
        val p = InsertionMotion.pose(0f, optical = false)
        assertEquals(0f, p.appear, 0f)
        assertEquals(0f, p.travel, 0f)
        assertEquals(0f, p.rotationY, 0f)
        assertEquals(0f, p.glow, 0f)
        assertEquals(0f, p.alpha, 0f)
    }

    @Test
    fun `ends seated in the slot`() {
        val p = InsertionMotion.pose(1f, optical = false)
        assertEquals(1f, p.travel, 1e-4f)
        assertEquals(1f, p.glow, 1e-4f)
        assertEquals(0f, p.alpha, 1e-4f) // ya dentro de la ranura
        assertEquals(0f, p.impact, 1e-4f)
        assertEquals(360f, p.rotationY, 1e-3f) // una vuelta completa: sin espejo al terminar
    }

    @Test
    fun `travel and glow never go backwards`() {
        var lastTravel = -1f
        var lastGlow = -1f
        for (t in steps()) {
            val p = InsertionMotion.pose(t, optical = false)
            assertTrue("travel at $t", p.travel >= lastTravel - 1e-5f)
            assertTrue("glow at $t", p.glow >= lastGlow - 1e-5f)
            lastTravel = p.travel
            lastGlow = p.glow
        }
    }

    @Test
    fun `values stay in sane ranges`() {
        for (optical in listOf(false, true)) for (t in steps()) {
            val p = InsertionMotion.pose(t, optical)
            assertTrue(p.appear in 0f..1f)
            assertTrue(p.alpha in 0f..1f)
            assertTrue(p.shadowAlpha in 0f..1f)
            assertTrue(p.glow in 0f..1f)
            assertTrue(p.impact in 0f..1f)
            assertTrue(p.squashY in 0.4f..1f)
            assertTrue(p.scale > 0f)
        }
    }

    @Test
    fun `cartridge spins in 3D and tilts, disc only rotates in plane`() {
        val mid = InsertionMotion.pose(0.4f, optical = false)
        assertTrue(mid.rotationY > 0f)
        assertEquals(0f, mid.rotationZ, 0f)
        val late = InsertionMotion.pose(0.85f, optical = false)
        assertTrue(late.rotationX > 40f)

        val disc = InsertionMotion.pose(0.5f, optical = true)
        assertEquals(0f, disc.rotationY, 0f)
        assertEquals(0f, disc.rotationX, 0f)
        assertTrue(disc.rotationZ > 0f)
    }

    @Test
    fun `arc peaks mid flight and impact pulses near the end`() {
        val mid = InsertionMotion.pose(0.45f, optical = false)
        assertTrue(mid.arc > InsertionMotion.pose(0.05f, optical = false).arc)
        assertTrue(mid.arc > InsertionMotion.pose(0.99f, optical = false).arc)
        val hit = InsertionMotion.pose(0.92f, optical = false)
        assertTrue(hit.impact > 0.9f)
    }

    @Test
    fun `duration is between one and one and a half seconds`() {
        assertTrue(InsertionMotion.DURATION_MS in 1000..1500)
    }

    @Test
    fun `progress outside range is clamped`() {
        assertEquals(InsertionMotion.pose(0f, false), InsertionMotion.pose(-3f, false))
        assertEquals(InsertionMotion.pose(1f, false), InsertionMotion.pose(7f, false))
    }
}
