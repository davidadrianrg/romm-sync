package com.davidadrianrg.atril.core.localgames

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalGamePathsTest {

    private val authority = "content://com.android.externalstorage.documents"

    @Test
    fun `primary storage document becomes an emulated path`() {
        assertEquals(
            "/storage/emulated/0/ROMs/3do/Gex.chd",
            LocalGamePaths.fromPickedUri("$authority/document/primary%3AROMs%2F3do%2FGex.chd"),
        )
    }

    @Test
    fun `sd card volume keeps its id`() {
        assertEquals(
            "/storage/1234-ABCD/ROMs/gba/Metroid Fusion.gba",
            LocalGamePaths.fromPickedUri("$authority/document/1234-ABCD%3AROMs%2Fgba%2FMetroid%20Fusion.gba"),
        )
    }

    @Test
    fun `tree-scoped document uris resolve too`() {
        assertEquals(
            "/storage/emulated/0/ROMs/n64/dk.z64",
            LocalGamePaths.fromPickedUri("$authority/tree/primary%3AROMs/document/primary%3AROMs%2Fn64%2Fdk.z64"),
        )
    }

    @Test
    fun `plus signs in names survive`() {
        assertEquals(
            "/storage/emulated/0/ROMs/a+b.sfc",
            LocalGamePaths.fromPickedUri("$authority/document/primary%3AROMs%2Fa+b.sfc"),
        )
    }

    @Test
    fun `plain paths and file uris pass through`() {
        assertEquals("/sdcard/x.gba", LocalGamePaths.fromPickedUri("/sdcard/x.gba"))
        assertEquals("/sdcard/My Game.gba", LocalGamePaths.fromPickedUri("file:///sdcard/My%20Game.gba"))
    }

    @Test
    fun `unresolvable or unsafe uris return null`() {
        assertNull(LocalGamePaths.fromPickedUri("content://com.google.android.apps.docs.storage/document/abc"))
        assertNull(LocalGamePaths.fromPickedUri("$authority/document/home%3Adoc.txt"))
        assertNull(LocalGamePaths.fromPickedUri("$authority/document/primary%3A..%2F..%2Fetc%2Fpasswd"))
        assertNull(LocalGamePaths.fromPickedUri("$authority/document/primary"))
        assertNull(LocalGamePaths.fromPickedUri(""))
    }

    @Test
    fun `platform is guessed from the closest known folder`() {
        val known = listOf("n64", "gba", "3do")
        assertEquals("3do", LocalGamePaths.guessPlatform("/storage/emulated/0/ROMs/3do/Gex.chd", known))
        assertEquals("n64", LocalGamePaths.guessPlatform("/roms/GBA/backup/N64/x.z64", known))
        assertNull(LocalGamePaths.guessPlatform("/storage/emulated/0/Download/x.bin", known))
        // el nombre del fichero no cuenta como carpeta
        assertNull(LocalGamePaths.guessPlatform("/downloads/gba", known))
    }
}
