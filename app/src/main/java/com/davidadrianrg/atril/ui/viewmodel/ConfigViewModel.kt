package com.davidadrianrg.atril.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davidadrianrg.atril.BuildConfig
import com.davidadrianrg.atril.core.auth.DeviceAuthRequest
import com.davidadrianrg.atril.core.auth.QrLoginState
import com.davidadrianrg.atril.core.auth.runQrLogin
import com.davidadrianrg.atril.core.i18n.tr
import com.davidadrianrg.atril.data.local.ServerConfig
import com.davidadrianrg.atril.data.repository.RomRepository
import com.davidadrianrg.atril.data.repository.SettingsRepository
import com.davidadrianrg.atril.data.sync.SaveSyncManager
import com.davidadrianrg.atril.data.update.ApkInstallHelper
import com.davidadrianrg.atril.data.update.AppUpdateChecker
import com.davidadrianrg.atril.data.update.InstallLaunchResult
import com.davidadrianrg.atril.data.update.UpdateCheckResult
import com.davidadrianrg.atril.data.update.UpdateDownloadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ConfigViewModel(
    private val settingsRepository: SettingsRepository,
    private val romRepository: RomRepository? = null,
    private val saveSyncManager: SaveSyncManager? = null,
    private val updateChecker: AppUpdateChecker? = null,
    private val apkInstallHelper: ApkInstallHelper? = null,
) : ViewModel() {

    val settings: StateFlow<ServerConfig> = settingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ServerConfig("", "", "", 2),
        )

    // ── Actualizaciones desde GitHub Releases ──────────────────────────
    // null = aún no se ha comprobado nada en esta sesión.
    private val _updateCheckState = MutableStateFlow<UpdateCheckResult?>(null)
    val updateCheckState: StateFlow<UpdateCheckResult?> = _updateCheckState.asStateFlow()

    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate.asStateFlow()

    /** Mensaje de feedback del último intento de lanzar el instalador. */
    private val _installFeedback = MutableStateFlow<String?>(null)
    val installFeedback: StateFlow<String?> = _installFeedback.asStateFlow()

    val updateDownloadState: StateFlow<UpdateDownloadState> =
        apkInstallHelper?.downloadState ?: MutableStateFlow(UpdateDownloadState.Idle)

    /** Version currently installed on this device. */
    val currentVersion: String = BuildConfig.VERSION_NAME

    // ── Restricciones del auto-sync + política de conflictos + skip ────

    val syncWifiOnly: StateFlow<Boolean> = settingsRepository.syncWifiOnly
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val syncChargingOnly: StateFlow<Boolean> = settingsRepository.syncChargingOnly
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val conflictPolicy: StateFlow<String> = settingsRepository.conflictPolicy
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "ask")
    val skippedVersion: StateFlow<String> = settingsRepository.skippedVersion
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    /** Cambia una restricción y reprograma el trabajo periódico si toca. */
    fun setSyncConstraints(wifiOnly: Boolean? = null, chargingOnly: Boolean? = null) {
        viewModelScope.launch {
            wifiOnly?.let { settingsRepository.setSyncWifiOnly(it) }
            chargingOnly?.let { settingsRepository.setSyncChargingOnly(it) }
            val interval = settingsRepository.saveSyncIntervalMinutes.first()
            if (interval > 0) {
                saveSyncManager?.schedulePeriodicSync(
                    interval,
                    replace = true,
                    wifiOnly = wifiOnly ?: settingsRepository.syncWifiOnly.first(),
                    chargingOnly = chargingOnly ?: settingsRepository.syncChargingOnly.first(),
                )
            }
        }
    }

    fun setConflictPolicy(policyId: String) {
        viewModelScope.launch { settingsRepository.setConflictPolicy(policyId) }
    }

    /** Omite la versión disponible (deja de ofrecerse hasta la siguiente). */
    fun skipCurrentVersion() {
        val r = _updateCheckState.value as? UpdateCheckResult.UpdateAvailable ?: return
        viewModelScope.launch {
            settingsRepository.setSkippedVersion(r.latestVersion)
            _updateCheckState.value = UpdateCheckResult.UpToDate
        }
    }

    /** Limpia la versión omitida y vuelve a comprobar. */
    fun recheckSkippedVersion() {
        viewModelScope.launch {
            settingsRepository.setSkippedVersion("")
            checkForUpdates()
        }
    }

    /** Queries GitHub for the latest release and updates [updateCheckState]. */
    fun checkForUpdates() {
        val checker = updateChecker ?: return
        if (_isCheckingUpdate.value) return
        _isCheckingUpdate.value = true
        viewModelScope.launch {
            _updateCheckState.value = checker.check()
            _isCheckingUpdate.value = false
        }
    }

    /** Starts the APK download for the latest known update, if any. */
    fun downloadUpdate() {
        val helper = apkInstallHelper ?: return
        val update = _updateCheckState.value as? UpdateCheckResult.UpdateAvailable ?: return
        viewModelScope.launch { helper.download(update) }
    }

    /** Opens the system installer with the downloaded APK. */
    fun installUpdate() {
        val helper = apkInstallHelper ?: return
        val ready = helper.downloadState.value as? UpdateDownloadState.ReadyToInstall ?: return
        when (val result = helper.launchInstaller(ready.apkFile)) {
            is InstallLaunchResult.Started -> _installFeedback.value = null
            is InstallLaunchResult.NeedsPermission -> _installFeedback.value =
                tr("android.config.install_permission_hint")
            is InstallLaunchResult.Error -> _installFeedback.value = result.message
        }
    }

    /** Discards a downloaded update APK and resets the section. */
    fun cancelUpdate() {
        val helper = apkInstallHelper ?: return
        helper.cleanup()
    }

    // ── Escaneo de biblioteca ──────────────────────────────────────────
    private val _scanState = MutableStateFlow<LibraryScanState>(LibraryScanState.Idle)
    val scanState: StateFlow<LibraryScanState> = _scanState.asStateFlow()

    /**
     * Recorre la biblioteca en disco y marca como descargados los juegos cuyo
     * fichero exista en la ruta esperada.
     */
    fun scanLibrary(serverUrl: String, apiKey: String) {
        val repo = romRepository ?: return
        if (_scanState.value is LibraryScanState.Scanning) return
        viewModelScope.launch {
            _scanState.value = LibraryScanState.Scanning(null)
            repo.configureApi(serverUrl, apiKey)
            val config = settingsRepository.settings.first()
            val romsRootPaths = buildList {
                add(config.romsRootPath)
                if (config.dualRomsPathsEnabled) add(config.secondaryRomsPath)
            }
            val result = repo.scanDownloadedLibrary(
                romsRootPaths = romsRootPaths,
                onProgress = { platformName ->
                    _scanState.value = LibraryScanState.Scanning(platformName)
                },
            )
            _scanState.value = if (result.isSuccess) {
                LibraryScanState.Done(detected = result.detected, scanned = result.scanned)
            } else {
                LibraryScanState.Error(result.error ?: tr("android.config.scan_error"))
            }
        }
    }

    fun dismissScanResult() {
        _scanState.value = LibraryScanState.Idle
    }

    // ── Login con QR (device auth de RomM) + modo solo-local ───────────

    private val _qrLogin = MutableStateFlow<QrLoginState>(QrLoginState.Idle)
    val qrLogin: StateFlow<QrLoginState> = _qrLogin.asStateFlow()
    private var qrJob: kotlinx.coroutines.Job? = null

    fun startQrLogin(serverUrl: String) {
        qrJob?.cancel()
        qrJob = viewModelScope.launch {
            val req = DeviceAuthRequest(
                clientDeviceIdentifier = settingsRepository.clientDeviceId(),
                name = "Atril (${android.os.Build.MODEL})",
                platform = "android",
                clientVersion = BuildConfig.VERSION_NAME,
            )
            runQrLogin(serverUrl, req, onState = { _qrLogin.value = it }, onApproved = { origin, token ->
                settingsRepository.setServerUrl(origin)
                settingsRepository.setApiKey(token.accessToken)
                settingsRepository.setLocalOnly(false)
                romRepository?.configureApi(origin, token.accessToken)
            })
        }
    }

    fun cancelQrLogin() {
        qrJob?.cancel()
        _qrLogin.value = QrLoginState.Idle
    }

    fun useLocalOnly() {
        viewModelScope.launch { settingsRepository.setLocalOnly(true) }
    }

    /** Olvida servidor y token; los juegos locales se conservan. */
    fun disconnectRomM() {
        viewModelScope.launch { settingsRepository.disconnectRomM() }
    }

    fun setServerUrl(url: String) {
        viewModelScope.launch { settingsRepository.setServerUrl(url) }
    }

    fun setApiKey(key: String) {
        viewModelScope.launch { settingsRepository.setApiKey(key) }
    }

    fun setRomsRootPath(path: String) {
        viewModelScope.launch { settingsRepository.setRomsRootPath(path) }
    }

    fun setSecondaryRomsPath(path: String) {
        viewModelScope.launch { settingsRepository.setSecondaryRomsPath(path) }
    }

    fun setDualRomsPathsEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDualRomsPathsEnabled(enabled) }
    }

    fun setMaxConcurrentDownloads(max: Int) {
        viewModelScope.launch { settingsRepository.setMaxConcurrentDownloads(max) }
    }

    fun setWifiOnlyDownloads(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setWifiOnlyDownloads(enabled) }
    }

    fun setRetroArchBasePath(path: String) {
        viewModelScope.launch { settingsRepository.setRetroArchBasePath(path) }
    }

    val theme = settingsRepository.theme

    /** Claves de SteamGridDB / ScreenScraper para las carátulas de juegos locales. */
    val scraperCredentials = settingsRepository.scraperCredentials

    fun saveScraperCredentials(c: com.davidadrianrg.atril.core.localgames.ScraperCredentials) {
        viewModelScope.launch { settingsRepository.setScraperCredentials(c) }
    }

    fun setTheme(t: com.davidadrianrg.atril.core.theme.AppTheme) {
        viewModelScope.launch { settingsRepository.setTheme(t.id) }
    }

    /** Idioma: "" = sistema, "en", "es". Aplica en caliente vía LocaleController. */
    val language = settingsRepository.language

    fun setLanguage(tag: String) {
        viewModelScope.launch {
            settingsRepository.setLanguage(tag)
            com.davidadrianrg.atril.util.LocaleController.apply(tag)
        }
    }

    /**
     * Activa/desactiva la función de sincronización de saves. Al desactivar
     * cancela cualquier trabajo de sync encolado; al reactivar restaura el
     * periodo configurado si existía.
     */
    fun setSaveSyncEnabled(enabled: Boolean) {
        val manager = saveSyncManager ?: return
        viewModelScope.launch {
            settingsRepository.setSaveSyncEnabled(enabled)
            if (enabled) {
                val interval = settingsRepository.saveSyncIntervalMinutes.first()
                if (interval > 0) {
                    manager.schedulePeriodicSync(interval)
                }
            } else {
                manager.cancelAllSyncWork()
                manager.stopWatchService()
            }
            if (enabled && settingsRepository.saveSyncWatchEnabled.first()) {
                manager.startWatchService()
            }
        }
    }

    fun setEsdeDataPath(path: String) {
        viewModelScope.launch { settingsRepository.setEsdeDataDir(path) }
    }

    fun setRetroHraiMediaPath(path: String) {
        viewModelScope.launch { settingsRepository.setRetroHraiMediaPath(path) }
    }

    /** Muestra/oculta la exportación de metadatos a ES-DE. */
    fun setEsdeExportEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setEsdeExportEnabled(enabled) }
    }

    /** Muestra/oculta la exportación de metadatos a RetroHRAI. */
    fun setRetroHraiExportEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setRetroHraiExportEnabled(enabled) }
    }
}

/**
 * Estado del escaneo de biblioteca local.
 */
sealed class LibraryScanState {
    data object Idle : LibraryScanState()
    data class Scanning(val platformName: String?) : LibraryScanState()
    data class Done(val detected: Int, val scanned: Int) : LibraryScanState()
    data class Error(val message: String) : LibraryScanState()
}
