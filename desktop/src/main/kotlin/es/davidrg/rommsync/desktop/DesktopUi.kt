package es.davidrg.rommsync.desktop

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.davidrg.rommsync.core.remote.dto.PlatformDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DesktopApp(state: DesktopAppState) {
    val section by state.section.collectAsState()
    Row(Modifier.fillMaxSize()) {
        NavigationRail(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ) {
            Spacer(Modifier.height(8.dp))
            NavigationRailItem(
                selected = section == Section.PLATFORMS,
                onClick = { state.navigate(Section.PLATFORMS) },
                icon = { Text("🎮") },
                label = { Text("Plataformas") },
            )
            NavigationRailItem(
                selected = section == Section.LIBRARY,
                onClick = { state.navigate(Section.LIBRARY) },
                icon = { Text("🕹") },
                label = { Text("Biblioteca") },
            )
            NavigationRailItem(
                selected = section == Section.DOWNLOADS,
                onClick = { state.navigate(Section.DOWNLOADS) },
                icon = { Text("⬇") },
                label = { Text("Descargas") },
            )
            NavigationRailItem(
                selected = section == Section.SAVES,
                onClick = { state.navigate(Section.SAVES) },
                icon = { Text("💾") },
                label = { Text("Saves") },
            )
            NavigationRailItem(
                selected = section == Section.SETTINGS,
                onClick = { state.navigate(Section.SETTINGS) },
                icon = { Text("⚙") },
                label = { Text("Ajustes") },
            )
        }
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
}

@Composable
fun PlatformsScreen(state: DesktopAppState) {
    val platforms by state.platforms.collectAsState()
    val hidden by state.hiddenPlatforms.collectAsState()
    val loading by state.loadingPlatforms.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Plataformas", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.weight(1f))
            val allOn = hidden.isEmpty()
            // Paridad con Android (setAllVisible(!allVisible)): si todas están
            // activas las desactiva, y viceversa. Antes se pasaba `allOn` tal
            // cual, así que con todas activas el botón era un no-op.
            OutlinedButton(onClick = { state.toggleAllPlatforms(!allOn) }) {
                Text(if (allOn) "Desactivar todas" else "Activar todas")
            }
        }
        Spacer(Modifier.height(12.dp))
        if (loading) { CircularProgressIndicator() }
        // Contenedor con scrollbar lateral: sin él no hay feedback de posición
        // ni arrastre de scroll en las plataformas que quedan fuera de pantalla.
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val gridState = rememberLazyGridState()
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(240.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                gridItems(platforms, key = { it.id }) { p ->
                    PlatformConfigCard(p, hidden.contains(p.slug), state)
                }
            }
            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                adapter = rememberScrollbarAdapter(gridState),
            )
        }
    }
}

@Composable
private fun PlatformConfigCard(p: PlatformDto, hidden: Boolean, state: DesktopAppState) {
    var showConfig by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (hidden) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(Modifier.padding(14.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        p.displayName ?: p.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "${p.romCount} ROMs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = !hidden, onCheckedChange = { state.togglePlatform(p.slug) })
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = { showConfig = true }) {
                Text("Configuración", style = MaterialTheme.typography.labelMedium)
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
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.setPlatformFolder(p.slug, folder)
                state.setPlatformSavesPath(p.slug, savesPath)
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
            }
        },
    )
}

@Composable
fun LibraryScreen(state: DesktopAppState) {
    val roms by state.roms.collectAsState()
    val loading by state.loadingRoms.collectAsState()
    val search by state.search.collectAsState()
    val filter by state.filter.collectAsState()
    val version by state.downloadedVersion.collectAsState()
    val games = remember(roms, search, filter, version) { state.games() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        // Selector de plataforma (dropdown por slug)
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
            Spacer(Modifier.width(12.dp))
            OutlinedTextField(
                value = search,
                onValueChange = state::setSearch,
                label = { Text("Buscar juego…") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = filter == LibraryFilter.ALL, onClick = { state.setFilter(LibraryFilter.ALL) }, label = { Text("Todos") })
            FilterChip(selected = filter == LibraryFilter.MISSING, onClick = { state.setFilter(LibraryFilter.MISSING) }, label = { Text("Faltantes") })
            FilterChip(selected = filter == LibraryFilter.DOWNLOADED, onClick = { state.setFilter(LibraryFilter.DOWNLOADED) }, label = { Text("Descargados") })
        }
        Spacer(Modifier.height(8.dp))
        if (loading) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("Cargando ROMs…")
            }
        }
        val selectedGame by state.selectedGame.collectAsState()
        val g = selectedGame
        if (g != null) {
            GameDetailPanel(g, state)
        } else {
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
            // Grid con scrollbar lateral para navegar bibliotecas grandes.
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
                        GameCardItem(card, state)
                    }
                    if (visibleCount < games.size) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(22.dp))
                            }
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

