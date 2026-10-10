package es.davidrg.rommsync.core.i18n

import es.davidrg.rommsync.core.sync.ConflictPolicy
import es.davidrg.rommsync.core.sync.platform.SaveHandlerRegistry
import java.io.File
import java.util.Locale
import java.util.Properties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class I18nTest {

    private val resources = File("src/main/resources/i18n")
    private val base = I18n.load(null)!!

    private fun translations(): Map<String, Properties> =
        resources.listFiles { f -> f.name.matches(Regex("messages_[a-z]+\\.properties")) }!!
            .associate { it.name.removePrefix("messages_").removeSuffix(".properties") to I18n.load(it.name.removePrefix("messages_").removeSuffix(".properties"))!! }

    private fun placeholders(text: String): Set<String> =
        Regex("\\{\\d+\\}").findAll(text).map { it.value }.toSet()

    @Test
    fun `there is at least one translation`() {
        assertTrue(translations().isNotEmpty())
    }

    @Test
    fun `every translation has exactly the keys of the English file`() {
        for ((lang, props) in translations()) {
            val missing = base.stringPropertyNames() - props.stringPropertyNames()
            val extra = props.stringPropertyNames() - base.stringPropertyNames()
            assertTrue("[$lang] missing keys: $missing", missing.isEmpty())
            assertTrue("[$lang] keys not in messages.properties: $extra", extra.isEmpty())
        }
    }

    @Test
    fun `every translation uses the same placeholders as English`() {
        for ((lang, props) in translations()) {
            for (key in base.stringPropertyNames()) {
                val translated = props.getProperty(key) ?: continue
                assertEquals(
                    "[$lang] placeholders of $key",
                    placeholders(base.getProperty(key)),
                    placeholders(translated),
                )
            }
        }
    }

    @Test
    fun `every key used in the sources exists`() {
        val call = Regex("""\btr\(\s*"([A-Za-z0-9_.]+)"""")
        val used = listOf("src/main", "../desktop/src/main", "../app/src/main")
            .map(::File)
            .filter { it.isDirectory }
            .flatMap { dir -> dir.walk().filter { it.extension == "kt" }.toList() }
            .flatMap { file -> call.findAll(file.readText()).map { it.groupValues[1] to file.name }.toList() }
        val unknown = used.filter { (key, _) -> base.getProperty(key) == null }
        assertTrue("unknown keys: $unknown", unknown.isEmpty())
    }

    @Test
    fun `labels built from enum ids are all translated`() {
        for (policy in ConflictPolicy.entries) {
            assertNotEquals(policy.displayName, "conflict_policy.${policy.id}")
        }
        for (emulator in SaveHandlerRegistry.EmulatorId.entries) {
            assertNotEquals(emulator.displayName, "emulator.${emulator.id}")
        }
    }

    @Test
    fun `placeholders are filled in order and unknown keys fall back to the key`() {
        assertEquals("1 of 2: «x»", I18n.format("{0} of {1}: «{2}»", 1, 2, "x"))
        assertEquals("{0} stays when nothing fills it", I18n.format("{0} stays when nothing fills it"))
        assertEquals("no.such.key", tr("no.such.key"))
    }

    @Test
    fun `switching the locale switches the texts`() {
        val previous = I18n.locale
        try {
            I18n.locale = Locale.ENGLISH
            val english = tr("conflict_policy.ask")
            I18n.locale = Locale.forLanguageTag("es")
            assertNotEquals(english, tr("conflict_policy.ask"))
            assertEquals(Locale.forLanguageTag("es"), I18n.textLocale)
            I18n.locale = Locale.forLanguageTag("fr")
            assertEquals("unknown languages fall back to English", english, tr("conflict_policy.ask"))
            assertEquals("dates follow the shown language", Locale.ENGLISH, I18n.textLocale)
        } finally {
            I18n.locale = previous
        }
    }
}
