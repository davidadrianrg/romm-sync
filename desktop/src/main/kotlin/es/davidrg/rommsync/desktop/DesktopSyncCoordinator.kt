package es.davidrg.rommsync.desktop

import java.net.InetAddress
import es.davidrg.rommsync.core.i18n.tr
import es.davidrg.rommsync.core.remote.NetworkModule
import es.davidrg.rommsync.core.remote.RomMApiService
import es.davidrg.rommsync.core.remote.dto.ClientSaveState
import es.davidrg.rommsync.core.remote.dto.DeviceRegistrationRequest
import es.davidrg.rommsync.core.remote.dto.NegotiateRequest
import es.davidrg.rommsync.core.remote.dto.SessionCompleteRequest
import es.davidrg.rommsync.core.sync.ConflictPolicy
import es.davidrg.rommsync.core.sync.SaveBackupManager
import es.davidrg.rommsync.core.sync.platform.LocalSave
import es.davidrg.rommsync.core.sync.platform.SaveHandler
import es.davidrg.rommsync.core.sync.platform.SaveHandlerRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Orquesta el ciclo completo de sincronización de saves con el servidor RomM:
 * 1. Asegura que el dispositivo está registrado.
 * 2. Escanea saves locales.
 * 3. Negocia con el servidor (qué subir, qué bajar, qué conflictos).
 * 4. Ejecuta las operaciones.
 * 5. Cierra la sesión.
 */
