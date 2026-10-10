package es.davidrg.rommsync.desktop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.davidrg.rommsync.core.i18n.tr
import es.davidrg.rommsync.core.remote.dto.PlatformDto
import es.davidrg.rommsync.core.sync.platform.SaveHandlerRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DesktopApp(state: DesktopAppState) {
    val section by state.section.collectAsState()
    val snackbar by state.snackbar.collectAsState()

    LaunchedEffect(snackbar) {
        if (snackbar != null) {
            delay(2800)
            state.consumeSnackbar()
        }
    }

    Box(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            AppNavigationRail(state, section)
            Surface(Modifier.weight(1f).fillMaxSize()) {
                when (section) {
                    Section.PLATFORMS -> PlatformsScreen(state)
                    Section.LIBRARY -> LibraryScreen(state)
                    Section.DOWNLOADS -> DownloadsScreen(state)
                    Section.SAVES -> SavesScreen(state)
                    Section.SETTINGS -> SettingsScreen(state)
                }
            }
        }
        // Snackbar flotante (feedback de acciones, igual que Android).
        snackbar?.let { msg ->
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shape = RoundedCornerShape(10.dp),
                shadowElevation = 8.dp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp),
            ) {
                Text(msg, Modifier.padding(horizontal = 16.dp, vertical = 12.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun AppNavigationRail(state: DesktopAppState, section: Section) {
    val tasks by state.tasks.collectAsState()
    val activeDownloads = tasks.count { it.active }

    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        header = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        AppIcons.Gamepad,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text("RomM Sync", style = MaterialTheme.typography.labelMedium)
            }
        },
    ) {
        Spacer(Modifier.height(12.dp))
        RailItem(state, Section.PLATFORMS, section, AppIcons.Storage, tr("nav.platforms"))
        RailItem(state, Section.LIBRARY, section, AppIcons.Gamepad, tr("nav.library"))
        RailItem(
            state, Section.DOWNLOADS, section, AppIcons.Download, tr("nav.downloads"),
            badge = activeDownloads.takeIf { it > 0 },
        )
        RailItem(state, Section.SAVES, section, AppIcons.Sync, tr("nav.saves"))
        RailItem(state, Section.SETTINGS, section, AppIcons.Settings, tr("nav.settings"))
        Spacer(Modifier.weight(1f))
        Text(
            "v${DesktopConfig.appVersion}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )
    }
}

@Composable
private fun RailItem(
    state: DesktopAppState,
    target: Section,
    current: Section,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    badge: Int? = null,
) {
    val selected = current == target
    NavigationRailItem(
        selected = selected,
        onClick = { state.navigate(target) },
        icon = {
            if (badge != null) {
                BadgedBox(badge = {
                    Badge(containerColor = MaterialTheme.colorScheme.error) { Text("$badge") }
                }) {
                    Icon(icon, contentDescription = label)
                }
            } else {
                Icon(icon, contentDescription = label)
            }
        },
        label = { Text(label) },
    )
}

// ═══════════════════════════════════════════════════════════════════════
// Plataformas
// ═══════════════════════════════════════════════════════════════════════

@Composable
fun PlatformsScreen(state: DesktopAppState) {
    val platforms by state.platforms.collectAsState()
    val hidden by state.hiddenPlatforms.collectAsState()
    val loading by state.loadingPlatforms.collectAsState()
    val connected by state.connected.collectAsState()
    val stats by state.localStats.collectAsState()

    LaunchedEffect(connected) {
        if (connected) state.refreshLocalStats()
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        ScreenHeader(
            tr("nav.platforms"),
            subtitle = tr("platforms.visible_count", platforms.size - hidden.size, platforms.size),
        ) {
            IconButton(onClick = state::refreshCurrentView, enabled = connected) {
                Icon(AppIcons.Refresh, contentDescription = tr("platforms.refresh"))
            }
            Spacer(Modifier.width(8.dp))
            val allOn = hidden.isEmpty()
            OutlinedButton(onClick = { state.toggleAllPlatforms(!allOn) }) {
                Text(if (allOn) tr("platforms.disable_all") else tr("platforms.enable_all"))
            }
        }
        Spacer(Modifier.height(12.dp))

        if (!connected && platforms.isEmpty()) {
            if (loading) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                EmptyState(
                    icon = AppIcons.Storage,
                    title = tr("common.no_server_configured"),
                    description = tr("platforms.empty.no_server"),
                )
            }
        } else {
            // Stats locales de la biblioteca descargada
            val totalRoms = stats.values.sumOf { it.romCount }
            if (totalRoms > 0) {
                val totalBytes = stats.values.sumOf { it.totalBytes }
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tr("platforms.local_roms", totalRoms), style = MaterialTheme.typography.titleSmall)
                            Text(
                                tr("platforms.with_downloads", stats.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            formatBytes(totalBytes),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            if (loading && platforms.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val gridState = rememberLazyGridState()
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(260.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    gridItems(platforms, key = { it.id }) { p ->
                        PlatformConfigCard(p, hidden.contains(p.slug), stats[p.slug], state)
                    }
                }
                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(gridState),
                )
            }
        }
    }
}

@Composable
private fun PlatformConfigCard(
    p: PlatformDto,
    hidden: Boolean,
    stat: PlatformLocalStat?,
    state: DesktopAppState,
) {
    var showConfig by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (hidden) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(14.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Avatar: logo de la plataforma si el servidor lo sirve, si no
                // las 2 primeras letras del slug (como Android).
                Box(
                    Modifier.size(44.dp).background(
                        if (hidden) MaterialTheme.colorScheme.surfaceContainerHigh
                        else MaterialTheme.colorScheme.primaryContainer,
                        CircleShape,
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    val logoBitmap = p.logoUrl?.takeIf { it.isNotBlank() }?.let { produceCover(it) }
                    if (logoBitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = logoBitmap,
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.size(36.dp).background(
                                MaterialTheme.colorScheme.surfaceContainerHighest,
                                androidx.compose.foundation.shape.CircleShape,
                            ),
                        )
                    } else {
                        Text(
                            p.slug.take(2).uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (hidden) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        p.displayName ?: p.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    val subtitle = buildString {
                        append(p.slug)
                        append(" • ${p.romCount} ROMs")
                        stat?.let { append(" • "); append(tr("platforms.card.local_count", it.romCount)) }
                    }
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = !hidden, onCheckedChange = { state.togglePlatform(p.slug) })
            }
            Spacer(Modifier.height(6.dp))
            Row {
                TextButton(onClick = { showConfig = true }) {
                    Text(tr("platforms.card.configure"), style = MaterialTheme.typography.labelMedium)
                }
                stat?.takeIf { it.totalBytes > 0 }?.let {
                    Spacer(Modifier.weight(1f))
                    Text(
                        formatBytes(it.totalBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically).padding(end = 4.dp),
                    )
                }
            }
        }
        if (showConfig) {
            PlatformConfigDialog(p, state) { showConfig = false }
        }
    }
}

@Composable
private fun PlatformConfigDialog(p: PlatformDto, state: DesktopAppState, onDismiss: () -> Unit) {
    val cfg = remember { state.platformConfig(p.slug) }
    var folder by remember { mutableStateOf(cfg.romsFolderOverride ?: p.slug) }
    var savesPath by remember { mutableStateOf(cfg.savesPathOverride ?: "") }
    var emulator by remember { mutableStateOf(cfg.emulatorId ?: "") }

    // Emuladores aplicables a desktop (se excluye el handler nativo Android).
    val emulators = remember(p.slug) {
        SaveHandlerRegistry.getAvailableEmulators(p.slug)
            .filter { it != SaveHandlerRegistry.EmulatorId.ANDROID_NATIVE }
    }
    val defaultEmu = remember(p.slug) { SaveHandlerRegistry.getDefaultEmulator(p.slug) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.setPlatformFolder(p.slug, folder)
                state.setPlatformSavesPath(p.slug, savesPath)
                state.setPlatformEmulator(p.slug, emulator)
                onDismiss()
            }) { Text(tr("common.save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("common.cancel")) } },
        title = { Text(p.displayName ?: p.name) },
        text = {
            Column {
                OutlinedTextField(
                    folder,
                    { folder = it },
                    label = { Text(tr("platforms.config.roms_folder")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    savesPath,
                    { savesPath = it },
                    label = { Text(tr("platforms.config.saves_path")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text(tr("platforms.config.sync_emulator"), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = emulator.isBlank(),
                        onClick = { emulator = "" },
                        label = { Text(tr("platforms.config.emulator_auto", defaultEmu.displayName)) },
                    )
                    emulators.forEach { emu ->
                        FilterChip(
                            selected = emulator == emu.id,
                            onClick = { emulator = emu.id },
                            label = { Text(emu.displayName) },
                        )
                    }
                }
            }
        },
    )
}

// ═══════════════════════════════════════════════════════════════════════
// Biblioteca
// ═══════════════════════════════════════════════════════════════════════

@Composable
fun LibraryScreen(state: DesktopAppState) {
    val roms by state.roms.collectAsState()
    val loading by state.loadingRoms.collectAsState()
    val search by state.search.collectAsState()
    val filter by state.filter.collectAsState()
    val version by state.downloadedVersion.collectAsState()
    val connected by state.connected.collectAsState()
    val tasks by state.tasks.collectAsState()
    val sort by state.sort.collectAsState()
    val regionFilter by state.regionFilter.collectAsState()
    val games = remember(roms, search, filter, version, sort, regionFilter) { state.games() }
    val ratio = rememberMedianCoverRatio(remember(roms) { state.games() }, state.config.serverUrl)

    var showBatchDialog by remember { mutableStateOf(false) }
    val missingCount = remember(roms, version) { state.missingGames().size }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        // Fila 1: título + selector de plataforma + refrescar + lote
        var platMenu by remember { mutableStateOf(false) }
        val selSlug by state.selectedPlatformSlug.collectAsState()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("nav.library"), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(12.dp))
            Box {
                OutlinedButton(onClick = { platMenu = true }) {
                    Text(visiblePlatformsLabel(state, selSlug))
                }
                DropdownMenu(expanded = platMenu, onDismissRequest = { platMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(tr("library.all_platforms")) },
                        onClick = { state.selectPlatformBySlug(null); platMenu = false },
                    )
                    state.visiblePlatforms().forEach { p ->
                        DropdownMenuItem(
                            text = { Text("${p.displayName ?: p.name} (${p.romCount})") },
                            onClick = { state.selectPlatformBySlug(p.slug); platMenu = false },
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = state::refreshCurrentView, enabled = connected) {
                Icon(AppIcons.Refresh, contentDescription = tr("library.refresh"))
            }
            Spacer(Modifier.weight(1f))
            if (missingCount > 0) {
                FilledTonalButton(onClick = { showBatchDialog = true }) {
                    Icon(AppIcons.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr("library.download_missing", missingCount))
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        // Fila 2: búsqueda
        OutlinedTextField(
            value = search,
            onValueChange = state::setSearch,
            placeholder = { Text(tr("library.search_placeholder")) },
            leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
            trailingIcon = {
                if (search.isNotBlank()) {
                    IconButton(onClick = { state.setSearch("") }) {
                        Icon(AppIcons.Close, contentDescription = tr("library.clear_search"))
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))

        // Fila 3: filtros + orden + región + aleatorio
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            FilterChip(
                selected = filter == LibraryFilter.ALL,
                onClick = { state.setFilter(LibraryFilter.ALL) },
                label = { Text(tr("library.filter.all")) },
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = filter == LibraryFilter.MISSING,
                onClick = { state.setFilter(LibraryFilter.MISSING) },
                label = { Text(tr("library.filter.missing")) },
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = filter == LibraryFilter.DOWNLOADED,
                onClick = { state.setFilter(LibraryFilter.DOWNLOADED) },
                label = { Text(tr("library.filter.downloaded")) },
            )
            Spacer(Modifier.weight(1f))

            // Juego aleatorio de la vista actual
            if (games.isNotEmpty()) {
                IconButton(onClick = { state.openGame(games.random()) }) {
                    Icon(AppIcons.Casino, contentDescription = tr("library.random_game"))
                }
            }

            // Filtro por región
            if (state.availableRegions().isNotEmpty()) {
                var regionMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { regionMenu = true }) {
                        Icon(
                            AppIcons.Public,
                            contentDescription = tr("library.filter_region"),
                            tint = if (regionFilter != null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(expanded = regionMenu, onDismissRequest = { regionMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(tr("library.all_regions")) },
                            onClick = { state.setRegionFilter(null); regionMenu = false },
                        )
                        state.availableRegions().forEach { r ->
                            DropdownMenuItem(
                                text = { Text(if (r == regionFilter) "● $r" else "  $r") },
                                onClick = { state.setRegionFilter(r); regionMenu = false },
                            )
                        }
                    }
                }
            }

            // Orden
            var sortMenu by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { sortMenu = true }) { Text(tr("library.sort_by", sort.label) + " ▾") }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    LibrarySort.entries.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(if (s == sort) "● ${s.label}" else "  ${s.label}") },
                            onClick = { state.setSort(s); sortMenu = false },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        val selectedGame by state.selectedGame.collectAsState()
        val g = selectedGame
        when {
            g != null -> GameDetailPanel(g, state)
            loading && roms.isEmpty() -> {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            games.isEmpty() -> {
                Box(Modifier.weight(1f)) {
                    LibraryEmptyState(state, search, filter, connected)
                }
            }
            else -> {
                // Carga incremental: lotes de 60 con detección de fin de scroll
                var visibleCount by remember { mutableStateOf(60) }
                val gridState = rememberLazyGridState()
                val endReached by remember {
                    derivedStateOf {
                        val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        val total = gridState.layoutInfo.totalItemsCount
                        total > 0 && last >= total - 12
                    }
                }
                LaunchedEffect(endReached, games.size) {
                    if (endReached && visibleCount < games.size) {
                        visibleCount = minOf(visibleCount + 60, games.size)
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(150.dp),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        gridItems(games.take(visibleCount), key = { it.rep.id }) { card ->
                            GameCardItem(card, state, ratio, tasks)
                        }
                        if (visibleCount < games.size) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(22.dp))
                                }
                            }
                        }
                    }
                    // Botón volver arriba (como el FAB de Android)
                    val showTop by remember {
                        derivedStateOf { gridState.firstVisibleItemIndex > 6 }
                    }
                    if (showTop) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showTop,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                        ) {
                            val scope = androidx.compose.runtime.rememberCoroutineScope()
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shadowElevation = 4.dp,
                                modifier = Modifier.clickable {
                                    scope.launch { gridState.animateScrollToItem(0) }
                                },
                            ) {
                                Icon(
                                    AppIcons.KeyboardArrowUp,
                                    contentDescription = tr("library.back_to_top"),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(10.dp).size(22.dp),
                                )
                            }
                        }
                    }
                    VerticalScrollbar(
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        adapter = rememberScrollbarAdapter(gridState),
                    )
                }
            }
        }
    }

    if (showBatchDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDialog = false },
            icon = { Icon(AppIcons.DownloadDone, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(tr("library.batch.title", missingCount)) },
            text = {
                Text(
                    tr("library.batch.message"),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    state.enqueueMissing()
                    showBatchDialog = false
                }) { Text(tr("common.download")) }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDialog = false }) { Text(tr("common.cancel")) }
            },
        )
    }
}

