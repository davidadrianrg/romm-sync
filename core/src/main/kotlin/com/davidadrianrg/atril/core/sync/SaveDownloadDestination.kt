package com.davidadrianrg.atril.core.sync

/**
 * Destino de una descarga manual de un save: la ruta de sync configurada del juego, u «otra
 * carpeta» elegida por el usuario. Pura (compartida Android/escritorio) para poder probarla.
 */
object SaveDownloadDestination {
    /** Carpeta absoluta, sin segmentos `..` ni vacía. */
    fun isValidFolder(path: String?): Boolean {
        val p = path?.trim().orEmpty()
        if (p.isEmpty() || !p.startsWith("/")) return false
        return p.split('/').none { it == ".." }
    }

    /** [custom] si es válida; si no, la ruta de sync [syncBase]. */
    fun resolve(custom: String?, syncBase: String): String =
        if (isValidFolder(custom)) custom!!.trim().trimEnd('/').ifEmpty { "/" } else syncBase
}
