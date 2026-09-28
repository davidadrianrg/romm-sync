package es.davidrg.rommsync.core.sync

import java.io.File
import java.util.Properties

/**
 * Versionado local de saves: antes de que una operación de sync sobrescriba
 * un save en disco, se guarda una copia aquí. Se conservan las últimas
 * [keepPerFile] versiones por fichero y el usuario puede restaurarlas.
 *
 * Los blobs se guardan tal cual los prepara el SaveHandler
 * (prepareForUpload: zip para saves de directorio), así que restaurar es
 * simplemente volver a pasarlos por extractDownload.
 *
 * Layout: baseDir/<romId>/<nombre-seguro>.<epochMillis>.bak
 *         baseDir/<romId>/index.properties (bak → fileName|ruta original)
 */
class SaveBackupManager(
    private val baseDir: File,
    private val keepPerFile: Int = 5,
) {

    data class BackupVersion(
        val romId: Int,
        val fileName: String,
        val backupFile: File,
        val timestamp: Long,
        val sizeBytes: Long,
    )

    /** Copia [source] (fichero preparado por el handler) como nueva versión. */
    @Synchronized
    fun backup(romId: Int, fileName: String, source: File): File? {
        if (!source.isFile) return null
        val dir = romDir(romId)
        val stamp = System.currentTimeMillis()
        val dest = File(dir, "${sanitize(fileName)}.$stamp.bak")
        source.copyTo(dest, overwrite = true)
        index(dir).let { props ->
            props["f.${dest.name}"] = fileName
            props.store(dir.resolve("index.properties").outputStream(), null)
        }
        prune(romId)
        return dest
    }

    /** Todas las versiones de un ROM, más recientes primero. */
    fun versions(romId: Int): List<BackupVersion> {
        val dir = romDir(romId)
        if (!dir.isDirectory) return emptyList()
        val props = index(dir)
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".bak") }
            ?.mapNotNull { f ->
                val fileName = props.getProperty("f.${f.name}") ?: return@mapNotNull null
                val stamp = f.name.removeSuffix(".bak").substringAfterLast('.').toLongOrNull() ?: f.lastModified()
                BackupVersion(romId, fileName, f, stamp, f.length())
            }
            ?.sortedByDescending { it.timestamp }
            ?: emptyList()
    }

    fun versions(romId: Int, fileName: String): List<BackupVersion> =
        versions(romId).filter { it.fileName == fileName }

    private fun romDir(romId: Int): File = File(baseDir, romId.toString()).apply { mkdirs() }

    private fun index(dir: File): Properties {
        val props = Properties()
        val idx = dir.resolve("index.properties")
        if (idx.isFile) runCatching { idx.inputStream().use { props.load(it) } }
        return props
    }

    private fun prune(romId: Int) {
        val all = versions(romId).groupBy { it.fileName }
        for ((_, list) in all) {
            list.drop(keepPerFile).forEach { it.backupFile.delete() }
        }
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._ -]"), "_").take(120)
}
