package com.davidadrianrg.atril.core.launch

import com.davidadrianrg.atril.core.launch.EmulatorCatalog.Family
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmulatorSelectorTest {

    private val retroArch = "com.retroarch"
    private val retroArch64 = "com.retroarch.aarch64"
    private val mupen = "org.mupen64plusae.v3.fzurita"

    @Test
    fun `RetroArch is the default for Dreamcast and below`() {
        for (slug in listOf("gb", "gbc", "gba", "nes", "snes", "n64", "genesis-slash-megadrive", "sms",
            "psx", "saturn", "dc")) {
            assertEquals(slug, Family.RETROARCH, EmulatorSelector.candidates(slug).first())
        }
    }

    @Test
    fun `standalone defaults follow the user's emulator choices`() {
        assertEquals(Family.ARMSX2, EmulatorSelector.candidates("ps2").first())
        assertEquals(Family.ARMSX3, EmulatorSelector.candidates("ps3").first())
        assertEquals(Family.DOLPHIN, EmulatorSelector.candidates("ngc").first())
        assertEquals(Family.DOLPHIN, EmulatorSelector.candidates("wii").first())
        assertEquals(Family.HAKUX, EmulatorSelector.candidates("xbox").first())
        assertEquals(Family.EDEN, EmulatorSelector.candidates("switch").first())
        assertEquals(Family.XENDROID_PLUS, EmulatorSelector.candidates("xbox360").first())
    }

    @Test
    fun `N64 offers Mupen64Plus FZ as alternative`() {
        assertEquals(listOf(Family.RETROARCH, Family.MUPEN64PLUS_FZ), EmulatorSelector.candidates("n64"))
    }

    @Test
    fun `override wins over defaults`() {
        val sel = EmulatorSelector.select("n64", setOf(retroArch, mupen), overrideFamilyId = "mupen64plus_fz")
        assertEquals(EmulatorSelection.Ready(Family.MUPEN64PLUS_FZ, Family.MUPEN64PLUS_FZ.variants[0], true), sel)
    }

    @Test
    fun `override for an unsupported platform is ignored`() {
        val sel = EmulatorSelector.select("snes", setOf(retroArch, mupen), overrideFamilyId = "mupen64plus_fz")
        assertEquals(Family.RETROARCH, (sel as EmulatorSelection.Ready).family)
    }

    @Test
    fun `save emulator is used when launchable`() {
        assertEquals(Family.RETROARCH, EmulatorSelector.candidates("gc", saveEmulatorId = "retroarch").first())
        // aethersx2 no es lanzable: se ignora y queda el defecto.
        assertEquals(Family.ARMSX2, EmulatorSelector.candidates("ps2", saveEmulatorId = "aethersx2").first())
    }

    @Test
    fun `falls back to the next installed emulator`() {
        val sel = EmulatorSelector.select("n64", setOf(mupen))
        assertEquals(Family.MUPEN64PLUS_FZ, (sel as EmulatorSelection.Ready).family)
        assertFalse(sel.preferred)
    }

    @Test
    fun `falls back to another package variant of the same family`() {
        val sel = EmulatorSelector.select("snes", setOf(retroArch64)) as EmulatorSelection.Ready
        assertEquals(retroArch64, sel.variant.packageName)
        assertTrue(sel.preferred)
    }

    @Test
    fun `nothing installed reports candidates`() {
        assertEquals(
            EmulatorSelection.NotInstalled(listOf(Family.DOLPHIN, Family.RETROARCH)),
            EmulatorSelector.select("gamecube", emptySet()),
        )
    }

    @Test
    fun `unknown platform is unsupported`() {
        assertEquals(EmulatorSelection.Unsupported, EmulatorSelector.select("android", setOf(retroArch)))
    }

    @Test
    fun `DroidDeck is not a platform emulator`() {
        assertTrue(EmulatorCatalog.allPackages.none { it.contains("droiddeck", ignoreCase = true) })
    }
}
