package es.davidrg.rommsync.core.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SaveNamesTest {

    @Test
    fun `strips the slot version tag before the extension`() {
        assertEquals(
            "Pokemon - Edicion Azul.srm",
            SaveNames.stripVersionTag("Pokemon - Edicion Azul [2026-11-01_12-34-56].srm"),
        )
    }

    @Test
    fun `keeps plain file names untouched`() {
        assertEquals("Metroid Fusion.sav", SaveNames.stripVersionTag("Metroid Fusion.sav"))
    }

    @Test
    fun `strips tags from zip saves`() {
        assertEquals(
            "Dolphin Save.zip",
            SaveNames.stripVersionTag("Dolphin Save [2026-10-31_08-00-00].zip"),
        )
    }

    @Test
    fun `does not strip a timestamp that is part of the name`() {
        assertEquals(
            "Game [2026-11-01] notes.srm",
            SaveNames.stripVersionTag("Game [2026-11-01] notes.srm"),
        )
    }
}
