package com.davidadrianrg.atril.data.metadata

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.davidadrianrg.atril.data.local.RomSyncDatabase
import com.davidadrianrg.atril.data.local.SettingsDataStore
import com.davidadrianrg.atril.core.i18n.tr
import com.davidadrianrg.atril.core.media.GameMediaDownloader
import com.davidadrianrg.atril.core.media.GameMediaPaths
import com.davidadrianrg.atril.core.media.GameMediaPlanner
import com.davidadrianrg.atril.core.media.MediaKind
import com.davidadrianrg.atril.core.remote.NetworkModule
import com.davidadrianrg.atril.domain.model.Rom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File

/**
 * Worker que descarga metadatos (covers, screenshots, videos, manuals) de RomM
 * y los escribe en la estructura de carpetas de ES-DE, además de actualizar
 * el gamelist.xml por plataforma.
 *
 * Estructura destino:
 * ```
 * <mediaRoot>/<slug>/{covers,screenshots,videos,manuals}/<name>.<ext>
 * (mediaRoot = carpeta de media genérica; por defecto <roms_root>/downloaded_media)
 * <esde_data_dir>/gamelists/<slug>/gamelist.xml
 * ```
 */
class MetadataExportWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val platformId = inputData.getInt(KEY_PLATFORM_ID, -1)
        val platformSlug = inputData.getString(KEY_PLATFORM_SLUG)
            ?: return@withContext Result.failure()

        if (platformId < 0) return@withContext Result.failure()

        val dataStore = SettingsDataStore(applicationContext)
        val serverUrl = dataStore.getServerUrlBlocking()
        val apiKey = dataStore.getApiKeyBlocking()
        val romsRootPath = dataStore.getRomsRootPathBlocking()
        val esdeDataDir = dataStore.getEsdeDataDirBlocking()
        val mediaRoot = GameMediaPaths.resolveRoot(dataStore.getMediaDownloadPathBlocking(), romsRootPath)

        if (apiKey.isEmpty() || serverUrl.isEmpty()) {
            return@withContext Result.failure()
        }

        // Flags: qué tipos de media descargar
        val exportCovers = inputData.getBoolean(KEY_COVERS, true)
        val exportScreenshots = inputData.getBoolean(KEY_SCREENSHOTS, true)
        val exportVideos = inputData.getBoolean(KEY_VIDEOS, true)
        val exportManuals = inputData.getBoolean(KEY_MANUALS, true)
        val exportGamelist = inputData.getBoolean(KEY_GAMELIST, true)

        createNotificationChannel()
        try {
            setForeground(createForegroundInfo(platformSlug, 0, 0))
        } catch (e: Exception) {
            Log.w(TAG, "Could not start foreground", e)
        }

        val api = NetworkModule.createApiService(serverUrl, apiKey)
        val mediaDownloader = GameMediaDownloader(OkHttpClient.Builder().build(), apiKey, serverUrl) { Log.w(TAG, it) }
        val db = RomSyncDatabase.getDatabase(applicationContext)

        // Paginación: obtener todos los ROMs de la plataforma
        val allRomEntries = mutableListOf<GamelistWriter.GameEntry>()
        var offset = 0
        val limit = 100
        var hasMore = true
        var mediaDownloaded = 0
        var mediaSkipped = 0

        while (hasMore) {
            val params = buildMap {
                put("platform_ids", platformId.toString())
                put("limit", limit.toString())
                put("offset", offset.toString())
                put("order_by", "name")
                put("order_dir", "asc")
            }

            val response = try {
                api.getRoms(params)
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching ROMs at offset $offset", e)
                return@withContext Result.retry()
            }

            if (response.items.isEmpty()) break

            for (romDto in response.items) {
                val rom = mapRomDtoToDomain(romDto, serverUrl)
                val mediaBaseName = (rom.fileNameNoExt ?: rom.fileName.substringBeforeLast("."))
                    .replace(Regex("[^a-zA-Z0-9._ -]"), "")
                    .trim()

                // Media genérica: mismas carpetas y descargador que la descarga de juegos
                // (sin duplicar ficheros). Los tipos desmarcados se omiten.
                val wanted = buildSet {
                    if (exportCovers) add(MediaKind.COVERS)
                    if (exportScreenshots) add(MediaKind.SCREENSHOTS)
                    if (exportVideos) add(MediaKind.VIDEOS)
                    if (exportManuals) add(MediaKind.MANUALS)
                }
                val report = mediaDownloader.run(romDto, mediaRoot, kinds = wanted)
                mediaDownloaded += report.downloaded
                mediaSkipped += report.failed.size
                val mediaFiles = GameMediaPlanner.plan(romDto, serverUrl, mediaRoot)
                    .filter { it.kind in wanted && it.target.exists() }
                    .groupBy { it.kind.dir }
                    .mapValues { (_, v) -> esdePath(v.first().target, romsRootPath) }

                // Crear entry para gamelist
                allRomEntries.add(
                    GamelistWriter.romToEntry(rom, romsRootPath, platformSlug, mediaBaseName, mediaFiles)
                )

                // Actualizar notificación
                val progress = ((offset + allRomEntries.size) * 100 /
                        (response.total.coerceAtLeast(1))).coerceIn(0, 100)
                try {
                    setForeground(createForegroundInfo(platformSlug, progress, mediaDownloaded))
                } catch (_: Exception) {}
            }

            offset += limit
            hasMore = offset < response.total
        }

        // Escribir/actualizar gamelist.xml
        if (exportGamelist && allRomEntries.isNotEmpty()) {
            val gamelistDir = File("$esdeDataDir/gamelists/$platformSlug")
            gamelistDir.mkdirs()
            val gamelistFile = File(gamelistDir, "gamelist.xml")

            val existing = GamelistWriter.readGamelist(gamelistFile)
            val xml = GamelistWriter.mergeAndSerialize(existing, allRomEntries)
            gamelistFile.writeText(xml, Charsets.UTF_8)
            Log.i(TAG, "Gamelist written: ${gamelistFile.absolutePath} (${allRomEntries.size} entries)")
        }

        Result.success(workDataOf(
            KEY_MEDIA_DOWNLOADED to mediaDownloaded,
            KEY_MEDIA_SKIPPED to mediaSkipped,
            KEY_ENTRIES to allRomEntries.size,
        ))
    }

    /** Ruta para el gamelist: relativa a %ROMPATH% (./…) si la media está bajo la raíz de ROMs. */
    private fun esdePath(file: File, romsRoot: String): String {
        val rel = runCatching { file.relativeTo(File(romsRoot)).invariantSeparatorsPath }.getOrNull()
        return if (rel != null && !rel.startsWith("..")) "./$rel" else file.absolutePath
    }

    /**
     * Mapea RomDto a Rom de dominio inline (sin acceder al Repository
     * que requiere coroutine context distinto).
     */
    private fun mapRomDtoToDomain(
        dto: com.davidadrianrg.atril.core.remote.dto.RomDto,
        serverUrl: String,
    ): Rom {
        val base = serverUrl.trimEnd('/') + "/"
        return Rom(
            id = dto.id,
            name = dto.name.ifBlank { dto.fileName },
            fileName = dto.fileName,
            fileSizeBytes = dto.fileSizeBytes,
            platformId = dto.platformId,
            platformSlug = dto.platformSlug ?: "",
            coverUrlSmall = dto.pathCoverSmall?.let { base + it.removePrefix("/") },
            coverUrlLarge = dto.pathCoverLarge?.let { base + it.removePrefix("/") },
            files = dto.files.map { com.davidadrianrg.atril.domain.model.RomFile(it.filename, it.size) },
            isMulti = dto.multi || dto.hasMultipleFiles,
            revision = dto.revision,
            regions = dto.regions,
            languages = dto.languages,
            genres = dto.genres,
            summary = dto.summary,
            fileNameNoTags = dto.fileNameNoTags,
            fileNameNoExt = dto.fileNameNoExt,
            fileExtension = dto.fileExtension,
            igdbId = dto.igdbId,
            screenshots = dto.mergedScreenshots.map { base + it.removePrefix("/") },
            videoPath = dto.pathVideo?.let { base + it.removePrefix("/") },
            manualPath = dto.pathManual?.let { base + it.removePrefix("/") },
            igdbMetadata = dto.igdbMetadata?.let { meta ->
                com.davidadrianrg.atril.domain.model.IgdbMetadata(
                    totalRating = meta.totalRating?.toDoubleOrNull(),
                    firstReleaseDate = meta.firstReleaseDate,
                    genres = meta.genres,
                    companies = meta.companies,
                    gameModes = meta.gameModes,
                    playerCount = meta.playerCount,
                )
            },
        )
    }

    private fun createForegroundInfo(slug: String, progress: Int, mediaCount: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(tr("android.metadata.notification_title", slug))
            .setContentText(tr("android.metadata.notification_text", mediaCount))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, progress, progress == 0)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                tr("android.metadata.channel_name"),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = tr("android.metadata.channel_description") }
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val TAG = "MetadataExportWorker"
        const val WORK_NAME_PREFIX = "metadata_export_"

        const val KEY_PLATFORM_ID = "platform_id"
        const val KEY_PLATFORM_SLUG = "platform_slug"
        const val KEY_COVERS = "export_covers"
        const val KEY_SCREENSHOTS = "export_screenshots"
        const val KEY_VIDEOS = "export_videos"
        const val KEY_MANUALS = "export_manuals"
        const val KEY_GAMELIST = "export_gamelist"

        const val KEY_MEDIA_DOWNLOADED = "media_downloaded"
        const val KEY_MEDIA_SKIPPED = "media_skipped"
        const val KEY_ENTRIES = "entries_count"

        private const val CHANNEL_ID = "metadata_export"
        private const val NOTIFICATION_ID = 3001
    }
}
