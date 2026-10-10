package es.davidrg.rommsync.desktop

import es.davidrg.rommsync.core.i18n.I18n
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DesktopEsdeExporterTest {

    private val systemLocale = I18n.locale

    @Before
    fun spanish() {
        I18n.locale = Locale.forLanguageTag("es")
    }

    @After
    fun restoreLocale() {
        I18n.locale = systemLocale
    }

    @get:Rule
    val tmp = TemporaryFolder()

    private fun e(path: String, name: String) = DesktopEsdeExporter.Entry(path, name)

    @Test
    fun `gamelist nuevo se crea con todas las entradas`() {
        val (xml, added) = DesktopEsdeExporter.mergeGamelist(
            null,
            listOf(e("./gb/game.gba", "Game"), e("./gb/other.gba", "Other")),
        )
        assertEquals(2, added)
        assertTrue(xml.startsWith("<?xml"))
        assertTrue(xml.contains("<gameList>"))
        assertTrue(xml.contains("<path>./gb/game.gba</path>"))
        assertTrue(xml.contains("<name>Game</name>"))
        assertTrue(xml.contains("<path>./gb/other.gba</path>"))
    }

    @Test
    fun `entrada existente no se duplica ni se modifica`() {
        val existing = """
            <?xml version="1.0"?>
            <gameList>
              <!-- editado a mano -->
              <game>
                <path>./gb/game.gba</path>
                <name>Mi nombre editado</name>
                <desc>descripción manual</desc>
              </game>
            </gameList>
        """.trimIndent()

        val (xml, added) = DesktopEsdeExporter.mergeGamelist(existing, listOf(e("./gb/game.gba", "Game")))
        assertEquals(0, added)
        assertEquals(existing, xml) // verbatim, sin reescritura
    }

    @Test
    fun `entradas nuevas se añaden al final preservando las existentes`() {
        val existing = """
            <?xml version="1.0"?>
            <gameList>
              <game>
                <path>./gb/game.gba</path>
                <name>Game</name>
              </game>
            </gameList>
        """.trimIndent()

        val (xml, added) = DesktopEsdeExporter.mergeGamelist(
            existing,
            listOf(e("./gb/game.gba", "Game"), e("./gb/new.gba", "New")),
        )
        assertEquals(1, added)
        val gameCount = Regex("<game>").findAll(xml).count()
        assertEquals(2, gameCount)
        // La existente queda antes que la nueva.
        assertTrue(xml.indexOf("./gb/game.gba") < xml.indexOf("./gb/new.gba"))
        assertTrue(xml.contains("<path>./gb/new.gba</path>"))
        assertTrue(xml.trimEnd().endsWith("</gameList>"))
    }

    @Test
    fun `xml con caracteres especiales se escapa`() {
        val (xml, added) = DesktopEsdeExporter.mergeGamelist(null, listOf(e("./a&b/x<1>.zip", "Rom & \"Quest\"")))
        assertEquals(1, added)
        assertTrue(xml.contains("<path>./a&amp;b/x&lt;1&gt;.zip</path>"))
        assertTrue(xml.contains("<name>Rom &amp; &quot;Quest&quot;</name>"))
    }

    @Test
    fun `export completo escribe y vuelve a fusionar en disco`() {
        val libFile = tmp.newFile("library.properties")
        val library = DesktopLibrary(libFile)
        library.upsertRom(
            DesktopLibrary.RomEntry(1, "Game One", "game1.gba", "gb", tmp.newFolder().absolutePath),
        )
        val esdeDir = tmp.newFolder()
        val exporter = DesktopEsdeExporter(esdeDir.absolutePath, tmp.root.absolutePath, library)

        val first = exporter.export()
        assertTrue(first.contains("Añadidas 1 entradas"))
        val gamelist = java.io.File(java.io.File(esdeDir, "gamelists/gb"), "gamelist.xml")
        assertTrue(gamelist.isFile)
        val firstText = gamelist.readText()

        // Segunda exportación del mismo ROM: idempotente, no duplica.
        val second = exporter.export()
        assertTrue(second.contains("Añadidas 0 entradas"))
        assertEquals(firstText, gamelist.readText())
    }
}
