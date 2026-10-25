package es.davidrg.rommsync.ui.screens

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
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
import es.davidrg.rommsync.RomMSyncApplication
import es.davidrg.rommsync.core.i18n.tr
import es.davidrg.rommsync.data.update.UpdateCheckResult
import es.davidrg.rommsync.data.update.UpdateDownloadState
import es.davidrg.rommsync.ui.components.FolderPickerDialog
import es.davidrg.rommsync.ui.viewmodel.ConfigViewModel
import es.davidrg.rommsync.ui.viewmodel.LibraryScanState
import es.davidrg.rommsync.util.hasAllFilesAccess
import es.davidrg.rommsync.util.rememberNotificationPermissionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen() {
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
    val compact = es.davidrg.rommsync.ui.components.rememberWindowInfo().isCompact
    rememberNotificationPermissionState()

    var serverUrl by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var apiKeyVisible by remember { mutableStateOf(false) }

    // ── Folder picker dialog state ───────────────────────────────────────
    var showRomsPicker by remember { mutableStateOf(false) }
    var showSecondaryRomsPicker by remember { mutableStateOf(false) }
    var showEsdePicker by remember { mutableStateOf(false) }
    var showRetroHraiPicker by remember { mutableStateOf(false) }

    LaunchedEffect(settings.serverUrl, settings.apiKey) {
        if (serverUrl.isEmpty() && settings.serverUrl.isNotEmpty()) serverUrl = settings.serverUrl
        if (apiKey.isEmpty() && settings.apiKey.isNotEmpty()) apiKey = settings.apiKey
    }

    // Cargar ruta de datos de ES-DE
    val esdeDataDir by container.settingsRepository.esdeDataDir.collectAsState(
        initial = es.davidrg.rommsync.data.local.SettingsDataStore.DEFAULT_ESDE_DATA_DIR,
    )

    // Cargar ruta de media de RetroHRAI
    val retroHraiMediaPath by container.settingsRepository.retroHraiMediaPath.collectAsState(
        initial = es.davidrg.rommsync.data.local.SettingsDataStore.DEFAULT_RETROHRAI_MEDIA_PATH,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("platforms.card.configure")) },
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
                            onClick = { es.davidrg.rommsync.util.requestAllFilesAccess(context) },
                        ) {
                            Text(tr("android.config.grant_permission"))
                        }
                    }
                }
            }

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

            // ── ES-DE Data Dir ───────────────────────────────────────────
            SettingsSection(
                icon = Icons.Outlined.Folder,
                title = tr("settings.esde.title"),
            ) {
                // Toggle maestro: oculta la función de exportación a ES-DE
                var esdeEnabledLocal by remember { mutableStateOf<Boolean?>(null) }
                val esdeEnabled by container.settingsRepository.esdeExportEnabled.collectAsState(initial = true)
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

            // ── RetroHRAI Media ──────────────────────────────────────────
            SettingsSection(
                icon = Icons.Outlined.Folder,
                title = tr("android.config.retrohrai_section"),
            ) {
                // Toggle maestro: oculta la exportación a RetroHRAI
                var retroHraiEnabledLocal by remember { mutableStateOf<Boolean?>(null) }
                val retroHraiEnabled by container.settingsRepository.retroHraiExportEnabled.collectAsState(initial = true)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tr("android.config.use_retrohrai"), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            tr("android.config.use_retrohrai_hint"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = retroHraiEnabledLocal ?: retroHraiEnabled,
                        onCheckedChange = { enabled ->
                            retroHraiEnabledLocal = enabled
                            viewModel.setRetroHraiExportEnabled(enabled)
                        },
                    )
                }
                if (retroHraiEnabledLocal ?: retroHraiEnabled) {
                OutlinedTextField(
                    value = retroHraiMediaPath,
                    onValueChange = { viewModel.setRetroHraiMediaPath(it) },
                    label = { Text(tr("android.config.retrohrai_media_path_label")) },
                    placeholder = { Text("/storage/emulated/0/RetroHrai/media") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(modifier = Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = { showRetroHraiPicker = true },
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
                    tr("android.config.retrohrai_media_hint"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                }
            }

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
                        es.davidrg.rommsync.core.sync.ConflictPolicy.entries.forEach { p ->
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

            // ── Guardar ─────────────────────────────────────────────────
            Button(
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

    // ── Folder picker dialogs ───────────────────────────────────────────
    if (showRomsPicker) {
        FolderPickerDialog(
            initialPath = settings.romsRootPath.ifBlank {
                es.davidrg.rommsync.data.local.SettingsDataStore.DEFAULT_ROMS_PATH
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
                es.davidrg.rommsync.data.local.SettingsDataStore.DEFAULT_ESDE_DATA_DIR
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
                es.davidrg.rommsync.data.local.SettingsDataStore.DEFAULT_SECONDARY_ROMS_PATH
            },
            onDismiss = { showSecondaryRomsPicker = false },
            onSelect = { path ->
                viewModel.setSecondaryRomsPath(path)
                showSecondaryRomsPicker = false
            },
        )
    }
    if (showRetroHraiPicker) {
        FolderPickerDialog(
            initialPath = retroHraiMediaPath.ifBlank {
                es.davidrg.rommsync.data.local.SettingsDataStore.DEFAULT_RETROHRAI_MEDIA_PATH
            },
            onDismiss = { showRetroHraiPicker = false },
            onSelect = { path ->
                viewModel.setRetroHraiMediaPath(path)
                showRetroHraiPicker = false
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
