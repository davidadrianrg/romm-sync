package es.davidrg.rommsync.data.sync

import android.os.Build
import android.util.Log
import es.davidrg.rommsync.data.local.SettingsDataStore
import es.davidrg.rommsync.data.local.dao.RomDao
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
import es.davidrg.rommsync.core.util.RootShell
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
class SyncCoordinator(
    private val settingsDataStore: SettingsDataStore,
    private val romDao: RomDao,
    private val platformDao: es.davidrg.rommsync.data.local.dao.PlatformDao,
    private val cacheDir: File,
    private val syncedHashStore: SyncedHashStore? = null,
    private val backupManager: SaveBackupManager? = null,
    private val conflictPolicy: ConflictPolicy = ConflictPolicy.ASK,
) {

    suspend fun runSync(): SyncResult = withContext(Dispatchers.IO) {
        val serverUrl = settingsDataStore.getServerUrlBlocking()
        val apiKey = settingsDataStore.getApiKeyBlocking()
        val retroArchBase = settingsDataStore.getRetroArchBasePathBlocking()

        if (serverUrl.isEmpty() || apiKey.isEmpty()) {
            return@withContext SyncResult(error = "Servidor no configurado")
        }

        val api = NetworkModule.createApiService(serverUrl, apiKey)

        // 1. Asegurar registro del dispositivo
        val deviceId = ensureDeviceRegistered(api)
            ?: return@withContext SyncResult(error = "No se pudo registrar el dispositivo. Comprueba permisos y conexión.")

        // 2. Escanear saves locales de ROMs descargados
        val allDownloadedRoms = romDao.getAllDownloadedRomsBlocking()
        if (allDownloadedRoms.isEmpty()) {
            return@withContext SyncResult(message = "No hay ROMs descargados para sincronizar")
        }

        val downloadedRoms = allDownloadedRoms.filterNot { it.excludedFromSync }
        if (downloadedRoms.isEmpty()) {
            return@withContext SyncResult(message = "Todos los ROMs están excluidos de la sincronización")
        }

        val platformConfigs = platformDao.getAllPlatformsBlocking().associateBy { it.slug }
        val localSavesMap = mutableMapOf<Int, List<LocalSave>>()
        val handlerByRom = mutableMapOf<Int, SaveHandler>()
        val pathWarnings = mutableListOf<String>()

        for (rom in downloadedRoms) {
            val config = platformConfigs[rom.platformSlug]
            val handler = SaveHandlerRegistry.getHandler(
                platformSlug = rom.platformSlug,
                emulatorId = config?.emulatorId,
            )
            handlerByRom[rom.romId] = handler

            val effectiveBasePath = resolveSavesBasePath(rom, config, retroArchBase)

            // Una ruta configurada (por juego o por plataforma) que no se puede
            // leer haría que el handler devolviera 0 saves en silencio: el sync
            // diría "Todo sincronizado" sin pista alguna. Avisar explícitamente.
            val explicitOverride = !rom.savesPathOverride.isNullOrBlank() ||
                config?.savesPathOverride?.isNotBlank() == true
            if (explicitOverride && !isPathReadable(effectiveBasePath) &&
                pathWarnings.size < MAX_PATH_WARNINGS
            ) {
                Log.w(TAG, "Ruta de saves configurada ilegible: '${rom.name}' → $effectiveBasePath")
                pathWarnings.add("${rom.name}: $effectiveBasePath")
            }

            // ── Atajo por fingerprint: si los saves no cambiaron desde el
            // último sync, saltarse el zipeo+hash completo de este ROM. ──
            val cachedFp = syncedHashStore?.getFingerprint(rom.romId)
            if (cachedFp != null) {
                val currentFp = handler.savesFingerprint(
                    romId = rom.romId,
                    romFileName = rom.fileName,
                    platformSlug = rom.platformSlug,
                    savesBasePath = effectiveBasePath,
                    romLocalPath = rom.localPath,
                )
                if (currentFp != null && currentFp == cachedFp) {
                    Log.d(TAG, "Skipping unchanged saves for '${rom.name}' (fingerprint hit)")
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
            Log.w(TAG, "Negotiate failed", e)
            return@withContext SyncResult(error = "Error en negociación: ${e.message}")
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
                            syncedHashStore?.setSyncedHash(op.romId, save.fileName, save.sha1)
                        } else {
                            failed++
                            failedRomIds.add(op.romId)
                            failures.add(
                                FailedOpInfo(
                                    romId = op.romId,
                                    romName = downloadedRoms.find { it.romId == op.romId }?.name ?: "rom ${op.romId}",
                                    fileName = op.fileName,
                                    action = "subida",
                                    reason = "No se pudo subir al servidor",
                                ),
                            )
                        }
                    } else {
                        failed++
                        failedRomIds.add(op.romId)
                        failures.add(
                            FailedOpInfo(
                                romId = op.romId,
                                romName = downloadedRoms.find { it.romId == op.romId }?.name ?: "rom ${op.romId}",
                                fileName = op.fileName,
                                action = "subida",
                                reason = "El save desapareció del disco antes de subirlo",
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
                                syncedHashStore?.setSyncedHash(op.romId, op.fileName, hash)
                            }
                        } else {
                            failed++
                            failedRomIds.add(op.romId)
                            failures.add(
                                FailedOpInfo(
                                    romId = op.romId,
                                    romName = downloadedRoms.find { it.romId == op.romId }?.name ?: "rom ${op.romId}",
                                    fileName = op.fileName,
                                    action = "descarga",
                                    reason = "Fallo al descargar o extraer en disco",
                                ),
                            )
                        }
                    } else {
                        failed++
                        failedRomIds.add(op.romId)
                        failures.add(
                            FailedOpInfo(
                                romId = op.romId,
                                romName = downloadedRoms.find { it.romId == op.romId }?.name ?: "rom ${op.romId}",
                                fileName = op.fileName,
                                action = "descarga",
                                reason = "El ROM ya no está descargado en este dispositivo",
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
                            Log.w(TAG, "Conflicto sin resolver: ${op.fileName} para rom ${op.romId}: ${op.reason}")
                        }
                    }
                }
                "no_op" -> {
                    // Ya sincronizado: registrar hash local para que el preview sepa
                    val save = localSavesMap[op.romId]?.find { it.fileName == op.fileName }
                    if (save != null) {
                        syncedHashStore?.setSyncedHash(op.romId, save.fileName, save.sha1)
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
            Log.w(TAG, "Failed to complete session", e)
        }

        // 6. Sellar fingerprints de los ROMs cuyo sync terminó sin fallos:
        // en el próximo ciclo, si el save no cambió, se saltará el zipeo.
        for (rom in downloadedRoms) {
            if (failed == 0 || rom.romId !in failedRomIds) {
                val handler = handlerByRom[rom.romId] ?: continue
                val config = platformConfigs[rom.platformSlug]
                val effectiveBasePath = resolveSavesBasePath(rom, config, retroArchBase)
                val fp = handler.savesFingerprint(
                    romId = rom.romId,
                    romFileName = rom.fileName,
                    platformSlug = rom.platformSlug,
                    savesBasePath = effectiveBasePath,
                    romLocalPath = rom.localPath,
                )
                if (fp != null) syncedHashStore?.setFingerprint(rom.romId, fp)
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
                    romName = downloadedRoms.find { it.romId == op.romId }?.name ?: "rom ${op.romId}",
                    fileName = op.fileName,
                    serverUpdatedAt = op.serverUpdatedAt,
                    reason = op.reason,
                    saveId = op.saveId,
                )
            },
            failedDetails = failures,
            message = buildResultMessage(completed, failed, conflicts.size, pathWarnings),
        )
    }

    /**
     * true si el directorio es legible por la app: bien vía API File, o bien
     * vía root (rutas `/data/data/...` en teléfonos rooteados).
     */
    private fun isPathReadable(path: String): Boolean {
        if (File(path).isDirectory) return true
        return RootShell.available &&
            RootShell.run("test -d ${RootShell.sq(path)}") != null
    }

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
        val serverUrl = settingsDataStore.getServerUrlBlocking()
        val apiKey = settingsDataStore.getApiKeyBlocking()
        val retroArchBase = settingsDataStore.getRetroArchBasePathBlocking()

        if (serverUrl.isEmpty() || apiKey.isEmpty()) {
            return@withContext SyncResult(error = "Servidor no configurado")
        }

        val api = NetworkModule.createApiService(serverUrl, apiKey)
        val deviceId = ensureDeviceRegistered(api)
            ?: return@withContext SyncResult(error = "No se pudo registrar el dispositivo")

        val rom = romDao.getAllDownloadedRomsBlocking().find { it.romId == romId }
            ?: return@withContext SyncResult(error = "ROM $romId no está descargado en este dispositivo")

        val config = platformDao.getAllPlatformsBlocking().find { it.slug == rom.platformSlug }
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
                    ?: return@withContext SyncResult(error = "No se encontró el save local $fileName")

                val ok = executeUpload(api, save, rom.romId, deviceId, handler)
                if (ok) {
                    syncedHashStore?.setSyncedHash(rom.romId, save.fileName, save.sha1)
                    SyncResult(uploaded = 1, message = "Versión local subida: $fileName")
                } else {
                    SyncResult(error = "Fallo al subir $fileName")
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
                    return@withContext SyncResult(error = "Error en negociación: ${e.message}")
                }

                val op = negotiateResponse.operations.find {
                    it.romId == rom.romId && it.fileName == fileName
                } ?: return@withContext SyncResult(
                    error = "El servidor ya no reporta operaciones para $fileName",
                )

                val saveId = op.saveId
                    ?: return@withContext SyncResult(error = "El servidor no devolvió saveId para $fileName")

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
                        syncedHashStore?.setSyncedHash(rom.romId, fileName, hash)
                    }
                    try {
                        api.completeSession(
                            sessionId = negotiateResponse.sessionId,
                            request = SessionCompleteRequest(operationsCompleted = 1),
                        )
                    } catch (_: Exception) {}
                    SyncResult(downloaded = 1, message = "Versión del servidor restaurada: $fileName")
                } else {
                    SyncResult(error = "Fallo al descargar $fileName")
                }
            }
            else -> SyncResult(error = "Resolución desconocida: $resolution")
        }
    }

    /**
     * Guarda una copia de seguridad de la copia local de un save justo antes
     * de que una descarga del servidor la sobrescriba (blob preparado por el
     * handler, restaurable vía extractDownload).
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
     * Restaura una copia de seguridad de save sobre su ubicación original
     * (para la UI de historial de copias del detalle de juego).
     */
    suspend fun restoreBackup(romId: Int, fileName: String, backupFile: File): Boolean =
        withContext(Dispatchers.IO) {
            val rom = romDao.getAllDownloadedRomsBlocking().find { it.romId == romId }
                ?: return@withContext false
            val config = platformDao.getAllPlatformsBlocking().find { it.slug == rom.platformSlug }
            val handler = SaveHandlerRegistry.getHandler(rom.platformSlug, config?.emulatorId)
            val retroArchBase = settingsDataStore.getRetroArchBasePathBlocking()
            val base = resolveSavesBasePath(rom, config, retroArchBase)
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

    private fun resolveSavesBasePath(
        rom: es.davidrg.rommsync.data.local.entity.DownloadedRomEntity,
        config: es.davidrg.rommsync.data.local.entity.PlatformEntity?,
        retroArchBase: String,
    ): String {        rom.savesPathOverride?.takeIf { it.isNotBlank() }?.let { return it }
        config?.savesPathOverride?.takeIf { it.isNotBlank() }?.let { return it }
        return SaveHandlerRegistry.getDefaultSavesPath(
            emulatorId = config?.emulatorId
                ?: SaveHandlerRegistry.getDefaultEmulator(rom.platformSlug).id,
            platformSlug = rom.platformSlug,
            retroArchBase = retroArchBase,
        )
    }

    private suspend fun ensureDeviceRegistered(api: RomMApiService): String? {
        val cached = settingsDataStore.getSyncDeviceIdStringBlocking()
        if (!cached.isNullOrBlank()) return cached

        return try {
            val response = api.registerDevice(
                DeviceRegistrationRequest(
                    name = "${Build.MANUFACTURER} ${Build.MODEL}",
                    platform = "android",
                    hostname = Build.DEVICE,
                ),
            )
            settingsDataStore.setSyncDeviceIdString(response.deviceId)
            response.deviceId
        } catch (e: retrofit2.HttpException) {
            Log.e(TAG, "Device registration HTTP ${e.code()}: ${e.message()}", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Device registration failed", e)
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
            Log.w(TAG, "Upload failed for ${save.fileName}", e)
            false
        }
    }

    private suspend fun executeDownload(
        api: RomMApiService,
        saveId: Int,
        deviceId: String,
        rom: es.davidrg.rommsync.data.local.entity.DownloadedRomEntity,
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
            Log.w(TAG, "Download failed for $fileName (rom ${rom.romId})", e)
            false
        }
    }

    private fun buildResultMessage(
        completed: Int,
        failed: Int,
        conflicts: Int,
        pathWarnings: List<String> = emptyList(),
    ): String {
        val parts = mutableListOf<String>()
        if (completed > 0) parts.add("$completed completadas")
        if (failed > 0) parts.add("$failed fallidas")
        if (conflicts > 0) parts.add("$conflicts conflictos")
        if (pathWarnings.isNotEmpty()) {
            val shown = pathWarnings.take(3).joinToString(", ") +
                if (pathWarnings.size > 3) " (+${pathWarnings.size - 3} más)" else ""
            parts.add("⚠ ${pathWarnings.size} ruta(s) configurada(s) ilegible(s): $shown")
        }
        return if (parts.isEmpty()) "Todo sincronizado" else parts.joinToString(" · ")
    }
    companion object {
        private const val TAG = "SyncCoordinator"

        /** Tope de rutas ilegibles detalladas en el mensaje de resultado. */
        private const val MAX_PATH_WARNINGS = 10

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
