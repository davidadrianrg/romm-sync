package es.davidrg.rommsync.desktop

import es.davidrg.rommsync.core.remote.NetworkModule
import es.davidrg.rommsync.core.remote.RomMApiService
import es.davidrg.rommsync.core.remote.dto.PlatformDto
import es.davidrg.rommsync.core.remote.dto.RomDto
import es.davidrg.rommsync.core.sync.ConflictPolicy
import es.davidrg.rommsync.core.sync.SaveBackupManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Sección activa de la navegación desktop. */
enum class Section { PLATFORMS, LIBRARY, DOWNLOADS, SAVES, SETTINGS }

/** Filtro de la biblioteca. */
enum class LibraryFilter { ALL, MISSING, DOWNLOADED }

/** Criterios de ordenación de la biblioteca. */
enum class LibrarySort(val label: String) {
    NAME_ASC("Nombre A-Z"),
    NAME_DESC("Nombre Z-A"),
    SIZE_DESC("Tamaño ↓"),
    SIZE_ASC("Tamaño ↑"),
    YEAR_DESC("Año ↓"),
    YEAR_ASC("Año ↑"),
    RATING_DESC("Rating ↓"),
    RATING_ASC("Rating ↑"),
}

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
) {
    val active: Boolean get() = status == "queued" || status == "running"
}

/** Juego agrupado (multi-disc fusionados por igdbId, igual que Android). */
data class GameCard(
    val rep: RomDto,
    val discCount: Int,
    val groupRoms: List<RomDto>,
    val downloaded: Boolean,
)

/** Última sincronización de saves (persistida entre arranques). */
data class LastSyncInfo(val at: Long, val summary: String) {
    val exists: Boolean get() = at > 0L
}

/** Stats locales por plataforma: nº de ROMs descargados y bytes en disco. */
data class PlatformLocalStat(val romCount: Int, val totalBytes: Long)

/**
 * Estado central de la app desktop: conexión, plataformas, ROMs por
 * plataforma (paginados), agrupado multi-disc, cola de descargas con
 * progreso/cancelación/reintento, sincronización de saves y stats locales.
 */
class DesktopAppState(private val scope: CoroutineScope) {

    val config = DesktopConfig
    val library = DesktopLibrary(File(DesktopConfig.configDir, "library.properties"))

    /** Versionado local de saves (restaurables desde el detalle de juego). */
    val saveBackups = SaveBackupManager(File(DesktopConfig.configDir, "save-backups"))

    /** Fingerprints sellados por ROM (atajo del ciclo de sync). */
    private val hashStore = DesktopHashStore(File(DesktopConfig.configDir, "sync-hashes.properties"))

    private val _section = MutableStateFlow(Section.PLATFORMS)
    val section: StateFlow<Section> = _section

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private val _platforms = MutableStateFlow<List<PlatformDto>>(emptyList())
    val platforms: StateFlow<List<PlatformDto>> = _platforms

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

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing

    /** Mensajes efímeros estilo snackbar (acciones del usuario). */
    private val _snackbar = MutableStateFlow<String?>(null)
    val snackbar: StateFlow<String?> = _snackbar

    /** romId -> ruta local registrada en library.properties tras descargar. */
    private val downloadedIds: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    /** Se incrementa al completar descargas para recomponer la biblioteca. */
    private val _downloadedVersion = MutableStateFlow(0)
    val downloadedVersion: StateFlow<Int> = _downloadedVersion

    private var api: RomMApiService? = null

    // init al FINAL de la clase: las propiedades declaradas más abajo
    // (_hiddenPlatforms, _lastSync, _autoSyncMinutes…) deben estar ya
    // inicializadas cuando el bloque corra.

    fun navigate(s: Section) { _section.value = s }

    fun showSnackbar(message: String) { _snackbar.value = message }

    fun consumeSnackbar() { _snackbar.value = null }

    // ── Conexión / carga de datos ───────────────────────────────────────

    /** Conecta (guardando config) y carga plataformas. */
    fun connect(serverUrl: String, apiKey: String, silent: Boolean = false) {
        config.serverUrl = serverUrl
        config.apiKey = apiKey
        val svc = NetworkModule.createApiService(serverUrl, apiKey)
        api = svc
        refreshPlatforms(silent)
    }

