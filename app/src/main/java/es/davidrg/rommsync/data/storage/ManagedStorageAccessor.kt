package es.davidrg.rommsync.data.storage

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Acceso a `/storage/<vol>/Android/data|obb/…` sin root en Android 11+ (API 30),
 * usando `DocumentsContract` con el parámetro `manage=true`.
 *
 * Contexto: desde Android 11 (y con más garantía desde 13) la API File devuelve
 * null/EACCES para `Android/data` incluso con `MANAGE_EXTERNAL_STORAGE`. El
 * proveedor `com.android.externalstorage.documents` sí expone esos árboles
 * cuando la app tiene «Acceso a todos los archivos» y se le pasa `manage=true`
 * en la URI. Es el mecanismo que usa Argosy (launcher oficial de RomM) y el que
 * nos permite sincronizar saves de emuladores sin root ni SAF manual.
 *
 * En < API 30 se usa la API File normal.
 */
class ManagedStorageAccessor(private val context: Context) {

    data class Entry(
        val name: String,
        val documentId: String,
        val mimeType: String,
        val lastModified: Long,
        val size: Long,
        val isDirectory: Boolean,
    )

    companion object {
        private const val AUTHORITY = "com.android.externalstorage.documents"
        private const val PRIMARY = "primary"

        private val COLUMNS = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
        )

        private val MANAGED_RELATIVE = Regex(
            "^/storage/(emulated/0|[A-Za-z0-9][A-Za-z0-9-]*)/(Android/(?:data|obb)/.+)$",
        )

        /** true si el sistema soporta el parámetro manage (Android 11+). */
        fun supported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

        /** true si la app tiene «Acceso a todos los archivos». */
        fun hasAllFilesAccess(): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

        /**
         * Convierte una ruta absoluta de Android/data|obb en (volumeId, ruta relativa).
         * Devuelve null si la ruta no es gestionable por este mecanismo.
         */
        fun parseManagedPath(absPath: String): Pair<String, String>? {
            val clean = absPath.trim().trimEnd('/')
            val m = MANAGED_RELATIVE.find(clean) ?: return null
            val volume = if (m.groupValues[1] == "emulated/0") PRIMARY else m.groupValues[1]
            return volume to m.groupValues[2]
        }

