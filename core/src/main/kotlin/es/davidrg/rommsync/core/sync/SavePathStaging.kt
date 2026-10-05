package es.davidrg.rommsync.core.sync

import es.davidrg.rommsync.core.util.RootShell
import java.io.File

/**
 * Ruta de saves + copia staged opcional para rutas restringidas bajo /storage.
 */
data class StagedPath(val original: String, val dir: File) {
    /** true si [dir] es una copia temporal y no la propia ruta original. */
    val isCopy: Boolean get() = dir.path != original
}

/**
 * Acceso a rutas restringidas de Android (Android/data, Android/obb) mediante
 * staging con root.
 *
 * Desde Android 11 (y con garantía desde 13, además con los parches de
 * seguridad de marzo 2024) ni siquiera el permiso «Todos los archivos»
 * (MANAGE_EXTERNAL_STORAGE) permite leer Android/data con la API File: el
 * sistema FUSE devuelve EACCES para todas las apps. El único camino sin USB
 * es root (Magisk/su) — ZArchiver hace lo mismo con su ajuste de root.
 *
 * Estrategia: si una ruta bajo /storage no es legible por File y hay root,
 * copia el contenido a una carpeta staged accesible (cp -af + chmod 0777) y
 * devuelve esa copia para que los handlers existentes trabajen con la API
 * File tal cual. En descargas, [commit] copia staged → original.
 *
 * Límites: solo /storage (nunca /data/data — los handlers propios ya tienen
 * lógica root ahí), tope de tamaño (evita copiar un Android/data completo si
 * alguien lo toma como base de un handler), y todo método degrada a la ruta
 * original si algo falla.
 */
object SavePathStaging {

    /** Tope de la copia staged: 64 MiB. Los saves viven en KB–MB. */
    private const val MAX_STAGE_BYTES = 64L * 1024 * 1024

    /** Rutas FUSE donde staging aporta; el resto se ignora. */
    private fun isStagingCandidate(path: String): Boolean =
        path.startsWith("/storage/") || path.startsWith("/sdcard/")

    fun stage(original: String, stageRoot: File): StagedPath {
        val direct = File(original)

        // No candidato o ya legible por la app: nada que hacer.
        if (!isStagingCandidate(original)) return StagedPath(original, direct)
        if (direct.isDirectory && direct.listFiles() != null) return StagedPath(original, direct)
        if (!RootShell.available) return StagedPath(original, direct)
        if (RootShell.run("test -d ${RootShell.sq(original)}") == null) return StagedPath(original, direct)

        // Guard de tamaño: si esto pesa como un Android/data completo, no copiar.
        val sizeKb = RootShell.run("du -sk ${RootShell.sq(original)}")
            ?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.toLongOrNull()
        if (sizeKb != null && sizeKb * 1024 > MAX_STAGE_BYTES) return StagedPath(original, direct)

        val name = original.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80) + "_" + System.nanoTime()
        val staged = File(stageRoot, name)
        val ok = RootShell.run(
            "rm -rf ${RootShell.sq(staged.absolutePath)} && " +
                "mkdir -p ${RootShell.sq(staged.absolutePath)} && " +
                "cp -af ${RootShell.sq(original)}/. ${RootShell.sq(staged.absolutePath)}/ && " +
                "chmod -R 0777 ${RootShell.sq(staged.absolutePath)}",
            timeoutSec = 120,
        ) != null
        if (!ok || !staged.isDirectory || staged.listFiles() == null) {
            RootShell.run("rm -rf ${RootShell.sq(staged.absolutePath)}")
            return StagedPath(original, direct)
        }
        return StagedPath(original, staged)
    }

    /**
     * Copia staged de vuelta a la ruta original (tras extraer una descarga).
     * true si no hacía falta o se copió correctamente.
     */
    fun commit(staged: StagedPath): Boolean {
        if (!staged.isCopy) return true
        return RootShell.run(
            "cp -af ${RootShell.sq(staged.dir.absolutePath)}/. ${RootShell.sq(staged.original)}/ && " +
                "chmod -R 0777 ${RootShell.sq(staged.original)}",
            timeoutSec = 120,
        ) != null
    }

    /** Borra la copia staged (mejor esfuerzo; si falla, la barre el próximo arranque). */
    fun cleanup(staged: StagedPath) {
        if (!staged.isCopy) return
        if (!staged.dir.deleteRecursively()) {
            RootShell.run("rm -rf ${RootShell.sq(staged.dir.absolutePath)}")
        }
    }
}