    private fun refreshPlatforms(silent: Boolean = false) {
        val svc = api ?: return
        _loadingPlatforms.value = true
        scope.launch {
            try {
                val plats = svc.getPlatforms()
                _platforms.value = plats
                _connected.value = true
            } catch (e: Exception) {
                _connected.value = false
                if (!silent) showSnackbar("Error de conexión: ${e.message}")
            } finally {
                _loadingPlatforms.value = false
            }
        }
    }

    /** Recarga plataformas + ROMs de la vista actual (botón refrescar / F5). */
    fun refreshCurrentView() {
        if (_connected.value || api != null) {
            refreshPlatforms(silent = true)
            when (_section.value) {
                Section.LIBRARY -> refreshLibrary()
                else -> Unit
            }
        }
    }

    /** Recarga los ROMs de la plataforma seleccionada (o todas las visibles). */
    fun refreshLibrary() {
        _roms.value = emptyList()
        loadRomsForSlug(_selectedPlatformSlug.value)
    }

    /** Igual pero por slug (null = todas las plataformas visibles). */
    private fun loadRomsForSlug(slug: String?) {
        val svc = api ?: return
        scope.launch {
            _loadingRoms.value = true
            try {
                val all = mutableListOf<RomDto>()
                val targets = if (slug == null) {
                    visiblePlatforms()
                } else {
                    visiblePlatforms().filter { it.slug == slug }
                }
                for (p in targets) {
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
                _roms.value = all
            } catch (e: Exception) {
                showSnackbar("Error cargando ROMs: ${e.message}")
            } finally {
                _loadingRoms.value = false
            }
        }
    }

    fun setSearch(q: String) { _search.value = q }

    fun setFilter(f: LibraryFilter) { _filter.value = f }

    // ── Orden y filtro por región de la biblioteca ──────────────────────

    private val _sort = MutableStateFlow(LibrarySort.NAME_ASC)
    val sort: StateFlow<LibrarySort> = _sort

    fun setSort(s: LibrarySort) { _sort.value = s }

    /** null = todas las regiones. */
    private val _regionFilter = MutableStateFlow<String?>(null)
    val regionFilter: StateFlow<String?> = _regionFilter

    fun setRegionFilter(r: String?) { _regionFilter.value = r }

    /** Regiones distintas presentes en los ROMs cargados (para el selector). */
    fun availableRegions(): List<String> =
        _roms.value.flatMap { it.regions }.distinct().sorted()

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

    // ── Config por plataforma: carpeta ROMs, saves y emulador ──────────

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

    /** Fija el emulador cuyos saves se sincronizan (vacío = default slug). */
    fun setPlatformEmulator(slug: String, emulatorId: String?) {
        val cur = library.platform(slug)
        library.upsertPlatform(cur.copy(emulatorId = emulatorId?.takeIf { it.isNotBlank() }))
    }

    /** Stats locales (ROMs en disco por plataforma), calculadas en IO. */
    private val _localStats = MutableStateFlow<Map<String, PlatformLocalStat>>(emptyMap())
    val localStats: StateFlow<Map<String, PlatformLocalStat>> = _localStats

    fun refreshLocalStats() {
        scope.launch {
            val stats = withContext(Dispatchers.IO) { computeLocalStats() }
            _localStats.value = stats
        }
    }

    private fun computeLocalStats(): Map<String, PlatformLocalStat> {
        val roms = library.roms()
        val bySlug = roms.groupBy { it.platformSlug }
        val seenPaths = mutableSetOf<String>()
        val out = mutableMapOf<String, PlatformLocalStat>()
        for ((slug, entries) in bySlug) {
            var bytes = 0L
            for (entry in entries) {
                val p = entry.localPath ?: continue
                if (p in seenPaths) continue // dir compartida: contar una vez
                seenPaths += p
                val f = File(p)
                bytes += when {
                    f.isFile -> f.length()
                    f.isDirectory -> dirSizeCapped(f)
                    else -> 0L
                }
            }
            out[slug] = PlatformLocalStat(entries.size, bytes)
        }
        return out
    }

    /** Suma el tamaño de un árbol con tope de ficheros (aprox. suficiente). */
    private fun dirSizeCapped(dir: File): Long {
        var total = 0L
        var count = 0
        val stack = ArrayDeque<File>()
        stack.addLast(dir)
        while (stack.isNotEmpty() && count < 2000) {
            val d = stack.removeLast()
            val children = d.listFiles() ?: continue
            for (c in children) {
                if (c.isFile) { total += c.length(); count++ }
                else if (c.isDirectory) stack.addLast(c)
            }
        }
        return total
    }

    // ── Selector de plataforma en la barra de la biblioteca ─────────────

    private val _selectedPlatformSlug = MutableStateFlow<String?>(null)
    val selectedPlatformSlug: StateFlow<String?> = _selectedPlatformSlug

    /** null = todas las plataformas. */
    fun selectPlatformBySlug(slug: String?) {
        _selectedPlatformSlug.value = slug
        _roms.value = emptyList()
        loadRomsForSlug(slug)
    }

    // ── Detalle de juego ───────────────────────────────────────────────

    private val _selectedGame = MutableStateFlow<GameCard?>(null)
    val selectedGame: StateFlow<GameCard?> = _selectedGame

    fun openGame(card: GameCard) { _selectedGame.value = card }
    fun closeGame() { _selectedGame.value = null }

    // ── Biblioteca: agrupado, filtro y búsqueda ─────────────────────────

    /** Una tarjeta por entrada de la API con filtro, región, búsqueda y orden.
     *  Los multidisco reales vienen ya agrupados (multi/hasMultipleFiles);
     *  NO agrupar por igdbId (Golden Sun 1 y 2 comparten igdb_id y se fusionaban). */
    fun games(): List<GameCard> {
        val romsList = _roms.value
        val q = _search.value.trim().lowercase()
        val region = _regionFilter.value
        val rep = romsList
            .map { rom ->
                val isMulti = rom.multi || rom.hasMultipleFiles
                GameCard(
                    rep = rom,
                    discCount = if (isMulti) rom.files.size.coerceAtLeast(2) else 1,
                    groupRoms = listOf(rom),
                    downloaded = rom.id in downloadedIds,
                )
            }
            .filter { card ->
                val match = q.isEmpty() || card.rep.name.lowercase().contains(q)
                val regionMatch = region == null || card.rep.regions.any { it.equals(region, ignoreCase = true) }
                val f = when (_filter.value) {
                    LibraryFilter.ALL -> true
                    LibraryFilter.MISSING -> !card.downloaded
                    LibraryFilter.DOWNLOADED -> card.downloaded
                }
                match && regionMatch && f
            }
        return sorted(rep)
    }

    private fun sorted(cards: List<GameCard>): List<GameCard> = when (_sort.value) {
        LibrarySort.NAME_ASC -> cards.sortedBy { it.rep.name.lowercase() }
        LibrarySort.NAME_DESC -> cards.sortedByDescending { it.rep.name.lowercase() }
        LibrarySort.SIZE_DESC -> cards.sortedByDescending { it.rep.fileSizeBytes }
        LibrarySort.SIZE_ASC -> cards.sortedBy { it.rep.fileSizeBytes }
        LibrarySort.YEAR_DESC -> cards.sortedByDescending { releaseYear(it.rep) ?: 0L }
        LibrarySort.YEAR_ASC -> cards.sortedBy { releaseYear(it.rep) ?: Long.MAX_VALUE }
        LibrarySort.RATING_DESC -> cards.sortedByDescending { rating(it.rep) }
        LibrarySort.RATING_ASC -> cards.sortedBy { rating(it.rep) }
    }

    private fun releaseYear(rep: RomDto): Long? =
        rep.igdbMetadata?.firstReleaseDate?.takeIf { it > 0 }

    private fun rating(rep: RomDto): Double =
        rep.igdbMetadata?.totalRating?.toDoubleOrNull() ?: -1.0

    /** Juegos no descargados de la vista actual, SIN aplicar búsqueda. */
    fun missingGames(): List<GameCard> {
        val region = _regionFilter.value
        return _roms.value
            .map { rom ->
                val isMulti = rom.multi || rom.hasMultipleFiles
                GameCard(
                    rep = rom,
                    discCount = if (isMulti) rom.files.size.coerceAtLeast(2) else 1,
                    groupRoms = listOf(rom),
                    downloaded = rom.id in downloadedIds,
                )
            }
            .filter { !it.downloaded && (region == null || it.rep.regions.any { r -> r.equals(region, ignoreCase = true) }) }
            .sortedBy { it.rep.name.lowercase() }
    }

    // ── Cola de descargas ───────────────────────────────────────────────

    /** Jobs activos por romId (para cancelar). */
    private val taskJobs = ConcurrentHashMap<Int, Job>()

    /** Tope de descargas simultáneas (1-5), como en Android. */
    @Volatile
    private var downloadGate = Semaphore(DesktopConfig.maxConcurrentDownloads)

    private val _maxConcurrentDownloads = MutableStateFlow(DesktopConfig.maxConcurrentDownloads)
    val maxConcurrentDownloads: StateFlow<Int> = _maxConcurrentDownloads

    fun setMaxConcurrentDownloads(n: Int) {
        val v = n.coerceIn(1, 5)
        DesktopConfig.maxConcurrentDownloads = v
        _maxConcurrentDownloads.value = v
        downloadGate = Semaphore(v) // afecta a las descargas que aún no empezaron
    }

    /** Encola la descarga del juego completo (todos los discos del grupo). */
    fun enqueue(card: GameCard) {
        card.groupRoms.forEach { rom -> enqueueRom(rom, notify = false) }
        showSnackbar("Descargando ${card.rep.name}")
    }

    /** Descarga por lotes: encola todos los juegos faltantes de la vista. */
    fun enqueueMissing() {
        val missing = missingGames()
        if (missing.isEmpty()) return
        missing.forEach { card -> card.groupRoms.forEach { rom -> enqueueRom(rom, notify = false) } }
        showSnackbar("Encoladas ${missing.size} descargas")
    }

    /**
     * Encola un ROM individual. Guard anti-duplicados: si ya está en cola o
     * ejecutándose se ignora (antes esto duplicaba keys en la LazyColumn y
     * la crashaba).
     */
    private fun enqueueRom(rom: RomDto, notify: Boolean = true) {
        val svc = api ?: return
        if (_tasks.value.any { it.romId == rom.id && it.active }) return
        _tasks.value = _tasks.value + DesktopTask(rom.id, rom.name, rom.platformSlug, "queued")
        if (notify) showSnackbar("Descargando ${rom.name}")
        val job = scope.launch {
            downloadGate.withPermit {
                if (!_tasks.value.any { it.romId == rom.id && it.active }) return@withPermit
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
                    if (msg.startsWith("Error")) {
                        updateTask(rom.id) { it.copy(status = "error", message = msg) }
                        DesktopNotifier.notify("RomM Sync", "Error descargando ${rom.name}: $msg")
                    } else {
                        downloadedIds.add(rom.id)
                        _downloadedVersion.value = _downloadedVersion.value + 1
                        updateTask(rom.id) { it.copy(status = "done", message = msg) }
                        refreshLocalStats()
                        DesktopNotifier.notify("Descarga completada", rom.name)
                    }
                } catch (e: CancellationException) {
                    throw e // cancelada por el usuario; la tarea ya se quitó de la lista
                } catch (e: Exception) {
                    updateTask(rom.id) { it.copy(status = "error", message = e.message ?: e.javaClass.simpleName) }
                }
            }
        }
        taskJobs[rom.id] = job
        job.invokeOnCompletion { taskJobs.remove(rom.id, job) }
    }

