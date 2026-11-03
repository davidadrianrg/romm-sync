package com.davidadrianrg.atril.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davidadrianrg.atril.core.launch.LaunchPlatforms
import com.davidadrianrg.atril.core.library.DiscEntry
import com.davidadrianrg.atril.core.library.PhysicalMedia
import com.davidadrianrg.atril.core.library.MultiDiscGrouper
import com.davidadrianrg.atril.core.remote.NetworkModule
import com.davidadrianrg.atril.core.remote.dto.RomDto
import com.davidadrianrg.atril.core.sync.platform.SaveHandlerRegistry
import com.davidadrianrg.atril.data.local.dao.PlatformDao
import com.davidadrianrg.atril.data.local.dao.RomDao
import com.davidadrianrg.atril.data.local.entity.DownloadedRomEntity
import com.davidadrianrg.atril.data.repository.LauncherDisplayMode
import com.davidadrianrg.atril.data.repository.PhysicalMediaRepository
import com.davidadrianrg.atril.data.repository.RomRepository
import com.davidadrianrg.atril.data.repository.SettingsRepository
import com.davidadrianrg.atril.domain.model.Rom
import com.davidadrianrg.atril.domain.model.RomFile
import com.davidadrianrg.atril.launch.EmulatorLauncher
import com.davidadrianrg.atril.launch.LaunchPlan
import com.davidadrianrg.atril.launch.LaunchRequest
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import com.davidadrianrg.atril.domain.model.withPhysicalMediaOverride
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Un juego descargado listo para pintar en la estantería y abrir su detalle. */
data class ShelfItem(
    val rom: Rom,
    val platformLabel: String,
    val groupRomIds: List<Int>,
    val discCount: Int,
    val savesPathOverride: String?,
    val excludedFromSync: Boolean,
    val platformSavesPath: String,
    val isManualDownloaded: Boolean,
    val fileSizeBytes: Long,
    /** Ruta local del representante (vacía si se marcó a mano). */
    val localPath: String = "",
    /** Ficheros conocidos del juego en RomM (para resolver zip-streams). */
    val knownFileNames: List<String> = emptyList(),
    /** Emulador de saves configurado explícitamente para la plataforma. */
    val platformEmulatorId: String? = null,
    /** Preferencia del juego; null = usar el valor global. */
    val displayModePreference: LauncherDisplayMode? = null,
    /** Imagen del medio físico cacheada en el dispositivo (existe). */
    val physicalMediaFile: String? = null,
    /** Modo efectivo: medio físico solo si se eligió y está cacheado. */
    val showPhysical: Boolean = false,
    /** Override del emulador de lanzamiento para la plataforma (id de familia). */
    val launchOverride: String? = null,
) {
    val launchable: Boolean get() = localPath.isNotBlank()
    val hasRemotePhysicalMedia: Boolean get() = rom.physicalMediaUrl != null
    val isOptical: Boolean get() = LaunchPlatforms.isOptical(rom.platformSlug)
}

/**
 * Estantería: todos los juegos descargados (Room), agrupados por juego
 * (multidisco) y enriquecidos con los metadatos del servidor (carátula).
 *
 * Si un ROM ya no existe en el servidor o la petición falla, se pinta igual
 * con los datos locales: la estantería nunca oculta un juego descargado.
 */