class DesktopSyncCoordinator(
    private val config: DesktopConfig,
    private val library: DesktopLibrary,
    private val cacheDir: File,
    private val backupManager: SaveBackupManager? = null,
    private val hashStore: DesktopHashStore? = null,
    private val conflictPolicy: ConflictPolicy = ConflictPolicy.ASK,
) {

    suspend fun runSync(): SyncResult = withContext(Dispatchers.IO) {
        val serverUrl = config.serverUrl
        val apiKey = config.apiKey
        val retroArchBase = ""

        if (serverUrl.isEmpty() || apiKey.isEmpty()) {
            return@withContext SyncResult(error = tr("common.server_not_configured"))
        }

        val api = NetworkModule.createApiService(serverUrl, apiKey)

        // 1. Asegurar registro del dispositivo
        val deviceId = ensureDeviceRegistered(api)
            ?: return@withContext SyncResult(error = tr("sync.error.device_registration_detail"))

        // 2. Escanear saves locales de ROMs descargados
        val allDownloadedRoms = library.roms()
        if (allDownloadedRoms.isEmpty()) {
            return@withContext SyncResult(message = tr("sync.no_downloaded_roms"))
        }

        val downloadedRoms = allDownloadedRoms.filterNot { it.excludedFromSync }
        if (downloadedRoms.isEmpty()) {
            return@withContext SyncResult(message = tr("sync.all_excluded"))
        }

        val platformConfigs = library.roms().map { it.platformSlug }.distinct().associateWith { library.platform(it) }
        val localSavesMap = mutableMapOf<Int, List<LocalSave>>()
        val handlerByRom = mutableMapOf<Int, SaveHandler>()

        for (rom in downloadedRoms) {
            val config = platformConfigs[rom.platformSlug]
            val handler = SaveHandlerRegistry.getHandler(
                platformSlug = rom.platformSlug,
                emulatorId = config?.emulatorId,
            )
            handlerByRom[rom.romId] = handler

            val effectiveBasePath = resolveSavesBasePath(rom, config, retroArchBase)

            // ── Atajo por fingerprint: si los saves no cambiaron desde el
            // último sync limpio, saltarse el zipeo+hash de este ROM. ──
            val cachedFp = hashStore?.getFingerprint(rom.romId)
            if (cachedFp != null) {
                val currentFp = handler.savesFingerprint(
                    romId = rom.romId,
                    romFileName = rom.fileName,
                    platformSlug = rom.platformSlug,
                    savesBasePath = effectiveBasePath,
                    romLocalPath = rom.localPath,
                )
                if (currentFp != null && currentFp == cachedFp) {
                    continue
                }
            }

            val saves = handler.findSaves(
                romId = rom.romId,
                romFileName = rom.fileName,
                platformSlug = rom.platformSlug,
                savesBasePath = effectiveBasePath,
                romLocalPath = rom.localPath,
            )
            if (saves.isNotEmpty()) {
                localSavesMap[rom.romId] = saves
            }
        }

        // 3. Negociar con la API real de RomM
        val clientSaves = mutableListOf<ClientSaveState>()
        for ((romId, saves) in localSavesMap) {
            for (save in saves) {
                clientSaves.add(
                    ClientSaveState(
                        romId = romId,
                        fileName = save.fileName,
                        contentHash = save.sha1,
                        updatedAt = formatIso8601(save.lastModified),
                        fileSizeBytes = save.file.length().toInt(),
                    )
                )
            }
        }

        val negotiateRequest = NegotiateRequest(
            deviceId = deviceId,
            saves = clientSaves,
        )

        val negotiateResponse = try {
            api.negotiateSync(negotiateRequest)
        } catch (e: Exception) {
            println("Error: " + e.message)
            return@withContext SyncResult(error = tr("sync.error.negotiation", e.message))
        }

        // 4. Ejecutar operaciones
        var completed = 0
        var failed = 0
        var autoResolvedConflicts = 0
        val failedRomIds = mutableSetOf<Int>()
        val failures = mutableListOf<FailedOpInfo>()
        val conflicts = mutableListOf<es.davidrg.rommsync.core.remote.dto.SyncOperation>()

        for (op in negotiateResponse.operations) {
            when (op.action) {
                "upload" -> {
                    val save = localSavesMap[op.romId]?.find { it.fileName == op.fileName }
                    val handler = handlerByRom[op.romId]
                    if (save != null && handler != null) {
                        val ok = executeUpload(api, save, op.romId, deviceId, handler)
                        if (ok) {
                            completed++

                        } else {
                            failed++
                            failedRomIds.add(op.romId)
                            failures.add(
                                FailedOpInfo(
                                    romId = op.romId,
                                    romName = downloadedRoms.find { it.romId == op.romId }?.name ?: tr("sync.unknown_rom", op.romId),
                                    fileName = op.fileName,
                                    action = tr("sync.failed.action.upload"),
                                    reason = tr("sync.failed.upload_failed"),
                                ),
                            )
                        }
                    } else {
                        failed++
                        failedRomIds.add(op.romId)
                        failures.add(
                            FailedOpInfo(
                                romId = op.romId,
                                romName = downloadedRoms.find { it.romId == op.romId }?.name ?: tr("sync.unknown_rom", op.romId),
                                fileName = op.fileName,
                                action = tr("sync.failed.action.upload"),
                                reason = tr("sync.failed.save_missing"),
                            ),
                        )
                    }
                }
                "download" -> {
                    val rom = downloadedRoms.find { it.romId == op.romId }
                    val handler = handlerByRom[op.romId]
                    val opSaveId = op.saveId
                    if (rom != null && opSaveId != null && handler != null) {
                        val config = platformConfigs[rom.platformSlug]
                        val effectiveBasePath = resolveSavesBasePath(rom, config, retroArchBase)
                        // Copia de seguridad de la copia local antes de pisarla
                        backupLocalSave(op.romId, op.fileName, localSavesMap[op.romId], handler)
                        val ok = executeDownload(
                            api = api,
                            saveId = opSaveId,
                            deviceId = deviceId,
                            rom = rom,
                            fileName = op.fileName,
                            savesBasePath = effectiveBasePath,
                            handler = handler,
                        )
                        if (ok) {
                            completed++
                            // Tras un download exitoso, el hash local es el del servidor
                            op.serverContentHash?.let { hash ->

                            }
                        } else {
                            failed++
                            failedRomIds.add(op.romId)
                            failures.add(
                                FailedOpInfo(
                                    romId = op.romId,
                                    romName = downloadedRoms.find { it.romId == op.romId }?.name ?: tr("sync.unknown_rom", op.romId),
                                    fileName = op.fileName,
                                    action = tr("sync.failed.action.download"),
                                    reason = tr("sync.failed.download_failed"),
                                ),
                            )
                        }
                    } else {
                        failed++
                        failedRomIds.add(op.romId)
                        failures.add(
                            FailedOpInfo(
                                romId = op.romId,
                                romName = downloadedRoms.find { it.romId == op.romId }?.name ?: tr("sync.unknown_rom", op.romId),
                                fileName = op.fileName,
                                action = tr("sync.failed.action.download"),
                                reason = tr("sync.failed.rom_not_downloaded"),
                            ),
                        )
                    }
                }
                "conflict" -> {
                    // Política automática si el usuario no quiere decidir uno a uno
                    when (conflictPolicy) {
                        ConflictPolicy.PREFER_LOCAL -> {
                            val save = localSavesMap[op.romId]?.find { it.fileName == op.fileName }
                            val handler = handlerByRom[op.romId]
                            if (save != null && handler != null &&
                                executeUpload(api, save, op.romId, deviceId, handler)
                            ) {
                                completed++
                                autoResolvedConflicts++
                            } else {
                                conflicts.add(op)
                            }
                        }
                        ConflictPolicy.PREFER_SERVER -> {
                            val handler = handlerByRom[op.romId]
                            val rom = downloadedRoms.find { it.romId == op.romId }
                            val saveId = op.saveId
                            if (handler != null && rom != null && saveId != null) {
                                val config = platformConfigs[rom.platformSlug]
                                val base = resolveSavesBasePath(rom, config, retroArchBase)
                                backupLocalSave(op.romId, op.fileName, localSavesMap[op.romId], handler)
                                if (executeDownload(api, saveId, deviceId, rom, op.fileName, base, handler)) {
                                    completed++
                                    autoResolvedConflicts++
                                } else {
                                    conflicts.add(op)
                                }
                            } else {
                                conflicts.add(op)
                            }
                        }
                        ConflictPolicy.ASK -> {
                            conflicts.add(op)
                            println("Conflicto sin resolver: ${op.fileName} para rom ${op.romId}: ${op.reason}")
                        }
                    }
                }
                "no_op" -> {
                    // Ya sincronizado: registrar hash local para que el preview sepa
                    val save = localSavesMap[op.romId]?.find { it.fileName == op.fileName }
                    if (save != null) {

                    }
                }
            }
        }

        // 5. Cerrar sesión
        try {
            api.completeSession(
                sessionId = negotiateResponse.sessionId,
                request = SessionCompleteRequest(
                    operationsCompleted = completed,
                    operationsFailed = failed,
                ),
            )
        } catch (e: Exception) {
            println("Error: " + e.message)
        }

        // 6. Sellar fingerprints de los ROMs cuyo ciclo terminó sin fallos:
        // en el próximo ciclo, si el save no cambió, se saltará el zipeo.
        if (hashStore != null) {
            for (rom in downloadedRoms) {
                if (rom.romId in failedRomIds) continue
                val handler = handlerByRom[rom.romId] ?: continue
                val config = platformConfigs[rom.platformSlug]
                val base = resolveSavesBasePath(rom, config, retroArchBase)
                val fp = handler.savesFingerprint(
                    romId = rom.romId,
                    romFileName = rom.fileName,
                    platformSlug = rom.platformSlug,
                    savesBasePath = base,
                    romLocalPath = rom.localPath,
                )
                if (fp != null) hashStore.setFingerprint(rom.romId, fp)
            }
        }

        SyncResult(
            uploaded = negotiateResponse.operations.count { it.action == "upload" },
            downloaded = negotiateResponse.operations.count { it.action == "download" },
            conflicts = conflicts.size,
            autoResolvedConflicts = autoResolvedConflicts,
            conflictDetails = conflicts.map { op ->
                ConflictInfo(
                    romId = op.romId,
                    romName = downloadedRoms.find { it.romId == op.romId }?.name ?: tr("sync.unknown_rom", op.romId),
                    fileName = op.fileName,
                    serverUpdatedAt = op.serverUpdatedAt,
                    reason = op.reason,
                    saveId = op.saveId,
                )
            },
            failedDetails = failures,
            message = buildResultMessage(completed, failed, conflicts.size),
        )
    }

    /**
     * Negociación sin efectos: lista qué subidas/bajadas haría un runSync y
     * qué conflictos hay (botón "Comprobar cambios" de la pestaña Saves).
     */
    suspend fun scanPendingSaves(): PendingSavesReport = withContext(Dispatchers.IO) {
        val serverUrl = config.serverUrl
        val apiKey = config.apiKey
        val retroArchBase = ""

        if (serverUrl.isEmpty() || apiKey.isEmpty()) {
            return@withContext PendingSavesReport(error = tr("common.server_not_configured"))
        }
        val api = NetworkModule.createApiService(serverUrl, apiKey)
        val deviceId = ensureDeviceRegistered(api)
            ?: return@withContext PendingSavesReport(error = tr("sync.error.device_registration"))

        val allDownloadedRoms = library.roms()
        if (allDownloadedRoms.isEmpty()) {
            return@withContext PendingSavesReport()
        }
        val downloadedRoms = allDownloadedRoms.filterNot { it.excludedFromSync }

        val platformConfigs = downloadedRoms.map { it.platformSlug }.distinct().associateWith { library.platform(it) }
        val localSavesMap = mutableMapOf<Int, List<LocalSave>>()

        for (rom in downloadedRoms) {
            val config = platformConfigs[rom.platformSlug]
            val handler = SaveHandlerRegistry.getHandler(
                platformSlug = rom.platformSlug,
                emulatorId = config?.emulatorId,
            )
            val effectiveBasePath = resolveSavesBasePath(rom, config, retroArchBase)
            val saves = handler.findSaves(
                romId = rom.romId,
                romFileName = rom.fileName,
                platformSlug = rom.platformSlug,
                savesBasePath = effectiveBasePath,
                romLocalPath = rom.localPath,
            )
            if (saves.isNotEmpty()) localSavesMap[rom.romId] = saves
        }

        val clientSaves = mutableListOf<ClientSaveState>()
        for ((romId, saves) in localSavesMap) {
            for (save in saves) {
                clientSaves.add(
                    ClientSaveState(
                        romId = romId,
                        fileName = save.fileName,
                        contentHash = save.sha1,
                        updatedAt = formatIso8601(save.lastModified),
                        fileSizeBytes = save.file.length().toInt(),
                    ),
                )
            }
        }

        val negotiateResponse = try {
            api.negotiateSync(NegotiateRequest(deviceId = deviceId, saves = clientSaves))
        } catch (e: Exception) {
            return@withContext PendingSavesReport(error = tr("sync.error.negotiation", e.message))
        }

        val romNameById = downloadedRoms.associate { it.romId to it.name }
        PendingSavesReport(
            uploads = negotiateResponse.operations
                .filter { it.action == "upload" }
                .map { PendingSaveItem(it.romId, romNameById[it.romId] ?: tr("sync.unknown_rom", it.romId), it.fileName) },
            downloads = negotiateResponse.operations
                .filter { it.action == "download" }
                .map { PendingSaveItem(it.romId, romNameById[it.romId] ?: tr("sync.unknown_rom", it.romId), it.fileName) },
            conflicts = negotiateResponse.operations
                .filter { it.action == "conflict" }
                .map {
                    ConflictInfo(
                        romId = it.romId,
                        romName = romNameById[it.romId] ?: tr("sync.unknown_rom", it.romId),
                        fileName = it.fileName,
                        serverUpdatedAt = it.serverUpdatedAt,
                        reason = it.reason,
                        saveId = it.saveId,
                    )
                },
        )
    }

    data class PendingSaveItem(
        val romId: Int,
        val romName: String,
        val fileName: String,
    )

    data class PendingSavesReport(
        val uploads: List<PendingSaveItem> = emptyList(),
        val downloads: List<PendingSaveItem> = emptyList(),
        val conflicts: List<ConflictInfo> = emptyList(),
        val error: String? = null,
    )

    /**
     * Resuelve un conflicto pendiente forzando la dirección elegida por el
     * usuario:
     * - "local": sube la versión local con overwrite (gana este dispositivo).
     * - "server": negocia para obtener el saveId y descarga la versión del
     *   servidor sobrescribiendo la local.
     *
     * @return mensaje descriptivo del resultado.
     */
    suspend fun runConflictResolution(
        romId: Int,
        fileName: String,
        resolution: String,
    ): SyncResult = withContext(Dispatchers.IO) {
        val serverUrl = config.serverUrl
        val apiKey = config.apiKey
        val retroArchBase = ""

        if (serverUrl.isEmpty() || apiKey.isEmpty()) {
            return@withContext SyncResult(error = tr("common.server_not_configured"))
        }

        val api = NetworkModule.createApiService(serverUrl, apiKey)
        val deviceId = ensureDeviceRegistered(api)
            ?: return@withContext SyncResult(error = tr("sync.error.device_registration"))

        val rom = library.roms().find { it.romId == romId }
            ?: return@withContext SyncResult(error = tr("sync.error.rom_not_downloaded", romId))

        val config = library.platform(rom.platformSlug)
        val handler = SaveHandlerRegistry.getHandler(
            platformSlug = rom.platformSlug,
            emulatorId = config?.emulatorId,
        )
        val effectiveBasePath = resolveSavesBasePath(rom, config, retroArchBase)

        when (resolution) {
            "local" -> {
                val saves = handler.findSaves(
                    romId = rom.romId,
                    romFileName = rom.fileName,
                    platformSlug = rom.platformSlug,
                    savesBasePath = effectiveBasePath,
                    romLocalPath = rom.localPath,
                )
                val save = saves.find { it.fileName == fileName }
                    ?: return@withContext SyncResult(error = tr("sync.error.local_save_not_found", fileName))

                val ok = executeUpload(api, save, rom.romId, deviceId, handler)
                if (ok) {

                    SyncResult(uploaded = 1, message = tr("sync.conflict.local_uploaded", fileName))
                } else {
                    SyncResult(error = tr("sync.error.upload_failed", fileName))
                }
            }
            "server" -> {
                // Negociar para descubrir el saveId del servidor para este save
                val localSaves = handler.findSaves(
                    romId = rom.romId,
                    romFileName = rom.fileName,
                    platformSlug = rom.platformSlug,
                    savesBasePath = effectiveBasePath,
                    romLocalPath = rom.localPath,
                )
                val clientSaves = localSaves.map { save ->
                    ClientSaveState(
                        romId = rom.romId,
                        fileName = save.fileName,
                        contentHash = save.sha1,
                        updatedAt = formatIso8601(save.lastModified),
                        fileSizeBytes = save.file.length().toInt(),
                    )
                }
                val negotiateResponse = try {
                    api.negotiateSync(NegotiateRequest(deviceId = deviceId, saves = clientSaves))
                } catch (e: Exception) {
                    return@withContext SyncResult(error = tr("sync.error.negotiation", e.message))
                }

                val op = negotiateResponse.operations.find {
                    it.romId == rom.romId && it.fileName == fileName
                } ?: return@withContext SyncResult(
                    error = tr("sync.error.no_server_operation", fileName),
                )

                val saveId = op.saveId
                    ?: return@withContext SyncResult(error = tr("sync.error.no_save_id", fileName))

                // Copia de seguridad de la copia local antes de pisarla
                backupLocalSave(rom.romId, fileName, localSaves, handler)

                val ok = executeDownload(
                    api = api,
                    saveId = saveId,
                    deviceId = deviceId,
                    rom = rom,
                    fileName = fileName,
                    savesBasePath = effectiveBasePath,
                    handler = handler,
                )
                if (ok) {
                    op.serverContentHash?.let { hash ->

                    }
                    try {
                        api.completeSession(
                            sessionId = negotiateResponse.sessionId,
                            request = SessionCompleteRequest(operationsCompleted = 1),
                        )
                    } catch (_: Exception) {}
                    SyncResult(downloaded = 1, message = tr("sync.conflict.server_restored", fileName))
                } else {
                    SyncResult(error = tr("sync.error.download_failed", fileName))
                }
            }
            else -> SyncResult(error = tr("sync.error.unknown_resolution", resolution))
        }
    }

    private fun resolveSavesBasePath(
        rom: DesktopLibrary.RomEntry,
        config: DesktopLibrary.PlatformEntry?,
        retroArchBase: String,
    ): String {
        rom.savesPathOverride?.takeIf { it.isNotBlank() }?.let { return it }
        config?.savesPathOverride?.takeIf { it.isNotBlank() }?.let { return it }
        return SaveHandlerRegistry.getDefaultSavesPath(
            emulatorId = config?.emulatorId
                ?: SaveHandlerRegistry.getDefaultEmulator(rom.platformSlug).id,
            platformSlug = rom.platformSlug,
            retroArchBase = retroArchBase,
        )
    }

    /**
     * Guarda una copia de seguridad de la copia local de un save justo antes
     * de que una descarga del servidor la sobrescriba. El blob es el fichero
     * preparado por el handler (zip para saves de directorio), restaurable
     * vía extractDownload.
     */
    private suspend fun backupLocalSave(
        romId: Int,
        fileName: String,
        saves: List<LocalSave>?,
        handler: SaveHandler,
    ) {
        val manager = backupManager ?: return
        val local = saves?.find { it.fileName == fileName } ?: return
        runCatching { manager.backup(romId, fileName, handler.prepareForUpload(local)) }
    }

    /**
     * Restaura una copia de seguridad sobre la ubicación del save (para la
     * UI de historial de copias). El blob vuelve a pasar por extractDownload
     * del handler, así que sirve para saves de fichero y de directorio.
     */
    suspend fun restoreBackup(romId: Int, fileName: String, backupFile: File): Boolean =
        withContext(Dispatchers.IO) {
            val rom = library.roms().find { it.romId == romId } ?: return@withContext false
            val platCfg = library.platform(rom.platformSlug)
            val handler = SaveHandlerRegistry.getHandler(rom.platformSlug, platCfg.emulatorId)
            val base = resolveSavesBasePath(rom, platCfg, "")
            runCatching {
                handler.extractDownload(
                    tempFile = backupFile,
                    romFileName = rom.fileName,
                    platformSlug = rom.platformSlug,
                    savesBasePath = base,
                    targetFileName = fileName,
                )
            }.getOrDefault(false)
        }

    private suspend fun ensureDeviceRegistered(api: RomMApiService): String? {
        val cached = config.deviceId
        if (!cached.isNullOrBlank()) return cached

        return try {
            val response = api.registerDevice(
                DeviceRegistrationRequest(
                    name = "RomM Sync Desktop",
                    platform = "linux",
                    hostname = runCatching { InetAddress.getLocalHost().hostName }.getOrDefault("desktop"),
                ),
            )
            config.deviceId = response.deviceId
            response.deviceId
        } catch (e: retrofit2.HttpException) {
            println("Error: " + e.message)
            null
        } catch (e: Exception) {
            println("Error: " + e.message)
            null
        }
    }

    private suspend fun executeUpload(
        api: RomMApiService,
        save: LocalSave,
        romId: Int,
        deviceId: String,
        handler: SaveHandler,
    ): Boolean {
        return try {
            val fileToUpload = handler.prepareForUpload(save)
            val requestFile = fileToUpload.asRequestBody("application/octet-stream".toMediaType())
            val filePart = MultipartBody.Part.createFormData("saveFile", save.fileName, requestFile)
            api.uploadSave(
                file = filePart,
                romId = romId,
                deviceId = deviceId,
                overwrite = true,
            )
            true
        } catch (e: Exception) {
            println("Error: " + e.message)
            false
        }
    }

    private suspend fun executeDownload(
        api: RomMApiService,
        saveId: Int,
        deviceId: String,
        rom: DesktopLibrary.RomEntry,
        fileName: String,
        savesBasePath: String,
        handler: SaveHandler,
    ): Boolean {
        return try {
            val responseBody = api.downloadSave(saveId, deviceId)
            val tempFile = File(cacheDir, "sync_dl_${rom.romId}_$fileName")
            FileOutputStream(tempFile).use { out ->
                responseBody.byteStream().use { input ->
                    input.copyTo(out)
                }
            }

            val ok = handler.extractDownload(
                tempFile = tempFile,
                romFileName = rom.fileName,
                platformSlug = rom.platformSlug,
                savesBasePath = savesBasePath,
                targetFileName = fileName,
            )
            tempFile.delete()
            ok
        } catch (e: Exception) {
            println("Error: " + e.message)
            false
        }
    }

    private fun buildResultMessage(completed: Int, failed: Int, conflicts: Int): String {
        val parts = mutableListOf<String>()
        if (completed > 0) parts.add(tr("sync.result.completed", completed))
        if (failed > 0) parts.add(tr("sync.result.failed", failed))
        if (conflicts > 0) parts.add(tr("sync.result.conflicts", conflicts))
        return if (parts.isEmpty()) tr("sync.result.all_synced") else parts.joinToString(", ")
    }
    companion object {
        

        private fun formatIso8601(millis: Long): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            return sdf.format(Date(millis))
        }
    }
}

data class SyncResult(
    val uploaded: Int = 0,
    val downloaded: Int = 0,
    val conflicts: Int = 0,
    val autoResolvedConflicts: Int = 0,
    val conflictDetails: List<ConflictInfo> = emptyList(),
    val failedDetails: List<FailedOpInfo> = emptyList(),
    val message: String? = null,
    val error: String? = null,
) {
    val isSuccess: Boolean get() = error == null
}

/**
 * Detalle de una operación de sync que falló, para mostrar en la UI.
 */
data class FailedOpInfo(
    val romId: Int,
    val romName: String,
    val fileName: String,
    val action: String,
    val reason: String,
)

/**
 * Detalle de un conflicto detectado durante la negociación: la copia local
 * y la del servidor difieren y ambas son más recientes que la última
 * sincronización conocida.
 */
data class ConflictInfo(
    val romId: Int,
    val romName: String,
    val fileName: String,
    val serverUpdatedAt: String?,
    val reason: String?,
    val saveId: Int?,
)
