package com.davidadrianrg.atril.core.launch

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LaunchFileResolverTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun file(path: String, content: String = "x"): File =
        File(tmp.root, path).apply { parentFile.mkdirs(); writeText(content) }

    private fun found(result: LaunchFile): File = (result as LaunchFile.Found).file

    @Test
    fun `blank path is a manual download`() {
        assertEquals(LaunchFile.Manual, LaunchFileResolver.resolve(""))
    }

    @Test
    fun `existing file is returned as is`() {
        val rom = file("gba/Game.gba")
        assertEquals(rom, found(LaunchFileResolver.resolve(rom.absolutePath)))
    }

    @Test
    fun `sibling m3u referencing the disc wins`() {
        val disc1 = file("psx/FF7 (Disc 1).chd")
        file("psx/FF7 (Disc 2).chd")
        val m3u = file("psx/FF7.m3u", "FF7 (Disc 1).chd\nFF7 (Disc 2).chd\n")
        file("psx/Other.m3u", "Other.chd\n")
        assertEquals(m3u, found(LaunchFileResolver.resolve(disc1.absolutePath)))
    }

    @Test
    fun `directory prefers cue over bin`() {
        file("psx/Game/Game (Track 1).bin")
        val cue = file("psx/Game/Game.cue")
        file("psx/Game/readme.txt")
        assertEquals(cue, found(LaunchFileResolver.resolve(File(tmp.root, "psx/Game").absolutePath)))
    }

    @Test
    fun `zip-stream extraction is found through known file names`() {
        val cue = file("psx/Game.cue")
        file("psx/Game.bin")
        val ghostZip = File(tmp.root, "psx/Game.zip").absolutePath
        assertEquals(cue, found(LaunchFileResolver.resolve(ghostZip, listOf("Game.bin", "Game.cue"))))
    }

    @Test
    fun `zip-stream extraction into a same-name folder is found`() {
        val gdi = file("dc/Shenmue/disc.gdi")
        file("dc/Shenmue/track01.bin")
        val ghostZip = File(tmp.root, "dc/Shenmue.zip").absolutePath
        assertEquals(gdi, found(LaunchFileResolver.resolve(ghostZip)))
    }

    @Test
    fun `missing file is reported`() {
        val path = File(tmp.root, "snes/Gone.sfc").absolutePath
        assertTrue(LaunchFileResolver.resolve(path) is LaunchFile.Missing)
    }
}