@Composable
fun GameDetailPanel(card: GameCard, state: DesktopAppState) {
    val tasks by state.tasks.collectAsState()
    val groupIds = card.groupRoms.map { it.id }
    val downloading = tasks.any { it.romId in groupIds && (it.status == "running" || it.status == "queued") }
    val rep = card.rep
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = state::closeGame) { Text("← Volver") }
            Spacer(Modifier.width(8.dp))
            Text(rep.name, style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            CoverImage(
                coverUrl = rep.urlCover,
                pathCover = rep.pathCoverLarge ?: rep.pathCoverSmall,
                serverUrl = state.config.serverUrl,
            )
            Spacer(Modifier.width(20.dp))
            Column {
                if (card.discCount > 1) Text("Multi-disc: ${card.discCount} discos", style = MaterialTheme.typography.titleSmall)
                rep.summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Spacer(Modifier.height(8.dp))
                Text("Tamaño: ${rep.fileSizeBytes.toSizeLabel()}", style = MaterialTheme.typography.bodySmall)
                rep.platformSlug?.let { Text("Plataforma: $it", style = MaterialTheme.typography.bodySmall) }
                Spacer(Modifier.height(12.dp))
                Row {
                    if (card.downloaded) {
                        Text("✓ Descargado", color = MaterialTheme.colorScheme.secondary)
                    } else if (downloading) {
                        Text("Descargando…")
                    } else {
                        Button(onClick = { state.enqueue(card) }) { Text("Descargar") }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Discos / versiones (${card.groupRoms.size})", style = MaterialTheme.typography.titleMedium)
        card.groupRoms.forEach { rom ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(rom.fileName, Modifier.weight(1f))
                Text(rom.fileSizeBytes.toSizeLabel(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun GameCardItem(card: GameCard, state: DesktopAppState) {
    Card(
        modifier = Modifier
            .padding(2.dp)
            .fillMaxWidth()
            .clickable { state.openGame(card) },
    ) {
        Box {
            CoverImage(coverUrl = card.rep.urlCover, pathCover = card.rep.pathCoverSmall, serverUrl = state.config.serverUrl)
            if (card.discCount > 1) {
                Text(
                    "${card.discCount} discos",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            if (card.downloaded) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Descargado",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp),
                )
            }
        }
        Column(Modifier.padding(8.dp)) {
            Text(card.rep.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                card.rep.fileSizeBytes.toSizeLabel(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun CoverImage(coverUrl: String?, pathCover: String?, serverUrl: String) {
    val url = remember(coverUrl, pathCover, serverUrl) {
        when {
            !coverUrl.isNullOrBlank() -> coverUrl
            !pathCover.isNullOrBlank() -> serverUrl.trimEnd('/') + "/" + pathCover.removePrefix("/")
            else -> null
        }
    }
    if (url != null) {
        val bitmap = produceCover(url)
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(0.72f),
            )
        } else {
            CoverPlaceholder()
        }
    } else {
        CoverPlaceholder()
    }
}

@Composable
private fun CoverPlaceholder() {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text("🎮", style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun produceCover(url: String): androidx.compose.ui.graphics.ImageBitmap? {
    // Carga async: caché LRU en disco (CoverCache) + decodificación en IO.
    val bitmap by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, url) {
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            val cached = CoverCache.fetch(url)
            if (cached != null) {
                runCatching {
                    java.io.FileInputStream(cached).use { input ->
                        value = loadImageBitmap(input)
                    }
                }
            }
        }
    }
    return bitmap
}

@Composable
fun DownloadsScreen(state: DesktopAppState) {
    val tasks by state.tasks.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Descargas", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.weight(1f))
            if (tasks.any { it.status == "done" || it.status == "error" }) {
                TextButton(onClick = state::clearFinished) { Text("Limpiar terminadas") }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (tasks.isEmpty()) {
            Text("Sin descargas aún. Encola juegos desde la Biblioteca.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val listState = rememberLazyListState()
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(tasks, key = { it.romId }) { t ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(t.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        t.platformSlug ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                when (t.status) {
                                    "running" -> Text("${t.speedBps.toSpeedLabel()}", style = MaterialTheme.typography.bodyMedium)
                                    "done" -> Icon(Icons.Filled.CheckCircle, "OK", tint = MaterialTheme.colorScheme.secondary)
                                    "error" -> Text("Error", color = MaterialTheme.colorScheme.error)
                                }
                            }
                            if (t.status == "running") {
                                Spacer(Modifier.height(6.dp))
                                if (t.totalBytes > 0) {
                                    LinearProgressIndicator(
                                        progress = { (t.bytesRead.toFloat() / t.totalBytes).coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Text(
                                        "${t.bytesRead.toSizeLabel()} / ${t.totalBytes.toSizeLabel()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                } else {
                                    LinearProgressIndicator(Modifier.fillMaxWidth())
                                }
                            }
                            t.message?.let {
                                if (t.status == "error") {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 2)
                                }
                            }
                        }
                    }
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
fun SavesScreen(state: DesktopAppState) {
    val syncing by state.syncing.collectAsState()
    val syncStatus by state.syncStatus.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Saves", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(
            "Sincroniza los archivos de guardado entre este dispositivo y el servidor RomM. " +
                "La negociación detecta cuál es la copia más reciente en cada juego.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = state::syncSaves, enabled = !syncing) {
                if (syncing) {
                    CircularProgressIndicator(Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (syncing) "Sincronizando…" else "Sincronizar ahora")
            }
        }
        syncStatus?.let {
            Spacer(Modifier.height(12.dp))
            Surface(
                color = if (it.startsWith("Error")) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(it, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(16.dp))

        // ── Comprobar cambios ──
        val scanning by state.scanningSaves.collectAsState()
        val report by state.pendingReport.collectAsState()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Cambios pendientes", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = state::scanSaves, enabled = !scanning) {
                if (scanning) {
                    CircularProgressIndicator(Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(if (scanning) "Comprobando…" else "Comprobar cambios")
            }
        }
        report?.let { r ->
            Spacer(Modifier.height(8.dp))
            if (r.error != null) {
                Text(r.error, color = MaterialTheme.colorScheme.error)
            } else if (r.uploads.isEmpty() && r.downloads.isEmpty() && r.conflicts.isEmpty()) {
                Text("Todo sincronizado — sin cambios pendientes.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                if (r.uploads.isNotEmpty()) {
                    Text("Subirán (${r.uploads.size}):", style = MaterialTheme.typography.titleSmall)
                    r.uploads.forEach { Text("  ↑ ${it.romName} — ${it.fileName}", style = MaterialTheme.typography.bodySmall) }
                }
                if (r.downloads.isNotEmpty()) {
                    Text("Bajarán (${r.downloads.size}):", style = MaterialTheme.typography.titleSmall)
                    r.downloads.forEach { Text("  ↓ ${it.romName} — ${it.fileName}", style = MaterialTheme.typography.bodySmall) }
                }
                if (r.conflicts.isNotEmpty()) {
                    Text("Conflictos (${r.conflicts.size}):", style = MaterialTheme.typography.titleSmall)
                    r.conflicts.forEach { c ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("  ⚠ ${c.romName} — ${c.fileName}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { state.resolveConflict(c.romId, c.fileName, "local") }) { Text("Gana local") }
                            TextButton(onClick = { state.resolveConflict(c.romId, c.fileName, "server") }) { Text("Gana servidor") }
                        }
                    }
                }
            }
        }

        // ── Programar cada X minutos ──
        Spacer(Modifier.height(16.dp))
        val autoMin by state.autoSyncMinutes.collectAsState()
        var autoMenu by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Sincronización automática", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            Box {
                OutlinedButton(onClick = { autoMenu = true }) {
                    Text(if (autoMin == 0) "Desactivada ▾" else "Cada $autoMin min ▾")
                }
                DropdownMenu(expanded = autoMenu, onDismissRequest = { autoMenu = false }) {
                    listOf(0, 5, 15, 30, 60).forEach { m ->
                        DropdownMenuItem(
                            text = { Text(if (m == 0) "Desactivada" else "Cada $m minutos") },
                            onClick = { state.setAutoSyncMinutes(m); autoMenu = false },
                        )
                    }
    }
            }
        }
    }
}

@Composable
fun SettingsScreen(state: DesktopAppState) {
    val serverUrl = remember { mutableStateOf(state.config.serverUrl) }
    val apiKey = remember { mutableStateOf(state.config.apiKey) }
    val romsRoot = remember { mutableStateOf(state.config.romsRoot) }
    val connected by state.connected.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Ajustes", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(serverUrl.value, { serverUrl.value = it }, label = { Text("URL del servidor") }, modifier = Modifier.fillMaxWidth().width(420.dp))
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(apiKey.value, { apiKey.value = it }, label = { Text("API Key") }, modifier = Modifier.fillMaxWidth().width(420.dp))
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(romsRoot.value, { romsRoot.value = it }, label = { Text("Carpeta raíz de ROMs") }, modifier = Modifier.fillMaxWidth().width(420.dp))
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                state.config.romsRoot = romsRoot.value
                state.connect(serverUrl.value, apiKey.value)
            }) { Text("Guardar y conectar") }
            Spacer(Modifier.width(12.dp))
            if (connected) {
                Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Conectado", color = MaterialTheme.colorScheme.secondary)
            }
        }
        Spacer(Modifier.height(20.dp))

        // ── Exportar metadata a ES-DE ──
        Text("ES-DE", style = MaterialTheme.typography.titleMedium)
        var esdeDir by remember { mutableStateOf(state.config.esdeDataDir) }
        OutlinedTextField(
            esdeDir,
            { esdeDir = it },
            label = { Text("Carpeta de datos de ES-DE") },
            modifier = Modifier.fillMaxWidth().width(420.dp),
        )
        Spacer(Modifier.height(8.dp))
        val esdeRunning by state.esdeRunning.collectAsState()
        val esdeStatus by state.esdeStatus.collectAsState()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                state.config.esdeDataDir = esdeDir
                state.exportEsde()
            }, enabled = !esdeRunning) {
                if (esdeRunning) { CircularProgressIndicator(Modifier.size(14.dp)); Spacer(Modifier.width(6.dp)) }
                Text("Exportar metadata a ES-DE")
            }
        }
        esdeStatus?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(20.dp))

        // ── Actualizaciones ──
        Text("Actualizaciones", style = MaterialTheme.typography.titleMedium)
        val updState by state.updateState.collectAsState()
        val upd = updState
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = state::checkUpdate, enabled = !upd.checking && !upd.downloading) {
                if (upd.checking) { CircularProgressIndicator(Modifier.size(14.dp)); Spacer(Modifier.width(6.dp)) }
                Text("Buscar actualizaciones")
            }
            // Con actualización disponible y asset de esta arquitectura: botón
            // directo de descargar+instalar in-place (AppImage se reemplaza).
            if (upd.info?.available == true && upd.info.downloadUrl != null && !upd.installed) {
                Spacer(Modifier.width(10.dp))
                Button(onClick = state::installUpdate, enabled = !upd.downloading) {
                    if (upd.downloading) { CircularProgressIndicator(Modifier.size(14.dp)); Spacer(Modifier.width(6.dp)) }
                    Text(if (upd.downloading) "Descargando…" else "Descargar e instalar v${upd.info.latestVersion}")
                }
            }
            if (upd.restartAvailable) {
                Spacer(Modifier.width(10.dp))
                Button(onClick = state::restartApp) { Text("Reiniciar ahora") }
            }
        }
        if (upd.downloading && upd.totalBytes > 0) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { (upd.downloadedBytes.toFloat() / upd.totalBytes).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().width(420.dp),
            )
            Text(
                "${upd.downloadedBytes.toSizeLabel()} / ${upd.totalBytes.toSizeLabel()}",
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

        Spacer(Modifier.height(20.dp))
        Text("Acerca de", style = MaterialTheme.typography.titleMedium)
        Text("RomM Sync desktop v" + DesktopConfig.appVersion, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun Long.toSizeLabel(): String {
    if (this <= 0) return "—"
    val gb = this / 1024.0 / 1024.0 / 1024.0
    val mb = this / 1024.0 / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.1f GB", gb)
        mb >= 1.0 -> String.format("%.0f MB", mb)
        else -> String.format("%.0f KB", this / 1024.0)
    }
}

private fun Long.toSpeedLabel(): String = toSizeLabel() + "/s"

private fun visiblePlatformsLabel(state: DesktopAppState, selSlug: String?): String {
    if (selSlug == null) return "Todas las plataformas ▾"
    val p = state.visiblePlatforms().firstOrNull { it.slug == selSlug }
    return (p?.displayName ?: p?.name ?: selSlug) + " ▾"
}