    /** Cancela la descarga activa/en cola de un ROM y la quita de la lista. */
    fun cancelDownload(romId: Int) {
        taskJobs.remove(romId)?.cancel()
        _tasks.value = _tasks.value.filterNot { it.romId == romId && it.active }
    }

    /** Cancela todas las descargas activas y las quita de la lista. */
    fun cancelAll() {
        val active = _tasks.value.filter { it.active }
        active.forEach { taskJobs.remove(it.romId)?.cancel() }
        _tasks.value = _tasks.value.filterNot { it.active }
        if (active.isNotEmpty()) showSnackbar("Canceladas ${active.size} descargas")
    }

    /** Reintenta una descarga fallida (reanuda desde el parcial si existe). */
    fun retryDownload(romId: Int) {
        val rom = _roms.value.firstOrNull { it.id == romId }
        if (rom == null) {
            showSnackbar("No se encontró el ROM en la biblioteca actual")
            return
        }
        _tasks.value = _tasks.value.filterNot { it.romId == romId }
        enqueueRom(rom)
    }

    private fun updateTask(romId: Int, transform: (DesktopTask) -> DesktopTask) {
        _tasks.value = _tasks.value.map { if (it.romId == romId) transform(it) else it }
    }

    /** Limpia las tareas terminadas de la cola. */
    fun clearFinished() {
        _tasks.value = _tasks.value.filter { it.active }
    }

