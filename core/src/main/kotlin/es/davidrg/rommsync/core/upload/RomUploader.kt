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
 * Para juegos multi-fichero (carpeta): se sube el primer fichero como ROM nueva,
 * se espera al escaneo y el resto se sube con `rom_id` — RomM promociona la ROM
 * de un solo fichero a carpeta y la deja como ROM multi-fichero.
 *
 * El `complete` ensambla los chunks en el servidor; tras Cloudflare puede dar
 * 524 aunque RomM termine, así que se verifica por API antes de fallar.
 */
class RomUploader(private val api: RomMApiService) {

    /** Sube un fichero como ROM nueva. */
    suspend fun upload(
        file: File,
        platformId: Int,
        onProgress: (Int) -> Unit = {},
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val started = try {
                startUpload(file, platformId, UploadStartRequest(filename = file.name, overwrite = false))
            } catch (e: HttpException) {
                val detail = serverDetail(e)
                // La subida anterior (524 de Cloudflare) pudo completarse: el
                // fichero ya está en la carpeta de la plataforma y RomM rechaza
                // el duplicado con 400 "File ... already exists".
                if (e.code() == 400 && detail?.contains("already exists", ignoreCase = true) == true) {
                    onProgress(100)
                    if (awaitRomId(platformId, file.name) != null) return@runCatching
                    throw IllegalStateException(tr("library.upload.already_exists", file.name), e)
                }
                throw IllegalStateException("HTTP ${e.code()}: ${detail ?: e.message()}", e)
            }
            sendChunks(file, started.uploadId, onProgress)
            completeNewRom(started.uploadId, platformId, file.name)
            onProgress(100)
        }
    }

    /**
     * Sube un fichero DENTRO de una ROM existente. Si la ROM era de un solo
     * fichero, RomM la promociona a carpeta automáticamente.
     */
    suspend fun uploadIntoRom(
        file: File,
        romId: Int,
        platformId: Int,
        onProgress: (Int) -> Unit = {},
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val started = startUpload(
                file,
                platformId,
                UploadStartRequest(romId = romId, folder = "", filename = file.name, overwrite = true),
            )
            sendChunks(file, started.uploadId, onProgress)
            completeIntoRom(started.uploadId, romId, file.name)
            onProgress(100)
        }
    }

    private suspend fun startUpload(
        file: File,
        platformId: Int,
        target: UploadStartRequest,
    ) = api.startRomUpload(
        platformId = platformId.toString(),
        filename = file.name,
        totalSize = file.length(),
        totalChunks = totalChunks(file.length()),
        target = target,
    )

    private suspend fun sendChunks(file: File, uploadId: String, onProgress: (Int) -> Unit) {
        val size = file.length()
        if (size == 0L) return
        val chunks = totalChunks(size)
        val chunkSize = chunkSize(size, chunks)
        val mediaType = "application/octet-stream".toMediaType()
        RandomAccessFile(file, "r").use { raf ->
            var sent = 0L
            for (index in 0 until chunks) {
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

    private suspend fun completeNewRom(uploadId: String, platformId: Int, fileName: String) {
        val err = tryComplete(uploadId) ?: return
        if (awaitRomId(platformId, fileName) == null) {
            throw IllegalStateException(tr("library.upload.timeout"), err)
        }
    }

    private suspend fun completeIntoRom(uploadId: String, romId: Int, fileName: String) {
        val err = tryComplete(uploadId) ?: return
        repeat(VERIFY_ATTEMPTS) {
            delay(VERIFY_DELAY_MS)
            if (romHasFile(romId, fileName)) return
        }
        throw IllegalStateException(tr("library.upload.timeout"), err)
    }

    /**
     * Intenta el complete. Devuelve la excepción si el resultado es incierto
     * (5xx/52x/408/409/404 o timeout de socket) o null si RomM confirmó.
     */
    private suspend fun tryComplete(uploadId: String): Exception? = try {
        api.completeRomUpload(uploadId)
        null
    } catch (e: HttpException) {
        val transient = e.code() == 404 || e.code() == 408 || e.code() == 409 ||
            e.code() == 500 || e.code() == 502 || e.code() == 503 || e.code() == 504 ||
            e.code() in 520..527
        if (transient) e
        else throw IllegalStateException("HTTP ${e.code()}: ${serverDetail(e) ?: e.message()}", e)
    } catch (e: IOException) {
        e
    }

    /** Busca una ROM por nombre de fichero (fs_name o dentro de files). */
    suspend fun findRomId(platformId: Int, fileName: String): Int? = runCatching {
        val baseName = fileName.substringBeforeLast('.')
        api.getRoms(
            mapOf(
                "platform_ids" to platformId.toString(),
                "search_term" to baseName,
                "limit" to "50",
            ),
        ).items.firstOrNull { rom ->
            rom.fileName.equals(fileName, ignoreCase = true) ||
                rom.files.any { it.filename.equals(fileName, ignoreCase = true) }
        }?.id
    }.getOrNull()

    /** Escanea y espera a que la ROM aparezca; devuelve su id. */
    suspend fun awaitRomId(platformId: Int, fileName: String): Int? {
        for (attempt in 0 until VERIFY_ATTEMPTS) {
            // Re-escanea cada ~30 s por si el primer escaneo llegó antes del commit.
            if (attempt % 3 == 0) triggerQuickScan(platformId)
            delay(VERIFY_DELAY_MS)
            findRomId(platformId, fileName)?.let { return it }
        }
        return null
    }

    private suspend fun romHasFile(romId: Int, fileName: String): Boolean = runCatching {
        api.getRom(romId).files.any { it.filename.equals(fileName, ignoreCase = true) }
    }.getOrDefault(false)

    /**
     * Lanza un escaneo rápido de la plataforma tras subir (la web de RomM hace
     * lo mismo por socket). Best-effort.
     */
    suspend fun triggerQuickScan(platformId: Int): Boolean = runCatching {
        api.startScan(ScanRequest(type = "quick", platforms = listOf(platformId)))
        true
    }.getOrDefault(false)

    /** Extrae el `detail` JSON que devuelve RomM en los errores HTTP. */
    private fun serverDetail(e: HttpException): String? = runCatching {
        val body = e.response()?.errorBody()?.string()
        if (body.isNullOrBlank()) return@runCatching null
        Regex("\"detail\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
            .find(body)?.groupValues?.get(1)
            ?.replace("\\\"", "\"")
            ?.replace("\\n", " ")
    }.getOrNull()

    private fun totalChunks(size: Long): Int =
        if (size == 0L) 0 else ((size + CHUNK_TARGET - 1) / CHUNK_TARGET).toInt()

    private fun chunkSize(size: Long, chunks: Int): Long =
        if (chunks == 0) 0 else (size + chunks - 1) / chunks

    private companion object {
        /** 30 × 10 s = 5 min de margen para que el servidor termine de ensamblar. */
        const val VERIFY_ATTEMPTS = 30
        const val VERIFY_DELAY_MS = 10_000L
        const val CHUNK_TARGET = 8L * 1024 * 1024
    }
}
