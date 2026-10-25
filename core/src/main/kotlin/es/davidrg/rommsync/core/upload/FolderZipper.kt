package es.davidrg.rommsync.core.upload

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Empaqueta una carpeta en un .zip para subirla como UN juego multi-fichero.
 *
 * RomM no permite crear una ROM-carpeta nueva por API (los ficheros subidos
 * sueltos se escanean como ROMs independientes), pero sí ingiere un ZIP con
 * varios miembros como una ROM multi-fichero. Nivel de compresión 1: los ROMs
 * suelen venir ya comprimidos y así el zip es casi una copia rápida.
 */
object FolderZipper {

    fun zip(folder: File, destZip: File, onProgress: (Int) -> Unit = {}): Boolean {
        if (!folder.isDirectory) return false
        val files = folder.walkTopDown().filter { it.isFile }.toList()
        if (files.isEmpty()) return false

        return runCatching {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(destZip))).use { zos ->
                zos.setLevel(1)
                files.forEachIndexed { index, file ->
                    val entryName = file.relativeTo(folder).invariantSeparatorsPath
                    zos.putNextEntry(ZipEntry(entryName))
                    file.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                    onProgress(((index + 1) * 100) / files.size)
                }
            }
            true
        }.getOrDefault(false)
    }
}
