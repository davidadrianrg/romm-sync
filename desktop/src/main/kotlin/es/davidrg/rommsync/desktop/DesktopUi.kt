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
        RailItem(state, Section.PLATFORMS, section, AppIcons.Storage, "Plataformas")
        RailItem(state, Section.LIBRARY, section, AppIcons.Gamepad, "Biblioteca")
        RailItem(
            state, Section.DOWNLOADS, section, AppIcons.Download, "Descargas",
            badge = activeDownloads.takeIf { it > 0 },
        )
        RailItem(state, Section.SAVES, section, AppIcons.Sync, "Saves")
        RailItem(state, Section.SETTINGS, section, AppIcons.Settings, "Ajustes")
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
            "Plataformas",
            subtitle = "${platforms.size - hidden.size} de ${platforms.size} visibles",
        ) {
            IconButton(onClick = state::refreshCurrentView, enabled = connected) {
                Icon(AppIcons.Refresh, contentDescription = "Actualizar")
            }
            Spacer(Modifier.width(8.dp))
            val allOn = hidden.isEmpty()
            OutlinedButton(onClick = { state.toggleAllPlatforms(!allOn) }) {
                Text(if (allOn) "Desactivar todas" else "Activar todas")
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
                    title = "Sin servidor configurado",
                    description = "Conecta tu servidor RomM en Ajustes para ver tus plataformas.",
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
                            Text("$totalRoms ROMs locales", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${stats.size} plataformas con descargas",
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
                // Avatar con las 2 primeras letras del slug (como Android)
                Box(
                    Modifier.size(44.dp).background(
                        if (hidden) MaterialTheme.colorScheme.surfaceContainerHigh
                        else MaterialTheme.colorScheme.primaryContainer,
                        CircleShape,
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        p.slug.take(2).uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (hidden) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onPrimaryContainer,
                    )
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
                        stat?.let { append(" • ${it.romCount} locales") }
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
                    Text("Configuración", style = MaterialTheme.typography.labelMedium)
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
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        title = { Text(p.displayName ?: p.name) },
        text = {
            Column {
                OutlinedTextField(
                    folder,
                    { folder = it },
                    label = { Text("Carpeta de ROMs (relativa al root)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    savesPath,
                    { savesPath = it },
                    label = { Text("Ruta de saves (vacío = default emulador)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text("Emulador para sync", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = emulator.isBlank(),
                        onClick = { emulator = "" },
                        label = { Text("Auto (${defaultEmu.displayName})") },
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
    val games = remember(roms, search, filter, version) { state.games() }
    val ratio = rememberMedianCoverRatio(remember(roms) { state.games() }, state.config.serverUrl)

    var showBatchDialog by remember { mutableStateOf(false) }
    val missingCount = remember(roms, version) { state.missingGames().size }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        // Fila 1: título + selector de plataforma + refrescar + lote
        var platMenu by remember { mutableStateOf(false) }
        val selSlug by state.selectedPlatformSlug.collectAsState()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Biblioteca", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(12.dp))
            Box {
                OutlinedButton(onClick = { platMenu = true }) {
                    Text(visiblePlatformsLabel(state, selSlug))
                }
                DropdownMenu(expanded = platMenu, onDismissRequest = { platMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Todas las plataformas") },
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
                Icon(AppIcons.Refresh, contentDescription = "Refrescar")
            }
            Spacer(Modifier.weight(1f))
            if (missingCount > 0) {
                FilledTonalButton(onClick = { showBatchDialog = true }) {
                    Icon(AppIcons.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Descargar faltantes ($missingCount)")
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        // Fila 2: búsqueda
        OutlinedTextField(
            value = search,
            onValueChange = state::setSearch,
            placeholder = { Text("Buscar juego…") },
            leadingIcon = { Icon(AppIcons.Search, contentDescription = null) },
            trailingIcon = {
                if (search.isNotBlank()) {
                    IconButton(onClick = { state.setSearch("") }) {
                        Icon(AppIcons.Close, contentDescription = "Limpiar")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))

        // Fila 3: filtros
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = filter == LibraryFilter.ALL,
                onClick = { state.setFilter(LibraryFilter.ALL) },
                label = { Text("Todos") },
            )
            FilterChip(
                selected = filter == LibraryFilter.MISSING,
                onClick = { state.setFilter(LibraryFilter.MISSING) },
                label = { Text("Faltantes") },
            )
            FilterChip(
                selected = filter == LibraryFilter.DOWNLOADED,
                onClick = { state.setFilter(LibraryFilter.DOWNLOADED) },
                label = { Text("Descargados") },
            )
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
                                    contentDescription = "Volver arriba",
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
            title = { Text("¿Descargar $missingCount juegos faltantes?") },
            text = {
                Text(
                    "Se encolarán las descargas de todos los juegos de la vista actual que no estén en disco. " +
                        "Puedes seguir navegando mientras se descargan.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    state.enqueueMissing()
                    showBatchDialog = false
                }) { Text("Descargar") }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDialog = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun LibraryEmptyState(state: DesktopAppState, search: String, filter: LibraryFilter, connected: Boolean) {
    val (title, desc) = when {
        !connected -> "Sin servidor configurado" to "Conecta tu servidor RomM en Ajustes."
        search.isNotBlank() -> "Sin resultados" to "Ningún juego coincide con «$search»."
        filter == LibraryFilter.DOWNLOADED && state.missingGames().isNotEmpty() ->
            "Sin descargas" to "Todavía no has descargado juegos de esta vista."
        filter == LibraryFilter.MISSING -> "Todo descargado" to "Ya tienes en disco todos los juegos de esta vista."
        else -> "Sin juegos" to "Esta plataforma no tiene ROMs en el servidor."
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
                        "${card.discCount} discos",
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
                        contentDescription = "Descargado",
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
                        contentDescription = "Descargar",
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
    if (selSlug == null) return "Todas las plataformas ▾"
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
            "Descargas",
            subtitle = if (active > 0) "$active en progreso" else "${tasks.size} en la lista",
        ) {
            if (tasks.any { !it.active }) {
                TextButton(onClick = state::clearFinished) { Text("Limpiar terminadas") }
            }
            if (active > 0) {
                Spacer(Modifier.width(4.dp))
                TextButton(
                    onClick = state::cancelAll,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Cancelar todo") }
            }
        }
        Spacer(Modifier.height(12.dp))

        if (tasks.isEmpty()) {
            EmptyState(
                icon = AppIcons.Download,
                title = "No hay descargas en cola",
                description = "Las ROMs que descargues aparecerán aquí.",
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
                            contentDescription = "Cancelar",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                    t.status == "error" -> TextButton(onClick = { state.retryDownload(t.romId) }) {
                        Icon(AppIcons.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reintentar")
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
                            "${formatBytes(t.bytesRead)} descargados" +
                                if (t.speedBps > 0) " · ${formatSpeed(t.speedBps)}" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            when (t.status) {
                "done" -> Text(
                    "Descarga completada",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
                "error" -> Text(
                    t.message ?: "Error",
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
        Text("Saves", style = MaterialTheme.typography.headlineSmall)
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
            Text(if (syncing) "Sincronizando…" else "Sincronizar ahora")
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
                        "Fallidos (${failed.size})",
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
                            "… y ${failed.size - 20} más",
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
            Text("Cambios pendientes", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            FilledTonalButton(onClick = state::scanSaves, enabled = !scanning) {
                if (scanning) {
                    CircularProgressIndicator(Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(if (scanning) "Comprobando…" else "Comprobar cambios")
            }
        }
        Spacer(Modifier.height(8.dp))
        report?.let { r ->
            if (r.error != null) {
                Text(r.error, color = MaterialTheme.colorScheme.error)
            } else if (r.uploads.isEmpty() && r.downloads.isEmpty() && r.conflicts.isEmpty()) {
                Text(
                    "Todo sincronizado — sin cambios pendientes.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                if (r.uploads.isNotEmpty()) {
                    Text("Subirán (${r.uploads.size}):", style = MaterialTheme.typography.titleSmall)
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
                    Text("Bajarán (${r.downloads.size}):", style = MaterialTheme.typography.titleSmall)
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
                                "Conflictos (${r.conflicts.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Text(
                                "El mismo save cambió aquí y en el servidor. Elige qué versión conservar.",
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
                                        c.fileName + (c.serverUpdatedAt?.let { " · Servidor: $it" } ?: ""),
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
                                            Text("Local")
                                        }
                                        FilledTonalButton(
                                            onClick = { state.resolveConflict(c.romId, c.fileName, "server") },
                                            enabled = !syncing,
                                        ) {
                                            Icon(AppIcons.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Servidor")
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
        Text("Sincronización automática", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        val autoMin by state.autoSyncMinutes.collectAsState()
        Text(
            if (autoMin == 0) "Desactivada" else "Cada $autoMin minutos",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 5, 15, 30, 60).forEach { m ->
                FilterChip(
                    selected = autoMin == m,
                    onClick = { state.setAutoSyncMinutes(m) },
                    label = { Text(if (m == 0) "Off" else "$m m") },
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
                        Text("Sincronizando", style = MaterialTheme.typography.titleMedium)
                        Text(
                            syncStatus ?: "Conectando con el servidor RomM…",
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
                        Text("Última sincronización", style = MaterialTheme.typography.titleMedium)
                        Text(
                            formatTimestamp(lastSync.at),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (lastSync.summary.isNotBlank()) {
                            Text(
                                "Resultado: ${lastSync.summary}",
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
                        Text("Sin sincronizar", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Pulsa el botón para sincronizar tus saves",
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
        Text("Ajustes", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        // ── Servidor ──
        SettingsSection(icon = AppIcons.Storage, title = "Servidor RomM") {
            OutlinedTextField(
                serverUrl.value,
                { serverUrl.value = it },
                label = { Text("URL del servidor") },
                placeholder = { Text("https://romm.midominio.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                apiKey.value,
                { apiKey.value = it },
                label = { Text("API Key") },
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
                            contentDescription = if (showApiKey) "Ocultar" else "Mostrar",
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
                ) { Text("Guardar y conectar") }
                Spacer(Modifier.width(12.dp))
                if (connected) {
                    Icon(
                        AppIcons.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Conectado", color = MaterialTheme.colorScheme.secondary)
                }
            }
        }

        // ── Directorio de ROMs ──
        SettingsSection(icon = AppIcons.Folder, title = "Directorio de ROMs (ES-DE)") {
            OutlinedTextField(
                romsRoot.value,
                { romsRoot.value = it },
                label = { Text("Carpeta raíz de ROMs") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row {
                OutlinedButton(onClick = {
                    pickDirectory("Carpeta raíz de ROMs", romsRoot.value)?.let { romsRoot.value = it }
                }) {
                    Icon(AppIcons.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Seleccionar carpeta")
                }
                Spacer(Modifier.width(12.dp))
                Button(onClick = { state.config.romsRoot = romsRoot.value }) { Text("Guardar") }
            }
            Text(
                "Los juegos se guardan en <raíz>/<plataforma>/ (configurable por plataforma en la sección Plataformas).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ── Descargas simultáneas ──
        val maxDowns by state.maxConcurrentDownloads.collectAsState()
        SettingsSection(icon = AppIcons.Download, title = "Descargas simultáneas") {
            Text(
                "$maxDowns descargas en paralelo",
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
        SettingsSection(icon = AppIcons.Image, title = "Datos de ES-DE (gamelist)") {
            var esdeDir by remember { mutableStateOf(state.config.esdeDataDir) }
            OutlinedTextField(
                esdeDir,
                { esdeDir = it },
                label = { Text("Carpeta de datos de ES-DE") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row {
                OutlinedButton(onClick = {
                    pickDirectory("Carpeta de datos de ES-DE", esdeDir)?.let { esdeDir = it }
                }) {
                    Icon(AppIcons.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Seleccionar carpeta")
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
                    Text("Exportar metadata a ES-DE")
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
                "Fusiona (sin sobrescribir) tus descargas en gamelist.xml por plataforma.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ── Actualizaciones ──
        SettingsSection(icon = AppIcons.Refresh, title = "Actualizaciones") {
            val updState by state.updateState.collectAsState()
            val upd = updState
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = state::checkUpdate, enabled = !upd.checking && !upd.downloading) {
                    if (upd.checking) {
                        CircularProgressIndicator(Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text("Buscar actualizaciones")
                }
                if (upd.info?.available == true && upd.info.downloadUrl != null && !upd.installed) {
                    Spacer(Modifier.width(10.dp))
                    Button(onClick = state::installUpdate, enabled = !upd.downloading) {
                        if (upd.downloading) {
                            CircularProgressIndicator(Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(if (upd.downloading) "Descargando…" else "Descargar e instalar v${upd.info.latestVersion}")
                    }
                }
                if (upd.restartAvailable) {
                    Spacer(Modifier.width(10.dp))
                    Button(onClick = state::restartApp) { Text("Reiniciar ahora") }
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
                    "Nueva versión disponible: v${upd.info.latestVersion} (tienes v${upd.info.currentVersion})",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            upd.message?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // ── Acerca de ──
        SettingsSection(icon = AppIcons.Gamepad, title = "Acerca de") {
            Text(
                "RomM Sync desktop v${DesktopConfig.appVersion}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Atajos: Ctrl+1…5 cambia de sección · F5 refresca la vista.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
