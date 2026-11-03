package com.davidadrianrg.atril.core.localgames

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import com.squareup.moshi.JsonClass
import org.junit.Test
import java.io.File

class LocalGameStoreTest {

    private fun file() = File.createTempFile("local", ".json").apply { delete(); deleteOnExit() }

    @Test
    fun `titles are cleaned from file names`() {
        assertEquals("Super Mario 64", LocalGameNames.titleFromFileName("Super Mario 64 (USA) [!].z64"))
        assertEquals("Donkey Kong Country", LocalGameNames.titleFromFileName("/roms/snes/Donkey_Kong_Country (Europe) (Rev 1).sfc"))
        assertEquals("Gex", LocalGameNames.titleFromFileName("Gex.chd"))
        assertEquals("(USA)", LocalGameNames.titleFromFileName("(USA)"))
        // El fallback no debe arrastrar el directorio.
        assertEquals("(USA)", LocalGameNames.titleFromFileName("/roms/n64/(USA).z64"))
    }

    @Test
    fun `add persists across reloads and is sorted`() {
        val f = file()
        val s = LocalGameStore(f) { 42L }
        s.add("zelda", "n64", "/a/zelda.z64")
        val b = s.add("Banjo", "n64", "/a/banjo.z64")
        assertEquals(42L, b.addedAt)

        val reopened = LocalGameStore(f)
        assertEquals(listOf("Banjo", "zelda"), reopened.all().map { it.title })
        assertEquals("/a/banjo.z64", reopened.get(b.id)?.path)
    }

    @Test
    fun `blank title falls back to the file name`() {
        val g = LocalGameStore(file()).add("  ", "gba", "/x/Metroid_Fusion (USA).gba")
        assertEquals("Metroid Fusion", g.title)
    }

    @Test
    fun `ids are unique`() {
        val s = LocalGameStore(file())
        assertNotEquals(s.add("a", "n64", "/a").id, s.add("a", "n64", "/a").id)
    }

    @Test
    fun `update keeps the id and persists`() {
        val f = file()
        val s = LocalGameStore(f)
        val g = s.add("Gex", "3do", "/g")
        val u = s.update(g.id) { it.copy(id = "hacked", coverPath = "/c.png", scrapeStatus = ScrapeStatus.DONE) }!!
        assertEquals(g.id, u.id)
        val again = LocalGameStore(f).get(g.id)!!
        assertEquals("/c.png", again.coverPath)
        assertEquals(ScrapeStatus.DONE, again.scrapeStatus)
        assertNull(s.update("missing") { it })
    }

    @Test
    fun `remove deletes only that game`() {
        val s = LocalGameStore(file())
        val a = s.add("a", "n64", "/a"); val b = s.add("b", "n64", "/b")
        assertTrue(s.remove(a.id))
        assertFalse(s.remove(a.id))
        assertEquals(listOf(b.id), s.all().map { it.id })
    }

    @Test
    fun `a corrupt file is kept aside and the store starts empty`() {
        val f = file()
        f.writeText("{not json")
        val s = LocalGameStore(f)
        assertTrue(s.all().isEmpty())
        assertTrue(File(f.path + ".corrupt").readText() == "{not json")
        s.add("ok", "n64", "/ok")
        assertEquals(1, LocalGameStore(f).all().size)
    }

    @Test
    fun `local game is annotated so R8 keeps its JSON field names`() {
        assertTrue(LocalGame::class.java.isAnnotationPresent(JsonClass::class.java))
        assertTrue(ScrapeStatus::class.java.isAnnotationPresent(JsonClass::class.java))
    }
}