    // ── Eliminar descarga local ─────────────────────────────────────────

    /**
     * Borra del disco los ficheros de un juego descargado y lo quita del
     * registro local. Para ROMs extraídos de zip en la carpeta compartida de
     * la plataforma se niega (borraría otros juegos).
     */
    fun deleteDownload(card: GameCard) {
        scope.launch {
            var sharedDir = false
            var deletedAny = false
            withContext(Dispatchers.IO) {
                for (rom in card.groupRoms) {
                    val entry = library.rom(rom.id) ?: continue
                    val path = entry.localPath ?: continue
                    val f = File(path)
                    when {
                        f.isFile -> {
                            if (f.delete()) deletedAny = true
                        }
                        f.isDirectory -> {
                            // Solo borramos si la carpeta es exclusiva del juego
                            // (subcarpeta con su nombre), nunca la carpeta de la
                            // plataforma completa.
                            val platformDir = es.davidrg.rommsync.core.download.PathMapper
                                .getPlatformDir(config.romsRoot, rom.platformSlug ?: "unknown")
                            if (f.canonicalPath == platformDir.canonicalPath) {
                                sharedDir = true
                            } else {
                                f.deleteRecursively()
                                deletedAny = true
                            }
                        }
                    }
                    library.removeRom(rom.id)
                    downloadedIds.remove(rom.id)
                }
            }
            _downloadedVersion.value = _downloadedVersion.value + 1
            refreshLocalStats()
            showSnackbar(
                when {
                    sharedDir -> "Eliminado del registro (la carpeta de la plataforma es compartida: borra los ficheros a mano)"
                    deletedAny -> "Eliminado ${card.rep.name}"
                    else -> "Eliminado ${card.rep.name} del registro (ficheros no encontrados)"
                },
            )
        }
    }