@Composable
private fun LibraryEmptyState(state: DesktopAppState, search: String, filter: LibraryFilter, connected: Boolean) {
    val (title, desc) = when {
        !connected -> tr("common.no_server_configured") to tr("library.empty.no_server.description")
        search.isNotBlank() -> tr("library.empty.no_results.title") to tr("library.empty.no_results.description", search)
        filter == LibraryFilter.DOWNLOADED && state.missingGames().isNotEmpty() ->
            tr("library.empty.no_downloads.title") to tr("library.empty.no_downloads.description")
        filter == LibraryFilter.MISSING ->
            tr("library.empty.all_downloaded.title") to tr("library.empty.all_downloaded.description")
        else -> tr("library.empty.no_games.title") to tr("library.empty.no_games.description")
    }
    EmptyState(icon = AppIcons.Gamepad, title = title, description = desc)
}

/** Card de juego estilo Android: portada con scrim, título sobre la imagen y badges. */
@Composable
private fun GameCardItem(card: GameCard, state: DesktopAppState, ratio: Float, tasks: List<DesktopTask>) {
    val downloading = tasks.any { it.romId in card.groupRoms.map { r -> r.id } && it.active }
    Card(
        modifier = Modifier
            .padding(2.dp)
            .fillMaxWidth()
            .clickable { state.openGame(card) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box {
            CoverImage(
                coverUrl = card.rep.urlCover,
                pathCover = card.rep.pathCoverSmall ?: card.rep.pathCoverLarge,
                serverUrl = state.config.serverUrl,
                aspectRatio = ratio,
            )

            // Badge multi-disco (arriba-izquierda)
            if (card.discCount > 1) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Icon(
                        AppIcons.Album,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        tr("library.card.discs", card.discCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }
            }

            // Estado arriba-derecha: descargado o botón de descarga
            when {
                card.downloaded -> Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        AppIcons.CheckCircle,
                        contentDescription = tr("common.downloaded"),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(16.dp),
                    )
                }
                downloading -> Unit
                else -> Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(30.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .clickable { state.enqueue(card) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        AppIcons.Download,
                        contentDescription = tr("common.download"),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Overlay de descarga en curso
            if (downloading) {
                Box(
                    Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(Modifier.size(30.dp), color = Color.White)
                }
            }

            // Título sobre scrim inferior (como Android)
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.55f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.78f),
                        ),
                    ),
            )
            Column(
                Modifier.align(Alignment.BottomStart).padding(10.dp),
            ) {
                Text(
                    card.rep.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    formatBytes(card.rep.fileSizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.75f),
                )
            }
        }
    }
}

