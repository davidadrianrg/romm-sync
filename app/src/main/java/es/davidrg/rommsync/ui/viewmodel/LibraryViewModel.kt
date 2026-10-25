package es.davidrg.rommsync.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.davidrg.rommsync.core.i18n.tr
import es.davidrg.rommsync.core.library.DiscEntry
import es.davidrg.rommsync.core.library.MultiDiscGrouper
import es.davidrg.rommsync.data.repository.RomRepository
import es.davidrg.rommsync.domain.model.ApiResult
import es.davidrg.rommsync.domain.model.DownloadStatus
import es.davidrg.rommsync.domain.model.ErrorKind
import es.davidrg.rommsync.domain.model.Rom
import es.davidrg.rommsync.domain.model.RomWithStatus
import es.davidrg.rommsync.download.DownloadManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val romRepository: RomRepository,
    private val downloadManager: DownloadManager? = null,
    private val aspectRatioCalculator: es.davidrg.rommsync.util.CoverAspectRatioCalculator? = null,
) : ViewModel() {

    /** Plataformas cuya medición de aspect ratio ya se ha lanzado en esta sesión. */
    private val measuringPlatforms = mutableSetOf<Int>()

    /**
     * Mide y persiste el aspect ratio real de los covers de una plataforma si
     * aún no se ha calculado. Reutiliza las URLs de cover ya cargadas en la
     * rejilla, así que no genera peticiones extra de listado.
     *
     * @param platformId plataforma a medir.
     * @param alreadyMeasured true si la plataforma ya tiene un ratio cacheado.
     * @param coverUrls URLs de cover disponibles (de los ROMs ya cargados).
     */
    fun ensureAspectRatioMeasured(
        platformId: Int,
        alreadyMeasured: Boolean,
        coverUrls: List<String>,
    ) {
        val calculator = aspectRatioCalculator ?: return
        if (alreadyMeasured) return
        if (coverUrls.isEmpty()) return
        // Evita relanzar la medición mientras está en curso.
        if (!measuringPlatforms.add(platformId)) return

        viewModelScope.launch {
            val ratio = calculator.measure(coverUrls)
            if (ratio != null) {
                romRepository.updatePlatformMeasuredAspectRatio(platformId, ratio)
            }
            measuringPlatforms.remove(platformId)
        }
    }

    private val _selectedPlatformId = MutableStateFlow<Int?>(null)
    val selectedPlatformId = _selectedPlatformId.asStateFlow()

    private val _roms = MutableStateFlow<List<Rom>>(emptyList())
    val roms = _roms.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    /** True when actively loading the next page (not the first). */
    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    /** False when we've fetched all pages for this platform/search. */
    private val _hasMore = MutableStateFlow(true)
    val hasMore = _hasMore.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    /** True mientras se mueve un ROM entre almacenamientos (bloquea la UI). */
    private val _isMovingRom = MutableStateFlow(false)
    val isMovingRom: StateFlow<Boolean> = _isMovingRom.asStateFlow()

    /** Progreso del move en curso: nombre del juego a (copiados, total) bytes. */
    private val _movingProgress = MutableStateFlow<Pair<String, Pair<Long, Long>>?>(null)
    val movingProgress: StateFlow<Pair<String, Pair<Long, Long>>?> = _movingProgress.asStateFlow()

    /** Current server-side search term (null = no search, browse all). */
    private var currentSearch: String? = null

    companion object {
        private const val PAGE_SIZE = 50
    }

    /** One-shot events for UI feedback (snackbars). */
    private val _events = MutableSharedFlow<LibraryEvent>(extraBufferCapacity = 5)
    val events: SharedFlow<LibraryEvent> = _events.asSharedFlow()

    /** Observa la configuración de sync (ruta/exclusión) de un juego descargado. */
    fun observeRomSyncConfig(romId: Int) = romRepository.observeDownloadedRom(romId)

    /** Fija (o limpia con null) el override de ruta de saves de un juego concreto. */
    fun setRomSavesPathOverride(romId: Int, path: String?) {
        viewModelScope.launch {
            romRepository.updateRomSavesPathOverride(romId, path)
        }
    }

    /** Excluye o incluye un juego en la sincronización de partidas. */
    fun setRomExcludedFromSync(romId: Int, excluded: Boolean) {
        viewModelScope.launch {
            romRepository.updateRomExcludedFromSync(romId, excluded)
        }
    }

    val downloadedIds: StateFlow<Set<Int>> = romRepository.getDownloadedRomIds()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet(),
        )

    /**
     * Live set of ROM IDs currently downloading (ENQUEUED or RUNNING).
     * Driven by WorkManager observeDownloads() — auto-updates when downloads
     * complete or fail, so the card spinner resolves to a checkmark.
     */
    private val activeDownloads: StateFlow<Set<Int>> =
        (downloadManager?.observeDownloads() ?: kotlinx.coroutines.flow.flowOf(emptyList<es.davidrg.rommsync.domain.model.DownloadTask>()))
            .map { tasks ->
                tasks.filter { it.isRunning || (!it.isCompleted && !it.isFailed) }
                    .map { it.romId }
                    .toSet()
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptySet(),
            )

    // ── Orden y filtro por región (declarados ANTES de romsWithStatus,
    // que los referencia como fuentes del combine) ─────────────────────

    private val _sort = MutableStateFlow(RomSort.NAME_ASC)
    val sort: StateFlow<RomSort> = _sort

    fun setSort(s: RomSort) { _sort.value = s }

    private val _regionFilter = MutableStateFlow<String?>(null)
    val regionFilter: StateFlow<String?> = _regionFilter

    fun setRegionFilter(r: String?) { _regionFilter.value = r }

    /** Regiones distintas presentes en los ROMs cargados (para el selector). */
    val availableRegions: StateFlow<List<String>> = _roms
        .map { roms -> roms.flatMap { it.regions }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * ROMs de la plataforma tal como los devuelve la API de RomM, agrupadas
     * con [MultiDiscGrouper]: una card por juego, con los discos sueltos y
     * los supersets/duplicados colapsados (p. ej. MGS Twin Snakes tenía 3
     * entradas en RomM: Disc 1, Disc 2 y el multi-disco completo).
     *
     * NO se agrupa solo por igdbId: Golden Sun 1 y 2 comparten igdb_id y
     * deben seguir siendo juegos distintos.
     *
     * Se aplica además el filtro por región y el orden elegidos en la barra
     * de la biblioteca (client-side sobre las páginas ya cargadas).
     */
    val romsWithStatus: StateFlow<List<RomWithStatus>> = combine(
        _roms, downloadedIds, activeDownloads, sort, regionFilter,
    ) { roms, downloaded, downloading, sortType, region ->
        val groups = MultiDiscGrouper.group(
            roms.map { rom ->
                DiscEntry(
                    id = rom.id,
                    name = rom.name,
                    platformSlug = rom.platformSlug,
                    igdbId = rom.igdbId,
                    fileNames = rom.files.map { it.filename }.ifEmpty { listOf(rom.fileName) },
                )
            }
        )
        val byId = roms.associateBy { it.id }
        groups.mapNotNull { group ->
            val rom = byId[group.representativeId] ?: return@mapNotNull null
            val status = when {
                group.downloadIds.all { it in downloaded } -> DownloadStatus.DOWNLOADED
                group.downloadIds.any { it in downloading } -> DownloadStatus.DOWNLOADING
                else -> DownloadStatus.NOT_DOWNLOADED
            }
            RomWithStatus(
                rom = rom,
                status = status,
                discCount = group.discCount,
                groupRomIds = group.downloadIds,
            )
        }
            // Filtro por región y orden elegidos
            .filter { card ->
                region == null || card.rom.regions.any { it.equals(region, ignoreCase = true) }
            }
            .let { grouped ->
                val list = when (sortType) {
                    RomSort.NAME_ASC -> grouped.sortedBy { it.rom.name.lowercase() }
                    RomSort.NAME_DESC -> grouped.sortedByDescending { it.rom.name.lowercase() }
                    RomSort.SIZE_DESC -> grouped.sortedByDescending { it.rom.fileSizeBytes }
                    RomSort.SIZE_ASC -> grouped.sortedBy { it.rom.fileSizeBytes }
                    RomSort.YEAR_DESC -> grouped.sortedByDescending { it.rom.igdbMetadata?.firstReleaseDate ?: 0L }
                    RomSort.YEAR_ASC -> grouped.sortedBy { it.rom.igdbMetadata?.firstReleaseDate ?: Long.MAX_VALUE }
                    RomSort.RATING_DESC -> grouped.sortedByDescending { it.rom.igdbMetadata?.totalRating ?: -1.0 }
                    RomSort.RATING_ASC -> grouped.sortedBy { it.rom.igdbMetadata?.totalRating ?: -1.0 }
                }
                list
            }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    fun selectPlatform(platformId: Int, serverUrl: String, apiKey: String) {
        _selectedPlatformId.value = platformId
        currentSearch = null
        _platformAllRoms.value = emptyList()
        loadRoms(serverUrl, apiKey, reset = true)
    }

    /**
     * Server-side search: fetches ROMs matching [query] from the ENTIRE
     * platform via the RomM API `search` parameter. Replaces the current list.
     * Pass empty string to clear search and reload full list.
     */
    fun searchRoms(query: String, serverUrl: String, apiKey: String) {
        currentSearch = query.ifBlank { null }
        _platformAllRoms.value = emptyList()
        loadRoms(serverUrl, apiKey, reset = true)
    }

    /** Called by infinite scroll when user nears the bottom. */
    fun loadMore(serverUrl: String, apiKey: String) {
        if (_isLoadingMore.value || !_hasMore.value || _isLoading.value) return
        loadRoms(serverUrl, apiKey, reset = false)
    }

    /** Called by UI when user taps download. Emits snackbar feedback. */
    fun onDownloadEnqueued(romName: String) {
        viewModelScope.launch {
            _events.emit(LibraryEvent.DownloadStarted(romName))
        }
    }

    /**
     * Elimina una ROM descargada: borra el archivo del disco y la desmarca
     * como descargada. El estado de la UI se actualiza solo porque
     * [downloadedIds] observa Room. Acepta el grupo completo (multi-disc).
     */
    fun deleteDownloadedRom(rom: Rom, groupRomIds: List<Int> = listOf(rom.id)) {
        viewModelScope.launch {
            var anyOk = false
            groupRomIds.forEach { id ->
                if (romRepository.deleteDownloadedRom(id)) anyOk = true
            }
            if (anyOk) {
                _events.emit(LibraryEvent.DownloadDeleted(rom.name))
            } else {
                _events.emit(LibraryEvent.Error(tr("android.library.delete_failed", rom.name)))
            }
        }
    }

    /**
     * Mueve una ROM descargada entre almacenamientos (interna <-> SD).
     * Copia + verifica + borra; el original se conserva si algo falla.
     * El progreso de la copia se expone en [movingProgress].
     */
    fun moveDownloadedRom(rom: Rom, targetRoot: String) {
        viewModelScope.launch {
            _isMovingRom.value = true
            _movingProgress.value = rom.name to (0L to 0L)
            val result = romRepository.moveDownloadedRom(
                romId = rom.id,
                targetRoot = targetRoot,
                onProgress = { copied, total ->
                    _movingProgress.value = rom.name to (copied to total)
                },
            )
            _isMovingRom.value = false
            _movingProgress.value = null
            when (result) {
                is es.davidrg.rommsync.data.repository.MoveResult.Success ->
                    _events.emit(LibraryEvent.RomMoved(rom.name, result.target.absolutePath))
                es.davidrg.rommsync.data.repository.MoveResult.NotDownloaded ->
                    _events.emit(LibraryEvent.Error(tr("android.library.move_not_downloaded", rom.name)))
                es.davidrg.rommsync.data.repository.MoveResult.SourceMissing ->
                    _events.emit(LibraryEvent.Error(tr("android.library.move_source_missing", rom.name)))
                is es.davidrg.rommsync.data.repository.MoveResult.Error ->
                    _events.emit(LibraryEvent.Error(tr("android.library.move_error", rom.name, result.message)))
            }
        }
    }

    /**
     * Batch download: enqueues every ROM in [roms] that is not already downloading.
     * Used by the "Descargar faltantes" action in the library toolbar.
     *
     * @param romsRootPath raíz destino (modo dos rutas); null = la principal.
     */
    fun enqueueBatchDownload(roms: List<Rom>, serverUrl: String, romsRootPath: String? = null) {
        val manager = downloadManager ?: run {
            viewModelScope.launch { _events.emit(LibraryEvent.Error(tr("android.library.download_manager_unavailable"))) }
            return
        }
        // Skip ROMs that are already enqueued/running to avoid duplicate work.
        val currentlyDownloading = activeDownloads.value
        val toEnqueue = roms.filter { it.id !in currentlyDownloading }
        if (toEnqueue.isEmpty()) {
            viewModelScope.launch { _events.emit(LibraryEvent.Error(tr("android.library.no_new_roms"))) }
            return
        }
        viewModelScope.launch {
            toEnqueue.forEach { rom ->
                manager.enqueueDownload(rom = rom, serverUrl = serverUrl, romsRootPath = romsRootPath)
            }
            _events.emit(LibraryEvent.BatchDownloadStarted(toEnqueue.size))
        }
    }

    private val _platformAllRoms = MutableStateFlow<List<Rom>>(emptyList())
    private val _allPlatformLoading = MutableStateFlow(false)
    val allPlatformLoading: StateFlow<Boolean> = _allPlatformLoading

    /** ROMs de TODA la plataforma que aún no están descargadas (todas las páginas). */
    val platformMissingRoms: StateFlow<List<Rom>> = combine(_platformAllRoms, downloadedIds) { all, downloaded ->
        all.filter { it.id !in downloaded }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Carga todas las páginas de la plataforma seleccionada (para el batch). */
    fun loadAllPlatformRoms() {
        val platformId = _selectedPlatformId.value ?: return
        if (_allPlatformLoading.value) return
        viewModelScope.launch {
            _allPlatformLoading.value = true
            when (val result = romRepository.fetchAllRomsForPlatform(platformId, currentSearch)) {
                is ApiResult.Success -> _platformAllRoms.value = result.data
                is ApiResult.Error -> _events.emit(LibraryEvent.Error(result.message))
            }
            _allPlatformLoading.value = false
        }
    }

    /**
     * Pull-to-refresh: reloads the first page for the currently selected
     * platform keeping the active search term (if any).
     */
    fun refresh(serverUrl: String, apiKey: String) {
        if (_selectedPlatformId.value == null) return
        loadRoms(serverUrl, apiKey, reset = true)
    }

    private fun loadRoms(serverUrl: String, apiKey: String, reset: Boolean) {
        val platformId = _selectedPlatformId.value ?: return
        // Guard de generación: con el debounce de búsqueda pueden solaparse
        // dos loadRoms (p. ej. "mar" y "mario"); si la respuesta VIEJA llega
        // después que la nueva, pisaría la lista con resultados obsoletos —
        // la causa intermitente de los "Sin resultados" falsos.
        val generation = ++loadGeneration
        viewModelScope.launch {
            if (reset) {
                _isLoading.value = true
                _hasMore.value = true
            } else {
                _isLoadingMore.value = true
            }
            _error.value = null
            romRepository.configureApi(serverUrl, apiKey)
            val offset = if (reset) 0 else _roms.value.size
            when (val result = romRepository.fetchRoms(
                platformId,
                offset = offset,
                search = currentSearch,
            )) {
                is ApiResult.Success -> {
                    if (generation == loadGeneration) {
                        _roms.value = if (reset) result.data else _roms.value + result.data
                        _hasMore.value = result.data.size >= PAGE_SIZE
                    }
                }
                is ApiResult.Error -> {
                    if (generation == loadGeneration) {
                        val userMessage = result.kind.toUserMessage()
                        _error.value = userMessage
                        _events.emit(LibraryEvent.Error(userMessage))
                    }
                }
            }
            if (generation == loadGeneration) {
                _isLoading.value = false
                _isLoadingMore.value = false
            }
        }
    }

    /** Token de la última carga lanzada (guard anti-respuesta-obsoleta). */
    private var loadGeneration = 0
}

/**
 * Maps [ErrorKind] to user-facing Spanish messages.
 */
private fun ErrorKind.toUserMessage(): String = when (this) {
    ErrorKind.NETWORK -> tr("android.library.error_network")
    ErrorKind.AUTH -> tr("android.library.error_auth")
    ErrorKind.NOT_FOUND -> tr("android.library.error_not_found")
    ErrorKind.SERVER -> tr("android.library.error_server")
    ErrorKind.UNKNOWN -> tr("android.library.error_unknown")
}

/** One-shot UI events emitted by the ViewModel. */
sealed class LibraryEvent {
    data class DownloadStarted(val romName: String) : LibraryEvent()
    data class BatchDownloadStarted(val count: Int) : LibraryEvent()
    data class DownloadDeleted(val romName: String) : LibraryEvent()
    /** ROM movido entre almacenamientos con éxito; [targetPath] es la nueva ruta. */
    data class RomMoved(val romName: String, val targetPath: String) : LibraryEvent()
    data class Error(val message: String) : LibraryEvent()
}

/** Criterios de ordenación de la biblioteca (paridad con el desktop). */
enum class RomSort(val labelKey: String) {
    NAME_ASC("state.library_sort.name_asc"),
    NAME_DESC("state.library_sort.name_desc"),
    SIZE_DESC("state.library_sort.size_desc"),
    SIZE_ASC("state.library_sort.size_asc"),
    YEAR_DESC("state.library_sort.year_desc"),
    YEAR_ASC("state.library_sort.year_asc"),
    RATING_DESC("state.library_sort.rating_desc"),
    RATING_ASC("state.library_sort.rating_asc"),
}
