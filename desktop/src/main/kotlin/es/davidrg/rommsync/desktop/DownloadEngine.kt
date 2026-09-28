package es.davidrg.rommsync.desktop

import es.davidrg.rommsync.core.download.PathMapper
import es.davidrg.rommsync.core.remote.RomMApiService
import es.davidrg.rommsync.core.remote.dto.RomDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import kotlin.coroutines.cancellation.CancellationException

/**
 * Motor de descargas de la app desktop. Replica la lógica del DownloadWorker
 * de Android: fichero único → disco directo con reanudación vía Range;
 * multi-fichero (RomM zipea al vuelo con mod_zip, sin Content-Length) →
 * extracción en streaming con contador de bytes.
 *
 * Cancelación: los streams comprueban el Job de la corrutina llamante en cada
 * reporte de progreso; al cancelar se lanza CancellationException, se cierra
 * la conexión y el fichero parcial queda en disco para reanudar luego.
 */
class DownloadEngine(
    private val api: RomMApiService,
    private val romsRoot: File,
    private val cacheDir: File,
    private val library: DesktopLibrary? = null,
) {

    /** Progreso notificado durante la descarga. total=-1 → indeterminado (zip). */
    data class Progress(val bytesRead: Long, val total: Long, val speedBps: Long)

    suspend fun download(
        rom: RomDto,
        onProgress: (Progress) -> Unit = {},
    ): String = withContext(Dispatchers.IO) {
        val fileName = rom.fileName
        // Carpeta por plataforma (override del usuario) o slug por defecto
        val folderOverride = library?.platform(rom.platformSlug ?: "")?.romsFolderOverride
        val targetDir = if (!folderOverride.isNullOrBlank()) {
            File(romsRoot, folderOverride).also { it.mkdirs() }
        } else {
            PathMapper.getPlatformDir(romsRoot.absolutePath, rom.platformSlug ?: "unknown").also { it.mkdirs() }
        }

        // Fichero objetivo y parcial previo (para reanudar) se calculan ANTES
        // de la petición: el Range depende de lo que ya hay en disco.
        val target = File(targetDir, fileName)
        val existing = if (target.isFile) target.length() else 0L
        val range = if (existing > 0L) "bytes=$existing-" else null

        val response = api.downloadRom(rom.id, fileName, range)
        if (!response.isSuccessful) {
            when (response.code()) {
                416 -> {
                    // Range insatisfiable: el fichero ya está completo en disco.
                    registerInLibrary(rom, target)
                    return@withContext "Descargado ${rom.name} (ya completo)"
                }
                else -> {
                    response.body()?.close()
                    response.raw().close()
                    return@withContext "Error HTTP ${response.code()}"
                }
            }
        }
        val body = response.body() ?: return@withContext "Respuesta vacía"

        val job = coroutineContext[Job]

        // ¿Zip en streaming? (multi-fichero: sin Content-Length)
        val isZipStream = body.contentLength() == -1L
        if (isZipStream) {
            val created = extractZipStream(body, targetDir, job, onProgress)
            registerInLibrary(rom, targetDir)
            return@withContext "Descargado ${rom.name} (${created.size} ficheros extraídos)"
        }

        // Fichero único — con reanudación: si hay un parcial de una descarga
        // previa (fallida o cancelada) se pide Range y se concatena (206);
        // si el servidor ignora el Range (200) se empieza de cero.
        val resumed = existing > 0L && response.code() == 206
        if (existing > 0L && !resumed && target.isFile) {
            target.delete() // 200 ignorando Range: descartar el parcial
        }
        // En 206 Content-Length es lo que QUEDA, no el total.
        val total = if (resumed) existing + body.contentLength() else body.contentLength()
        val baseBytes = if (resumed) existing else 0L
        val counting = CountingInputStream(body.byteStream(), baseBytes) { read, speed ->
            if (job?.isActive == false) throw CancellationException("Descarga cancelada")
            onProgress(Progress(read, total, speed))
        }
        FileOutputStream(target, resumed).use { out -> counting.copyTo(out) }
        registerInLibrary(rom, target)
        return@withContext "Descargado ${rom.name} → ${target.absolutePath}"
    }

    private fun registerInLibrary(rom: RomDto, localPath: File) {
        library?.upsertRom(
            DesktopLibrary.RomEntry(
                romId = rom.id,
                name = rom.name,
                fileName = rom.fileName,
                platformSlug = rom.platformSlug ?: "unknown",
                localPath = localPath.absolutePath,
            ),
        )
    }

    private fun extractZipStream(
        body: okhttp3.ResponseBody,
        targetDir: File,
        job: Job?,
        onProgress: (Progress) -> Unit,
    ): List<File> {
        val createdFiles = mutableListOf<File>()
        val counting = CountingInputStream(body.byteStream(), 0L) { read, speed ->
            if (job?.isActive == false) throw CancellationException("Descarga cancelada")
            onProgress(Progress(read, -1L, speed))
        }
        try {
            ZipInputStream(counting).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    val outFile = File(targetDir, entry.name)
                    if (!outFile.canonicalPath.startsWith(targetDir.canonicalPath)) {
                        zipIn.closeEntry(); entry = zipIn.nextEntry; continue
                    }
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { out -> zipIn.copyTo(out) }
                        createdFiles.add(outFile)
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
        } catch (e: Exception) {
            createdFiles.forEach { if (it.exists()) it.delete() }
            throw e
        }
        return createdFiles
    }
}

/**
 * InputStream que cuenta bytes leídos y notifica progreso con velocidad EMA
 * (misma suavización que el DownloadWorker de Android para que el número no
 * oscile con las ráfagas de TCP/writeback de disco).
 */
private class CountingInputStream(
    private val delegate: java.io.InputStream,
    /** Bytes ya en disco (reanudación): el contador arranca desde aquí. */
    private val baseBytes: Long,
    private val onReport: (bytesRead: Long, speed: Long) -> Unit,
) : java.io.FilterInputStream(delegate) {

    private var count = baseBytes
    private var emaBps = 0.0
    private var lastReport = baseBytes
    private var lastReportAt = System.currentTimeMillis()

    override fun read(): Int {
        val b = delegate.read()
        if (b >= 0) tally(1)
        return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        val n = delegate.read(b, off, len)
        if (n > 0) tally(n.toLong())
        return n
    }

    private fun tally(n: Long) {
        count += n
        val now = System.currentTimeMillis()
        val elapsed = now - lastReportAt
        if (elapsed >= 500) {
            val windowBps = (count - lastReport) * 1000.0 / elapsed
            // EMA con alfa de la ventana: ~2s de memoria
            emaBps = if (emaBps == 0.0) windowBps else emaBps * 0.75 + windowBps * 0.25
            lastReport = count
            lastReportAt = now
            onReport(count, emaBps.toLong())
        }
    }
}