private fun visiblePlatformsLabel(state: DesktopAppState, selSlug: String?): String {
    if (selSlug == null) return tr("library.all_platforms") + " ▾"
    val p = state.visiblePlatforms().firstOrNull { it.slug == selSlug }
    return (p?.displayName ?: p?.name ?: selSlug) + " ▾"
}

// ═══════════════════════════════════════════════════════════════════════
// Descargas
// ═══════════════════════════════════════════════════════════════════════

@Composable
fun DownloadsScreen(state: DesktopAppState) {
    val tasks by state.tasks.collectAsState()
    val active = tasks.count { it.active }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        ScreenHeader(
            tr("nav.downloads"),
            subtitle = if (active > 0) tr("downloads.in_progress", active) else tr("downloads.in_list", tasks.size),
        ) {
            if (tasks.any { !it.active }) {
                TextButton(onClick = state::clearFinished) { Text(tr("downloads.clear_finished")) }
            }
            if (active > 0) {
                Spacer(Modifier.width(4.dp))
                TextButton(
                    onClick = state::cancelAll,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(tr("downloads.cancel_all")) }
            }
        }
        Spacer(Modifier.height(12.dp))

        if (tasks.isEmpty()) {
            EmptyState(
                icon = AppIcons.Download,
                title = tr("downloads.empty.title"),
                description = tr("downloads.empty.description"),
            )
        } else {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val listState = rememberLazyListState()
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(tasks, key = { it.romId }) { t ->
                        DownloadCard(t, state)
                    }
                }
                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(listState),
                )
            }
        }
    }
}

