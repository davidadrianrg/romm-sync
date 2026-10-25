package es.davidrg.rommsync.util

import es.davidrg.rommsync.core.i18n.I18n
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale

/**
 * Cambio de idioma en caliente (Android).
 *
 * [apply] fija el locale de [I18n] y emite en [revision]; MainActivity envuelve
 * el árbol Compose en `key(revision)` para que TODOS los `tr()` se vuelvan a
 * evaluar sin reiniciar la app.
 */
object LocaleController {

    val revision = MutableStateFlow(0)

    /** @param tag "" = idioma del sistema; p. ej. "en", "es". */
    fun apply(tag: String) {
        I18n.locale = if (tag.isBlank()) Locale.getDefault() else Locale.forLanguageTag(tag)
        revision.value++
    }
}
