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

    suspend fun download(rom: RomDto): String = withContext(Dispatchers.IO) {
        val fileName = rom.fileName
        val targetDir = PathMapper.getPlatformDir(romsRoot.absolutePath, rom.platformSlug ?: "unknown")
        targetDir.mkdirs()

        val response = api.downloadRom(rom.id, fileName)
        if (!response.isSuccessful) {
            return@withContext "Error HTTP ${response.code()}"
        }
        val body = response.body() ?: return@withContext "Respuesta vacía"

        // ¿Zip en streaming? (multi-fichero: sin Content-Length)
        val isZipStream = body.contentLength() == -1L
        if (isZipStream) {
            val created = extractZipStream(body, targetDir)
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
        FileOutputStream(target).use { out -> body.byteStream().copyTo(out) }
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
