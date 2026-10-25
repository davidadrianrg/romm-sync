package es.davidrg.rommsync.data.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Empaqueta un árbol SAF (carpeta elegida con ACTION_OPEN_DOCUMENT_TREE) en un
 * ZIP sin copiar primero los ficheros al cache: los miembros se leen con
 * ContentResolver y se escriben directamente al zip.
 *
 * RomM no permite crear por API una ROM-carpeta nueva; sí ingiere un ZIP con
 * varios miembros como una ROM multi-fichero, así que "subir carpeta" = subir
 * este zip.
 */
object SafFolderZipper {

    fun zip(
        treeUri: Uri,
        destZip: File,
        context: Context,
        onProgress: (Int) -> Unit = {},
    ): Boolean {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return false
        val entries = mutableListOf<Pair<String, DocumentFile>>()

        fun collect(dir: DocumentFile, prefix: String) {
            dir.listFiles().forEach { child ->
                val name = child.name ?: return@forEach
                val path = if (prefix.isEmpty()) name else "$prefix/$name"
                if (child.isDirectory) collect(child, path) else entries.add(path to child)
            }
        }
        collect(root, "")
        if (entries.isEmpty()) return false

        return runCatching {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(destZip))).use { zos ->
                zos.setLevel(1)
                entries.forEachIndexed { index, (path, doc) ->
                    zos.putNextEntry(ZipEntry(path))
                    val input = context.contentResolver.openInputStream(doc.uri) ?: return false
                    input.use { it.copyTo(zos) }
                    zos.closeEntry()
                    onProgress(((index + 1) * 100) / entries.size)
                }
            }
            true
        }.getOrDefault(false)
    }
}
