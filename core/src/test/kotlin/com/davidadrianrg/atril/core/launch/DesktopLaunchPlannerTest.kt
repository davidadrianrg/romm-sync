package com.davidadrianrg.atril.core.launch

import com.davidadrianrg.atril.core.launch.DesktopLaunchPlanner.Config
import com.davidadrianrg.atril.core.launch.DesktopLaunchPlanner.Plan
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DesktopLaunchPlannerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** Crea un ejecutable falso en una carpeta que hace de PATH. */
    private fun fakeBin(name: String): Pair<File, String> {
        val dir = tmp.newFolder("bin")
        val exe = File(dir, name).apply { writeText("#!/bin/sh\n"); setExecutable(true) }
        return exe to dir.absolutePath
    }

    @Test
    fun platformWithoutConfigurationIsNotConfigured() {
        val plan = DesktopLaunchPlanner.plan("gba", Config(), "/r/g.gba")
        assertEquals(Plan.NotConfigured("gba"), plan)
    }

    @Test
    fun catalogDefaultsBuildTheCommand() {
        val (exe, path) = fakeBin("retroarch")
        val plan = DesktopLaunchPlanner.plan("gba", Config(emulatorId = "retroarch"), "/home/u/My Roms/g.gba", pathEnv = path)
        plan as Plan.Ready
        assertEquals(
            listOf(exe.absolutePath, "-L", "mgba_libretro.so", "/home/u/My Roms/g.gba"),
            plan.command,
        )
    }

    @Test
    fun userOverridesExecutableAndArgs() {
        val (exe, _) = fakeBin("myemu")
        val config = Config("custom", executable = exe.absolutePath, args = "--fs {rom}")
        val plan = DesktopLaunchPlanner.plan("zzz", config, "/r/a b.bin") as Plan.Ready
        assertEquals(listOf(exe.absolutePath, "--fs", "/r/a b.bin"), plan.command)
    }

    @Test
    fun missingExecutableIsReportedByName() {
        val plan = DesktopLaunchPlanner.plan("gba", Config("retroarch"), "/r/g.gba", pathEnv = tmp.newFolder("empty").absolutePath)
        assertEquals(Plan.ExecutableMissing("retroarch", "RetroArch"), plan)
    }

    @Test
    fun absolutePathToMissingFileIsMissing() {
        val config = Config("custom", executable = "/no/such/emu", args = "{rom}")
        assertTrue(DesktopLaunchPlanner.plan("x", config, "/r/g") is Plan.ExecutableMissing)
    }

    @Test
    fun customWithoutExecutableAsksForOne() {
        assertEquals(Plan.NoExecutable("Custom"), DesktopLaunchPlanner.plan("x", Config("custom"), "/r/g"))
    }

    @Test
    fun platformWithoutCatalogMappingHasNoSuggestionsAndNoCore() {
        assertTrue(DesktopEmulatorCatalog.forPlatform("zzz-unknown").isEmpty())
        // RetroArch elegido a mano en una plataforma sin core: {core} no se puede resolver.
        val (_, path) = fakeBin("retroarch")
        val plan = DesktopLaunchPlanner.plan("zzz-unknown", Config("retroarch"), "/r/g", pathEnv = path)
        assertEquals(Plan.NoCore("zzz-unknown"), plan)
    }

    @Test
    fun invalidTemplatesAreRejected() {
        val (exe, _) = fakeBin("e")
        val bad = DesktopLaunchPlanner.plan("x", Config("custom", exe.absolutePath, """-f "oops"""), "/r/g")
        assertTrue(bad is Plan.InvalidArgs)
        val unknown = DesktopLaunchPlanner.plan("x", Config("custom", exe.absolutePath, "{rom} {nope}"), "/r/g")
        assertTrue(unknown is Plan.InvalidArgs)
    }

    @Test
    fun resolveExecutableSkipsNonExecutableFiles() {
        val dir = tmp.newFolder("p")
        File(dir, "noexec").writeText("x")
        assertNull(DesktopLaunchPlanner.resolveExecutable("noexec", dir.absolutePath))
        File(dir, "ok").apply { writeText("x"); setExecutable(true) }
        assertNotNull(DesktopLaunchPlanner.resolveExecutable("ok", dir.absolutePath))
    }

    @Test
    fun catalogIsConsistent() {
        val ids = DesktopEmulatorCatalog.all.map { it.id }
        assertEquals(ids.distinct(), ids)
        DesktopEmulatorCatalog.all.forEach { e ->
            assertTrue("${e.id} sin ejecutable", e.defaultExecutable.isNotBlank())
            assertTrue("${e.id} plantilla inválida", CommandTemplate.parse(e.defaultArgs) is CommandTemplate.Parsed.Ok)
        }
        assertEquals("mupen64plus_next_libretro.so", DesktopEmulatorCatalog.coreFor("n64")) // no la variante gles3
        assertEquals("mgba_libretro.so", DesktopEmulatorCatalog.coreFor("game-boy-advance"))
        assertTrue(DesktopEmulatorCatalog.forPlatform("ngc").any { it.id == "dolphin" })
    }
}
