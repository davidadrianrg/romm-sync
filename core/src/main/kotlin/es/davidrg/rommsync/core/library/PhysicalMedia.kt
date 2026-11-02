package es.davidrg.rommsync.core.library

/**
 * Imagen del medio físico (cartucho, disco, caja) que RomM guarda como
 * metadato `physical` y sirve desde sus resources.
 */
object PhysicalMedia {

    private const val RESOURCES_PREFIX = "assets/romm/resources/"

    /**
     * URL absoluta de un `physical_path` de RomM:
     * `<server>/assets/romm/resources/<path>`.
     *
     * Tolera rutas que ya traigan el prefijo de resources o una barra inicial.
     * Devuelve null si falta el servidor o la ruta.
     */
    fun resourceUrl(serverUrl: String, physicalPath: String?): String? {
        val server = serverUrl.trim().trimEnd('/')
        val path = physicalPath?.trim()?.removePrefix("/")?.takeIf { it.isNotEmpty() } ?: return null
        if (server.isEmpty()) return null
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val relative = path.removePrefix(RESOURCES_PREFIX)
        return "$server/$RESOURCES_PREFIX$relative"
    }

    /** Extensión de fichero para la caché local (por defecto `png`). */
    fun cacheExtension(url: String): String {
        val ext = url.substringBefore('?').substringAfterLast('/', "").substringAfterLast('.', "")
            .lowercase()
        return if (ext in setOf("png", "jpg", "jpeg", "webp")) ext else "png"
    }
}
