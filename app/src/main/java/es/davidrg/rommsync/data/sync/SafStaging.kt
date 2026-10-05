package es.davidrg.rommsync.data.sync

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import es.davidrg.rommsync.core.sync.StagedPath
import java.io.File

/**
 * Staging de saves vía SAF (sin root): copia el subárbol concedido por
 * [SafTreeStore] a una carpeta local para que los handlers trabajen con la
 * API File tal cual, y revierte los cambios con openOutputStream en [commit].
 *
 * Es el mismo mecanismo que ZArchiver usa en Android 11/12 para ver
 * `Android/data` sin root (el selector SAF dejaba elegir esas carpetas;
 * en Android 13+ está cerrado).
 */
object SafStaging {

    /** Copia el subárbol de [path] dentro del árbol [treeUri] a un directorio staged. */
    fun stage(path: String, stageRoot: File, treeUri: String, context: Context): StagedPath? {
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(treeUri)) ?: return null
        val target = navigate(tree, path, treeUri) ?: return null
        if (!target.isDirectory) return null

        val name = path.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80) + "_" + System.nanoTime()
        val staged = File(stageRoot, name)
        if (!staged.mkdirs() && !staged.isDirectory) return null

        if (!copyTree(context, target, staged)) {
            staged.deleteRecursively()
            return null
        }
        // Árbol realmente vacío: no aporta nada staged (findSaves vería lo mismo).
        if (staged.walkTopDown().none { it.isFile }) {
            staged.deleteRecursively()
            return null
        }
        return StagedPath(original = path, dir = staged, viaSaf = true)
    }

    /**
     * Escribe en el árbol concedido los ficheros cambiados en [staged]
     * (upsert por nombre; no borra extras — el sync restaura contenido, no
     * refleja borrados).
     */
    fun commit(staged: StagedPath, treeUri: String, context: Context): Boolean {
        if (!staged.isCopy) return true
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(treeUri)) ?: return false
        val target = navigate(tree, staged.original, treeUri) ?: return false
        return upsert(context, target, staged.dir)
    }

    /** Camina desde la raíz del árbol concedido hasta [path] (relativo al árbol). */
    private fun navigate(root: DocumentFile, path: String, treeUri: String): DocumentFile? {
        val treePath = SafTreeStore.pathFromTreeUri(Uri.parse(treeUri))?.trimEnd('/') ?: return null
        val p = path.trimEnd('/')
        if (p == treePath) return root
        if (!p.startsWith("$treePath/")) return null
        var cur = root
        for (seg in p.removePrefix("$treePath/").split('/').filter { it.isNotEmpty() }) {
            cur = cur.findFile(seg) ?: return null
        }
        return cur
    }

    /** Copia recursiva DocumentFile → File. false en el primer fallo de lectura. */
    private fun copyTree(context: Context, src: DocumentFile, dstDir: File): Boolean {
        for (child in src.listFiles()) {
            val name = child.name ?: continue
            if (child.isDirectory) {
                val sub = File(dstDir, name)
                if (!sub.mkdirs() && !sub.isDirectory) return false
                if (!copyTree(context, child, sub)) return false
            } else {
                val input = runCatching {
                    context.contentResolver.openInputStream(child.uri)
                }.getOrNull() ?: return false
                val out = File(dstDir, name)
                input.use { ins ->
                    out.outputStream().use { ins.copyTo(it) }
                }
            }
        }
        return true
    }

    private fun upsert(context: Context, dir: DocumentFile, local: File): Boolean {
        var ok = true
        val children = dir.listFiles()
            .mapNotNull { f -> f.name?.let { it to f } }
            .toMap()
        for (f in local.listFiles().orEmpty()) {
            if (f.isDirectory) {
                val sub = children[f.name]?.takeIf { it.isDirectory }
                    ?: dir.createDirectory(f.name)
                if (sub == null) {
                    ok = false
                    continue
                }
                ok = upsert(context, sub, f) && ok
            } else {
                val target = children[f.name]?.takeIf { !it.isDirectory }
                    ?: dir.createFile(mimeFor(f.name), f.name)
                if (target == null) {
                    ok = false
                    continue
                }
                val wrote = runCatching {
                    // "wt" = truncar: el save del servidor debe pisar el local.
                    val out = context.contentResolver.openOutputStream(target.uri, "wt")
                        ?: return@runCatching false
                    out.use { os -> f.inputStream().use { it.copyTo(os) } }
                    true
                }.getOrDefault(false)
                ok = wrote && ok
            }
        }
        return ok
    }

    private fun mimeFor(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: "application/octet-stream"
    }
}
