package com.davidadrianrg.atril.core.launch

import com.davidadrianrg.atril.core.launch.EmulatorCatalog.Family
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchSpecBuilderTest {

    private val env = LaunchEnvironment("/storage/emulated/0")

    private fun build(family: Family, rom: String, slug: String, variant: Int = 0) =
        LaunchSpecBuilder.build(family, family.variants[variant], rom, slug, env)!!

    @Test
    fun `RetroArch passes ROM, core and dirs as extras`() {
        val spec = build(Family.RETROARCH, "/storage/emulated/0/ROMs/n64/Mario.z64", "n64")
        assertEquals("com.retroarch", spec.packageName)
        assertEquals("com.retroarch.browser.retroactivity.RetroActivityFuture", spec.activityClass)
        assertEquals(EmulatorCatalog.ACTION_MAIN, spec.action)
        val extras = spec.extras.filterIsInstance<LaunchExtra.Text>().associate { it.key to it.value }
        assertEquals("/storage/emulated/0/ROMs/n64/Mario.z64", extras["ROM"])
        assertEquals("/data/data/com.retroarch/cores/mupen64plus_next_gles3_libretro_android.so", extras["LIBRETRO"])
        assertEquals("/storage/emulated/0/Android/data/com.retroarch/files/retroarch.cfg", extras["CONFIGFILE"])
        assertEquals("/data/data/com.retroarch", extras["DATADIR"])
        assertEquals("/storage/emulated/0", extras["SDCARD"])
        assertEquals("/storage/emulated/0/Android/data/com.retroarch/files", extras["EXTERNAL"])
        assertTrue(extras.containsKey("IME"))
        assertFalse(spec.romUriAsData)
        assertTrue(LaunchFlag.NO_HISTORY in spec.flags)
    }

    @Test
    fun `RetroArch core paths follow the installed variant`() {
        val spec = build(Family.RETROARCH, "/r/game.gba", "gba", variant = 1)
        val core = spec.extras.filterIsInstance<LaunchExtra.Text>().first { it.key == "LIBRETRO" }.value
        assertEquals("/data/data/com.retroarch.aarch64/cores/mgba_libretro_android.so", core)
    }

    @Test
    fun `RetroArch without a core returns null`() {
        assertNull(LaunchSpecBuilder.build(Family.RETROARCH, Family.RETROARCH.variants[0], "/r/x", "switch", env))
    }

    @Test
    fun `Dolphin uses AutoStartFile as a content uri extra`() {
        val spec = build(Family.DOLPHIN, "/r/gc/game.rvz", "ngc")
        assertEquals("org.dolphinemu.dolphinemu.ui.main.MainActivity", spec.activityClass)
        assertEquals(EmulatorCatalog.ACTION_MAIN, spec.action)
        assertEquals(listOf(LaunchExtra.RomUri("AutoStartFile")), spec.extras)
        assertTrue(spec.grantRomRead)
        assertFalse(spec.romUriAsData)
    }

    @Test
    fun `Eden, ARMSX2 and Mupen get the rom as VIEW data`() {
        for (family in listOf(Family.EDEN, Family.ARMSX2, Family.MUPEN64PLUS_FZ)) {
            val spec = build(family, "/r/game.bin", family.platforms.first())
            assertEquals(EmulatorCatalog.ACTION_VIEW, spec.action)
            assertTrue(spec.romUriAsData)
            assertEquals(LaunchSpecBuilder.DEFAULT_MIME, spec.mimeType)
            assertTrue(spec.grantRomRead)
            assertFalse(LaunchFlag.NO_HISTORY in spec.flags)
        }
        assertEquals("org.yuzu.yuzu_emu.activities.EmulationActivity", build(Family.EDEN, "/r/a.nsp", "switch").activityClass)
        assertEquals("com.armsx2.MainActivity", build(Family.ARMSX2, "/r/a.iso", "ps2").activityClass)
        assertEquals("paulscode.android.mupen64plusae.SplashActivity", build(Family.MUPEN64PLUS_FZ, "/r/a.z64", "n64").activityClass)
    }

    @Test
    fun `ARMSX3 and XenDroid+ take an absolute path extra`() {
        val ps3 = build(Family.ARMSX3, "/r/ps3/Game", "ps3")
        assertEquals("com.armsx2.BootSplashActivity", ps3.activityClass)
        assertEquals(listOf(LaunchExtra.Text("path", "/r/ps3/Game")), ps3.extras)
        assertFalse(ps3.grantRomRead)

        val x360 = build(Family.XENDROID_PLUS, "/r/x360/game.iso", "xbox360")
        assertEquals("xendroid.compose.fork", x360.packageName)
        assertEquals("xendroid.intent.action.xendroid", x360.action)
        assertEquals("xendroid.compose.EmulatorHostActivity", x360.activityClass)
        assertEquals(listOf(LaunchExtra.Text("game_uri", "/r/x360/game.iso")), x360.extras)
    }

    @Test
    fun `hakuX is a package-only VIEW intent`() {
        val spec = build(Family.HAKUX, "/r/xbox/game.iso", "xbox")
        assertEquals("com.rfandango.haku_x", spec.packageName)
        assertNull(spec.activityClass)
        assertTrue(spec.romUriAsData)
        assertFalse(Family.HAKUX.recipe.verified)
    }
}
