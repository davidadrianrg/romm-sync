package es.davidrg.rommsync.desktop

import es.davidrg.rommsync.core.remote.NetworkModule
import es.davidrg.rommsync.core.remote.RomMApiService
import es.davidrg.rommsync.core.remote.dto.PlatformDto
import es.davidrg.rommsync.core.remote.dto.RomDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Sección activa de la navegación desktop. */
enum class Section { PLATFORMS, LIBRARY, DOWNLOADS, SAVES, SETTINGS }

/** Filtro de la biblioteca. */
enum class LibraryFilter { ALL, MISSING, DOWNLOADED }

/** Estado de un elemento de la cola de descargas. */
data class DesktopTask(
    val romId: Int,
    val name: String,
    val platformSlug: String?,
    val status: String, // queued | running | done | error
    val bytesRead: Long = 0,
    val totalBytes: Long = -1L,
    val speedBps: Long = 0,
    val message: String? = null,
)

/** Juego agrupado (multi-disc fusionados por igdbId, igual que Android). */
data class GameCard(
    val rep: RomDto,
    val discCount: Int,
    val groupRoms: List<RomDto>,
    val downloaded: Boolean,
)

/**
 * Estado central de la app desktop: conexión, plataformas, ROMs por
 * plataforma (paginados), agrupado multi-disc, cola de descargas con
 * progreso y resultado de sincronización de saves.
 */
class DesktopAppState(private val scope: CoroutineScope) {

    val config = DesktopConfig
    val library = DesktopLibrary(File(DesktopConfig.configDir, "library.properties"))

    private val _section = MutableStateFlow(Section.PLATFORMS)
    val section: StateFlow<Section> = _section

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private val _platforms = MutableStateFlow<List<PlatformDto>>(emptyList())
    val platforms: StateFlow<List<PlatformDto>> = _platforms

    private val _selectedPlatformId = MutableStateFlow<Int?>(null)
    val selectedPlatformId: StateFlow<Int?> = _selectedPlatformId

    private val _roms = MutableStateFlow<List<RomDto>>(emptyList())
    val roms: StateFlow<List<RomDto>> = _roms

    private val _loadingRoms = MutableStateFlow(false)
    val loadingRoms: StateFlow<Boolean> = _loadingRoms

    private val _loadingPlatforms = MutableStateFlow(false)
    val loadingPlatforms: StateFlow<Boolean> = _loadingPlatforms

    private val _search = MutableStateFlow("")
    val search: StateFlow<String> = _search

    private val _filter = MutableStateFlow(LibraryFilter.ALL)
    val filter: StateFlow<LibraryFilter> = _filter

    private val _tasks = MutableStateFlow<List<DesktopTask>>(emptyList())
    val tasks: StateFlow<List<DesktopTask>> = _tasks

    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _syncStatus

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing

    /** romId -> ruta local registrada en library.properties tras descargar. */
    private val downloadedIds: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    /** Se incrementa al completar descargas para recomponer la biblioteca. */
    private val _downloadedVersion = MutableStateFlow(0)
    val downloadedVersion: StateFlow<Int> = _downloadedVersion

    // ── Plataformas: visibilidad (activar/desactivar como Android) ──────

    private val _hiddenPlatforms = MutableStateFlow<Set<String>>(emptySet())
    val hiddenPlatforms: StateFlow<Set<String>> = _hiddenPlatforms

    fun visiblePlatforms(): List<PlatformDto> =
        _platforms.value.filter { it.slug !in _hiddenPlatforms.value }

    fun togglePlatform(slug: String) {
        val next = if (slug in _hiddenPlatforms.value) _hiddenPlatforms.value - slug else _hiddenPlatforms.value + slug
        _hiddenPlatforms.value = next
        DesktopConfig.hiddenPlatforms = next.joinToString(",")
    }

    /** Activa o desactiva TODAS las plataformas de golpe (botón global). */
    fun toggleAllPlatforms(enable: Boolean) {
        val next = if (enable) emptySet() else _platforms.value.map { it.slug }.toSet()
        _hiddenPlatforms.value = next
        DesktopConfig.hiddenPlatforms = next.joinToString(",")
    }

    // ── Config por plataforma: carpeta de ROMs y ruta de saves ─────────

    /** Config persistida de una plataforma (emulator, saves, carpeta ROMs). */
    fun platformConfig(slug: String) = library.platform(slug)

    /** Fija la carpeta de ROMs relativa al root (vacío = slug por defecto). */
    fun setPlatformFolder(slug: String, folder: String) {
        val cur = library.platform(slug)
        library.upsertPlatform(cur.copy(romsFolderOverride = folder.ifBlank { null }))
    }

