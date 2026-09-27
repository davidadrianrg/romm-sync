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

    private var api: RomMApiService? = null

    init {
        downloadedIds.addAll(library.roms().map { it.romId })
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
