package com.davidadrianrg.atril.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class RomPhysicalOverrideTest {

    private fun rom(id: Int, physical: String? = null) = Rom(
        id = id, name = "g$id", fileName = "g$id.z64", fileSizeBytes = 1, platformId = 1,
        platformSlug = "n64", coverUrlSmall = null, coverUrlLarge = null, files = emptyList(),
        isMulti = false, revision = null, regions = emptyList(), physicalMediaUrl = physical,
    )

    @Test
    fun `fills the url when the list had none`() {
        val r = rom(7).withPhysicalMediaOverride(mapOf(7 to "https://romm/x.png"))
        assertEquals("https://romm/x.png", r.physicalMediaUrl)
    }

    @Test
    fun `ignores roms without an override`() {
        val r = rom(7)
        assertSame(r, r.withPhysicalMediaOverride(mapOf(8 to "https://romm/x.png")))
        assertNull(r.withPhysicalMediaOverride(emptyMap()).physicalMediaUrl)
    }

    @Test
    fun `never replaces an existing url`() {
        val r = rom(7, "https://romm/old.png")
        assertSame(r, r.withPhysicalMediaOverride(mapOf(7 to "https://romm/new.png")))
    }
}
