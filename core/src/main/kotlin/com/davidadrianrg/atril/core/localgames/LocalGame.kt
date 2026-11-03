package com.davidadrianrg.atril.core.localgames

import com.squareup.moshi.JsonClass

/**
 * Juego añadido a mano, que NO está en RomM. Es una fuente local separada: la
 * app nunca lo presenta ni lo sube como si fuera de RomM.
 *
 * `@JsonClass` hace que la regla `-keep @JsonClass` de R8 conserve los nombres:
 * sin ella, la release minificada guardaría claves ofuscadas y una versión
 * posterior marcaría el JSON como corrupto (perdiendo la lista).
 */
@JsonClass(generateAdapter = false)
data class LocalGame(
    val id: String,
    val title: String,
    /** Slug de plataforma (los mismos que usa el resto de la app, p. ej. `n64`). */
    val platformSlug: String,
    /** Ruta absoluta del fichero (o URI `content://` en Android). */
    val path: String,
    /** Carátula descargada en disco, si se hizo scrapeo o el usuario la eligió. */
    val coverPath: String? = null,
    val summary: String? = null,
    /** Quién aportó carátula/metadatos: `steamgriddb`, `screenscraper`, `manual`. */
    val scrapeSource: String? = null,
    val scrapeStatus: ScrapeStatus = ScrapeStatus.NONE,
    val addedAt: Long = 0L,
)

/** Se persiste por nombre: `@JsonClass` evita que R8 renombre las constantes. */
@JsonClass(generateAdapter = false)
enum class ScrapeStatus {
    /** Sin intentar (placeholder). */
    NONE,

    /** Se encontró carátula/metadatos. */
    DONE,

    /** Se buscó y el proveedor no tiene el juego. */
    NOT_FOUND,

    /** Falló la red/credenciales; se puede reintentar. */
    FAILED,
}