@Composable
private fun DownloadCard(t: DesktopTask, state: DesktopAppState) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusBadge(t.status)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        (t.platformSlug ?: "").uppercase(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when {
                    t.active -> IconButton(onClick = { state.cancelDownload(t.romId) }) {
                        Icon(
                            AppIcons.Close,
                            contentDescription = tr("common.cancel"),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                    t.status == "error" -> TextButton(onClick = { state.retryDownload(t.romId) }) {
                        Icon(AppIcons.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(tr("downloads.retry"))
                    }
                }
            }

            if (t.status == "running") {
                Spacer(Modifier.height(8.dp))
                if (t.totalBytes > 0) {
                    val progress by animateFloatAsState(
                        targetValue = (t.bytesRead.toFloat() / t.totalBytes).coerceIn(0f, 1f),
                        label = "downloadProgress",
                    )
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    Row {
                        Text(
                            "${formatBytes(t.bytesRead)} / ${formatBytes(t.totalBytes)}" +
                                if (t.speedBps > 0) " · ${formatSpeed(t.speedBps)}" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    // Zip en streaming: total desconocido, pero bytes + velocidad sí.
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (t.bytesRead > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            tr("downloads.bytes_downloaded", formatBytes(t.bytesRead)) +
                                if (t.speedBps > 0) " · ${formatSpeed(t.speedBps)}" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            when (t.status) {
                "done" -> Text(
                    tr("common.download_completed"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                "error" -> Text(
                    t.message ?: tr("common.error"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val (bg, icon, tint) = when (status) {
        "running" -> Triple(MaterialTheme.colorScheme.primaryContainer, AppIcons.Download, MaterialTheme.colorScheme.onPrimaryContainer)
        "queued" -> Triple(MaterialTheme.colorScheme.surfaceContainerHighest, AppIcons.Schedule, MaterialTheme.colorScheme.onSurfaceVariant)
        "done" -> Triple(MaterialTheme.colorScheme.secondaryContainer, AppIcons.CheckCircle, MaterialTheme.colorScheme.onSecondaryContainer)
        else -> Triple(MaterialTheme.colorScheme.errorContainer, AppIcons.ErrorOutline, MaterialTheme.colorScheme.onErrorContainer)
    }
    Box(
        Modifier.size(40.dp).background(bg, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (status == "running") {
            CircularProgressIndicator(Modifier.size(20.dp), color = tint)
        } else {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Saves
// ═══════════════════════════════════════════════════════════════════════

@Composable
fun SavesScreen(state: DesktopAppState) {
    val syncing by state.syncing.collectAsState()
    val syncStatus by state.syncStatus.collectAsState()
    val lastSync by state.lastSync.collectAsState()
    val failed by state.lastFailed.collectAsState()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(tr("nav.saves"), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        SyncStatusCard(syncing, syncStatus, lastSync)
        Spacer(Modifier.height(12.dp))

        Button(
            onClick = state::syncSaves,
            enabled = !syncing,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            if (syncing) {
                CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(10.dp))
            }
            Text(if (syncing) tr("saves.sync_button.syncing") else tr("saves.sync_button.sync_now"))
        }
        Spacer(Modifier.height(16.dp))

        // ── Fallos del último ciclo ──
        if (failed.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        tr("saves.failed.title", failed.size),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(Modifier.height(6.dp))
                    failed.take(20).forEach { f ->
                        Text(
                            "${f.romName} — ${f.fileName} (${f.action}): ${f.reason}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    if (failed.size > 20) {
                        Text(
                            tr("saves.failed.more", failed.size - 20),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // ── Comprobar cambios ──
        val scanning by state.scanningSaves.collectAsState()
        val report by state.pendingReport.collectAsState()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("saves.pending.title"), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            FilledTonalButton(onClick = state::scanSaves, enabled = !scanning) {
                if (scanning) {
                    CircularProgressIndicator(Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(if (scanning) tr("saves.pending.checking") else tr("common.check_changes"))
            }
        }
        Spacer(Modifier.height(8.dp))
        report?.let { r ->
            if (r.error != null) {
                Text(r.error, color = MaterialTheme.colorScheme.error)
            } else if (r.uploads.isEmpty() && r.downloads.isEmpty() && r.conflicts.isEmpty()) {
                Text(
                    tr("saves.pending.none"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                if (r.uploads.isNotEmpty()) {
                    Text(tr("saves.pending.uploads", r.uploads.size), style = MaterialTheme.typography.titleSmall)
                    r.uploads.forEach {
                        Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                AppIcons.Upload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("${it.romName} — ${it.fileName}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (r.downloads.isNotEmpty()) {
                    Text(tr("saves.pending.downloads", r.downloads.size), style = MaterialTheme.typography.titleSmall)
                    r.downloads.forEach {
                        Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                AppIcons.Download,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("${it.romName} — ${it.fileName}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (r.conflicts.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                tr("saves.conflicts.title", r.conflicts.size),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Text(
                                tr("saves.conflicts.description"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Spacer(Modifier.height(8.dp))
                            r.conflicts.forEach { c ->
                                Column(Modifier.padding(vertical = 6.dp)) {
                                    Text(
                                        c.romName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                    Text(
                                        c.fileName + (c.serverUpdatedAt?.let { " · " + tr("saves.conflicts.server_time", it) } ?: ""),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        FilledTonalButton(
                                            onClick = { state.resolveConflict(c.romId, c.fileName, "local") },
                                            enabled = !syncing,
                                        ) {
                                            Icon(AppIcons.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(tr("saves.conflicts.keep_local"))
                                        }
                                        FilledTonalButton(
                                            onClick = { state.resolveConflict(c.romId, c.fileName, "server") },
                                            enabled = !syncing,
                                        ) {
                                            Icon(AppIcons.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(tr("saves.conflicts.keep_server"))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Sincronización automática (chips como Android) ──
        Spacer(Modifier.height(20.dp))
        Text(tr("saves.auto_sync.title"), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        val autoMin by state.autoSyncMinutes.collectAsState()
        Text(
            if (autoMin == 0) tr("saves.auto_sync.disabled") else tr("saves.auto_sync.every_minutes", autoMin),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 5, 15, 30, 60).forEach { m ->
                FilterChip(
                    selected = autoMin == m,
                    onClick = { state.setAutoSyncMinutes(m) },
                    label = { Text(if (m == 0) tr("saves.auto_sync.off_chip") else "$m m") },
                )
            }
        }
    }
}

@Composable
private fun SyncStatusCard(
    syncing: Boolean,
    syncStatus: String?,
    lastSync: LastSyncInfo,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                syncing -> {
                    CircularProgressIndicator(Modifier.size(40.dp))
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(tr("saves.status.syncing"), style = MaterialTheme.typography.titleMedium)
                        Text(
                            syncStatus ?: tr("saves.status.connecting"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                lastSync.exists -> {
                    Icon(
                        AppIcons.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(tr("saves.status.last_sync"), style = MaterialTheme.typography.titleMedium)
                        Text(
                            formatTimestamp(lastSync.at),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (lastSync.summary.isNotBlank()) {
                            Text(
                                tr("saves.status.result", lastSync.summary),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                else -> {
                    Icon(
                        AppIcons.Sync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(tr("saves.status.never_synced"), style = MaterialTheme.typography.titleMedium)
                        Text(
                            tr("saves.status.never_synced_hint"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Ajustes
// ═══════════════════════════════════════════════════════════════════════

@Composable
fun SettingsScreen(state: DesktopAppState) {
    val serverUrl = remember { mutableStateOf(state.config.serverUrl) }
    val apiKey = remember { mutableStateOf(state.config.apiKey) }
    val romsRoot = remember { mutableStateOf(state.config.romsRoot) }
    val connected by state.connected.collectAsState()
    var showApiKey by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).widthIn(max = SettingsMaxWidth),
    ) {
        Text(tr("nav.settings"), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        // ── Servidor ──
        SettingsSection(icon = AppIcons.Storage, title = tr("settings.server.title")) {
            OutlinedTextField(
                serverUrl.value,
                { serverUrl.value = it },
                label = { Text(tr("settings.server.url")) },
                placeholder = { Text(tr("settings.server.url_placeholder")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                apiKey.value,
                { apiKey.value = it },
                label = { Text(tr("settings.server.api_key")) },
                singleLine = true,
                visualTransformation = if (showApiKey) {
                    androidx.compose.ui.text.input.VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { showApiKey = !showApiKey }) {
                        Icon(
                            if (showApiKey) AppIcons.VisibilityOff else AppIcons.Visibility,
                            contentDescription = if (showApiKey) tr("settings.server.hide_api_key") else tr("common.show"),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        state.config.romsRoot = romsRoot.value
                        state.connect(serverUrl.value, apiKey.value)
                    },
                    enabled = serverUrl.value.isNotBlank() && apiKey.value.isNotBlank(),
                ) { Text(tr("settings.server.save_and_connect")) }
                Spacer(Modifier.width(12.dp))
                if (connected) {
                    Icon(
                        AppIcons.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(tr("settings.server.connected"), color = MaterialTheme.colorScheme.secondary)
                }
            }
        }

        // ── Directorio de ROMs ──
        SettingsSection(icon = AppIcons.Folder, title = tr("settings.roms.title")) {
            OutlinedTextField(
                romsRoot.value,
                { romsRoot.value = it },
                label = { Text(tr("common.roms_root_folder")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row {
                OutlinedButton(onClick = {
                    pickDirectory(tr("common.roms_root_folder"), romsRoot.value)?.let { romsRoot.value = it }
                }) {
                    Icon(AppIcons.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr("common.select_folder"))
                }
                Spacer(Modifier.width(12.dp))
                Button(onClick = { state.config.romsRoot = romsRoot.value }) { Text(tr("common.save")) }
            }
            Text(
                tr("settings.roms.hint"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ── Escanear biblioteca ──
        SettingsSection(icon = AppIcons.Storage, title = tr("common.scan_library")) {
            Text(
                tr("settings.scan.description"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            val scanState by state.scanState.collectAsState()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = state::scanLibrary, enabled = connected && scanState !is DesktopAppState.ScanState.Running) {
                    if (scanState is DesktopAppState.ScanState.Running) {
                        CircularProgressIndicator(Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(tr("settings.scan.scan_now"))
                }
                Spacer(Modifier.width(12.dp))
                when (val s = scanState) {
                    is DesktopAppState.ScanState.Running -> Text(
                        s.status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    is DesktopAppState.ScanState.Done -> Text(
                        s.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    DesktopAppState.ScanState.Idle -> Unit
                }
            }
        }

        // ── Descargas simultáneas ──
        val maxDowns by state.maxConcurrentDownloads.collectAsState()
        SettingsSection(icon = AppIcons.Download, title = tr("settings.downloads.title")) {
            Text(
                tr("settings.downloads.parallel", maxDowns),
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = maxDowns.toFloat(),
                onValueChange = { state.setMaxConcurrentDownloads(it.toInt()) },
                valueRange = 1f..5f,
                steps = 3,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // ── ES-DE ──
        SettingsSection(icon = AppIcons.Image, title = tr("settings.esde.title")) {
            var esdeDir by remember { mutableStateOf(state.config.esdeDataDir) }
            OutlinedTextField(
                esdeDir,
                { esdeDir = it },
                label = { Text(tr("common.esde_data_folder")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row {
                OutlinedButton(onClick = {
                    pickDirectory(tr("common.esde_data_folder"), esdeDir)?.let { esdeDir = it }
                }) {
                    Icon(AppIcons.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tr("common.select_folder"))
                }
                Spacer(Modifier.width(12.dp))
                val esdeRunning by state.esdeRunning.collectAsState()
                Button(
                    onClick = {
                        state.config.esdeDataDir = esdeDir
                        state.exportEsde()
                    },
                    enabled = !esdeRunning,
                ) {
                    if (esdeRunning) {
                        CircularProgressIndicator(Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(tr("settings.esde.export"))
                }
            }
            val esdeStatus by state.esdeStatus.collectAsState()
            esdeStatus?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                tr("settings.esde.hint"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ── Saves y conflictos ──
        SettingsSection(icon = AppIcons.Sync, title = tr("settings.saves.title")) {
            Text(
                tr("settings.saves.conflict_policy_prompt"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            val policy by state.conflictPolicy.collectAsState()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                es.davidrg.rommsync.core.sync.ConflictPolicy.entries.forEach { p ->
                    FilterChip(
                        selected = policy == p,
                        onClick = { state.setConflictPolicy(p) },
                        label = { Text(p.displayName) },
                    )
                }
            }
            Text(
                tr("settings.saves.backup_hint"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ── Sistema ──
        SettingsSection(icon = AppIcons.Gamepad, title = tr("settings.system.title")) {
            var closeToTray by remember { mutableStateOf(DesktopConfig.closeToTray) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("settings.system.close_to_tray"), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        tr("settings.system.close_to_tray_hint"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = closeToTray, onCheckedChange = {
                    closeToTray = it
                    DesktopConfig.closeToTray = it
                })
            }
        }

        // ── Actualizaciones ──
        SettingsSection(icon = AppIcons.Refresh, title = tr("settings.updates.title")) {
            val updState by state.updateState.collectAsState()
            val upd = updState
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = state::checkUpdate, enabled = !upd.checking && !upd.downloading) {
                    if (upd.checking) {
                        CircularProgressIndicator(Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(tr("settings.updates.check"))
                }
                if (upd.info?.available == true && upd.info.downloadUrl != null && !upd.installed) {
                    Spacer(Modifier.width(10.dp))
                    Button(onClick = state::installUpdate, enabled = !upd.downloading) {
                        if (upd.downloading) {
                            CircularProgressIndicator(Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            if (upd.downloading) {
                                tr("common.downloading")
                            } else {
                                tr("settings.updates.download_install", upd.info.latestVersion)
                            },
                        )
                    }
                }
                if (upd.restartAvailable) {
                    Spacer(Modifier.width(10.dp))
                    Button(onClick = state::restartApp) { Text(tr("settings.updates.restart_now")) }
                }
            }
            if (upd.downloading && upd.totalBytes > 0) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (upd.downloadedBytes.toFloat() / upd.totalBytes).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "${formatBytes(upd.downloadedBytes)} / ${formatBytes(upd.totalBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (upd.info?.available == true && !upd.downloading) {
                Spacer(Modifier.height(6.dp))
                Text(
                    tr("settings.updates.available", upd.info.latestVersion, upd.info.currentVersion),
                    style = MaterialTheme.typography.bodySmall,
                )
                // Changelog del release (cuerpo tal cual de GitHub)
                if (upd.info.releaseNotes.isNotBlank()) {
                    var showNotes by remember { mutableStateOf(false) }
                    TextButton(onClick = { showNotes = !showNotes }) {
                        Text(if (showNotes) tr("settings.updates.hide_notes") else tr("settings.updates.show_notes"))
                    }
                    if (showNotes) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                        ) {
                            Text(
                                upd.info.releaseNotes,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .padding(12.dp)
                                    .heightIn(max = 260.dp)
                                    .verticalScroll(rememberScrollState()),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = state::skipUpdateVersion) { Text(tr("settings.updates.skip_version")) }
            }
            if (upd.info?.available == true && upd.info.latestVersion == state.config.skippedVersion) {
                TextButton(onClick = state::recheckSkippedUpdate) { Text(tr("settings.updates.recheck")) }
            }
            upd.message?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // ── Acerca de ──
        SettingsSection(icon = AppIcons.Gamepad, title = tr("settings.about.title")) {
            Text(
                tr("settings.about.version", DesktopConfig.appVersion),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                tr("settings.about.shortcuts"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
