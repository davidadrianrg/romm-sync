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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    val selected by state.selectedPlatformId.collectAsState()
    val loading by state.loadingPlatforms.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Plataformas", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        if (loading) { CircularProgressIndicator() }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(220.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            gridItems(platforms, key = { it.id }) { p ->
                val isSel = selected == p.id
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        state.selectPlatform(p.id)
                        state.navigate(Section.LIBRARY)
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSel) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(p.displayName ?: p.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${p.romCount} ROMs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Biblioteca", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(16.dp))
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
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            gridItems(games, key = { it.rep.id }) { card ->
                GameCardItem(card, state)
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
            .clickable { state.enqueue(card) },
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
    // Carga async de la cover: descarga en IO y decodifica a ImageBitmap.
    val bitmap by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, url) {
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            runCatching {
                java.net.URL(url).openStream().use { input ->
                    value = loadImageBitmap(input)
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
            LazyColumn {
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
        }
    }
}

@Composable
fun SavesScreen(state: DesktopAppState) {
    val syncing by state.syncing.collectAsState()
    val syncStatus by state.syncStatus.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
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
                Text(it, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
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
    Column(Modifier.fillMaxSize().padding(16.dp)) {
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
        Text("Acerca de", style = MaterialTheme.typography.titleMedium)
        Text("RomM Sync desktop", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
