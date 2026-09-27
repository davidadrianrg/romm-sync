package es.davidrg.rommsync.desktop

import es.davidrg.rommsync.core.download.PathMapper
import es.davidrg.rommsync.core.remote.RomMApiService
import es.davidrg.rommsync.core.remote.dto.RomDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Motor de descargas de la app desktop. Replica la lógica del DownloadWorker
 * de Android: fichero único → disco directo; multi-fichero (RomM zipea al
 * vuelo con mod_zip, sin Content-Length) → extracción en streaming.
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

        val response = api.downloadRom(rom.id, fileName)
        if (!response.isSuccessful) {
            return@withContext "Error HTTP ${response.code()}"
        }
        val body = response.body() ?: return@withContext "Respuesta vacía"

        // ¿Zip en streaming? (multi-fichero: sin Content-Length)
        val isZipStream = body.contentLength() == -1L
        if (isZipStream) {
            val created = extractZipStream(body, targetDir)
            // Progreso aproximado del zip: bytes leídos vía fuente espejo no
            // disponible sin envolver el stream; el total es desconocido, así
            // que se muestra indeterminado en la cola.
            library?.upsertRom(
                DesktopLibrary.RomEntry(
                    romId = rom.id,
                    name = rom.name,
                    fileName = fileName,
                    platformSlug = rom.platformSlug ?: "unknown",
                    localPath = targetDir.absolutePath,
                ),
            )
            return@withContext "Descargado ${rom.name} (${created.size} ficheros extraídos)"
        }

        // Fichero único
        val target = File(targetDir, fileName)
        val total = body.contentLength()
        val counting = CountingInputStream(body.byteStream()) { read, speed ->
            onProgress(Progress(read, total, speed))
        }
        FileOutputStream(target).use { out -> counting.copyTo(out) }
        library?.upsertRom(
            DesktopLibrary.RomEntry(
                romId = rom.id,
                name = rom.name,
                fileName = fileName,
                platformSlug = rom.platformSlug ?: "unknown",
                localPath = target.absolutePath,
            ),
        )
        return@withContext "Descargado ${rom.name} → ${target.absolutePath}"
    }

    private fun extractZipStream(body: okhttp3.ResponseBody, targetDir: File): List<File> {
        val createdFiles = mutableListOf<File>()
        try {
            ZipInputStream(body.byteStream()).use { zipIn ->
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
    private val onReport: (bytesRead: Long, speedBps: Long) -> Unit,
) : java.io.FilterInputStream(delegate) {

    private var count = 0L
    private var emaBps = 0.0
    private var lastReport = 0L
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
