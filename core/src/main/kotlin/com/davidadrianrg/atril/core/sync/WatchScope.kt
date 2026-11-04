package com.davidadrianrg.atril.core.sync

/**
 * Qué carpetas merece la pena vigilar en tiempo real. El watcher no debe recorrer el árbol
 * de ROMs ni los directorios de emuladores: solo las carpetas de guardado de plataformas con
 * al menos un juego descargado y con sync activo, a poca profundidad y sin directorios de
 * cachés/ROMs/medios.
 */
object WatchScope {
    /** Profundidad máxima bajo cada raíz de saves (raíz = 0). */
    const val MAX_DEPTH = 2

    /** Tope duro de carpetas observadas. */
    const val MAX_DIRS = 60

    /** Nombres de directorio que nunca contienen saves (comparación sin mayúsculas). */
    val IGNORED_DIR_NAMES: Set<String> = setOf(
        "roms", "rom", "games", "cache", "caches", "code_cache", "thumbnails", "logs", "log",
        "shaders", "shader", "textures", "assets", "overlays", "cheats", "system", "bios",
        "screenshots", "videos", "media", "downloaded_media", "temp", "tmp", "covers",
        "manuals", "backups", "save-backups", "autoconfig", "config", "playlists", "database",
    )

    data class DownloadedGame(val platformSlug: String, val excludedFromSync: Boolean)

    /** Plataformas con al menos un juego descargado que sincroniza. */
    fun activePlatforms(games: List<DownloadedGame>): Set<String> =
        games.filter { !it.excludedFromSync }.map { it.platformSlug }.toSet()

    /** ¿Se debe ignorar este camino (o cualquiera de sus segmentos)? */
    fun isExcludedPath(path: String): Boolean {
        val p = path.replace('\\', '/')
        if (p.contains("/Android/data/") || p.contains("/Android/obb/")) return true // FUSE: sin eventos
        return p.split('/').any { it.lowercase() in IGNORED_DIR_NAMES }
    }

    /** ¿Entrar en el subdirectorio [name] a profundidad [depth]? */
    fun shouldDescend(name: String, depth: Int): Boolean =
        depth <= MAX_DEPTH && !name.startsWith(".") && name.lowercase() !in IGNORED_DIR_NAMES
}
