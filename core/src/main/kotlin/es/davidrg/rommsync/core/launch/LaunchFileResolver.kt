package es.davidrg.rommsync.core.launch

import java.io.File

/** Fichero que se entrega al emulador. */
sealed interface LaunchFile {
    data class Found(val file: File) : LaunchFile

    /** Marcado como descargado a mano: la app no conoce el fichero. */
    data object Manual : LaunchFile

    /** La ruta guardada ya no existe en el dispositivo. */
    data class Missing(val path: String) : LaunchFile
}

/**
 * Resuelve el fichero lanzable de un juego descargado.
 *
 * - `localPath` vacío → [LaunchFile.Manual].
 * - Fichero existente → ese fichero, salvo que un `.m3u` hermano lo referencie
 *   (multidisco): entonces el `.m3u`.
 * - Carpeta → `.m3u` > índice de disco (`cue`, `gdi`, …) > mayor fichero.
 * - No existe: las ROMs multiarchivo descargadas como zip-stream se extraen en
 *   la carpeta de la plataforma y `localPath` apunta al `.zip` teórico. Se
 *   buscan los ficheros conocidos del juego (o una carpeta con su nombre) en
 *   esa carpeta.
 */
object LaunchFileResolver {

    /** Índices de disco y formatos de imagen, por prioridad. */
    private val PRIORITY = listOf(
        "m3u", "cue", "gdi", "chd", "ccd", "mds", "cso", "iso", "rvz", "gcz", "wbfs", "wia",
        "nsp", "xci", "xiso", "xex", "pbp",
    )

    private val IGNORED = setOf(
        "txt", "nfo", "jpg", "jpeg", "png", "webp", "sav", "srm", "state", "xml", "json", "md", "pdf",
    )

    fun resolve(localPath: String, knownFileNames: List<String> = emptyList()): LaunchFile {
        if (localPath.isBlank()) return LaunchFile.Manual
        val file = File(localPath)
        if (file.isFile) return LaunchFile.Found(playlistFor(file) ?: file)
        if (file.isDirectory) return pick(listFiles(file))?.let { LaunchFile.Found(it) } ?: LaunchFile.Missing(localPath)

        val parent = file.parentFile ?: return LaunchFile.Missing(localPath)
        val sameNameDir = File(parent, file.nameWithoutExtension)
        val fromKnown = knownFileNames.flatMap { name ->
            listOf(File(parent, name), File(sameNameDir, name), File(file, name))
        }.filter { it.isFile }
        pick(fromKnown)?.let { return LaunchFile.Found(it) }
        if (sameNameDir.isDirectory) pick(listFiles(sameNameDir))?.let { return LaunchFile.Found(it) }
        return LaunchFile.Missing(localPath)
    }

    private fun listFiles(dir: File): List<File> =
        dir.walkTopDown().maxDepth(2).filter { it.isFile }.toList()

    private fun pick(files: List<File>): File? {
        val usable = files.filter { it.extension.lowercase() !in IGNORED && !it.name.startsWith(".") }
        if (usable.isEmpty()) return null
        for (ext in PRIORITY) {
            usable.filter { it.extension.equals(ext, ignoreCase = true) }
                .minByOrNull { it.name.lowercase() }
                ?.let { return it }
        }
        return usable.maxByOrNull { it.length() }
    }

    /** `.m3u` en la misma carpeta que lista [file], si existe. */
    private fun playlistFor(file: File): File? {
        if (file.extension.equals("m3u", ignoreCase = true)) return null
        val siblings = file.parentFile?.listFiles { f -> f.isFile && f.extension.equals("m3u", ignoreCase = true) }
            ?: return null
        return siblings.sortedBy { it.name }.firstOrNull { m3u ->
            runCatching {
                m3u.readLines().any { line -> line.trim().substringAfterLast('/').substringAfterLast('\\') == file.name }
            }.getOrDefault(false)
        }
    }
}
