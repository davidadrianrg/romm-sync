package com.davidadrianrg.atril.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.CheckCircle
import kotlinx.coroutines.launch
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.davidadrianrg.atril.RomMSyncApplication
import com.davidadrianrg.atril.core.i18n.tr
import com.davidadrianrg.atril.core.settings.SettingsCategory
import com.davidadrianrg.atril.data.update.UpdateCheckResult
import com.davidadrianrg.atril.data.update.UpdateDownloadState
import com.davidadrianrg.atril.ui.components.FolderPickerDialog
import com.davidadrianrg.atril.ui.viewmodel.ConfigViewModel
import com.davidadrianrg.atril.ui.viewmodel.LibraryScanState
import com.davidadrianrg.atril.util.hasAllFilesAccess
import com.davidadrianrg.atril.util.rememberNotificationPermissionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    onOpenPlatforms: () -> Unit = {},
    initialCategory: SettingsCategory = SettingsCategory.CONNECTION,
) {
    val context = LocalContext.current
    val container = (context.applicationContext as RomMSyncApplication).container

    val viewModel: ConfigViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                ConfigViewModel(
                    container.settingsRepository,
                    container.romRepository,
                    container.saveSyncManager,
                    container.appUpdateChecker,
                    container.apkInstallHelper,
                )
            }
        }
    )

    val settings by viewModel.settings.collectAsState()
    val scanState by viewModel.scanState.collectAsState()
    val updateCheckState by viewModel.updateCheckState.collectAsState()
    val updateDownloadState by viewModel.updateDownloadState.collectAsState()
    val isChecking by viewModel.isCheckingUpdate.collectAsState()
    val installFeedback by viewModel.installFeedback.collectAsState()
    val compact = com.davidadrianrg.atril.ui.components.rememberWindowInfo().isCompact
    rememberNotificationPermissionState()

    var serverUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var showManualToken by remember { mutableStateOf(false) }
    var settingsCat by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initialCategory) }
    var showQrDialog by remember { mutableStateOf(false) }
    val qrState by viewModel.qrLogin.collectAsState()

    // ── Folder picker dialog state ───────────────────────────────────────
    var showRomsPicker by remember { mutableStateOf(false) }
    var showSecondaryRomsPicker by remember { mutableStateOf(false) }
    var showEsdePicker by remember { mutableStateOf(false) }
    var showMediaPicker by remember { mutableStateOf(false) }

    LaunchedEffect(settings.serverUrl, settings.apiKey) {
        if (serverUrl.isEmpty() && settings.serverUrl.isNotEmpty()) serverUrl = settings.serverUrl
        if (apiKey.isEmpty() && settings.apiKey.isNotEmpty()) apiKey = settings.apiKey
    }

    // Cargar ruta de datos de ES-DE
    val esdeDataDir by container.settingsRepository.esdeDataDir.collectAsState(
        initial = com.davidadrianrg.atril.data.local.SettingsDataStore.DEFAULT_ESDE_DATA_DIR,
    )

    // Ruta de la media genérica ("" = <ROMs>/downloaded_media)
    val mediaDownloadPath by container.settingsRepository.mediaDownloadPath.collectAsState(initial = "")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("nav.settings")) },
                modifier = Modifier.height(if (compact) 48.dp else 64.dp),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsCategoryBar(settingsCat) { settingsCat = it }

            // ── Permiso de almacenamiento ───────────────────────────────
            if (!hasAllFilesAccess()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Spacer(modifier = Modifier.size(10.dp))
                            Text(
                                tr("android.config.storage_permission_title"),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            tr("android.config.storage_permission_message"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        FilledTonalButton(
                            onClick = { com.davidadrianrg.atril.util.requestAllFilesAccess(context) },
                        ) {
                            Text(tr("android.config.grant_permission"))
                        }
                    }
                }
            }

            if (settingsCat == SettingsCategory.CONNECTION) {
            // ── Servidor ────────────────────────────────────────────────
            SettingsSection(
                icon = Icons.Outlined.Dns,
                title = tr("settings.server.title"),
            ) {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text(tr("settings.server.url")) },
                    placeholder = { Text(tr("settings.server.url_placeholder")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri,
                    ),
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (!settings.isConfigured) {
                    Button(
                        onClick = { showQrDialog = true; viewModel.startQrLogin(serverUrl) },
                        enabled = serverUrl.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text(tr("romm.connect_qr")) }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { viewModel.useLocalOnly() },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text(tr("romm.local_only")) }
                    Text(
                        tr("romm.local_only_hint"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    if (settings.localOnly) {
                        Text(
                            tr("romm.disconnected_local"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = { viewModel.disconnectRomM(); serverUrl = ""; apiKey = "" },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text(tr("romm.disconnect")) }
                    Text(
                        tr("romm.disconnect_hint"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                TextButton(onClick = { showManualToken = !showManualToken }) {
                    Text(tr("romm.advanced_token"))
                }
                if (showManualToken) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text(tr("settings.server.api_key")) },
                    placeholder = { Text("rmm_...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = if (apiKeyVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                            Icon(
                                imageVector = if (apiKeyVisible) Icons.Filled.VisibilityOff
                                              else Icons.Filled.Visibility,
                                contentDescription = tr("android.config.api_key_toggle"),
                            )
                        }
                    },
                )
                }
            }
            }

            if (settingsCat == SettingsCategory.LOCAL) {
            // ── Almacenamiento ──────────────────────────────────────────
            SettingsSection(
                icon = Icons.Outlined.Folder,
                title = tr("settings.roms.title"),
            ) {
                OutlinedTextField(
                    value = settings.romsRootPath,
                    onValueChange = { viewModel.setRomsRootPath(it) },
                    label = { Text(tr("android.config.roms_root_path_label")) },
                    placeholder = { Text("/storage/emulated/0/ROMs") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = { showRomsPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Filled.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(tr("common.select_folder"))
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ── Modo dos rutas: memoria interna + tarjeta SD ────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            tr("android.config.dual_paths_title"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            tr("android.config.dual_paths_hint"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.size(12.dp))
                    Switch(
                        checked = settings.dualRomsPathsEnabled,
                        onCheckedChange = { viewModel.setDualRomsPathsEnabled(it) },
                    )
                }

                if (settings.dualRomsPathsEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = settings.secondaryRomsPath,
                        onValueChange = { viewModel.setSecondaryRomsPath(it) },
                        label = { Text(tr("android.config.secondary_path_label")) },
                        placeholder = { Text("/storage/emulated/1/ROMs") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = { showSecondaryRomsPicker = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            Icons.Filled.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(tr("android.config.select_sd_folder"))
                    }
                }
            }
            }

            if (settingsCat == SettingsCategory.CONNECTION) {
            // ── Plataformas ─────────────────────────────────────────────
            SettingsSection(
                icon = Icons.Outlined.Storage,
                title = tr("settings.platforms.title"),
            ) {
                Text(
                    tr("settings.platforms.description"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = onOpenPlatforms,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Outlined.Storage,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(tr("settings.platforms.open"))
                }
            }
            }

            if (settingsCat == SettingsCategory.LOCAL) {
            // ── Escanear biblioteca local ───────────────────────────────
            SettingsSection(
                icon = Icons.Outlined.Search,
                title = tr("common.scan_library"),
            ) {
                Text(
                    tr("android.config.scan_hint"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))

                val isScanning = scanState is LibraryScanState.Scanning
                FilledTonalButton(
                    onClick = { viewModel.scanLibrary(serverUrl, apiKey) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = settings.isConfigured && !isScanning,
                ) {
                    if (isScanning) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(tr("settings.scan.scan_now"))
                }

                when (val s = scanState) {
                    is LibraryScanState.Scanning -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            s.platformName?.let { tr("android.config.scanning", it) } ?: tr("android.config.scan_preparing"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    is LibraryScanState.Done -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            if (s.detected > 0) {
                                if (s.detected == 1) {
                                    tr("android.config.scan_done_single", s.detected, s.scanned)
                                } else {
                                    tr("android.config.scan_done_plural", s.detected, s.scanned)
                                }
                            } else {
                                tr("android.config.scan_done_none", s.scanned)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    is LibraryScanState.Error -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            tr("common.error_detail", s.message),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    LibraryScanState.Idle -> {}
                }

                if (!settings.isConfigured) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        tr("android.config.scan_needs_server"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            }

            if (settingsCat == SettingsCategory.ADVANCED) {
            // ── ES-DE Data Dir ───────────────────────────────────────────
            SettingsSection(
                icon = Icons.Outlined.Folder,
                title = tr("settings.esde.title"),
            ) {
                // Toggle maestro: oculta la función de exportación a ES-DE
                var esdeEnabledLocal by remember { mutableStateOf<Boolean?>(null) }
                val esdeEnabled by container.settingsRepository.esdeExportEnabled.collectAsState(initial = false)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tr("android.config.use_esde"), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            tr("android.config.use_esde_hint"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = esdeEnabledLocal ?: esdeEnabled,
                        onCheckedChange = { enabled ->
                            esdeEnabledLocal = enabled
                            viewModel.setEsdeExportEnabled(enabled)
                        },
                    )
                }
                if (esdeEnabledLocal ?: esdeEnabled) {
                OutlinedTextField(
                    value = esdeDataDir,
                    onValueChange = { viewModel.setEsdeDataPath(it) },
                    label = { Text(tr("android.config.esde_data_dir_label")) },
                    placeholder = { Text("/storage/emulated/0/ES-DE") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = { showEsdePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Filled.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(tr("common.select_folder"))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    tr("android.config.esde_data_hint"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                }
            }
            }

            if (settingsCat == SettingsCategory.DOWNLOADS) {
            // ── Media del juego al descargar (genérica) ───────────────────
            SettingsSection(
                icon = Icons.Outlined.Folder,
                title = tr("media.download.title"),
            ) {
                // Toggle maestro: sin RomM no tiene sentido (la media viene de RomM).
                var mediaEnabledLocal by remember { mutableStateOf<Boolean?>(null) }
                val mediaEnabled by container.settingsRepository.mediaDownloadEnabled.collectAsState(initial = false)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tr("media.download.toggle"), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            tr("media.download.hint"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = mediaEnabledLocal ?: mediaEnabled,
                        onCheckedChange = { enabled ->
                            mediaEnabledLocal = enabled
                            viewModel.setMediaDownloadEnabled(enabled)
                        },
                        enabled = settings.isConfigured,
                    )
                }
                if (settings.isConfigured && (mediaEnabledLocal ?: mediaEnabled)) {
                OutlinedTextField(
                    value = mediaDownloadPath,
                    onValueChange = { viewModel.setMediaDownloadPath(it) },
                    label = { Text(tr("media.download.path_label")) },
                    placeholder = { Text(com.davidadrianrg.atril.core.media.GameMediaPaths.resolveRoot("", settings.romsRootPath)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = { showMediaPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Filled.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(tr("common.select_folder"))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    tr("media.download.path_hint"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                }
            }
            }

            if (settingsCat == SettingsCategory.DOWNLOADS) {
            // ── Descargas ───────────────────────────────────────────────
            SettingsSection(
                icon = Icons.Outlined.DownloadForOffline,
                title = tr("settings.downloads.title"),
            ) {
                Text(
                    if (settings.maxConcurrentDownloads == 1) {
                        tr("android.config.downloads_parallel_one", settings.maxConcurrentDownloads)
                    } else {
                        tr("android.config.downloads_parallel_many", settings.maxConcurrentDownloads)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = settings.maxConcurrentDownloads.toFloat(),
                    onValueChange = { viewModel.setMaxConcurrentDownloads(it.toInt()) },
                    valueRange = 1f..5f,
                    steps = 3,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tr("android.config.wifi_only_downloads"), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            tr("android.config.wifi_only_downloads_hint"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = settings.wifiOnlyDownloads,
                        onCheckedChange = { viewModel.setWifiOnlyDownloads(it) },
                    )
                }
            }
            }

            if (settingsCat == SettingsCategory.APPEARANCE) {
            // ── Launcher: presentación por defecto ──────────────────────
            val launcherDefault by container.settingsRepository.launcherDefaultDisplay
                .collectAsState(initial = "card")
            val launcherScope = androidx.compose.runtime.rememberCoroutineScope()
            SettingsSection(
                icon = Icons.Outlined.SportsEsports,
                title = tr("settings.launcher.title"),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tr("settings.launcher.physical_default"), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            tr("settings.launcher.physical_default_desc"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = launcherDefault == "physical",
                        onCheckedChange = { physical ->
                            launcherScope.launch {
                                container.settingsRepository.setLauncherDefaultDisplay(
                                    if (physical) "physical" else "card",
                                )
                            }
                        },
                    )
                }
                // Medio físico automático: solo se ofrece con el launcher en modo físico y RomM conectado.
                if (launcherDefault == "physical" && settings.isConfigured) {
                    val autoPhysical by container.settingsRepository.autoPhysicalMedia.collectAsState(initial = false)
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tr("settings.launcher.auto_physical"), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                tr("settings.launcher.auto_physical_desc"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = autoPhysical,
                            onCheckedChange = { on -> launcherScope.launch { container.settingsRepository.setAutoPhysicalMedia(on) } },
                        )
                    }
                }
            }
            }

            if (settingsCat == SettingsCategory.SYNC) {
            // ── Sincronización de partidas ──────────────────────────────
            // Guardamos el estado local del toggle para que la UI no parpadee
            // con el valor inicial por defecto mientras carga DataStore.
            var saveSyncEnabledLocal by remember { mutableStateOf<Boolean?>(null) }
            val saveSyncEnabled by container.settingsRepository.saveSyncEnabled.collectAsState(initial = true)
            SettingsSection(
                icon = Icons.Outlined.Sync,
                title = tr("common.save_sync"),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tr("window.tray.sync_saves"), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            tr("android.config.sync_saves_hint"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = saveSyncEnabledLocal ?: saveSyncEnabled,
                        onCheckedChange = { enabled ->
                            saveSyncEnabledLocal = enabled
                            viewModel.setSaveSyncEnabled(enabled)
                        },
                    )
                }

                if (saveSyncEnabledLocal ?: saveSyncEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))

                    // Restricciones del auto-sync
                    val syncWifiOnly by viewModel.syncWifiOnly.collectAsState()
                    val syncChargingOnly by viewModel.syncChargingOnly.collectAsState()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tr("android.config.sync_wifi_only_label"), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                tr("android.config.sync_wifi_only_hint"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = syncWifiOnly,
                            onCheckedChange = { viewModel.setSyncConstraints(wifiOnly = it) },
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tr("android.config.sync_charging_only_label"), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                tr("android.config.sync_charging_only_hint"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = syncChargingOnly,
                            onCheckedChange = { viewModel.setSyncConstraints(chargingOnly = it) },
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Política de conflictos
                    val conflictPolicy by viewModel.conflictPolicy.collectAsState()
                    Text(tr("android.config.conflict_policy_prompt"), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        com.davidadrianrg.atril.core.sync.ConflictPolicy.entries.forEach { p ->
                            FilterChip(
                                selected = conflictPolicy == p.id,
                                onClick = { viewModel.setConflictPolicy(p.id) },
                                label = { Text(p.displayName, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                    Text(
                        tr("android.config.sync_backup_hint"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            }

            if (settingsCat == SettingsCategory.APPEARANCE) {
            // ── Idioma ─────────────────────────────────────────────────
            SettingsSection(
                icon = Icons.Outlined.Language,
                title = tr("config.language.title"),
            ) {
                val languageTag by viewModel.language.collectAsState(initial = "")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "" to tr("config.language.system"),
                        "en" to tr("config.language.en"),
                        "es" to tr("config.language.es"),
                    ).forEach { (tag, label) ->
                        FilterChip(
                            selected = languageTag == tag,
                            onClick = { viewModel.setLanguage(tag) },
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    tr("config.language.hint"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            }

            if (settingsCat == SettingsCategory.LOCAL) {
            // ── Carátulas de juegos locales (SteamGridDB / ScreenScraper) ──
            SettingsSection(
                icon = Icons.Outlined.Language,
                title = tr("config.localmeta.title"),
            ) {
                val saved by viewModel.scraperCredentials.collectAsState(
                    initial = com.davidadrianrg.atril.core.localgames.ScraperCredentials(),
                )
                var draft by remember(saved) { mutableStateOf(saved) }
                var justSaved by remember { mutableStateOf(false) }
                Text(
                    tr("config.localmeta.hint"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                @Composable
                fun field(label: String, value: String, secret: Boolean, onChange: (String) -> Unit) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { onChange(it); justSaved = false },
                        label = { Text(label) },
                        singleLine = true,
                        visualTransformation = if (secret) {
                            androidx.compose.ui.text.input.PasswordVisualTransformation()
                        } else {
                            androidx.compose.ui.text.input.VisualTransformation.None
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
                field(tr("config.localmeta.sgdb"), draft.steamGridDbKey, true) { draft = draft.copy(steamGridDbKey = it) }
                field(tr("config.localmeta.ss_user"), draft.ssUser, false) { draft = draft.copy(ssUser = it) }
                field(tr("config.localmeta.ss_pw"), draft.ssPassword, true) { draft = draft.copy(ssPassword = it) }
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { viewModel.saveScraperCredentials(draft); justSaved = true }, enabled = draft != saved) {
                        Text(tr("config.localmeta.save"))
                    }
                    if (justSaved) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(tr("config.localmeta.saved"), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            }

            if (settingsCat == SettingsCategory.APPEARANCE) {
            // ── Temas ──────────────────────────────────────────────────
            SettingsSection(
                icon = Icons.Outlined.Language,
                title = tr("config.theme.title"),
            ) {
                val themeId by viewModel.theme.collectAsState(initial = "")
                val current = com.davidadrianrg.atril.core.theme.AppTheme.fromId(themeId)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.davidadrianrg.atril.core.theme.AppTheme.entries.forEach { t ->
                        FilterChip(
                            selected = current == t,
                            onClick = { viewModel.setTheme(t) },
                            label = { Text(tr("config.theme.${t.id}"), style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    tr("config.theme.hint"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            }

            if (settingsCat == SettingsCategory.ABOUT) {
            // ── Controles (mando / teclado) ────────────────────────────
            SettingsSection(
                icon = Icons.Outlined.SportsEsports,
                title = tr("controls.title"),
            ) {
                listOf("controls.dpad", "controls.select", "controls.back", "controls.menu").forEach { k ->
                    Text(tr(k), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 3.dp))
                }
                Text(
                    tr("controls.note"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            }

            if (settingsCat == SettingsCategory.ABOUT) {
            // ── Actualizaciones ────────────────────────────────────────
            SettingsSection(
                icon = Icons.Outlined.SystemUpdateAlt,
                title = tr("settings.updates.title"),
            ) {
                Text(
                    tr("android.config.installed_version", viewModel.currentVersion),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))

                FilledTonalButton(
                    onClick = { viewModel.checkForUpdates() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isChecking,
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            Icons.Outlined.SystemUpdateAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(tr("settings.updates.check"))
                }

                when (val r = updateCheckState) {
                    is UpdateCheckResult.UpdateAvailable -> {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            tr("android.config.update_available", r.latestVersion),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            tr("android.config.update_size", "%.1f".format(r.apkSize / 1024f / 1024f)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Changelog del release (cuerpo tal cual de GitHub)
                        if (r.releaseNotes.isNotBlank()) {
                            var showNotes by remember { mutableStateOf(false) }
                            TextButton(onClick = { showNotes = !showNotes }) {
                                Text(if (showNotes) tr("settings.updates.hide_notes") else tr("settings.updates.show_notes"))
                            }
                            if (showNotes) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        r.releaseNotes,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier
                                            .padding(12.dp)
                                            .heightIn(max = 260.dp)
                                            .verticalScroll(rememberScrollState()),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        val skipped by viewModel.skippedVersion.collectAsState()
                        if (r.latestVersion == skipped) {
                            // Versión omitida: ofrecer re-comprobación sin instalador
                            Text(
                                tr("android.config.version_skipped", r.latestVersion),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FilledTonalButton(
                                onClick = { viewModel.recheckSkippedVersion() },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(tr("android.config.reoffer_version")) }
                        } else {
                            TextButton(onClick = { viewModel.skipCurrentVersion() }) {
                                Text(tr("settings.updates.skip_version"))
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))

                        when (val d = updateDownloadState) {
                            is UpdateDownloadState.Downloading -> {
                                LinearProgressIndicator(
                                    progress = { d.progress },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    tr("android.config.update_downloading_progress", "%.0f".format(d.progress * 100)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            is UpdateDownloadState.ReadyToInstall -> {
                                Button(
                                    onClick = { viewModel.installUpdate() },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(
                                        Icons.Outlined.SystemUpdateAlt,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.size(6.dp))
                                    Text(tr("android.config.update_install_now"))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    installFeedback
                                        ?: tr("android.config.update_install_hint"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (installFeedback != null) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            is UpdateDownloadState.Error -> {
                                Text(
                                    tr("common.error_detail", d.message),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                FilledTonalButton(
                                    onClick = { viewModel.downloadUpdate() },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(tr("android.config.update_retry_download"))
                                }
                            }
                            UpdateDownloadState.Idle -> {
                                FilledTonalButton(
                                    onClick = { viewModel.downloadUpdate() },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(
                                        Icons.Outlined.DownloadForOffline,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.size(6.dp))
                                    Text(tr("android.config.update_download", r.latestVersion))
                                }
                            }
                        }
                    }
                    is UpdateCheckResult.Error -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            tr("common.error_detail", r.message),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    UpdateCheckResult.UpToDate -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                tr("android.config.update_up_to_date"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                    null -> {}
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            }

            if (settingsCat == SettingsCategory.CONNECTION) {
            // ── Guardar (token manual) ─────────────────────────────────
            if (showManualToken) Button(
                onClick = {
                    viewModel.setServerUrl(serverUrl)
                    viewModel.setApiKey(apiKey)
                    container.configureApi(serverUrl, apiKey)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = serverUrl.isNotBlank() && apiKey.isNotBlank(),
            ) {
                Text(tr("android.config.save_button"), style = MaterialTheme.typography.labelLarge)
            }

            if (settings.isConfigured) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = tr("android.config.server_configured"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            }
        }
    }

    if (showQrDialog) {
        QrLoginDialog(
            state = qrState,
            onRetry = { viewModel.startQrLogin(serverUrl) },
            onClose = { viewModel.cancelQrLogin(); showQrDialog = false },
        )
        // Conectado: cerrar solo tras mostrar el éxito un instante.
        LaunchedEffect(qrState) {
            if (qrState is com.davidadrianrg.atril.core.auth.QrLoginState.Success) {
                kotlinx.coroutines.delay(1200)
                viewModel.cancelQrLogin(); showQrDialog = false
            }
        }
    }

    // ── Folder picker dialogs ───────────────────────────────────────────
    if (showRomsPicker) {
        FolderPickerDialog(
            initialPath = settings.romsRootPath.ifBlank {
                com.davidadrianrg.atril.data.local.SettingsDataStore.DEFAULT_ROMS_PATH
            },
            onDismiss = { showRomsPicker = false },
            onSelect = { path ->
                viewModel.setRomsRootPath(path)
                showRomsPicker = false
            },
        )
    }
    if (showEsdePicker) {
        FolderPickerDialog(
            initialPath = esdeDataDir.ifBlank {
                com.davidadrianrg.atril.data.local.SettingsDataStore.DEFAULT_ESDE_DATA_DIR
            },
            onDismiss = { showEsdePicker = false },
            onSelect = { path ->
                viewModel.setEsdeDataPath(path)
                showEsdePicker = false
            },
        )
    }
    if (showSecondaryRomsPicker) {
        FolderPickerDialog(
            initialPath = settings.secondaryRomsPath.ifBlank {
                com.davidadrianrg.atril.data.local.SettingsDataStore.DEFAULT_SECONDARY_ROMS_PATH
            },
            onDismiss = { showSecondaryRomsPicker = false },
            onSelect = { path ->
                viewModel.setSecondaryRomsPath(path)
                showSecondaryRomsPicker = false
            },
        )
    }
    if (showMediaPicker) {
        FolderPickerDialog(
            initialPath = mediaDownloadPath.ifBlank {
                com.davidadrianrg.atril.core.media.GameMediaPaths.resolveRoot("", settings.romsRootPath)
            },
            onDismiss = { showMediaPicker = false },
            onSelect = { path ->
                viewModel.setMediaDownloadPath(path)
                showMediaPicker = false
            },
        )
    }
}

@Composable
private fun SettingsSection(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun SettingsCategoryBar(selected: SettingsCategory, onSelect: (SettingsCategory) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SettingsCategory.entries.forEach { c ->
            androidx.compose.material3.FilterChip(
                selected = c == selected,
                onClick = { onSelect(c) },
                label = { Text(tr(c.labelKey)) },
            )
        }
    }
}
