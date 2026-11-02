package com.davidadrianrg.atril.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davidadrianrg.atril.core.i18n.tr
import com.davidadrianrg.atril.data.repository.RomRepository
import com.davidadrianrg.atril.domain.model.ApiResult
import com.davidadrianrg.atril.domain.model.ErrorKind
import com.davidadrianrg.atril.domain.model.Platform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Estadísticas de la biblioteca local descargada. */
data class LibraryStats(
    val totalRoms: Int,
    val totalBytes: Long,
    val byPlatform: List<PlatformStat>,
)

data class PlatformStat(
    val platformSlug: String,
    val romCount: Int,
    val totalBytes: Long,
)

class PlatformsViewModel(
    private val romRepository: RomRepository,
) : ViewModel() {

    val platforms: StateFlow<List<Platform>> = romRepository.getCachedPlatforms()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    /** Estadísticas locales de la biblioteca descargada. */
    val libraryStats: StateFlow<LibraryStats?> = romRepository.observeAllDownloadedRoms()
        .map { roms ->
            if (roms.isEmpty()) null
            else LibraryStats(
                totalRoms = roms.size,
                totalBytes = roms.sumOf { it.fileSizeBytes },
                byPlatform = roms.groupBy { it.platformSlug }
                    .map { (slug, list) -> PlatformStat(slug, list.size, list.sumOf { it.fileSizeBytes }) }
                    .sortedByDescending { it.romCount },
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null,
        )

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun refreshPlatforms(serverUrl: String, apiKey: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            romRepository.configureApi(serverUrl, apiKey)
            when (val result = romRepository.fetchAndCachePlatforms()) {
                is ApiResult.Success -> {
                    // platforms flow auto-refreshes from Room
                }
                is ApiResult.Error -> {
                    _error.value = result.kind.toUserMessage()
                }
            }
            _isLoading.value = false
        }
    }

    fun togglePlatformVisibility(platform: Platform) {
        viewModelScope.launch {
            romRepository.updatePlatformVisibility(platform.id, !platform.visible)
        }
    }

    fun updatePlatformEmulator(platformId: Int, emulatorId: String?) {
        viewModelScope.launch {
            romRepository.updatePlatformEmulator(platformId, emulatorId)
        }
    }

    fun updatePlatformSavesPath(platformId: Int, path: String?) {
        viewModelScope.launch {
            romRepository.updatePlatformSavesPath(platformId, path?.takeIf { it.isNotBlank() })
        }
    }

    fun updatePlatformDownloadStorage(
        platformId: Int,
        storage: com.davidadrianrg.atril.domain.model.PlatformDownloadStorage,
    ) {
        viewModelScope.launch {
            romRepository.updatePlatformDownloadStorage(platformId, storage)
        }
    }

    fun setAllVisible(visible: Boolean) {
        viewModelScope.launch {
            platforms.value.forEach { platform ->
                if (platform.visible != visible) {
                    romRepository.updatePlatformVisibility(platform.id, visible)
                }
            }
        }
    }
}

/**
 * Maps [ErrorKind] to user-facing Spanish messages.
 */
private fun ErrorKind.toUserMessage(): String = when (this) {
    ErrorKind.NETWORK -> tr("android.platforms.error.network")
    ErrorKind.AUTH -> tr("android.platforms.error.auth")
    ErrorKind.NOT_FOUND -> tr("android.platforms.error.not_found")
    ErrorKind.SERVER -> tr("android.platforms.error.server")
    ErrorKind.UNKNOWN -> tr("android.platforms.error.unknown")
}
