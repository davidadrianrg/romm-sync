package com.davidadrianrg.atril.data.sync

import android.content.Context
import com.davidadrianrg.atril.core.i18n.tr
import com.davidadrianrg.atril.core.remote.NetworkModule
import com.davidadrianrg.atril.core.sync.platform.SaveHandlerRegistry
import com.davidadrianrg.atril.data.local.RomSyncDatabase
import com.davidadrianrg.atril.data.local.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Descarga manual de un save concreto desde el servidor al dispositivo.
 *
 * Se usa desde el detalle del juego para saves que la negociación automática
 * no puede bajar (p. ej. subidas antiguas sin slot, que RomM trata como
 * archivo) o para restaurar una versión concreta.
 */
class ManualSaveDownloader(private val context: Context) {

    /** [destDir]: «otra carpeta»; null = la ruta de sync configurada. */
    suspend fun download(romId: Int, saveId: Int, fileName: String, destDir: String? = null): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val settings = SettingsDataStore(context)
                val database = RomSyncDatabase.getDatabase(context)

                val rom = database.romDao().getDownloadedRom(romId)
                    ?: error(tr("saves.server.download_not_downloaded"))
                val platform = database.platformDao().getAllPlatformsBlocking()
                    .firstOrNull { it.slug == rom.platformSlug }

                val serverUrl = settings.getServerUrlBlocking()
                val apiKey = settings.getApiKeyBlocking()
                require(serverUrl.isNotBlank() && apiKey.isNotBlank()) {
                    tr("common.server_not_configured")
                }

                val retroBase = settings.getRetroArchBasePathBlocking()
                val emulatorId = platform?.emulatorId
                    ?: SaveHandlerRegistry.getDefaultEmulator(rom.platformSlug).id
                val syncBase = rom.savesPathOverride?.takeIf { it.isNotBlank() }
                    ?: platform?.savesPathOverride?.takeIf { it.isNotBlank() }
                    ?: AndroidSavePathResolver.resolveDefault(
                        emulatorId = emulatorId,
                        platformSlug = rom.platformSlug,
                        retroArchBase = retroBase,
                        context = context,
                    )

                val rawBase = com.davidadrianrg.atril.core.sync.SaveDownloadDestination.resolve(destDir, syncBase)
                val api = NetworkModule.createApiService(serverUrl, apiKey)
                val body = api.downloadSave(saveId, settings.getSyncDeviceIdStringBlocking())
                val temp = File(context.cacheDir, "manual_save_${romId}_$saveId")
                try {
                    body.byteStream().use { input ->
                        temp.outputStream().use { output -> input.copyTo(output) }
                    }

                    val staged = SavePathAccess.stage(
                        rawBase,
                        File(context.cacheDir, "save_stage_manual"),
                        context,
                    )
                    try {
                        val handler = SaveHandlerRegistry.getHandler(
                            rom.platformSlug,
                            platform?.emulatorId,
                        )
                        val ok = handler.extractDownload(
                            tempFile = temp,
                            romFileName = rom.fileName,
                            platformSlug = rom.platformSlug,
                            savesBasePath = staged.dir.path,
                            targetFileName = com.davidadrianrg.atril.core.sync.SaveNames
                                .stripVersionTag(fileName),
                        )
                        if (!ok) error(tr("saves.server.download_error", "extract"))
                        SavePathAccess.commit(staged, context)
                        Unit
                    } finally {
                        SavePathAccess.cleanup(staged, context)
                    }
                } finally {
                    temp.delete()
                }
            }
        }
}
