package com.davidadrianrg.atril.launch

import com.davidadrianrg.atril.core.launch.EmulatorCatalog
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Android 11+ solo deja ver los paquetes declarados en `<queries>`: si un
 * emulador del catálogo no está ahí, el launcher lo daría por no instalado.
 */
class ManifestQueriesTest {

    @Test
    fun `manifest queries declare every catalog package`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val declared = Regex("""<package\s+android:name="([^"]+)"""").findAll(manifest)
            .map { it.groupValues[1] }
            .toSet()
        val missing = EmulatorCatalog.allPackages - declared
        assertTrue("Missing <queries> entries: $missing", missing.isEmpty())
    }

    @Test
    fun `file provider exposes ROM storage but not app data`() {
        val paths = File("src/main/res/xml/file_paths.xml").readText()
        assertTrue(paths.contains("<external-path"))
        assertTrue(paths.contains("""<root-path name="storage" path="storage" />"""))
        assertTrue("root-path must not expose /", !paths.contains("""<root-path name="root" path="." />"""))
    }
}
