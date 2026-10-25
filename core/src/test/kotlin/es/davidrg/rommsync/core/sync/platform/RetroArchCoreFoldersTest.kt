package es.davidrg.rommsync.core.sync.platform

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * RetroArch con «ordenar saves/states por core» y bases que apuntan
 * directamente al directorio de saves (savefile_directory del cfg).
 */
class RetroArchCoreFoldersTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `finds saves and states inside per-core folders`() = runBlocking {
        val root = tmp.newFolder("RetroArch")
        File(root, "saves/mGBA").mkdirs()
        File(root, "saves/mGBA/Game.srm").writeText("s")
        File(root, "states/bsnes").mkdirs()
        File(root, "states/bsnes/Game.state1").writeText("st")

        val saves = RetroArchSaveHandler().findSaves(
            romId = 1,
            romFileName = "Game.gba",
            platformSlug = "gba",
            savesBasePath = root.absolutePath,
            romLocalPath = null,
        )

        assertEquals(setOf("Game.srm", "Game.state1"), saves.map { it.fileName }.toSet())
    }

    @Test
    fun `works when the base path is the saves directory itself`() = runBlocking {
        val savesDir = tmp.newFolder("custom-saves")
        File(savesDir, "Game.srm").writeText("s")
        File(savesDir, "mGBA").mkdirs()
        File(savesDir, "mGBA/Game2.srm").writeText("s2")

        val direct = RetroArchSaveHandler().findSaves(1, "Game.gba", "gba", savesDir.absolutePath, null)
        assertEquals(listOf("Game.srm"), direct.map { it.fileName })

        val nested = RetroArchSaveHandler().findSaves(2, "Game2.gba", "gba", savesDir.absolutePath, null)
        assertEquals(listOf("Game2.srm"), nested.map { it.fileName })
    }
}
