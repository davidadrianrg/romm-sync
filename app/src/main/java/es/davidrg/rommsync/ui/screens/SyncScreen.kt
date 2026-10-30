package es.davidrg.rommsync.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import es.davidrg.rommsync.RomMSyncApplication
import es.davidrg.rommsync.core.i18n.tr
import es.davidrg.rommsync.data.local.SettingsDataStore
import es.davidrg.rommsync.data.sync.ConflictInfo
import es.davidrg.rommsync.data.sync.FailedOpInfo
import es.davidrg.rommsync.data.sync.SyncState
import es.davidrg.rommsync.ui.components.FolderPickerDialog
import es.davidrg.rommsync.ui.viewmodel.SavePreviewItem
import es.davidrg.rommsync.ui.viewmodel.SyncViewModel
import es.davidrg.rommsync.util.isIgnoringBatteryOptimizations
import es.davidrg.rommsync.util.requestIgnoreBatteryOptimizations
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen() {
    val context = LocalContext.current
    val container = (context.applicationContext as RomMSyncApplication).container
    val database = es.davidrg.rommsync.data.local.RomSyncDatabase.getDatabase(context)

    val viewModel: SyncViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                SyncViewModel(
                    settingsRepository = container.settingsRepository,
                    saveSyncManager = container.saveSyncManager,
                    romDao = database.romDao(),
                    platformDao = database.platformDao(),
                    syncedHashStore = es.davidrg.rommsync.data.sync.SyncedHashStore(context),
                    appContext = context.applicationContext,
                )
            }
        }
    )

    val settings by container.settingsRepository.settings.collectAsState(
        initial = es.davidrg.rommsync.data.local.ServerConfig("", "", "", 2)
    )
    val compact = es.davidrg.rommsync.ui.components.rememberWindowInfo().isCompact
    val syncState by viewModel.syncState.collectAsState()
    val lastTimestamp by viewModel.lastSyncTimestamp.collectAsState()
    val lastSummary by viewModel.lastSyncSummary.collectAsState()
    val retroArchBasePath by viewModel.retroArchBasePath.collectAsState()
    val syncInterval by viewModel.syncIntervalMinutes.collectAsState()
    val localSaves by viewModel.localSaves.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val pathWarnings by viewModel.pathWarnings.collectAsState()
    val scanChecked by viewModel.scanCheckedCount.collectAsState()
    val conflicts by viewModel.conflicts.collectAsState()
    val failedOps by viewModel.failedOps.collectAsState()
    val resolvingConflict by viewModel.resolvingConflict.collectAsState()

    var retroArchPath by remember { mutableStateOf("") }
    var showFolderPicker by remember { mutableStateOf(false) }

    LaunchedEffect(retroArchBasePath) {
        if (retroArchPath.isEmpty() && retroArchBasePath.isNotEmpty()) {
            retroArchPath = retroArchBasePath
        }
    }

    // Persistir resultado cuando el sync termina
    LaunchedEffect(syncState) {
        when (val s = syncState) {
            is SyncState.Success -> {
                container.settingsRepository.setLastSync(System.currentTimeMillis(), s.message)
            }
            is SyncState.Failed -> {
                container.settingsRepository.setLastSync(System.currentTimeMillis(), tr("common.error_detail", s.message))
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tr("android.sync.title")) },
                modifier = Modifier.height(if (compact) 48.dp else 64.dp),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp),
        ) {
            // -- Estado actual
            item { StatusCard(syncState, lastTimestamp, lastSummary, onCancel = viewModel::cancelSync) }

            // -- Conflictos pendientes de resolución
            if (conflicts.isNotEmpty()) {
                item(key = "conflicts_section") {
                    ConflictSection(
                        conflicts = conflicts,
                        resolvingKey = resolvingConflict,
                        onResolve = { romId, fileName, resolution ->
                            viewModel.resolveConflict(romId, fileName, resolution)
                        },
                    )
                }
            }

            // -- Fallidos del último sync (detalle de qué y por qué)
            if (failedOps.isNotEmpty()) {
                item(key = "failures_section") {
                    FailedOpsSection(failedOps)
                }
            }

            // -- Boton de sincronizar
            item {
                val isRunning = syncState is SyncState.Running || syncState is SyncState.Pending
                Button(
                    onClick = { viewModel.triggerSync() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = settings.isConfigured && !isRunning,
                ) {
                    if (isRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(modifier = Modifier.size(10.dp))
                        Text(
                            if (syncState is SyncState.Pending) tr("android.sync.button.pending")
                            else tr("saves.sync_button.syncing"),
                        )
                    } else {
                        Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(tr("saves.sync_button.sync_now"))
                    }
                }

                if (!settings.isConfigured) {
                    Text(
                        tr("android.sync.not_configured"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            // -- Ruta base RetroArch
            item {
                SyncSection(
                    icon = Icons.Outlined.Storage,
                    title = tr("android.sync.retroarch_base.title"),
                ) {
                    OutlinedTextField(
                        value = retroArchPath,
                        onValueChange = {
                            retroArchPath = it
                            viewModel.setRetroArchBasePath(it)
                        },
                        label = { Text(tr("android.sync.retroarch_base.label")) },
                        placeholder = { Text("/storage/emulated/0/RetroArch") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = { showFolderPicker = true },
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
                        tr("android.sync.retroarch_base.hint"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // -- Sync periodico
            item {
                SyncSection(
                    icon = Icons.Outlined.Schedule,
                    title = tr("saves.auto_sync.title"),
                ) {
                    Text(
                        if (syncInterval > 0) tr("saves.auto_sync.every_minutes", syncInterval)
                        else tr("saves.auto_sync.disabled"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val options = listOf(0, 1, 5, 15)
                        val labels = listOf(tr("saves.auto_sync.off_chip"), "1m", "5m", "15m")
                        options.forEachIndexed { index, minutes ->
                            FilterChip(
                                selected = syncInterval == minutes,
                                onClick = { viewModel.setSyncInterval(minutes) },
                                label = { Text(labels[index]) },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    if (syncInterval in 1..14) {
                        Text(
                            tr("saves.auto_sync.fast_hint"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        tr("android.sync.auto_sync.hint"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // Aviso de optimización de batería
                    if (syncInterval > 0 && !isIgnoringBatteryOptimizations(context)) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                            ),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    tr("android.sync.battery.title"),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    tr("android.sync.battery.message"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                FilledTonalButton(
                                    onClick = { requestIgnoreBatteryOptimizations(context) },
                                ) {
                                    Text(tr("android.sync.battery.disable"))
                                }
                            }
                        }
                    }
                }
            }

            // -- Preview de saves con cambios pendientes
            item {
                SyncSection(
                    icon = Icons.Outlined.Info,
                    title = tr("saves.pending.title"),
                ) {
                    if (isScanning) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                tr("android.sync.pending.checking"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else if (localSaves.isEmpty()) {
                        if (scanChecked < 0) {
                            Text(
                                tr("android.sync.pending.prompt"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            // Escaneo corrido sin resultados: decirlo explícitamente —
                            // repetir "Pulsa comprobar…" parecía que no había corrido.
                            Text(
                                if (scanChecked == 1) {
                                    tr("android.sync.pending.none_one", scanChecked)
                                } else {
                                    tr("android.sync.pending.none_many", scanChecked)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                tr("android.sync.pending.empty_hint"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Text(
                            if (localSaves.size == 1) {
                                tr("android.sync.pending.count_one", localSaves.size)
                            } else {
                                tr("android.sync.pending.count_many", localSaves.size)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            tr("android.sync.pending.upload_hint"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (pathWarnings.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    tr("android.sync.path_warnings.title"),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                pathWarnings.forEach { warning ->
                                    Text(
                                        warning,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    tr("android.sync.path_warnings.hint"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = { viewModel.scanLocalSaves() },
                        enabled = !isScanning,
                    ) {
                        Icon(
                            Icons.Outlined.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(tr("android.sync.pending.check_button"))
                    }
                }
            }

            // -- Lista de saves
            if (!isScanning && localSaves.isNotEmpty()) {
                val grouped = localSaves.groupBy { it.platformSlug }
                grouped.forEach { (platform, saves) ->
                    item(key = "header_$platform") {
                        Text(
                            platform,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    items(saves, key = { "${it.platformSlug}_${it.romName}_${it.fileName}" }) { save ->
                        SaveItemRow(save)
                    }
                    item(key = "divider_$platform") {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }

    // Folder picker dialog
    if (showFolderPicker) {
        FolderPickerDialog(
            initialPath = retroArchBasePath.ifBlank {
                SettingsDataStore.DEFAULT_RETROARCH_PATH
            },
            onDismiss = { showFolderPicker = false },
            onSelect = { path ->
                viewModel.setRetroArchBasePath(path)
                retroArchPath = path
                showFolderPicker = false
            },
        )
    }
}

@Composable
private fun StatusCard(
    syncState: SyncState,
    lastTimestamp: Long,
    lastSummary: String,
    onCancel: () -> Unit,
) {
    val (icon, iconTint, title, subtitle) = when (syncState) {
        SyncState.Idle -> {
            if (lastTimestamp > 0) {
                Quad(
                    Icons.Filled.CheckCircle,
                    MaterialTheme.colorScheme.secondary,
                    tr("saves.status.last_sync"),
                    formatTimestamp(lastTimestamp),
                )
            } else {
                Quad(
                    Icons.Outlined.Sync,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    tr("saves.status.never_synced"),
                    tr("saves.status.never_synced_hint"),
                )
            }
        }
        SyncState.Pending -> Quad(
            Icons.Filled.Sync,
            MaterialTheme.colorScheme.primary,
            tr("android.sync.status.queued"),
            tr("android.sync.status.waiting"),
        )
        SyncState.Running -> Quad(
            Icons.Filled.Sync,
            MaterialTheme.colorScheme.primary,
            tr("saves.status.syncing"),
            tr("saves.status.connecting"),
        )
        is SyncState.Success -> parseSyncResult(syncState.message, syncState.hasWarnings, syncState.allSynced)
        is SyncState.Failed -> Quad(
            Icons.Filled.Error,
            MaterialTheme.colorScheme.error,
            tr("common.error"),
            syncState.message,
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (syncState is SyncState.Running) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    strokeWidth = 4.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(48.dp),
                )
            }

            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (syncState is SyncState.Running || syncState is SyncState.Pending) {
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(tr("common.cancel"))
                }
            }

            if (syncState is SyncState.Idle && lastSummary.isNotBlank() && lastTimestamp > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        tr("android.sync.result_label") + " ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        lastSummary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun SaveItemRow(save: SavePreviewItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                save.romName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                save.fileName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            formatTimestamp(save.lastModified),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SyncSection(
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

/**
 * Sección con el detalle de operaciones fallidas del último sync: qué save,
 * de qué juego y por qué falló (subida o descarga).
 */
@Composable
private fun FailedOpsSection(failures: List<FailedOpInfo>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    tr("saves.failed.title", failures.size),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                tr("android.sync.failed.hint"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(modifier = Modifier.height(12.dp))

            failures.forEach { failure ->
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    Text(
                        failure.romName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${failure.fileName} — ${failure.action}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        failure.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

/**
 * Sección de conflictos detectados en el último sync. Cada conflicto ofrece
 * dos acciones explícitas: mantener la versión local (subirla) o restaurar
 * la versión del servidor (descargarla).
 */
@Composable
private fun ConflictSection(
    conflicts: List<ConflictInfo>,
    resolvingKey: String?,
    onResolve: (romId: Int, fileName: String, resolution: String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    tr("saves.conflicts.title", conflicts.size),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                tr("android.sync.conflicts.description"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(modifier = Modifier.height(12.dp))

            conflicts.forEach { conflict ->
                ConflictItemRow(
                    conflict = conflict,
                    isResolving = resolvingKey == "${conflict.romId}_${conflict.fileName}",
                    onResolve = onResolve,
                )
            }
        }
    }
}

@Composable
private fun ConflictItemRow(
    conflict: ConflictInfo,
    isResolving: Boolean,
    onResolve: (romId: Int, fileName: String, resolution: String) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(
            conflict.romName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            buildString {
                append(conflict.fileName)
                conflict.serverUpdatedAt?.let {
                    append("  ·  ")
                    append(tr("saves.conflicts.server_time", formatServerTimestamp(it)))
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(
                onClick = { onResolve(conflict.romId, conflict.fileName, "local") },
                enabled = !isResolving,
                modifier = Modifier.weight(1f),
            ) {
                if (isResolving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        Icons.Filled.CloudUpload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(modifier = Modifier.size(6.dp))
                Text(tr("saves.conflicts.keep_local"), maxLines = 1)
            }
            FilledTonalButton(
                onClick = { onResolve(conflict.romId, conflict.fileName, "server") },
                enabled = !isResolving,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    Icons.Filled.CloudDownload,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(tr("saves.conflicts.keep_server"), maxLines = 1)
            }
        }
    }
}

/** Formatea un timestamp ISO del servidor ("2026-08-17T14:23:00Z") legible. */
private fun formatServerTimestamp(iso: String): String = try {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
    val date = sdf.parse(iso.take(19))
    val output = SimpleDateFormat("d MMM HH:mm", Locale.getDefault())
    date?.let { output.format(it) } ?: iso.take(16)
} catch (_: Exception) {
    iso.take(16)
}
/**
 * Estado visual del resultado del sync: se decide por las señales explícitas
 * de SyncState.Success (avisos / nada que hacer), nunca leyendo el texto del
 * mensaje, que es traducible y en otro idioma rompería el chequeo.
 */
@Composable
private fun parseSyncResult(
    message: String,
    hasWarnings: Boolean,
    allSynced: Boolean,
): Quad<ImageVector, androidx.compose.ui.graphics.Color, String, String> {
    return if (hasWarnings) {
        Quad(
            Icons.Filled.Warning,
            MaterialTheme.colorScheme.tertiary,
            tr("android.sync.status.warnings"),
            message,
        )
    } else if (allSynced) {
        Quad(
            Icons.Filled.CheckCircle,
            MaterialTheme.colorScheme.secondary,
            tr("android.sync.status.all_synced"),
            tr("android.sync.status.no_changes"),
        )
    } else {
        Quad(
            Icons.Filled.CheckCircle,
            MaterialTheme.colorScheme.secondary,
            tr("android.sync.completed_title"),
            message,
        )
    }
}

private data class Quad<A, B, C, D>(
    val first: A, val second: B, val third: C, val fourth: D,
)

private fun formatTimestamp(millis: Long): String {
    if (millis <= 0) return ""
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(millis))
}
