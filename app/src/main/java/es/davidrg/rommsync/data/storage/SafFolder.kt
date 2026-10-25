package es.davidrg.rommsync.data.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

/**
 * Utilidades para la subida de una CARPETA elegida con ACTION_OPEN_DOCUMENT_TREE:
 * listar sus ficheros y copiarlos uno a uno al cache para subirlos (RomM crea
 * una ROM multi-fichero: primer fichero como ROM y el resto dentro de ella).
 */
object SafFolder {

    /** Todos los ficheros del árbol, recursivo, ordenados por ruta. */
    fun listFiles(treeUri: Uri, context: Context): List<DocumentFile> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val out = mutableListOf<DocumentFile>()
        fun collect(dir: DocumentFile) {
            dir.listFiles().forEach { child ->
                if (child.isDirectory) collect(child) else out.add(child)
            }
        }
        collect(root)
        return out.sortedBy { it.name?.lowercase() ?: "" }
    }

    /** Nombre de la carpeta elegida (para mensajes). */
    fun folderName(treeUri: Uri, context: Context): String =
        DocumentFile.fromTreeUri(context, treeUri)?.name ?: "upload"

    /** Copia un DocumentFile al cache con su nombre; null si falla. */
    fun copyToCache(context: Context, doc: DocumentFile, cacheDir: File): File? {
        val name = doc.name ?: return null
        val out = File(cacheDir, name)
        return runCatching {
            context.contentResolver.openInputStream(doc.uri)?.use { input ->
                out.outputStream().use { input.copyTo(it) }
            } ?: return null
            out
        }.getOrNull()
    }
}