class ShelfViewModel(
    private val romRepository: RomRepository,
    private val romDao: RomDao,
    private val platformDao: PlatformDao,
    private val settingsRepository: SettingsRepository,
    private val physicalMediaRepository: PhysicalMediaRepository,
    private val emulatorLauncher: EmulatorLauncher,
) : ViewModel() {

    private val dtoCache = mutableMapOf<Int, RomDto>()

    /** Juegos hidratados (Room + servidor), sin la capa de presentación. */
    private val _items = MutableStateFlow<List<ShelfItem>>(emptyList())

    /**
     * Juegos listos para el launcher: se combina la hidratación con la
     * presentación por juego, el valor global y los overrides de emulador
     * (cambiar el modo no vuelve a pedir nada al servidor).
     */
    val items: StateFlow<List<ShelfItem>> = combine(
        _items,
        physicalMediaRepository.observe(),
        settingsRepository.launcherDefaultDisplay,
        settingsRepository.launchEmulatorOverrides,
        romRepository.physicalMediaOverrides,
    ) { base, presentation, defaultMode, overrides, scraped ->
        val globalDefault = LauncherDisplayMode.fromId(defaultMode) ?: LauncherDisplayMode.CARD
        base.map { item ->
            val row = presentation[item.rom.id]
            val cached = row?.physicalMediaPath?.takeIf { File(it).isFile }
            val pref = LauncherDisplayMode.fromId(row?.displayMode)
            item.copy(
                rom = item.rom.withPhysicalMediaOverride(scraped),
                displayModePreference = pref,
                physicalMediaFile = cached,
                showPhysical = cached != null && (pref ?: globalDefault) == LauncherDisplayMode.PHYSICAL,
                launchOverride = overrides[LaunchPlatforms.canonical(item.rom.platformSlug)],
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())


    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(
                romDao.observeAllDownloadedRoms(),
                settingsRepository.settings,
                settingsRepository.retroArchBasePath,
            ) { downloaded, cfg, retroBase -> Triple(downloaded, cfg, retroBase) }
                .collectLatest { (downloaded, cfg, retroBase) ->
                    if (downloaded.isEmpty()) {
                        _items.value = emptyList()
                        _loading.value = false
                        return@collectLatest
                    }
                    _loading.value = true

                    val platforms = runCatching { platformDao.getAllPlatformsBlocking() }
                        .getOrDefault(emptyList())
                        .associateBy { it.slug }
                    val baseCoverUrl = cfg.serverUrl.trimEnd('/') + "/"
                    val api = if (cfg.serverUrl.isNotBlank() && cfg.apiKey.isNotBlank()) {
                        NetworkModule.createApiService(cfg.serverUrl, cfg.apiKey)
                    } else null
                    val semaphore = Semaphore(4)

                    // Hidratación por LISTADO de plataforma (la misma fuente que
                    // usa la Biblioteca): así las carátulas coinciden siempre.
                    // Luego, para ids que no aparezcan, se pide el detalle.
                    val dtoById = mutableMapOf<Int, RomDto>()
                    dtoCache.forEach { (id, dto) -> dtoById[id] = dto }

                    if (api != null) {
                        val platformIds = downloaded
                            .mapNotNull { platforms[it.platformSlug]?.id }
                            .distinct()
                        coroutineScope {
                            platformIds.map { platformId ->
                                async(Dispatchers.IO) {
                                    semaphore.withPermit {
                                        var offset = 0
                                        val pageSize = 500
                                        while (true) {
                                            val response = runCatching {
                                                api.getRoms(
                                                    mapOf(
                                                        "platform_ids" to platformId.toString(),
                                                        "limit" to pageSize.toString(),
                                                        "offset" to offset.toString(),
                                                        "order_by" to "name",
                                                        "order_dir" to "asc",
                                                    ),
                                                )
                                            }.getOrNull() ?: break
                                            response.items.forEach { dto ->
                                                dtoById[dto.id] = dto
                                                dtoCache[dto.id] = dto
                                            }
                                            if (response.items.size < pageSize) break
                                            offset += pageSize
                                            if (offset >= 10_000) break
                                        }
                                    }
                                }
                            }.awaitAll()
                        }

                        val missing = downloaded.map { it.romId }.filter { it !in dtoById }
                        if (missing.isNotEmpty()) {
                            coroutineScope {
                                missing.map { romId ->
                                    async(Dispatchers.IO) {
                                        semaphore.withPermit {
                                            val dto = runCatching { api.getRom(romId) }.getOrNull()
                                            if (dto != null) {
                                                dtoById[romId] = dto
                                                dtoCache[romId] = dto
                                            }
                                        }
                                    }
                                }.awaitAll()
                            }
                        }
                    }

                    val romsById = downloaded.associate { entity ->
                        entity.romId to buildRom(entity, dtoById[entity.romId], baseCoverUrl)
                    }

                    val discEntries = romsById.values.map { rom ->
                        DiscEntry(
                            id = rom.id,
                            name = rom.name,
                            platformSlug = rom.platformSlug,
                            igdbId = rom.igdbId,
                            fileNames = rom.files.map { it.filename }
                                .ifEmpty { listOf(rom.fileName) },
                        )
                    }
                    val downloadedIds = downloaded.map { it.romId }.toSet()

                    val items = MultiDiscGrouper.group(discEntries).mapNotNull { group ->
                        val repEntity = downloaded.firstOrNull { it.romId == group.representativeId }
                            ?: return@mapNotNull null
                        val repRom = romsById[group.representativeId] ?: return@mapNotNull null
                        val platform = platforms[repRom.platformSlug]
                        val emulatorId = platform?.emulatorId
                            ?: SaveHandlerRegistry.getDefaultEmulator(repRom.platformSlug).id
                        val platformSavesPath = platform?.savesPathOverride
                            ?.takeIf { it.isNotBlank() }
                            ?: SaveHandlerRegistry.getDefaultSavesPath(
                                emulatorId = emulatorId,
                                platformSlug = repRom.platformSlug,
                                retroArchBase = retroBase,
                            )
                        ShelfItem(
                            rom = repRom,
                            platformLabel = platform?.name ?: repRom.platformSlug.uppercase(),
                            groupRomIds = group.allIds.filter { it in downloadedIds },
                            discCount = group.discCount,
                            savesPathOverride = repEntity.savesPathOverride,
                            excludedFromSync = repEntity.excludedFromSync,
                            platformSavesPath = platformSavesPath,
                            isManualDownloaded = repEntity.localPath.isBlank(),
                            fileSizeBytes = downloaded
                                .filter { it.romId in group.allIds }
                                .sumOf { it.fileSizeBytes },
                            localPath = repEntity.localPath,
                            knownFileNames = repRom.files.map { it.filename },
                            platformEmulatorId = platform?.emulatorId,
                        )
                    }.sortedBy { it.rom.name.lowercase() }

                    _items.value = items
                    _loading.value = false
                }
        }
    }

    fun setSavesPathOverride(romId: Int, path: String?) {
        viewModelScope.launch { romDao.updateSavesPathOverride(romId, path) }
    }

    fun setExcludedFromSync(romId: Int, excluded: Boolean) {
        viewModelScope.launch { romDao.updateExcludedFromSync(romId, excluded) }
    }

    /** Borra del dispositivo todos los ficheros del grupo (multidisco incluido). */
    fun delete(item: ShelfItem) {
        viewModelScope.launch {
            var any = false
            item.groupRomIds.forEach { id ->
                if (romRepository.deleteDownloadedRom(id)) any = true
            }
            if (!any) {
                _messages.tryEmit("No se pudo borrar ${item.rom.name}")
            } else {
                physicalMediaRepository.remove((item.groupRomIds + item.rom.id).distinct())
            }
        }
    }

    // ── Lanzamiento ────────────────────────────────────────────────────

    fun currentEmulatorName(item: ShelfItem): String? = emulatorLauncher
        .currentFamily(item.rom.platformSlug, item.launchOverride, item.platformEmulatorId)
        ?.displayName

    suspend fun prepareLaunch(item: ShelfItem): LaunchPlan = emulatorLauncher.prepare(
        LaunchRequest(
            platformSlug = item.rom.platformSlug,
            platformLabel = item.platformLabel,
            localPath = item.localPath,
            knownFileNames = item.knownFileNames,
            overrideFamilyId = item.launchOverride,
            saveEmulatorId = item.platformEmulatorId,
        ),
    )

    fun startLaunch(activityContext: android.content.Context, plan: LaunchPlan.Ready): String? =
        emulatorLauncher.start(activityContext, plan)

    /**
     * Convierte el DTO del servidor en el modelo de dominio. Si no hay DTO
     * (servidor caído o ROM eliminada) construye un Rom mínimo con los datos
     * locales para que el detalle siga abriéndose.
     */
    private fun buildRom(entity: DownloadedRomEntity, dto: RomDto?, baseCoverUrl: String): Rom {
        if (dto == null) {
            return Rom(
                id = entity.romId,
                name = entity.name,
                fileName = entity.fileName,
                fileSizeBytes = entity.fileSizeBytes,
                platformId = entity.platformId,
                platformSlug = entity.platformSlug,
                coverUrlSmall = null,
                coverUrlLarge = null,
                files = emptyList(),
                isMulti = false,
                revision = null,
                regions = emptyList(),
            )
        }
        return Rom(
            id = dto.id,
            name = dto.name.ifBlank { dto.fileName },
            fileName = dto.fileName,
            fileSizeBytes = dto.fileSizeBytes,
            platformId = dto.platformId,
            platformSlug = dto.platformSlug ?: entity.platformSlug,
            coverUrlSmall = dto.urlCover
                ?: dto.pathCoverSmall?.let { baseCoverUrl + it.removePrefix("/") },
            coverUrlLarge = dto.pathCoverLarge?.let { baseCoverUrl + it.removePrefix("/") },
            files = dto.files.map { RomFile(it.filename, it.size, it.hash) },
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
            screenshots = dto.mergedScreenshots.map { baseCoverUrl + it.removePrefix("/") },
            videoPath = dto.pathVideo?.let { baseCoverUrl + it.removePrefix("/") },
            manualPath = dto.pathManual?.let { baseCoverUrl + it.removePrefix("/") },
            youtubeVideoId = dto.youtubeVideoId,
            physicalMediaUrl = PhysicalMedia.resourceUrl(baseCoverUrl, dto.physicalMediaPath),
        )
    }
}
