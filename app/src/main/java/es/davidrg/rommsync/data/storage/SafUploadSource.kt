package es.davidrg.rommsync.data.storage

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.documentfile.provider.DocumentFile
import es.davidrg.rommsync.core.upload.UploadSource
import java.io.EOFException
import java.io.FileInputStream
import java.io.IOException

/**
 * [UploadSource] sobre un documento SAF. Lee por offsets directamente del
 * fichero elegido: no se copia el juego completo al cache (los juegos grandes
 * agotaban el almacenamiento interno antes de empezar a subir).
 */
class SafUploadSource private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val input: FileInputStream,
    override val name: String,
    override val size: Long,
) : UploadSource {

    override fun read(offset: Long, length: Int): ByteArray = synchronized(this) {
        val channel = input.channel
        channel.position(offset)
        val buffer = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = channel.read(java.nio.ByteBuffer.wrap(buffer, read, length - read))
            if (n < 0) throw EOFException("SAF source ended at ${offset + read} of $size")
            read += n
        }
        buffer
    }

    override fun close() {
        runCatching { input.close() }
        runCatching { descriptor.close() }
    }

    companion object {
        fun from(context: Context, document: DocumentFile): SafUploadSource =
            from(context, document.uri, document.name ?: "rom.bin", document.length())

        fun from(context: Context, uri: Uri): SafUploadSource {
            val document = DocumentFile.fromSingleUri(context, uri)
            return from(
                context = context,
                uri = uri,
                name = document?.name ?: "rom.bin",
                size = document?.length() ?: -1L,
            )
        }

        fun from(context: Context, uri: Uri, name: String, size: Long): SafUploadSource {
            val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw IOException("Cannot open $uri")
            val actualSize = if (size > 0) size else descriptor.statSize
            if (actualSize <= 0) {
                descriptor.close()
                throw IOException("Unknown size for $uri")
            }
            return SafUploadSource(
                descriptor = descriptor,
                input = FileInputStream(descriptor.fileDescriptor),
                name = name,
                size = actualSize,
            )
        }
    }
}