    // ── Config de sync por juego ────────────────────────────────────────

    /** Excluye (o re-incluye) un juego de la sincronización de saves. */
    fun setRomExcluded(romId: Int, excluded: Boolean) {
        val entry = library.rom(romId) ?: return
        library.upsertRom(entry.copy(excludedFromSync = excluded))
    }

    /** Ruta de saves personalizada para un juego (null = hereda plataforma). */
    fun setRomSavesPath(romId: Int, path: String?) {
        val entry = library.rom(romId) ?: return
        library.upsertRom(entry.copy(savesPathOverride = path?.takeIf { it.isNotBlank() }))
    }

    /** Entrada local de un ROM (descargado) o null. */
    fun romEntry(romId: Int) = library.rom(romId)

    // ── Escanear biblioteca local (marcar ROMs ya presentes en disco) ────

    sealed class ScanState {
        data object Idle : ScanState()
        data class Running(val status: String) : ScanState()
        data class Done(val summary: String) : ScanState()
    }

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState

    /**
     * Recorre las carpetas de plataforma del root comparando los ficheros en
     * disco con los ROMs del servidor: los que ya existen se marcan como
     * descargados (equivalente al "Escanear biblioteca" de Android).
     */
    fun scanLibrary() {
        val svc = api ?: run { showSnackbar("Conecta el servidor primero"); return }
        if (_scanState.value is ScanState.Running) return
        scope.launch {
            _scanState.value = ScanState.Running("Preparando escaneo…")
            try {
                var detected = 0
                var checked = 0
                for (p in visiblePlatforms()) {
                    _scanState.value = ScanState.Running("Escaneando: ${p.displayName ?: p.name}…")
                    val romsOfPlatform = withContext(Dispatchers.IO) { fetchAllRoms(svc, p.id) }
                    checked += romsOfPlatform.size

                    val folder = library.platform(p.slug).romsFolderOverride ?: p.slug
                    val dir = File(config.romsRoot, folder)
                    val diskFiles = mutableSetOf<String>()
                    if (dir.isDirectory) {
                        withContext(Dispatchers.IO) {
                            dir.walkTopDown().take(4000).forEach { f -> if (f.isFile) diskFiles.add(f.name.lowercase()) }
                        }
                    }

                    for (rom in romsOfPlatform) {
                        val names = if (rom.files.isNotEmpty()) rom.files.map { it.filename } else listOf(rom.fileName)
                        val matched = names.firstOrNull { it.lowercase() in diskFiles }
                        if (matched != null && library.rom(rom.id) == null) {
                            withContext(Dispatchers.IO) {
                                library.upsertRom(
                                    DesktopLibrary.RomEntry(
                                        romId = rom.id,
                                        name = rom.name,
                                        fileName = rom.fileName,
                                        platformSlug = p.slug,
                                        localPath = File(dir, matched).absolutePath,
                                    ),
                                )
                            }
                            downloadedIds.add(rom.id)
                            detected++
                        }
                    }
                }
                _downloadedVersion.value = _downloadedVersion.value + 1
                refreshLocalStats()
                _scanState.value = ScanState.Done(
                    if (detected > 0) "$detected juegos detectados de $checked comprobados"
                    else "No se detectaron juegos nuevos ($checked comprobados)",
                )
            } catch (e: Exception) {
                _scanState.value = ScanState.Idle
                showSnackbar("Error escaneando: ${e.message}")
            }
        }
    }

