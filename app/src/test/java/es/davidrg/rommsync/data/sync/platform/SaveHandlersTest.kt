package es.davidrg.rommsync.data.sync.platform

import es.davidrg.rommsync.util.RomHeaderIdReader
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Tests de los handlers de saves con estructuras de directorios sintéticas
 * que replican los layouts reales de cada emulador en disco.
 */
class SaveHandlersTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun write(dir: File, relativePath: String, content: String): File {
        val f = File(dir, relativePath)
        f.parentFile!!.mkdirs()
        f.writeText(content)
        return f
    }

    // ── Eden (Switch): búsqueda del title-id en árboles custom ─────────

    @Test
    fun `eden encuentra save en estructura canonica user-profile-title`() = runTest {
        val base = tmp.newFolder("nand_save")
        val saveDir = write(base, "8000000000000123/0000000000000001/0100000000010000/chao/main.chao", "x")

        val saves = SwitchSaveHandler().findSaves(
            romId = 1,
            romFileName = "[0100000000010000] Super Mario Odyssey.nsp",
            platformSlug = "switch",
            savesBasePath = base.absolutePath,
            romLocalPath = null,
        )
        assertEquals(1, saves.size)
        assertTrue(saves[0].fileName.contains("0100000000010000"))
    }

    @Test
    fun `eden encuentra save cuando la base apunta a nand (2 niveles mas arriba)`() = runTest {
        val nand = tmp.newFolder("nand")
        write(nand, "user/save/8000000000000123/0000000000000001/0100000000010500/00/BOTW.sav", "y")

        val saves = SwitchSaveHandler().findSaves(
            romId = 2,
            romFileName = "Zelda [0100000000010500].xci",
            platformSlug = "switch",
            savesBasePath = nand.absolutePath,
            romLocalPath = null,
        )
        assertEquals(1, saves.size)
    }

    // ── ARMSX2 / PS2: folder memory cards ──────────────────────────────

    @Test
    fun `ps2 encuentra save en folder memcard con prefijo BA`() = runTest {
        val memcards = tmp.newFolder("memcards")
        write(memcards, "Shared.ps2/BASLUS-21050/icon.sys", "i")
        write(memcards, "Shared.ps2/BASLUS-21050/BASLUS-21050", "save")

        val saves = Ps2SaveHandler().findSaves(
            romId = 3,
            romFileName = "SLUS-21050 - Shadow of the Colossus.iso",
            platformSlug = "ps2",
            savesBasePath = memcards.absolutePath,
            romLocalPath = null,
        )
        assertEquals(1, saves.size)
        assertTrue(saves[0].fileName.startsWith("BASLUS-21050"))
    }

    // ── ARMSX3 (PS3): savedata bajo dev_hdd0/home ──────────────────────

    @Test
    fun `ps3 encuentra savedata por prefijo de serial`() = runTest {
        val hdd0 = tmp.newFolder("dev_hdd0")
        write(hdd0, "home/00000001/savedata/BLUS30181USERDATA/PARAM.SFO", "sfo")
        write(hdd0, "home/00000001/savedata/BLUS30181USERDATA/USRDIR/DATA.BIN", "bin")
        write(hdd0, "home/00000001/savedata/NPUA80110-GAME/PARAM.SFO", "other")

        val saves = Ps3SaveHandler().findSaves(
            romId = 4,
            romFileName = "BLUS-30181 - Demon's Souls.pkg",
            platformSlug = "ps3",
            savesBasePath = hdd0.absolutePath,
            romLocalPath = null,
        )
        assertEquals(1, saves.size)
        assertTrue(saves[0].fileName.startsWith("BLUS-30181"))
    }

    @Test
    fun `ps3 extrae zip a savedata con prefijo correcto`() = runTest {
        val hdd0 = tmp.newFolder("dev_hdd0_out")
        // Simula el servidor: zip con la carpeta del save
        val srcDir = tmp.newFolder("BLUS30181USERDATA")
        write(srcDir, "PARAM.SFO", "sfo")
        write(srcDir, "USRDIR/DATA.BIN", "bin")
        val zip = tmp.newFile("save.zip")
        zipFolderDeterministic(srcDir, zip)

        val ok = Ps3SaveHandler().extractDownload(
            tempFile = zip,
            romFileName = "BLUS-30181 - Demon's Souls.pkg",
            platformSlug = "ps3",
            savesBasePath = hdd0.absolutePath,
            targetFileName = "BLUS-30181_ps3_save.zip",
        )
        assertTrue(ok)
        // No había home/ previo: reconstruye con el usuario por defecto
        val out = File(hdd0, "home/00000001/savedata/BLUS30181USERDATA/USRDIR/DATA.BIN")
        assertTrue("Debe existir $out", out.isFile)
    }

    // ── melonDS: .sav junto a la ROM ───────────────────────────────────

    @Test
    fun `melonds encuentra sav junto a la rom`() = runTest {
        val roms = tmp.newFolder("roms")
        val rom = write(roms, "Pokemon Esmeralda.nds", "rom")
        write(roms, "Pokemon Esmeralda.sav", "save")

        val saves = MelonDsSaveHandler().findSaves(
            romId = 5,
            romFileName = "Pokemon Esmeralda.nds",
            platformSlug = "nds",
            savesBasePath = "/nonexistent/path",
            romLocalPath = rom.absolutePath,
        )
        assertEquals(1, saves.size)
    }

    // ── Azahar (3DS): title-id desde header NCCH sintético ─────────────

    @Test
    fun `3ds lee title-id del header NCCH`() {
        // Prefix sintético: "NCCH" en 0x100, title-id LE en 0x108
        val bytes = ByteArray(0x200)
        val magic = byteArrayOf(0x4E, 0x43, 0x43, 0x48)
        System.arraycopy(magic, 0, bytes, 0x100, 4)
        val titleId = 0x0004000000030200L
        for (i in 0 until 8) {
            bytes[0x108 + i] = ((titleId shr (8 * i)) and 0xFF).toByte()
        }
        val f = tmp.newFile("game.3ds")
        f.writeBytes(bytes)

        assertEquals("0004000000030200", RomHeaderIdReader.readGameId(f, "3ds"))
    }

    @Test
    fun `3ds ignora NCCH sin title-type 00040000`() {
        val bytes = ByteArray(0x200)
        val magic = byteArrayOf(0x4E, 0x43, 0x43, 0x48)
        System.arraycopy(magic, 0, bytes, 0x100, 4)
        val titleId = 0x0004003000000200L // update title, no juego
        for (i in 0 until 8) {
            bytes[0x108 + i] = ((titleId shr (8 * i)) and 0xFF).toByte()
        }
        val f = tmp.newFile("update.cia")
        f.writeBytes(bytes)

        assertNull(RomHeaderIdReader.readGameId(f, "3ds"))
    }
}