    /** Fija la ruta base de saves (vacío = default del emulador). */
    fun setPlatformSavesPath(slug: String, path: String) {
        val cur = library.platform(slug)
        library.upsertPlatform(cur.copy(savesPathOverride = path.ifBlank { null }))
    }

    // ── Selector de plataforma en la barra de la biblioteca ─────────────

    private val _selectedPlatformSlug = MutableStateFlow<String?>(null)
    val selectedPlatformSlug: StateFlow<String?> = _selectedPlatformSlug

    /** null = todas las plataformas. */
    fun selectPlatformBySlug(slug: String?) {
        _selectedPlatformSlug.value = slug
        _selectedPlatformId.value = null
        _roms.value = emptyList()
        loadRomsForSlug(slug)
    }

    // ── Detalle de juego ───────────────────────────────────────────────

    private val _selectedGame = MutableStateFlow<GameCard?>(null)
    val selectedGame: StateFlow<GameCard?> = _selectedGame

    fun openGame(card: GameCard) { _selectedGame.value = card }
    fun closeGame() { _selectedGame.value = null }

    // ── Saves: comprobar cambios (negociación sin ejecutar) ────────────

    private val _pendingReport = MutableStateFlow<DesktopSyncCoordinator.PendingSavesReport?>(null)
    val pendingReport: StateFlow<DesktopSyncCoordinator.PendingSavesReport?> = _pendingReport
    private val _scanningSaves = MutableStateFlow(false)
    val scanningSaves: StateFlow<Boolean> = _scanningSaves

    fun scanSaves() {
        if (_scanningSaves.value) return
        _scanningSaves.value = true
        scope.launch {
            try {
                val coordinator = DesktopSyncCoordinator(config, library, config.cacheDir)
                _pendingReport.value = coordinator.scanPendingSaves()
            } catch (e: Exception) {
                _syncStatus.value = "Error escaneando saves: ${e.message}"
            } finally {
                _scanningSaves.value = false
            }
        }
    }

    fun resolveConflict(romId: Int, fileName: String, resolution: String) {
        scope.launch {
            _syncing.value = true
            try {
                val coordinator = DesktopSyncCoordinator(config, library, config.cacheDir)
                val result = coordinator.runConflictResolution(romId, fileName, resolution)
                _syncStatus.value = result.message ?: if (result.isSuccess) "Conflicto resuelto" else result.error
                scanSaves()
            } catch (e: Exception) {
                _syncStatus.value = "Error resolviendo conflicto: ${e.message}"
            } finally {
                _syncing.value = false
            }
        }
    }

    // ── Sync automático cada X minutos ─────────────────────────────────

    private var autoSyncJob: Job? = null
    private val _autoSyncMinutes = MutableStateFlow(DesktopConfig.autoSyncMinutes)
    val autoSyncMinutes: StateFlow<Int> = _autoSyncMinutes

    fun setAutoSyncMinutes(minutes: Int) {
        _autoSyncMinutes.value = minutes
        DesktopConfig.autoSyncMinutes = minutes
        restartAutoSync()
    }

    private fun restartAutoSync() {
        autoSyncJob?.cancel()
        val minutes = _autoSyncMinutes.value
        if (minutes <= 0) return
        autoSyncJob = scope.launch {
            while (true) {
                kotlinx.coroutines.delay(minutes * 60_000L)
                if (!_syncing.value) syncSaves()
            }
    }
    }

    private var api: RomMApiService? = null

    init {
        downloadedIds.addAll(library.roms().map { it.romId })
        val rawHidden = config.hiddenPlatforms
        if (rawHidden.isNotBlank()) {
            _hiddenPlatforms.value = rawHidden.split(",").filter { it.isNotBlank() }.toSet()
        }
        if (config.autoSyncMinutes > 0) restartAutoSync()
        if (config.serverUrl.isNotBlank() && config.apiKey.isNotBlank()) {
            connect(config.serverUrl, config.apiKey, silent = true)
        }
    }

    fun navigate(s: Section) { _section.value = s }

    fun selectPlatform(id: Int?) {
        _selectedPlatformId.value = id
        _roms.value = emptyList()
        if (id != null) loadRoms(reset = true)
    }

    fun setSearch(q: String) { _search.value = q }

    fun setFilter(f: LibraryFilter) { _filter.value = f }

