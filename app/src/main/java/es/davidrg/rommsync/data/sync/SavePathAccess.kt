package es.davidrg.rommsync.data.sync

import android.content.Context
import es.davidrg.rommsync.core.sync.SavePathStaging
import es.davidrg.rommsync.core.sync.StagedPath
import es.davidrg.rommsync.core.util.RootShell
import java.io.File

/**
 * Punto único de staging de rutas de saves de la app Android. En orden:
 * 1. Legible por File → sin copia (lo normal: /storage/emulated/0/...).
 * 2. Root (teléfonos rooteados) → [SavePathStaging] (cp con su).
 * 3. Concesión SAF guardada (Android 11/12 sin root, el mecanismo de
 *    ZArchiver) → [SafStaging] (ContentResolver).
 *
 * Si nada funciona se devuelve la ruta original: los avisos de ruta
 * ilegible se encargan de explicarlo.
 */
object SavePathAccess {

    fun stage(path: String, stageRoot: File, context: Context): StagedPath {
        val direct = File(path)
        if (direct.isDirectory && direct.listFiles() != null) return StagedPath(path, direct)

        val rootStaged = SavePathStaging.stage(path, stageRoot)
        if (rootStaged.isCopy) return rootStaged

        val treeUri = SafTreeStore(context).findTreeUriFor(path) ?: return StagedPath(path, direct)
        return SafStaging.stage(path, stageRoot, treeUri, context) ?: StagedPath(path, direct)
    }

    fun commit(staged: StagedPath, context: Context): Boolean {
        if (!staged.isCopy) return true
        if (staged.viaSaf) {
            val treeUri = SafTreeStore(context).findTreeUriFor(staged.original) ?: return false
            return SafStaging.commit(staged, treeUri, context)
        }
        return SavePathStaging.commit(staged)
    }

    fun cleanup(staged: StagedPath, context: Context) {
        if (!staged.isCopy) return
        if (staged.viaSaf) {
            // staged es local y de la app: borrado directo.
            if (!staged.dir.deleteRecursively()) staged.dir.delete()
        } else {
            SavePathStaging.cleanup(staged)
        }
    }

    /** true si la ruta es utilizable: File, root o concesión SAF vigente. */
    fun isUsable(path: String, context: Context): Boolean {
        if (File(path).isDirectory) return true
        if (RootShell.available &&
            RootShell.run("test -d ${RootShell.sq(path)}") != null
        ) return true
        return SafTreeStore(context).hasGrantFor(path)
    }
}
