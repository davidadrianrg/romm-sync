package com.davidadrianrg.atril.core.upload

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile

/**
 * Origen de bytes para una subida. Permite subir desde un fichero o desde un
 * SAF URI sin copiar el juego completo a almacenamiento temporal.
 */
interface UploadSource : Closeable {

    val name: String
    val size: Long

    /** Lee exactamente [length] bytes en [offset]. */
    fun read(offset: Long, length: Int): ByteArray

    override fun close() = Unit
}

/** [UploadSource] sobre un fichero local (desktop, tests, ficheros propios). */
class FileUploadSource(private val file: File) : UploadSource {

    override val name: String = file.name

    override val size: Long = file.length()

    override fun read(offset: Long, length: Int): ByteArray {
        val buffer = ByteArray(length)
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(offset)
            raf.readFully(buffer)
        }
        return buffer
    }
}