    /** Conecta (guardando config) y carga plataformas. */
    fun connect(serverUrl: String, apiKey: String, silent: Boolean = false) {
        config.serverUrl = serverUrl
        config.apiKey = apiKey
        val svc = NetworkModule.createApiService(serverUrl, apiKey)
        api = svc
        _loadingPlatforms.value = true
        scope.launch {
            try {
                val plats = svc.getPlatforms()
                _platforms.value = plats
                _connected.value = true
            } catch (e: Exception) {
                _connected.value = false
                if (!silent) _syncStatus.value = "Error de conexión: ${e.message}"
            } finally {
                _loadingPlatforms.value = false
            }
        }
    }

    /** Carga ROMs de la plataforma seleccionada con paginación completa. */
    fun loadRoms(reset: Boolean) {
        val svc = api ?: return
        val platformId = _selectedPlatformId.value ?: return
        scope.launch {
            _loadingRoms.value = true
            try {
                val all = mutableListOf<RomDto>()
                var offset = 0
                while (true) {
                    val resp = svc.getRoms(
                        mapOf(
                            "platform_ids" to platformId.toString(),
                            "limit" to "500",
                            "offset" to offset.toString(),
                        ),
                    )
                    all += resp.items
                    offset += resp.items.size
                    if (resp.items.size < 500) break
                }
                _roms.value = all
            } catch (e: Exception) {
                _syncStatus.value = "Error cargando ROMs: ${e.message}"
            } finally {
                _loadingRoms.value = false
            }
        }
    }

    /** Igual pero por slug (null = todas las plataformas visibles). */
    private fun loadRomsForSlug(slug: String?) {
        val svc = api ?: return
        scope.launch {
            _loadingRoms.value = true
            try {
                val all = mutableListOf<RomDto>()
                if (slug == null) {
                    for (p in visiblePlatforms()) {
                        var offset = 0
                        while (true) {
                            val resp = svc.getRoms(
                                mapOf(
                                    "platform_ids" to p.id.toString(),
                                    "limit" to "500",
                                    "offset" to offset.toString(),
                                ),
                            )
                            all += resp.items
                            offset += resp.items.size
                            if (resp.items.size < 500) break
                        }
                    }
                } else {
                    val platId = visiblePlatforms().firstOrNull { it.slug == slug }?.id
                    if (platId != null) {
                        var offset = 0
                        while (true) {
                            val resp = svc.getRoms(
                                mapOf(
                                    "platform_ids" to platId.toString(),
                                    "limit" to "500",
                                    "offset" to offset.toString(),
                                ),
                            )
                            all += resp.items
                            offset += resp.items.size
                            if (resp.items.size < 500) break
                        }
                    }
                }
                _roms.value = all
            } catch (e: Exception) {
                _syncStatus.value = "Error cargando ROMs: ${e.message}"
            } finally {
                _loadingRoms.value = false
            }
        }
    }

    /** Juegos agrupados por igdbId con filtro y búsqueda aplicados. */
    fun games(): List<GameCard> {
        val romsList = _roms.value
        val q = _search.value.trim().lowercase()
        return romsList
            .groupBy { it.igdbId ?: it.id }
            .map { (_, group) ->
                val rep = group.maxByOrNull { it.files.size } ?: group.first()
                GameCard(
                    rep = rep,
                    discCount = if (group.size > 1) group.size else 1,
                    groupRoms = group,
                    downloaded = group.all { it.id in downloadedIds },
                )
            }
            .filter { card ->
                val match = q.isEmpty() || card.rep.name.lowercase().contains(q)
                val f = when (_filter.value) {
                    LibraryFilter.ALL -> true
                    LibraryFilter.MISSING -> !card.downloaded
                    LibraryFilter.DOWNLOADED -> card.downloaded
                }
                match && f
            }
            .sortedBy { it.rep.name.lowercase() }
    }

    /** Encola la descarga del juego completo (todos los discos del grupo). */
    fun enqueue(card: GameCard) {
        val svc = api ?: return
        card.groupRoms.forEach { rom ->
            val task = DesktopTask(rom.id, rom.name, rom.platformSlug, "queued")
            _tasks.value = _tasks.value + task
            scope.launch {
                updateTask(rom.id) { it.copy(status = "running") }
                try {
                    val engine = DownloadEngine(
                        api = svc,
                        romsRoot = File(config.romsRoot),
                        cacheDir = config.cacheDir,
                        library = library,
                    )
                    val msg = engine.download(rom) { p ->
                        updateTask(rom.id) { t ->
                            t.copy(bytesRead = p.bytesRead, totalBytes = p.total, speedBps = p.speedBps)
                        }
                    }
                    downloadedIds.add(rom.id)
                    _downloadedVersion.value = _downloadedVersion.value + 1
                    updateTask(rom.id) { it.copy(status = "done", message = msg) }
                } catch (e: Exception) {
                    updateTask(rom.id) { it.copy(status = "error", message = e.message) }
                }
            }
        }
    }

