package es.davidrg.rommsync.data.sync

import android.content.Context
import es.davidrg.rommsync.core.sync.StagedPath
import es.davidrg.rommsync.data.storage.ManagedStorageAccessor
import java.io.File

/**
 * Staging de saves sobre `Android/data|obb` sin root, vía [ManagedStorageAccessor]
 * (`DocumentsContract` + `manage=true`).
 *
 * Copia el directorio real a una carpeta local para que los handlers del core
 * trabajen con la API File de siempre, y en [commit] sube los cambios con
 * `openOutputStream` (truncando). Mismo contrato que [SafStaging], pero sin
 * necesitar que el usuario haya concedido un árbol SAF.
 */
object ManagedStaging {

    fun stage(path: String, stageRoot: File, context: Context): StagedPath? {
        if (!ManagedStorageAccessor.canManage(path)) return null
        val (volume, relative) = ManagedStorageAccessor.parseManagedPath(path) ?: return null
        val accessor = ManagedStorageAccessor(context)
        if (!accessor.isListable(volume, relative)) return null

        val name = path.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80) + "_" + System.nanoTime()
        val staged = File(stageRoot, name)
        if (!staged.mkdirs() && !staged.isDirectory) return null

        if (!copyTree(accessor, volume, relative, staged)) {
            staged.deleteRecursively()
            return null
        }
        // Directorio vacío: no aporta nada (findSaves vería lo mismo sin staging).
        if (staged.walkTopDown().none { it.isFile }) {
            staged.deleteRecursively()
            return null
        }
        return StagedPath(original = path, dir = staged, viaManaged = true)
    }

    /** Sube a Android/data los ficheros locales (upsert por nombre; no borra extras). */
    fun commit(staged: StagedPath, context: Context): Boolean {
        if (!staged.isCopy) return true
        val (volume, relative) = ManagedStorageAccessor.parseManagedPath(staged.original) ?: return false
        val accessor = ManagedStorageAccessor(context)
        return upsert(accessor, volume, relative, staged.dir)
    }

    private fun copyTree(
        accessor: ManagedStorageAccessor,
        volume: String,
        relative: String,
        dstDir: File,
    ): Boolean {
        val entries = accessor.list(volume, relative) ?: return false
        for (entry in entries) {
            val childRel = "$relative/${entry.name}"
            if (entry.isDirectory) {
                val sub = File(dstDir, entry.name)
                if (!sub.mkdirs() && !sub.isDirectory) return false
                if (!copyTree(accessor, volume, childRel, sub)) return false
            } else {
                val input = accessor.openInput(volume, childRel) ?: return false
                val out = File(dstDir, entry.name)
                try {
                    input.use { ins -> out.outputStream().use { ins.copyTo(it) } }
                } catch (_: Exception) {
                    return false
                }
            }
        }
        return true
    }

    private fun upsert(
        accessor: ManagedStorageAccessor,
        volume: String,
        relative: String,
        local: File,
    ): Boolean {
        var ok = true
        for (f in local.listFiles().orEmpty()) {
            val childRel = "$relative/${f.name}"
            if (f.isDirectory) {
                if (!accessor.makeDirectories(volume, childRel)) {
                    ok = false
                    continue
                }
                ok = upsert(accessor, volume, childRel, f) && ok
            } else {
                val out = accessor.openOutput(volume, childRel)
                if (out == null) {
                    ok = false
                    continue
                }
                val wrote = try {
                    out.use { os -> f.inputStream().use { it.copyTo(os) } }
                    true
                } catch (_: Exception) {
                    false
                }
                ok = wrote && ok
            }
        }
        return ok
    }
}
