package es.davidrg.rommsync.core.i18n

import java.io.InputStreamReader
import java.util.Locale
import java.util.Properties

/**
 * User-facing texts, shared by the desktop and Android apps.
 *
 * The texts live in `i18n/messages.properties` (English, also the fallback
 * for missing keys) and one `i18n/messages_<language>.properties` per
 * translation, read as UTF-8. Placeholders are positional: `{0}`, `{1}`...
 *
 * The language follows the system locale; [locale] can override it.
 */
object I18n {
    private const val BASE = "/i18n/messages"

    private val fallback: Properties = load(null) ?: Properties()

    @Volatile
    private var translation: Properties? = load(Locale.getDefault().language)

    @Volatile
    var locale: Locale = Locale.getDefault()
        set(value) {
            field = value
            translation = load(value.language)
        }

    /** The locale the texts are shown in: [locale] if it has a translation, else English. */
    val textLocale: Locale get() = if (translation != null) locale else Locale.ENGLISH

    fun tr(key: String, vararg args: Any?): String {
        val pattern = translation?.getProperty(key) ?: fallback.getProperty(key) ?: return key
        return format(pattern, *args)
    }

    internal fun format(pattern: String, vararg args: Any?): String =
        args.foldIndexed(pattern) { i, text, arg -> text.replace("{$i}", arg.toString()) }

    internal fun load(language: String?): Properties? {
        val name = if (language.isNullOrEmpty()) "$BASE.properties" else "${BASE}_$language.properties"
        val stream = I18n::class.java.getResourceAsStream(name) ?: return null
        return stream.use { Properties().apply { load(InputStreamReader(it, Charsets.UTF_8)) } }
    }
}

/** Shorthand for [I18n.tr]. */
fun tr(key: String, vararg args: Any?): String = I18n.tr(key, *args)
