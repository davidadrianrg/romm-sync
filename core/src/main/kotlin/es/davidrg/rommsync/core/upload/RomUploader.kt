package es.davidrg.rommsync.core.upload

import es.davidrg.rommsync.core.remote.RomMApiService
import es.davidrg.rommsync.core.remote.dto.ScanRequest
import es.davidrg.rommsync.core.remote.dto.UploadStartRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.RandomAccessFile

/**
 * Subida chunked de ROMs a RomM (mismo protocolo que la web):
 * `POST /api/roms/upload/start` → N × `PUT .../{upload_id}` → `POST .../complete`.
 *
 * El tamaño de chunk lo deriva el servidor de `total_size`/`total_chunks`
 * (ceil), así que se calcula aquí igual y cada chunk va exacto salvo el último.
 */
class RomUploader(private val api: RomMApiService) {

    suspend fun upload(
        file: File,
        platformId: Int,
        onProgress: (Int) -> Unit = {},
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val size = file.length()
            val targetChunk = 8L * 1024 * 1024
            val totalChunks = if (size == 0L) 0 else ((size + targetChunk - 1) / targetChunk).toInt()
            val chunkSize = if (totalChunks == 0) 0L else (size + totalChunks - 1) / totalChunks
            val mediaType = "application/octet-stream".toMediaType()

            val started = api.startRomUpload(
                platformId = platformId.toString(),
                filename = file.name,
                totalSize = size,
                totalChunks = totalChunks,
                target = UploadStartRequest(filename = file.name, overwrite = false),
            )
            val uploadId = started.uploadId

            if (totalChunks > 0) {
                RandomAccessFile(file, "r").use { raf ->
                    var sent = 0L
                    for (index in 0 until totalChunks) {
                        val offset = chunkSize * index
                        val length = minOf(chunkSize, size - offset).toInt()
                        val buffer = ByteArray(length)
                        raf.seek(offset)
                        raf.readFully(buffer)
                        api.uploadRomChunk(uploadId, index, buffer.toRequestBody(mediaType))
                        sent += length
                        onProgress(((sent * 100) / size).toInt())
                    }
                }
            }

            api.completeRomUpload(uploadId)
            onProgress(100)
        }
    }

    /**
     * Lanza un escaneo rápido de la plataforma tras subir (la web de RomM hace
     * lo mismo por socket). Best-effort: si el server lo rechaza, el juego
     * aparecerá en el siguiente escaneo.
     */
    suspend fun triggerQuickScan(platformId: Int): Boolean = runCatching {
        api.startScan(ScanRequest(type = "quick", platforms = listOf(platformId)))
        true
    }.getOrDefault(false)
}
