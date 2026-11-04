package com.davidadrianrg.atril.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.davidadrianrg.atril.RomMSyncApplication
import com.davidadrianrg.atril.core.i18n.tr
import com.davidadrianrg.atril.domain.model.Platform
import com.davidadrianrg.atril.ui.viewmodel.PlatformsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlatformsScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val container = (context.applicationContext as RomMSyncApplication).container

    val viewModel: PlatformsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { PlatformsViewModel(container.romRepository) }
        }
    )

    val platforms by viewModel.platforms.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val libraryStats by viewModel.libraryStats.collectAsState()
    val settings by container.settingsRepository.settings.collectAsState(
        initial = com.davidadrianrg.atril.data.local.ServerConfig("", "", "", 2)
    )

    val retroArchBasePath by container.settingsRepository.retroArchBasePath.collectAsState(
        initial = com.davidadrianrg.atril.data.local.SettingsDataStore.DEFAULT_RETROARCH_PATH,
    )

    val allVisible = platforms.isNotEmpty() && platforms.all { it.visible }
    val windowInfo = com.davidadrianrg.atril.ui.components.rememberWindowInfo()
    val compact = windowInfo.isCompact

    // La configuración de sync por plataforma solo tiene sentido si la
    // función está activada en Configuración.
    val saveSyncEnabled by container.settingsRepository.saveSyncEnabled.collectAsState(initial = true)
    val esdeExportEnabled by container.settingsRepository.esdeExportEnabled.collectAsState(initial = false)
    val retroHraiExportEnabled by container.settingsRepository.retroHraiExportEnabled.collectAsState(initial = false)

    LaunchedEffect(settings.isConfigured) {
        if (settings.isConfigured && platforms.isEmpty()) {
            viewModel.refreshPlatforms(settings.serverUrl, settings.apiKey)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = tr("common.back"),
                        )
                    }
                },
                title = { Text(tr("nav.platforms")) },
                modifier = Modifier.height(if (compact) 48.dp else 64.dp),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                actions = {
                    if (platforms.isNotEmpty()) {
                        TextButton(
                            onClick = { viewModel.setAllVisible(!allVisible) },
                            contentPadding = PaddingValues(horizontal = 10.dp),
                        ) {
                            Icon(
                                if (allVisible) Icons.Filled.Deselect else Icons.Filled.SelectAll,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(if (allVisible) tr("android.platforms.none") else tr("android.platforms.all"))
                        }
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            if (!settings.isConfigured) {
                com.davidadrianrg.atril.ui.components.EmptyState(
                    icon = Icons.Outlined.Storage,
                    title = tr("common.no_server_configured"),
                    description = tr("android.platforms.empty.no_server"),
                )
                return@Column
            }

            // Header card: resumen + actualizar
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            tr("android.platforms.visible_count", platforms.count { it.visible }, platforms.size),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            tr("android.platforms.visible_label"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalButton(
                        onClick = { viewModel.refreshPlatforms(settings.serverUrl, settings.apiKey) },
                        enabled = !isLoading,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(tr("platforms.refresh"))
                    }
                }
            }

            // Estadísticas locales: ROMs descargados y espacio por plataforma.
            // Desplegable: colapsada ocupa una sola fila para no tapar la
            // lista de plataformas; el detalle por plataforma va detrás.
            var statsExpanded by remember { mutableStateOf(false) }
            var showAllPlatforms by remember { mutableStateOf(false) }
            libraryStats?.let { stats ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    // Fila resumen siempre visible: abre/cierra el detalle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { statsExpanded = !statsExpanded }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            tr("platforms.local_roms", stats.totalRoms),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                formatStatsBytes(stats.totalBytes),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Icon(
                                if (statsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (statsExpanded) tr("android.platforms.hide_detail") else tr("android.platforms.show_detail"),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    // Detalle: top 5 plataformas por tamaño
                    if (statsExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 12.dp),
                        ) {
                            stats.byPlatform.take(5).forEach { p ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        p.platformSlug,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        "${p.romCount} · ${formatStatsBytes(p.totalBytes)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (stats.byPlatform.size > 5) {
                                TextButton(
                                    onClick = { showAllPlatforms = true },
                                    contentPadding = PaddingValues(
                                        horizontal = 0.dp,
                                        vertical = 0.dp,
                                    ),
                                    modifier = Modifier.padding(top = 2.dp),
                                ) {
                                    Text(
                                        tr("android.platforms.show_more_platforms", stats.byPlatform.size - 5),
                                    )
                                }
                            }
                        }
                    }
                }
                }


            // Sheet con el detalle COMPLETO de plataformas: la tarjeta solo
            // muestra el top 5 y el resto no cabe en el móvil.
            if (showAllPlatforms) {
                libraryStats?.let { stats ->
                    ModalBottomSheet(
                        onDismissRequest = { showAllPlatforms = false },
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                            Text(
                                tr("android.platforms.local_roms_by_platform"),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "${stats.totalRoms} ROMs · ${formatStatsBytes(stats.totalBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(
                                start = 24.dp,
                                end = 24.dp,
                                bottom = 24.dp,
                            ),
                        ) {
                            items(stats.byPlatform, key = { it.platformSlug }) { p ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        p.platformSlug,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        "${p.romCount} · ${formatStatsBytes(p.totalBytes)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
            ) {
                items(platforms, key = { it.id }) { platform ->
                    PlatformCard(
                        platform = platform,
                        retroArchBasePath = retroArchBasePath,
                        container = container,
                        syncEnabled = saveSyncEnabled,
                        esdeEnabled = esdeExportEnabled,
                        retroHraiEnabled = retroHraiExportEnabled,
                        dualPathsEnabled = settings.dualRomsPathsEnabled,
                        secondaryRomsPath = settings.secondaryRomsPath,
                        onToggle = { viewModel.togglePlatformVisibility(it) },
                        onEmulatorChange = { id, emu -> viewModel.updatePlatformEmulator(id, emu) },
                        onSavesPathChange = { id, path -> viewModel.updatePlatformSavesPath(id, path) },
                        onDownloadStorageChange = { id, storage ->
                            viewModel.updatePlatformDownloadStorage(id, storage)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlatformCard(
    platform: Platform,
    retroArchBasePath: String,
    container: com.davidadrianrg.atril.data.AppContainer,
    syncEnabled: Boolean,
    esdeEnabled: Boolean,
    retroHraiEnabled: Boolean,
    dualPathsEnabled: Boolean,
    secondaryRomsPath: String,
    onToggle: (Platform) -> Unit,
    onEmulatorChange: (Int, String?) -> Unit,
    onSavesPathChange: (Int, String?) -> Unit,
    onDownloadStorageChange: (Int, com.davidadrianrg.atril.domain.model.PlatformDownloadStorage) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val availableEmulators = remember(platform.slug) {
        com.davidadrianrg.atril.core.sync.platform.SaveHandlerRegistry.getAvailableEmulators(platform.slug)
    }
    val defaultEmulator = remember(platform.slug) {
        com.davidadrianrg.atril.core.sync.platform.SaveHandlerRegistry.getDefaultEmulator(platform.slug)
    }
    val currentEmulator = platform.emulatorId ?: defaultEmulator.id
    val defaultSavesPath = remember(currentEmulator, platform.slug, retroArchBasePath) {
        com.davidadrianrg.atril.core.sync.platform.SaveHandlerRegistry.getDefaultSavesPath(
            emulatorId = currentEmulator,
            platformSlug = platform.slug,
            retroArchBase = retroArchBasePath,
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (platform.visible) {
                MaterialTheme.colorScheme.surfaceContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                if (platform.visible) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHighest,
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = platform.slug.take(2).uppercase(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (platform.visible) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(modifier = Modifier.size(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            platform.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${platform.slug} • ${platform.romCount} ROMs • $currentEmulator",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // Botón de expandir configuración (sync, export y destino de
                // descarga): sin sentido si todas las funciones configurables
                // están desactivadas.
                if (syncEnabled || esdeEnabled || retroHraiEnabled || dualPathsEnabled) {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = tr("android.platforms.configure_sync"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(
                    checked = platform.visible,
                    onCheckedChange = { onToggle(platform) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                )
            }

            // Panel expandible con config de sync
            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))

                // ── Destino de descarga por defecto (modo dos rutas) ────
                if (dualPathsEnabled) {
                    Text(
                        tr("nav.downloads"),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        tr("android.platforms.downloads_hint"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        com.davidadrianrg.atril.domain.model.PlatformDownloadStorage.entries.forEach { storage ->
                            FilterChip(
                                selected = platform.downloadStorage == storage,
                                onClick = { onDownloadStorageChange(platform.id, storage) },
                                label = { Text(storage.label) },
                            )
                        }
                    }
                    // Ruta efectiva según la selección (feedback visual)
                    val effectivePath = when (platform.downloadStorage) {
                        com.davidadrianrg.atril.domain.model.PlatformDownloadStorage.INTERNAL ->
                            tr("android.platforms.storage_internal")
                        com.davidadrianrg.atril.domain.model.PlatformDownloadStorage.SD ->
                            secondaryRomsPath
                        else -> tr("android.platforms.storage_ask")
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        effectivePath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (syncEnabled) {
                Text(
                    tr("android.platforms.sync_config"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Selector de emulador
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    availableEmulators.forEach { emu ->
                        FilterChip(
                            selected = currentEmulator == emu.id,
                            onClick = {
                                onEmulatorChange(
                                    platform.id,
                                    if (emu.id == defaultEmulator.id) null else emu.id,
                                )
                            },
                            label = { Text(emu.displayName) },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Override de ruta de saves: campo editable + explorador.
                // Se puede escribir a mano (rutas /data en root) o elegir carpeta.
                var showFolderPicker by remember { mutableStateOf(false) }
                val hasOverride = !platform.savesPathOverride.isNullOrBlank()
                val displayedPath = platform.savesPathOverride?.takeIf { it.isNotBlank() }
                    ?: defaultSavesPath
                var manualPath by remember(platform.savesPathOverride, defaultSavesPath) {
                    mutableStateOf(displayedPath)
                }

                Text(
                    tr("android.platforms.saves_path"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (hasOverride) tr("android.platforms.saves_path_custom") else tr("android.platforms.saves_path_default"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = manualPath,
                    onValueChange = { manualPath = it },
                    label = { Text(tr("android.platforms.saves_path")) },
                    placeholder = { Text(tr("android.platforms.saves_path_placeholder")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        IconButton(onClick = { showFolderPicker = true }) {
                            Icon(
                                Icons.Filled.Folder,
                                contentDescription = tr("common.select_folder"),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(
                        onClick = { showFolderPicker = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            Icons.Filled.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(tr("common.select_folder"))
                    }
                    if (manualPath != displayedPath) {
                        Button(
                            onClick = { onSavesPathChange(platform.id, manualPath.trim()) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(tr("game.sync.save_path"))
                        }
                    } else if (hasOverride) {
                        TextButton(
                            onClick = { onSavesPathChange(platform.id, null) },
                        ) {
                            Text(tr("game.sync.reset"))
                        }
                    }
                }

                if (showFolderPicker) {
                    com.davidadrianrg.atril.ui.components.FolderPickerDialog(
                        initialPath = displayedPath,
                        onDismiss = { showFolderPicker = false },
                        onSelect = { selected ->
                            onSavesPathChange(platform.id, selected)
                            showFolderPicker = false
                        },
                    )
                }
                } // end if (syncEnabled)

                // ── Exportar metadatos (ES-DE / RetroHRAI) ────────────────
                // Visible si al menos uno de los dos frontends está activado
                // en Configuración.
                if (esdeEnabled || retroHraiEnabled) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    if (esdeEnabled) tr("android.platforms.export_esde") else tr("android.platforms.export_retrohrai"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (esdeEnabled) {
                    Text(
                        tr("android.platforms.export_esde_desc"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        tr("android.platforms.export_retrohrai_desc"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                val exportState by container.metadataExportManager
                    .observeExportState(platform.slug).collectAsState(
                        initial = com.davidadrianrg.atril.data.metadata.ExportState.Idle
                    )
                val isExporting = exportState is com.davidadrianrg.atril.data.metadata.ExportState.Running ||
                    exportState is com.davidadrianrg.atril.data.metadata.ExportState.Pending

                // Toggle: incluir RetroHRAI además de ES-DE (solo si ambos
                // frontends están activados)
                var exportRetroHrai by remember { mutableStateOf(false) }

                if (retroHraiEnabled && esdeEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Switch(
                            checked = exportRetroHrai,
                            onCheckedChange = { exportRetroHrai = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ),
                        )
                        Spacer(modifier = Modifier.size(10.dp))
                        Text(
                            tr("android.platforms.export_retrohrai_too"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                FilledTonalButton(
                    onClick = {
                        container.metadataExportManager.triggerExport(
                            platformId = platform.id,
                            platformSlug = platform.slug,
                            gamelist = esdeEnabled,
                            retroHrai = retroHraiEnabled && (exportRetroHrai || !esdeEnabled),
                        )
                    },
                    enabled = !isExporting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            Icons.Outlined.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(if (isExporting) tr("android.platforms.exporting") else tr("android.platforms.export_metadata"))
                }

                // Feedback de resultado
                when (exportState) {
                    is com.davidadrianrg.atril.data.metadata.ExportState.Success -> {
                        val s = exportState as com.davidadrianrg.atril.data.metadata.ExportState.Success
                        Text(
                            tr("android.platforms.export_success", s.mediaDownloaded, s.entriesCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    is com.davidadrianrg.atril.data.metadata.ExportState.Failed -> {
                        Text(
                            tr("android.platforms.export_error"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    else -> {}
                }
                } // end if (esdeEnabled || retroHraiEnabled)
            }
        }
    }
}

/** 1_500_000_000 → "1.4 GB" para la tarjeta de estadísticas. */
private fun formatStatsBytes(bytes: Long): String {
    val gb = bytes / 1_000_000_000.0
    val mb = bytes / 1_000_000.0
    return when {
        gb >= 1 -> String.format(java.util.Locale.getDefault(), "%.1f GB", gb)
        mb >= 1 -> String.format(java.util.Locale.getDefault(), "%.0f MB", mb)
        else -> "${bytes / 1000} KB"
    }
}
