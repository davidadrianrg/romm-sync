package es.davidrg.rommsync.core.upload

import es.davidrg.rommsync.core.i18n.tr
import es.davidrg.rommsync.core.remote.RomMApiService
import es.davidrg.rommsync.core.remote.dto.ScanRequest
import es.davidrg.rommsync.core.remote.dto.UploadStartRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.File
import java.io.IOException
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

            completeResilient(uploadId, platformId, file.name)
            onProgress(100)
        }
    }

    /**
     * `POST .../complete` ensambla los chunks en el servidor; con ficheros
     * grandes eso puede pasar del timeout de ~100 s de Cloudflare y el cliente
     * recibe un 524 aunque RomM termine el trabajo igualmente. En vez de dar la
     * subida por fallida: se lanza un escaneo y se comprueba por API si el ROM
     * aparece, reintentando durante unos minutos.
     */
    private suspend fun completeResilient(uploadId: String, platformId: Int, fileName: String) {
        var lastError: Exception? = null
        try {
            api.completeRomUpload(uploadId)
            return
        } catch (e: HttpException) {
            lastError = e
            val transient = e.code() == 404 || e.code() == 408 || e.code() == 409 ||
                e.code() == 500 || e.code() == 502 || e.code() == 503 || e.code() == 504 ||
                e.code() in 520..527
            if (!transient) throw e
        } catch (e: IOException) {
            lastError = e
        }

        var found = false
        for (attempt in 0 until VERIFY_ATTEMPTS) {
            // Re-escanea cada ~30 s por si el primer escaneo llegó antes del commit.
            if (attempt % 3 == 0) triggerQuickScan(platformId)
            delay(VERIFY_DELAY_MS)
            if (romExists(platformId, fileName)) {
                found = true
                break
            }
        }
        if (!found) {
            throw IllegalStateException(tr("library.upload.timeout"), lastError)
        }
    }

    /** ¿Ya está registrada en el servidor la ROM con ese nombre de fichero? */
    private suspend fun romExists(platformId: Int, fileName: String): Boolean = runCatching {
        val baseName = fileName.substringBeforeLast('.')
        val response = api.getRoms(
            mapOf(
                "platform_ids" to platformId.toString(),
                "search_term" to baseName,
                "limit" to "50",
            ),
        )
        response.items.any { rom ->
            rom.fileName.equals(fileName, ignoreCase = true) ||
                rom.files.any { it.filename.equals(fileName, ignoreCase = true) }
        }
    }.getOrDefault(false)

    /**
     * Lanza un escaneo rápido de la plataforma tras subir (la web de RomM hace
     * lo mismo por socket). Best-effort: si el server lo rechaza, el juego
     * aparecerá en el siguiente escaneo.
     */
    suspend fun triggerQuickScan(platformId: Int): Boolean = runCatching {
        api.startScan(ScanRequest(type = "quick", platforms = listOf(platformId)))
        true
    }.getOrDefault(false)

    private companion object {
        /** 30 × 10 s = 5 min de margen para que el servidor termine de ensamblar. */
        const val VERIFY_ATTEMPTS = 30
        const val VERIFY_DELAY_MS = 10_000L
    }
}