    private suspend fun fetchAllRoms(svc: RomMApiService, platformId: Int): List<RomDto> =
        withContext(Dispatchers.IO) {
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
            all
        }

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
                val coordinator = makeSyncCoordinator()
                _pendingReport.value = coordinator.scanPendingSaves()
            } catch (e: Exception) {
                showSnackbar("Error escaneando saves: ${e.message}")
            } finally {
                _scanningSaves.value = false
            }
        }
    }

    fun resolveConflict(romId: Int, fileName: String, resolution: String) {
        scope.launch {
            _syncing.value = true
            try {
                val coordinator = makeSyncCoordinator()
                val result = coordinator.runConflictResolution(romId, fileName, resolution)
                showSnackbar(result.message ?: if (result.isSuccess) "Conflicto resuelto" else result.error ?: "Error")
                scanSaves()
            } catch (e: Exception) {
                showSnackbar("Error resolviendo conflicto: ${e.message}")
            } finally {
                _syncing.value = false
            }
        }
    }

    private fun makeSyncCoordinator() = DesktopSyncCoordinator(
        config = config,
        library = library,
        cacheDir = config.cacheDir,
        backupManager = saveBackups,
        hashStore = hashStore,
        conflictPolicy = ConflictPolicy.fromId(config.conflictPolicy),
    )

    // ── Política de conflictos + historial de copias de saves ───────────

    private val _conflictPolicy = MutableStateFlow(ConflictPolicy.fromId(DesktopConfig.conflictPolicy))
    val conflictPolicy: StateFlow<ConflictPolicy> = _conflictPolicy

    fun setConflictPolicy(policy: ConflictPolicy) {
        _conflictPolicy.value = policy
        DesktopConfig.conflictPolicy = policy.id
    }

    /** Versiones de respaldo de un ROM (más recientes primero). */
    fun saveBackupVersions(romId: Int): List<SaveBackupManager.BackupVersion> =
        saveBackups.versions(romId)

    /** Restaura una copia de seguridad de save sobre su ubicación original. */
    fun restoreSaveBackup(version: SaveBackupManager.BackupVersion) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                makeSyncCoordinator().restoreBackup(version.romId, version.fileName, version.backupFile)
            }
            showSnackbar(if (ok) "Copia restaurada: ${version.fileName}" else "No se pudo restaurar ${version.fileName}")
            if (ok) scanSaves()
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

    // ── Sincronización de saves ─────────────────────────────────────────

    private val _lastSync = MutableStateFlow(LastSyncInfo(0L, ""))
    val lastSync: StateFlow<LastSyncInfo> = _lastSync

    /** Estado resumido del último sync (para la tarjeta de estado). */
    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _syncStatus

    private val _lastFailed = MutableStateFlow<List<FailedOpInfo>>(emptyList())
    val lastFailed: StateFlow<List<FailedOpInfo>> = _lastFailed

    /** Sincroniza saves con el servidor. */
    fun syncSaves() {
        if (_syncing.value) return
        _syncing.value = true
        _syncStatus.value = "Sincronizando saves…"
        scope.launch {
            try {
                val coordinator = makeSyncCoordinator()
                val result = coordinator.runSync()
                val summary = buildString {
                    if (result.error != null) append(result.error)
                    else append(result.message ?: "Sync completado")
                    if (result.uploaded > 0) append(" · ${result.uploaded} subidos")
                    if (result.downloaded > 0) append(" · ${result.downloaded} descargados")
                    if (result.autoResolvedConflicts > 0) append(" · ${result.autoResolvedConflicts} conflictos auto-resueltos")
                    if (result.conflicts > 0) append(" · ${result.conflicts} conflictos")
                }
                _syncStatus.value = summary
                _lastFailed.value = result.failedDetails
                config.lastSyncAt = System.currentTimeMillis()
                config.lastSyncSummary = summary
                _lastSync.value = LastSyncInfo(config.lastSyncAt, summary)
                if (result.conflicts > 0) {
                    DesktopNotifier.notify("RomM Sync", "Sync con ${result.conflicts} conflicto(s) pendiente(s)")
                } else if (result.failedDetails.isNotEmpty()) {
                    DesktopNotifier.notify("RomM Sync", "El sync terminó con ${result.failedDetails.size} fallo(s)")
                }
                // Refrescar el informe de pendientes tras el ciclo.
                scanSaves()
            } catch (e: Exception) {
                _syncStatus.value = "Error: ${e.message}"
            } finally {
                _syncing.value = false
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
                val result = withContext(Dispatchers.IO) {
                    DesktopEsdeExporter(config.esdeDataDir, config.romsRoot, library).export()
                }
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
                        info.available && info.latestVersion == config.skippedVersion ->
                            "Versión v${info.latestVersion} omitida — «Volver a comprobar» para verla de nuevo"
                        info.available -> null // la UI muestra el botón de instalar
                        else -> "Estás en la última versión (v${info.currentVersion})"
                    },
                )
            } catch (e: Exception) {
                _updateState.value = UpdateUiState(message = "Error comprobando: ${e.message}")
            }
        }
    }

    /** Omite la versión disponible (no volverá a ofrecerse hasta la siguiente). */
    fun skipUpdateVersion() {
        val info = _updateState.value.info ?: return
        config.skippedVersion = info.latestVersion
        _updateState.value = UpdateUiState(message = "Omitida v${info.latestVersion} hasta la próxima versión")
    }

    /** Limpia la versión omitida y vuelve a comprobar. */
    fun recheckSkippedUpdate() {
        config.skippedVersion = ""
        checkUpdate()
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

    init {
        downloadedIds.addAll(library.roms().map { it.romId })
        val rawHidden = config.hiddenPlatforms
        if (rawHidden.isNotBlank()) {
            _hiddenPlatforms.value = rawHidden.split(",").filter { it.isNotBlank() }.toSet()
        }
        if (config.lastSyncAt > 0L) {
            _lastSync.value = LastSyncInfo(config.lastSyncAt, config.lastSyncSummary)
        }
        refreshLocalStats()
        if (config.autoSyncMinutes > 0) restartAutoSync()
        if (config.serverUrl.isNotBlank() && config.apiKey.isNotBlank()) {
            connect(config.serverUrl, config.apiKey, silent = true)
        }
    }
}
