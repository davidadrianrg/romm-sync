package com.davidadrianrg.atril.core.localgames

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LocalGamesRepositoryTest {

    private fun repo(): Pair<LocalGamesRepository, File> {
        val dir = Files.createTempDirectory("localgames").toFile().apply { deleteOnExit() }
        val r = LocalGamesRepository(
            store = LocalGameStore(File(dir, "local_games.json")),
            coversDir = File(dir, "covers"),
            credentials = { ScraperCredentials("", "", "") },
        )
        return r to dir
    }

    @Test
    fun `replacing the cover changes its path and removes the old file`() = runBlocking {
        val (r, _) = repo()
        val game = r.add("Gex", "psx", "/roms/gex.chd")

        assertTrue(r.setManualCover(game.id, "first".byteInputStream()))
        val first = r.games.value.single().coverPath!!

        assertTrue(r.setManualCover(game.id, "second".byteInputStream()))
        val second = r.games.value.single().coverPath!!

        // Ruta distinta: la UI cachea por URL y si no cambiara seguiría la vieja.
        assertNotEquals(first, second)
        assertFalse(File(first).exists())
        assertEquals("second", File(second).readText())
    }

    @Test
    fun `removing a game deletes its cover`() = runBlocking {
        val (r, _) = repo()
        val game = r.add("Gex", "psx", "/roms/gex.chd")
        r.setManualCover(game.id, "img".byteInputStream())
        val cover = r.games.value.single().coverPath!!

        r.remove(game.id)

        assertTrue(r.games.value.isEmpty())
        assertFalse(File(cover).exists())
    }
}
