package es.davidrg.rommsync.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import es.davidrg.rommsync.core.remote.NetworkModule
import es.davidrg.rommsync.core.remote.dto.RomDto
import es.davidrg.rommsync.data.local.dao.PlatformDao
import es.davidrg.rommsync.data.local.dao.RomDao
import es.davidrg.rommsync.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Un juego descargado con su carátula y la etiqueta legible de plataforma. */
data class ShelfItem(
    val romId: Int,
    val platformId: Int,
    val name: String,
    val coverUrl: String?,
    val platformLabel: String,
)

/**
 * Estantería: todos los juegos descargados (Room), enriqueciendo cada entrada
 * con los metadatos del servidor (carátula) y el nombre de su plataforma.
 * Las carátulas se cachean en memoria por romId.
 */
class ShelfViewModel(
    private val romDao: RomDao,
    private val platformDao: PlatformDao,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val romCache = mutableMapOf<Int, RomDto>()

    private val _items = MutableStateFlow<List<ShelfItem>>(emptyList())
    val items: StateFlow<List<ShelfItem>> = _items.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        viewModelScope.launch {
            combine(romDao.observeAllDownloadedRoms(), settingsRepository.settings) { roms, cfg ->
                roms to cfg
            }.collectLatest { (downloaded, cfg) ->
                if (downloaded.isEmpty() || cfg.serverUrl.isBlank() || cfg.apiKey.isBlank()) {
                    _items.value = emptyList()
                    _loading.value = false
                    return@collectLatest
                }
                _loading.value = true
                val platformNames = runCatching { platformDao.getAllPlatformsBlocking() }
                    .getOrDefault(emptyList())
                    .associate { it.slug to it.name }
                val baseCoverUrl = cfg.serverUrl.trimEnd('/') + "/"
                val api = NetworkModule.createApiService(cfg.serverUrl, cfg.apiKey)
                val semaphore = Semaphore(4)

                val fetched = coroutineScope {
                    downloaded.map { entity ->
                        async(Dispatchers.IO) {
                            semaphore.withPermit {
                                val dto = romCache[entity.romId]
                                    ?: runCatching { api.getRom(entity.romId) }.getOrNull()
                                if (dto != null) romCache[entity.romId] = dto
                                dto?.let {
                                    val cover = it.urlCover
                                        ?: it.pathCoverLarge?.let { p -> baseCoverUrl + p.removePrefix("/") }
                                        ?: it.pathCoverSmall?.let { p -> baseCoverUrl + p.removePrefix("/") }
                                    ShelfItem(
                                        romId = it.id,
                                        platformId = it.platformId,
                                        name = it.name,
                                        coverUrl = cover,
                                        platformLabel = platformNames[it.platformSlug]
                                            ?: it.platformSlug?.uppercase()
                                            ?: entity.platformSlug.uppercase(),
                                    )
                                }
                            }
                        }
                    }.awaitAll()
                }

                _items.value = fetched.filterNotNull().sortedBy { it.name.lowercase() }
                _loading.value = false
            }
        }
    }
}
