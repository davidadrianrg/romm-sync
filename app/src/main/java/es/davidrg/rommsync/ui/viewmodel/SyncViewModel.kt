package es.davidrg.rommsync.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.davidrg.rommsync.data.local.SettingsDataStore
import es.davidrg.rommsync.data.local.dao.PlatformDao
import es.davidrg.rommsync.data.local.dao.RomDao
import es.davidrg.rommsync.data.repository.SettingsRepository
import es.davidrg.rommsync.data.sync.ConflictInfo
import es.davidrg.rommsync.data.sync.FailedOpInfo
import es.davidrg.rommsync.data.sync.SaveSyncManager
import es.davidrg.rommsync.data.sync.SyncState
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import es.davidrg.rommsync.data.sync.SyncedHashStore
import es.davidrg.rommsync.data.sync.SavePathAccess
import es.davidrg.rommsync.core.sync.platform.SaveHandlerRegistry
import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Modelo de datos para mostrar una partida local con cambios pendientes.
 */
data class SavePreviewItem(
    val romName: String,
    val platformSlug: String,
    val fileName: String,
    val lastModified: Long,
)

class SyncViewModel(
    private val settingsRepository: SettingsRepository,
    private val saveSyncManager: SaveSyncManager,
    private val romDao: RomDao,
    private val platformDao: PlatformDao,
    private val syncedHashStore: SyncedHashStore,
    private val appContext: Context,
) : ViewModel() {

    val retroArchBasePath: StateFlow<String> = settingsRepository.retroArchBasePath
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsDataStore.DEFAULT_RETROARCH_PATH)

    val syncState: StateFlow<SyncState> = saveSyncManager.observeSyncState()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncState.Idle)

    val lastSyncTimestamp: StateFlow<Long> = settingsRepository.lastSyncTimestamp
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val lastSyncSummary: StateFlow<String> = settingsRepository.lastSyncSummary
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val syncIntervalMinutes: StateFlow<Int> = settingsRepository.saveSyncIntervalMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _localSaves = MutableStateFlow<List<SavePreviewItem>>(emptyList())
    val localSaves: StateFlow<List<SavePreviewItem>> = _localSaves.asStateFlow()

    /** Rutas de saves configuradas (juego/plataforma) que no se pueden leer. */
    private val _pathWarnings = MutableStateFlow<List<String>>(emptyList())
    val pathWarnings: StateFlow<List<String>> = _pathWarnings.asStateFlow()

    /** Juegos comprobados en el último escaneo (-1 = aún sin comprobar). */
    private val _scanCheckedCount = MutableStateFlow(-1)
    val scanCheckedCount: StateFlow<Int> = _scanCheckedCount.asStateFlow()

    /** Raíz para copias staged de rutas restringidas (Android/data + root). */
    private val saveStageRoot = File(System.getProperty("java.io.tmpdir"), "save_stage")

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    /** Conflictos detectados en el último sync, para la UI de resolución. */
    private val _conflicts = MutableStateFlow<List<ConflictInfo>>(emptyList())
    val conflicts: StateFlow<List<ConflictInfo>> = _conflicts.asStateFlow()

    /** Operaciones fallidas en el último sync, para la UI de detalle. */
    private val _failedOps = MutableStateFlow<List<FailedOpInfo>>(emptyList())
    val failedOps: StateFlow<List<FailedOpInfo>> = _failedOps.asStateFlow()

    /** Resoluciones en curso (romId+fileName) para feedback en la UI. */
    private val _resolvingConflict = MutableStateFlow<String?>(null)
    val resolvingConflict: StateFlow<String?> = _resolvingConflict.asStateFlow()

    /** Carga los conflictos y fallidos del último sync persistido. */
    init {
        viewModelScope.launch {
            settingsRepository.lastSyncConflictsJson.collect { json ->
                _conflicts.value = parseConflictsJson(json)
            }
        }
        viewModelScope.launch {
            settingsRepository.lastSyncFailedJson.collect { json ->
                _failedOps.value = parseFailuresJson(json)
            }
        }
    }

    /** Resuelve un conflicto forzando la dirección elegida. */
    fun resolveConflict(romId: Int, fileName: String, resolution: String) {
        viewModelScope.launch {
            _resolvingConflict.value = "${romId}_$fileName"
            try {
                saveSyncManager.triggerConflictResolution(romId, fileName, resolution)
            } finally {
                _resolvingConflict.value = null
            }
            // Refrescar la lista de conflictos tras resolver
            _conflicts.value = _conflicts.value.filterNot {
                it.romId == romId && it.fileName == fileName
            }
            settingsRepository.setLastSyncConflicts(serializeConflicts(_conflicts.value))
            scanLocalSaves()
        }
    }

    private fun parseConflictsJson(json: String): List<ConflictInfo> = try {
        conflictMoshiAdapter.fromJson(json).orEmpty()
    } catch (_: Exception) {
        emptyList()
    }

    private fun parseFailuresJson(json: String): List<FailedOpInfo> = try {
        failuresMoshiAdapter.fromJson(json).orEmpty()
    } catch (_: Exception) {
        emptyList()
    }

    private fun serializeConflicts(conflicts: List<ConflictInfo>): String = try {
        conflictMoshiAdapter.toJson(conflicts)
    } catch (_: Exception) {
        "[]"
    }

    fun setRetroArchBasePath(path: String) {
        viewModelScope.launch {
            settingsRepository.setRetroArchBasePath(path)
            scanLocalSaves()
        }
    }

    fun setSyncInterval(minutes: Int) {
        viewModelScope.launch {
            settingsRepository.setSaveSyncIntervalMinutes(minutes)
            saveSyncManager.schedulePeriodicSync(minutes, replace = true)
        }
    }

    fun triggerSync() {
        saveSyncManager.triggerSync()
    }

    fun scanLocalSaves() {
        viewModelScope.launch {
            _isScanning.value = true
            val saves = withContext(Dispatchers.IO) {
                scanSavesFromDisk()
            }
            _localSaves.value = saves
            _isScanning.value = false
        }
    }

    private suspend fun scanSavesFromDisk(): List<SavePreviewItem> {
        // OJO: antes aquí había .stateIn(viewModelScope, Eagerly, DEFAULT).value —
        // la colecta es asíncrona, así que la lectura inmediata devolvía SIEMPRE
        // DEFAULT_RETROARCH_PATH: "Comprobar" escaneaba la ruta por defecto
        // mientras runSync leía la real → nunca veía cambios con ruta custom.
        val retroArchBase = settingsRepository.retroArchBasePath.first()

        val downloadedRoms = romDao.getAllDownloadedRoms()
        _scanCheckedCount.value = downloadedRoms.count { !it.excludedFromSync }
        if (downloadedRoms.isEmpty()) return emptyList()

        val platformConfigs = platformDao.getAllPlatformsBlocking().associateBy { it.slug }
        val results = mutableListOf<SavePreviewItem>()
        val warnings = mutableListOf<String>()

        for (rom in downloadedRoms) {
            if (rom.excludedFromSync) continue

            val config = platformConfigs[rom.platformSlug]
            val handler = SaveHandlerRegistry.getHandler(
                platformSlug = rom.platformSlug,
                emulatorId = config?.emulatorId,
            )

            val effectiveBasePath = rom.savesPathOverride?.takeIf { it.isNotBlank() }
                ?: config?.savesPathOverride?.takeIf { it.isNotBlank() }
                ?: SaveHandlerRegistry.getDefaultSavesPath(
                    emulatorId = config?.emulatorId
                        ?: SaveHandlerRegistry.getDefaultEmulator(rom.platformSlug).id,
                    platformSlug = rom.platformSlug,
                    retroArchBase = retroArchBase,
                )

            // Ruta configurada explícitamente pero ilegible: el handler no
            // encontrará nada y el sync parecería "al día" sin buscar aquí.
            val explicitOverride = !rom.savesPathOverride.isNullOrBlank() ||
                config?.savesPathOverride?.isNotBlank() == true
            if (explicitOverride && warnings.size < 10) {
                if (!SavePathAccess.isUsable(effectiveBasePath, appContext)) {
                    val restricted = effectiveBasePath.contains("/Android/data") ||
                        effectiveBasePath.contains("/Android/obb") ||
                        effectiveBasePath.contains("/data/data")
                    val hint = if (restricted) {
                        " — sin root, usa «Otorgar acceso» en el selector de carpetas"
                    } else ""
                    warnings.add("${rom.name}: $effectiveBasePath$hint")
                }
            }

            // Ruta restringida (Android/data): staging temporal vía root o SAF
            // para que el handler trabaje con la API File tal cual.
            val staged = SavePathAccess.stage(effectiveBasePath, saveStageRoot, appContext)
            val saves = try {
                handler.findSaves(
                    romId = rom.romId,
                    romFileName = rom.fileName,
                    platformSlug = rom.platformSlug,
                    savesBasePath = staged.dir.path,
                    romLocalPath = rom.localPath,
                )
            } finally {
                SavePathAccess.cleanup(staged, appContext)
            }

            for (save in saves) {
                // Solo mostrar saves con cambios pendientes
                if (syncedHashStore.isAlreadySynced(rom.romId, save.fileName, save.sha1)) continue

                results.add(
                    SavePreviewItem(
                        romName = rom.name,
                        platformSlug = rom.platformSlug,
                        fileName = save.fileName,
                        lastModified = save.lastModified,
                    )
                )
            }
        }

        _pathWarnings.value = warnings
        return results.sortedByDescending { it.lastModified }
    }

    companion object {
        /** Adaptador Moshi para persistir conflictos como JSON. */
        private val conflictMoshiAdapter by lazy {
            val moshi = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()
            moshi.adapter<List<ConflictInfo>>(
                Types.newParameterizedType(List::class.java, ConflictInfo::class.java),
            )
        }

        /** Adaptador Moshi para persistir operaciones fallidas como JSON. */
        private val failuresMoshiAdapter by lazy {
            val moshi = Moshi.Builder()
                .add(KotlinJsonAdapterFactory())
                .build()
            moshi.adapter<List<FailedOpInfo>>(
                Types.newParameterizedType(List::class.java, FailedOpInfo::class.java),
            )
        }
    }
}