        /** true si la ruta se puede abrir con este mecanismo ahora mismo. */
        fun canManage(absPath: String): Boolean =
            supported() && hasAllFilesAccess() && parseManagedPath(absPath) != null
    }

    // ── Listado / existencia ────────────────────────────────────────────

    fun list(volumeId: String, relativePath: String): List<Entry>? {
        if (!supported()) {
            val dir = File(filePath(volumeId, relativePath))
            if (!dir.isDirectory) return null
            return dir.listFiles()?.map { f ->
                Entry(
                    name = f.name,
                    documentId = "$volumeId:$relativePath/${f.name}",
                    mimeType = if (f.isDirectory) DocumentsContract.Document.MIME_TYPE_DIR else "application/octet-stream",
                    lastModified = f.lastModified(),
                    size = f.length(),
                    isDirectory = f.isDirectory,
                )
            }
        }
        val candidates = childUris(volumeId, relativePath)
        for (uri in candidates) {
            try {
                val entries = context.contentResolver.query(uri, COLUMNS, null, null, null)?.use { cursor ->
                    if (cursor.count == 0) emptyList() else readCursor(cursor)
                }
                if (entries != null) return entries
            } catch (_: Exception) {
                // siguiente estrategia
            }
        }
        return null
    }

    fun isListable(volumeId: String, relativePath: String): Boolean {
        if (!supported()) return File(filePath(volumeId, relativePath)).isDirectory
        if (!isDirectory(volumeId, relativePath)) return false
        return childUris(volumeId, relativePath).any { uri ->
            try {
                context.contentResolver.query(uri, COLUMNS, null, null, null)?.use { true } ?: false
            } catch (_: Exception) {
                false
            }
        }
    }

    fun exists(volumeId: String, relativePath: String): Boolean {
        if (!supported()) return File(filePath(volumeId, relativePath)).exists()
        return try {
            context.contentResolver.query(
                documentUri(volumeId, relativePath),
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                null, null, null,
            )?.use { it.moveToFirst() } ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun isDirectory(volumeId: String, relativePath: String): Boolean {
        if (!supported()) return File(filePath(volumeId, relativePath)).isDirectory
        return try {
            context.contentResolver.query(
                documentUri(volumeId, relativePath),
                arrayOf(DocumentsContract.Document.COLUMN_MIME_TYPE),
                null, null, null,
            )?.use { c -> c.moveToFirst() && c.getString(0) == DocumentsContract.Document.MIME_TYPE_DIR }
                ?: false
        } catch (_: Exception) {
            false
        }
    }

    // ── Lectura / escritura ─────────────────────────────────────────────

    fun openInput(volumeId: String, relativePath: String): InputStream? {
        if (!supported()) {
            val f = File(filePath(volumeId, relativePath))
            return if (f.isFile && f.canRead()) f.inputStream() else null
        }
        return try {
            context.contentResolver.openInputStream(documentUri(volumeId, relativePath))
        } catch (_: Exception) {
            null
        }
    }

    /** Escritura truncando; crea el fichero (y sus carpetas) si no existe. */
    fun openOutput(volumeId: String, relativePath: String): OutputStream? {
        if (!supported()) {
            val f = File(filePath(volumeId, relativePath))
            f.parentFile?.mkdirs()
            return try {
                f.outputStream()
            } catch (_: Exception) {
                null
            }
        }
        try {
            val stream = context.contentResolver.openOutputStream(
                documentUri(volumeId, relativePath), "wt",
            )
            if (stream != null) return stream
        } catch (_: Exception) {
            // no existía: se crea abajo
        }
        return try {
            val name = relativePath.substringAfterLast('/')
            val parent = relativePath.substringBeforeLast('/', "")
            if (name.isEmpty()) return null
            val parentUri = documentUri(volumeId, parent)
            val mime = mimeFor(name)
            val created = DocumentsContract.createDocument(
                context.contentResolver, parentUri, mime, name,
            ) ?: return null
            context.contentResolver.openOutputStream(created, "wt")
        } catch (_: Exception) {
            null
        }
    }

    /** Crea la jerarquía de directorios que falte. */
    fun makeDirectories(volumeId: String, relativePath: String): Boolean {
        if (relativePath.isBlank()) return true
        if (!supported()) return File(filePath(volumeId, relativePath)).mkdirs() ||
            File(filePath(volumeId, relativePath)).isDirectory
        var current = ""
        for (segment in relativePath.split('/').filter { it.isNotEmpty() }) {
            val parent = current
            current = if (current.isEmpty()) segment else "$current/$segment"
            if (exists(volumeId, current)) continue
            try {
                val parentUri = documentUri(volumeId, parent)
                val created = DocumentsContract.createDocument(
                    context.contentResolver, parentUri,
                    DocumentsContract.Document.MIME_TYPE_DIR, segment,
                )
                if (created == null) return false
            } catch (_: Exception) {
                return false
            }
        }
        return true
    }

    fun delete(volumeId: String, relativePath: String): Boolean {
        if (!supported()) return File(filePath(volumeId, relativePath)).delete()
        return try {
            DocumentsContract.deleteDocument(context.contentResolver, documentUri(volumeId, relativePath))
        } catch (_: Exception) {
            false
        }
    }

    // ── URIs ────────────────────────────────────────────────────────────

    private fun documentUri(volumeId: String, relativePath: String): Uri {
        val docId = "$volumeId:${relativePath.trim('/')}"
        val base = DocumentsContract.buildDocumentUri(AUTHORITY, docId)
        return applyManaged(base, relativePath)
    }

    /** Varias formas de construir la URI de hijos; el proveedor acepta alguna. */
    private fun childUris(volumeId: String, relativePath: String): List<Uri> {
        val rel = relativePath.trim('/')
        val docId = "$volumeId:$rel"
        val treeUri = Uri.Builder()
            .scheme("content")
            .authority(AUTHORITY)
            .appendPath("tree")
            .appendPath(docId)
            .build()
        return listOf(
            applyManaged(DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId), rel),
            applyManaged(DocumentsContract.buildChildDocumentsUri(AUTHORITY, docId), rel),
        )
    }

    private fun applyManaged(uri: Uri, relativePath: String): Uri {
        if (!supported()) return uri
        val rel = relativePath.trim('/')
        val needsManaged = rel == "Android" || rel.startsWith("Android/data") || rel.startsWith("Android/obb")
        return if (needsManaged) {
            uri.buildUpon().appendQueryParameter("manage", "true").build()
        } else {
            uri
        }
    }

    private fun filePath(volumeId: String, relativePath: String): String {
        val root = if (volumeId == PRIMARY) {
            Environment.getExternalStorageDirectory().absolutePath
        } else {
            "/storage/$volumeId"
        }
        return "$root/${relativePath.trim('/')}"
    }

    private fun readCursor(cursor: Cursor): List<Entry> {
        val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
        val modifiedIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
        val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
        val out = mutableListOf<Entry>()
        while (cursor.moveToNext()) {
            val mime = cursor.getString(mimeIdx) ?: ""
            out.add(
                Entry(
                    name = cursor.getString(nameIdx) ?: "",
                    documentId = cursor.getString(idIdx) ?: "",
                    mimeType = mime,
                    lastModified = cursor.getLong(modifiedIdx),
                    size = cursor.getLong(sizeIdx),
                    isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR,
                ),
            )
        }
        return out
    }

    private fun mimeFor(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
        "sav", "srm", "state", "ss", "sram", "gci", "mcr", "psu", "zip", "bin", "cue", "iso" -> "application/octet-stream"
        else -> "application/octet-stream"
    }
}
