package com.davidadrianrg.atril.core.localgames

import java.net.URLDecoder

/** Resolución de rutas para los juegos locales (puras, sin Android). */
object LocalGamePaths {

    private const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

    /**
     * Convierte lo que devuelve el selector de documentos de Android en una ruta
     * de fichero, o null si no se puede (nube, descargas virtuales…). Acepta una
     * ruta absoluta, `file://` y URIs `content://` del almacenamiento externo:
     * `…/document/primary:ROMs/3do/Gex.chd` → `/storage/emulated/0/ROMs/3do/Gex.chd`,
     * y `…/document/1234-ABCD:ROMs/x.gba` → `/storage/1234-ABCD/ROMs/x.gba`.
     */
    fun fromPickedUri(uri: String): String? {
        val raw = uri.trim()
        if (raw.startsWith("/")) return raw
        if (raw.startsWith("file://")) return decode(raw.removePrefix("file://")).takeIf { it.startsWith("/") }
        if (!raw.startsWith("content://$EXTERNAL_STORAGE_AUTHORITY/")) return null
        val docId = decode(raw.substringAfter("/document/", missingDelimiterValue = "").substringBefore('?'))
        if (docId.isEmpty() || ':' !in docId) return null
        val volume = docId.substringBefore(':')
        val relative = docId.substringAfter(':').trim('/')
        if (relative.isEmpty() || relative.split('/').any { it == ".." }) return null
        return when (volume) {
            "primary" -> "/storage/emulated/0/$relative"
            "home", "raw" -> null
            else -> "/storage/$volume/$relative"
        }
    }

    private fun decode(s: String) = runCatching { URLDecoder.decode(s.replace("+", "%2B"), "UTF-8") }.getOrDefault(s)

    /**
     * Adivina la plataforma por las carpetas del fichero (`…/ROMs/n64/juego.z64`
     * → `n64`), comparando con los [knownSlugs] sin distinguir mayúsculas.
     */
    fun guessPlatform(path: String, knownSlugs: Collection<String>): String? {
        val known = knownSlugs.associateBy { it.lowercase() }
        return path.split('/').dropLast(1).asReversed().firstNotNullOfOrNull { known[it.lowercase()] }
    }
}
