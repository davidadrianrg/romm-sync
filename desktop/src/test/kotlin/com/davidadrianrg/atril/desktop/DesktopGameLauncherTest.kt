package com.davidadrianrg.atril.desktop

import com.davidadrianrg.atril.core.launch.DesktopLaunchPlanner.Config
import com.davidadrianrg.atril.core.launch.DesktopLaunchPlanner.Plan
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Usa un «emulador» falso (script sh) que registra los argumentos que recibe. */
class DesktopGameLauncherTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Before
    fun onlyOnUnix() = assumeTrue(File("/bin/sh").canExecute())

    private lateinit var log: File
    private lateinit var binDir: File

    /** Crea `name` en una carpeta que hace de PATH; escribe cada argumento en una línea del log. */
    private fun fakeEmulator(name: String, exitCode: Int = 0): File {
        binDir = tmp.newFolder("bin")
        log = File(tmp.root, "args.log")
        return File(binDir, name).apply {
            writeText("#!/bin/sh\nfor a in \"\$@\"; do printf '%s\\n' \"\$a\" >> '${log.absolutePath}'; done\nexit $exitCode\n")
            setExecutable(true)
        }
    }

    private fun rom(name: String): File = File(tmp.newFolder("roms"), name).apply { writeText("rom") }

    private fun launcher() = DesktopGameLauncher(pathEnv = binDir.absolutePath)

    private fun Process.finish() = assertTrue(waitFor(10, TimeUnit.SECONDS))

    @Test
    fun launchesTheConfiguredEmulatorWithRomTemplate() {
        val exe = fakeEmulator("retroarch")
        val game = rom("Zelda.gba")
        val result = launcher().launch("gba", Config("retroarch"), game) as DesktopGameLauncher.Result.Started
        result.process.finish()
        assertEquals(listOf(exe.absolutePath, "-L", "mgba_libretro.so", game.absolutePath), result.command)
        assertEquals(listOf("-L", "mgba_libretro.so", game.absolutePath), log.readLines())
    }

    @Test
    fun pathWithSpacesAndShellMetacharactersIsOneLiteralArgument() {
        fakeEmulator("emu")
        val game = rom("My Game; touch pwned && \$(id) `id`.bin")
        val marker = File(game.parentFile, "pwned") // cwd del proceso = carpeta de la ROM
        val result = launcher().launch("zzz", Config("custom", "emu", "--fs {rom}"), game) as DesktopGameLauncher.Result.Started
        result.process.finish()
        assertEquals(listOf("--fs", game.absolutePath), log.readLines())
        assertFalse("el nombre del fichero se ejecutó como comando", marker.exists())
    }

    @Test
    fun missingExecutableIsRejectedWithoutStartingAnything() {
        fakeEmulator("something-else")
        val result = launcher().launch("gba", Config("retroarch"), rom("g.gba"))
        assertEquals(Plan.ExecutableMissing("retroarch", "RetroArch"), (result as DesktopGameLauncher.Result.Rejected).plan)
        assertFalse(log.exists())
    }

    @Test
    fun platformWithoutConfigurationDoesNotFallBackToTheSystemOpener() {
        fakeEmulator("retroarch")
        val result = launcher().launch("n64", Config(), rom("g.z64"))
        assertEquals(Plan.NotConfigured("n64"), (result as DesktopGameLauncher.Result.Rejected).plan)
        assertFalse("no debe arrancar nada", log.exists())
    }

    @Test
    fun platformWithoutMappingNeedsACustomEmulator() {
        fakeEmulator("retroarch")
        val result = launcher().launch("zzz-unknown", Config("retroarch"), rom("g.bin"))
        assertEquals(Plan.NoCore("zzz-unknown"), (result as DesktopGameLauncher.Result.Rejected).plan)
    }

    @Test
    fun emulatorThatExitsEarlyWithAnErrorIsDetected() {
        fakeEmulator("emu", exitCode = 3)
        val result = launcher().launch("zzz", Config("custom", "emu", "{rom}"), rom("g.bin")) as DesktopGameLauncher.Result.Started
        assertEquals(3, DesktopGameLauncher.earlyFailureCode(result.process, windowMs = 10_000))
    }

    @Test
    fun emulatorThatKeepsRunningIsNotAFailure() {
        binDir = tmp.newFolder("bin")
        File(binDir, "slow").apply { writeText("#!/bin/sh\nsleep 5\n"); setExecutable(true) }
        val result = launcher().launch("zzz", Config("custom", "slow", "{rom}"), rom("g.bin")) as DesktopGameLauncher.Result.Started
        try {
            assertNull(DesktopGameLauncher.earlyFailureCode(result.process, windowMs = 300))
        } finally {
            result.process.destroyForcibly()
        }
    }

    @Test
    fun libraryPersistsTheLaunchConfigPerPlatform() {
        val lib = DesktopLibrary(File(tmp.root, "library.properties"))
        lib.upsertPlatform(
            DesktopLibrary.PlatformEntry(
                "gba", emulatorId = "mgba",
                launchEmulatorId = "custom", launchExecutable = "/opt/emu/my emu", launchArgs = "--fs \"{rom}\"",
            ),
        )
        val back = DesktopLibrary(File(tmp.root, "library.properties")).platform("gba")
        assertEquals("custom", back.launchEmulatorId)
        assertEquals("/opt/emu/my emu", back.launchExecutable)
        assertEquals("--fs \"{rom}\"", back.launchArgs)
        assertEquals("mgba", back.emulatorId) // el emulador de sync no se pisa
        assertNull(DesktopLibrary(File(tmp.root, "library.properties")).platform("n64").launchEmulatorId)
    }
}