    // ── Exportar metadata a ES-DE ──────────────────────────────────────

    private val _esdeStatus = MutableStateFlow<String?>(null)
    val esdeStatus: StateFlow<String?> = _esdeStatus
    private val _esdeRunning = MutableStateFlow(false)
    val esdeRunning: StateFlow<Boolean> = _esdeRunning

    fun exportEsde() {
        if (_esdeRunning.value) return
        _esdeRunning.value = true
        scope.launch {
            try {
                val result = DesktopEsdeExporter(config, library).export()
                _esdeStatus.value = result
            } catch (e: Exception) {
                _esdeStatus.value = "Error exportando: ${e.message}"
            } finally {
                _esdeRunning.value = false
            }
        }
    }

    // ── Actualizaciones (GitHub releases) ──────────────────────────────

    /** Estado del flujo de actualización para la UI de Ajustes. */
    data class UpdateUiState(
        val checking: Boolean = false,
        val info: DesktopUpdater.UpdateInfo? = null,
        val downloading: Boolean = false,
        val downloadedBytes: Long = 0,
        val totalBytes: Long = -1,
        val installed: Boolean = false,
        val restartAvailable: Boolean = false,
        val message: String? = null,
    )

    private val _updateState = MutableStateFlow(UpdateUiState())
    val updateState: StateFlow<UpdateUiState> = _updateState

    fun checkUpdate() {
        val s = _updateState.value
        if (s.checking || s.downloading) return
        _updateState.value = UpdateUiState(checking = true)
        scope.launch {
            try {
                val info = withContext(Dispatchers.IO) { DesktopUpdater.check() }
                _updateState.value = UpdateUiState(
                    info = info,
                    message = when {
                        info == null -> "No se pudo comprobar (sin respuesta de GitHub)"
                        info.available -> null // la UI muestra el botón de instalar
                        else -> "Estás en la última versión (v${info.currentVersion})"
                    },
                )
            } catch (e: Exception) {
                _updateState.value = UpdateUiState(message = "Error comprobando: ${e.message}")
            }
        }
    }

    /** Descarga el AppImage nuevo, lo instala in-place y ofrece reiniciar. */
    fun installUpdate() {
        val s = _updateState.value
        val info = s.info
        val url = info?.downloadUrl
        if (info == null || url == null || s.downloading) return
        _updateState.value = s.copy(downloading = true, downloadedBytes = 0, totalBytes = -1, message = null)
        scope.launch {
            try {
                val msg = withContext(Dispatchers.IO) {
                    DesktopUpdater.downloadAndInstall(info, url) { read, total ->
                        _updateState.value = _updateState.value.copy(downloadedBytes = read, totalBytes = total)
                    }
                }
                val ok = msg.startsWith("Actualizado")
                _updateState.value = _updateState.value.copy(
                    downloading = false,
                    installed = ok,
                    restartAvailable = ok && DesktopUpdater.currentAppImage() != null,
                    message = msg,
                )
            } catch (e: Exception) {
                _updateState.value = _updateState.value.copy(downloading = false, message = "Error instalando: ${e.message}")
            }
        }
    }

    /** Relanza la app (tras instalar una actualización del AppImage). */
    fun restartApp() {
        DesktopUpdater.relaunch()
    }

    private fun updateTask(romId: Int, transform: (DesktopTask) -> DesktopTask) {
        _tasks.value = _tasks.value.map { if (it.romId == romId) transform(it) else it }
    }

    /** Limpia las tareas terminadas de la cola. */
    fun clearFinished() {
        _tasks.value = _tasks.value.filter { it.status == "running" || it.status == "queued" }
    }

    /** Sincroniza saves con el servidor. */
    fun syncSaves() {
        if (_syncing.value) return
        _syncing.value = true
        _syncStatus.value = "Sincronizando saves…"
        scope.launch {
            try {
                val coordinator = DesktopSyncCoordinator(
                    config = config,
                    library = library,
                    cacheDir = config.cacheDir,
                )
                val result = coordinator.runSync()
                _syncStatus.value = buildString {
                    if (result.error != null) append(result.error)
                    else append(result.message ?: "Sync completado")
                    if (result.uploaded > 0) append(" · ${result.uploaded} subidos")
                    if (result.downloaded > 0) append(" · ${result.downloaded} descargados")
                    if (result.conflicts > 0) append(" · ${result.conflicts} conflictos")
                }
            } catch (e: Exception) {
                _syncStatus.value = "Error: ${e.message}"
            } finally {
                _syncing.value = false
            }
    }
    }
}
