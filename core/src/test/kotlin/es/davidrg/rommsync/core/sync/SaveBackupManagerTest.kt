package es.davidrg.rommsync.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SaveBackupManagerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun manager(keep: Int = 5): SaveBackupManager =
        SaveBackupManager(tmp.newFolder(), keepPerFile = keep)

    private fun source(content: String): File =
        tmp.newFile().apply { writeText(content) }

    @Test
    fun `backup crea version listada con nombre original`() {
        val m = manager()
        val src = source("v1")
        val bak = m.backup(42, "game.sav", src)
        assertNotNull(bak)
        val versions = m.versions(42)
        assertEquals(1, versions.size)
        assertEquals("game.sav", versions[0].fileName)
        assertEquals("v1", versions[0].backupFile.readText())
        assertTrue(versions[0].timestamp > 0)
    }

    @Test
    fun `names con caracteres raros se sanitizan sin romper el listado`() {
        val m = manager()
        m.backup(7, "save/..\\weird:name.zip", source("data"))
        val versions = m.versions(7)
        assertEquals(1, versions.size)
        assertEquals("save/..\\weird:name.zip", versions[0].fileName)
    }

    @Test
    fun `prune conserva solo las N ultimas versiones por fichero`() {
        val m = manager(keep = 2)
        repeat(5) { i ->
            m.backup(1, "a.sav", source("a$i"))
            m.backup(1, "b.sav", source("b$i"))
            Thread.sleep(5) // timestamps distintos
        }
        assertEquals(2, m.versions(1, "a.sav").size)
        assertEquals(2, m.versions(1, "b.sav").size)
        // Las conservadas son las más recientes
        assertEquals("a4", m.versions(1, "a.sav")[0].backupFile.readText())
        assertEquals("a3", m.versions(1, "a.sav")[1].backupFile.readText())
    }

    @Test
    fun `roms distintos no comparten prunning`() {
        val m = manager(keep = 1)
        m.backup(1, "x.sav", source("r1"))
        Thread.sleep(5)
        m.backup(2, "x.sav", source("r2"))
        assertEquals(1, m.versions(1).size)
        assertEquals(1, m.versions(2).size)
    }

    @Test
    fun `backup de fuente inexistente devuelve null`() {
        val m = manager()
        assertNull(m.backup(3, "y.sav", File(tmp.root, "nope")))
        assertTrue(m.versions(3).isEmpty())
    }
}
