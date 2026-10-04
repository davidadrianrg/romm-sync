package es.davidrg.rommsync.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import es.davidrg.rommsync.core.remote.dto.RomDto
import java.net.URI

/**
 * Detalle de juego — paridad con el RomDetailSheet de Android: portada,
 * metadata IGDB, resumen expandible, screenshots con visor a pantalla
 * completa, filas de metadatos del fichero, acciones de descarga/eliminación
 * y configuración de sync por juego.
 */
@Composable
fun GameDetailPanel(card: GameCard, state: DesktopAppState) {
    val tasks by state.tasks.collectAsState()
    val romsVersion by state.downloadedVersion.collectAsState()
    val groupIds = card.groupRoms.map { it.id }
    val downloading = tasks.any { it.romId in groupIds && it.active }
    val rep = card.rep

    var expandedSummary by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = state::closeGame) {
                Text("← Volver")
            }
            Spacer(Modifier.width(12.dp))
            Text(
                rep.name,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(220.dp)) {
                CoverImage(
                    coverUrl = rep.urlCover,
                    pathCover = rep.pathCoverLarge ?: rep.pathCoverSmall,
                    serverUrl = state.config.serverUrl,
                    aspectRatio = 2f / 3f,
                )
            }
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f)) {
                rep.platformSlug?.let {
                    Text(
                        it.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                val metaLine = buildList {
                    if (rep.regions.isNotEmpty()) add(regionsLabel(rep))
                    rep.revision?.takeIf { it.isNotBlank() }?.let { add("Rev $it") }
                    releaseYear(rep)?.let { add("$it") }
                }.joinToString(" • ")
                if (metaLine.isNotBlank()) {
                    Text(
                        metaLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ratingRow(rep)
                Spacer(Modifier.height(8.dp))
                ChipsFlow(rep)
                Spacer(Modifier.height(8.dp))
                rep.summary?.let { summary ->
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (expandedSummary) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (summary.length > 120) {
                        TextButton(onClick = { expandedSummary = !expandedSummary }) {
                            Text(if (expandedSummary) "Ver menos" else "Ver más")
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                DetailRow("Archivo", rep.fileName)
                DetailRow("Tamaño", formatBytes(rep.fileSizeBytes))
                rep.fileNameNoTags?.let { DetailRow("Nombre limpio", it) }
                rep.fileExtension?.let { DetailRow("Extensión", it) }
                if (card.discCount > 1) DetailRow("Multi-archivo", "Sí (${card.discCount} archivos)")
                rep.igdbId?.let { DetailRow("IGDB ID", it.toString()) }

                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when {
                        card.downloaded -> {
                            Icon(
                                AppIcons.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Descargado", color = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(16.dp))
                            OutlinedButton(
                                onClick = { showDeleteDialog = true },
                                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                            ) {
                                Icon(AppIcons.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Eliminar descarga")
                            }
                        }
                        downloading -> {
                            CircularProgressIndicator(Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Descargando…")
                        }
                        else -> {
                            Button(onClick = { state.enqueue(card) }) {
                                Icon(AppIcons.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Descargar")
                            }
                        }
                    }
                }
            }
        }

        // ── Multimedia ──
        val screenshots = rep.mergedScreenshots
        if (screenshots.isNotEmpty() || rep.youtubeVideoId != null) {
            Spacer(Modifier.height(20.dp))
            Text("Multimedia", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (screenshots.isNotEmpty()) {
                var viewerIndex by remember { mutableStateOf<Int?>(null) }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(screenshots.size) { index ->
                        val url = resolveMediaUrl(null, screenshots[index], state.config.serverUrl)
                        ScreenshotThumb(url) { viewerIndex = index }
                    }
                }
                viewerIndex?.let { index ->
                    ScreenshotViewerDialog(
                        urls = screenshots.map { resolveMediaUrl(null, it, state.config.serverUrl) },
                        initialIndex = index,
                        onDismiss = { viewerIndex = null },
                    )
                }
            }
            rep.youtubeVideoId?.let { videoId ->
                if (screenshots.isNotEmpty()) Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { openTrailer(videoId) }) {
                    Icon(AppIcons.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Ver tráiler")
                }
            }
        }

        // ── Config de sync por juego ──
        if (card.downloaded) {
            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            RomSyncConfigSection(card, state, romsVersion)
        }

        // ── Archivos de la entrada (multi-disc = varios files en una ROM) ──
        if (rep.multi || rep.hasMultipleFiles || rep.files.size > 1) {
            Spacer(Modifier.height(20.dp))
            val fileRows = rep.files.ifEmpty { listOf(es.davidrg.rommsync.core.remote.dto.RomFileDto(rep.fileName, rep.fileSizeBytes)) }
            Text("Discos / versiones (${fileRows.size})", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            fileRows.forEach { file ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(file.filename, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        formatBytes(file.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(AppIcons.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("¿Eliminar «${rep.name}»?") },
            text = {
                Text(
                    if (card.discCount > 1) {
                        "Se borrarán los archivos de todos los discos de este juego del disco local. La copia del servidor RomM no se toca."
                    } else {
                        "Se borrará el archivo descargado del disco local. La copia del servidor RomM no se toca."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    state.deleteDownload(card)
                    state.closeGame()
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancelar") } },
        )
    }
}

/** Sección "Sincronización de partidas" del detalle (exclusión + ruta + copias). */
@Composable
private fun RomSyncConfigSection(card: GameCard, state: DesktopAppState, romsVersion: Int) {
    val entry = remember(card, romsVersion) { state.romEntry(card.rep.id) } ?: return
    var excluded by remember(card, romsVersion) { mutableStateOf(entry.excludedFromSync) }
    var savesPath by remember(card, romsVersion) { mutableStateOf(entry.savesPathOverride ?: "") }

    Text("Sincronización de partidas", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Excluir de la sincronización", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Este juego no subirá ni bajará saves",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = excluded, onCheckedChange = {
            excluded = it
            state.setRomExcluded(card.rep.id, it)
        })
    }
    Spacer(Modifier.height(12.dp))
    Text(
        if (entry.savesPathOverride.isNullOrBlank()) "Ruta de saves: heredada de la plataforma"
        else "Ruta de saves personalizada para este juego",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            savesPath,
            { savesPath = it },
            label = { Text("Ruta personalizada (vacío = default)") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = { pickDirectory("Carpeta de saves", savesPath)?.let { savesPath = it } }) {
            Icon(AppIcons.FolderOpen, contentDescription = "Seleccionar carpeta")
        }
    }
    Spacer(Modifier.height(6.dp))
    Row {
        TextButton(onClick = {
            state.setRomSavesPath(card.rep.id, savesPath)
            state.showSnackbar("Ruta de saves guardada")
        }) { Text("Guardar ruta") }
        if (!entry.savesPathOverride.isNullOrBlank() || savesPath.isNotBlank()) {
            TextButton(onClick = {
                savesPath = ""
                state.setRomSavesPath(card.rep.id, null)
            }) { Text("Restablecer") }
        }
    }

    // ── Historial de copias de seguridad (versionado de saves) ──
    val versions = remember(card, romsVersion, state.downloadedVersion.collectAsState().value) {
        state.saveBackupVersions(card.rep.id)
    }
    if (versions.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        Text("Copias de seguridad (${versions.size})", style = MaterialTheme.typography.titleSmall)
        Text(
            "Se guardan automáticamente antes de que un sync sobrescriba un save de este juego.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        versions.take(10).forEach { v ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(v.fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${formatTimestamp(v.timestamp)} · ${formatBytes(v.sizeBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { state.restoreSaveBackup(v) }) { Text("Restaurar") }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            Modifier.width(130.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipsFlow(rep: RomDto) {
    val chips = buildList {
        addAll(rep.genres.take(6))
        addAll(rep.languages.take(6))
    }
    if (chips.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        chips.forEach { AssistChipLike(it) }
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun AssistChipLike(text: String) {
    Box(
        Modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun ratingRow(rep: RomDto) {
    val rating = rep.igdbMetadata?.totalRating?.toDoubleOrNull()
    if (rating != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                AppIcons.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                String.format("%.0f", rating),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

private fun releaseYear(rep: RomDto): Int? {
    val ts = rep.igdbMetadata?.firstReleaseDate ?: return null
    return if (ts > 0) java.time.Instant.ofEpochSecond(ts).atZone(java.time.ZoneOffset.UTC).year else null
}

private fun regionsLabel(rep: RomDto): String = rep.regions.joinToString("/")

/** Miniatura de screenshot 16:9; click → visor. */
@Composable
private fun ScreenshotThumb(url: String?, onClick: () -> Unit) {
    if (url == null) return
    Card(
        modifier = Modifier.width(160.dp).aspectRatio(16f / 9f).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val bitmap = produceCover(url)
            if (bitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                CircularProgressIndicator(Modifier.size(18.dp))
            }
        }
    }
}

/**
 * Visor de screenshots a pantalla completa con pager horizontal, contador
 * n/total y zoom alternante al hacer click — paridad con el
 * ScreenshotViewerDialog de Android. En desktop el diálogo se dimensiona al
 * tamaño de la ventana principal (DialogProperties no tiene el flag Android
 * usePlatformDefaultSize).
 */
@Composable
fun ScreenshotViewerDialog(urls: List<String?>, initialIndex: Int, onDismiss: () -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Sin LocalWindow en desktop: se dimensiona a la pantalla completa.
    val screen = remember { java.awt.Toolkit.getDefaultToolkit().screenSize }
    val wDp = with(density) { (screen.width * 0.92).toInt().toDp() }
    val hDp = with(density) { (screen.height * 0.92).toInt().toDp() }

    Dialog(onDismissRequest = onDismiss) {
        val nonNull = urls.filterNotNull()
        val pagerState = androidx.compose.foundation.pager.rememberPagerState(
            initialPage = initialIndex.coerceIn(0, (nonNull.size - 1).coerceAtLeast(0)),
        ) { nonNull.size }
        var zoomed by remember { mutableStateOf(false) }
        Box(Modifier.size(wDp, hDp).background(Color.Black.copy(alpha = 0.95f))) {
            androidx.compose.foundation.pager.HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val bitmap = produceCover(nonNull[page])
                if (bitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = if (zoomed) ContentScale.FillBounds else ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().clickable { zoomed = !zoomed },
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }
            Text(
                "${pagerState.currentPage + 1} / ${nonNull.size}",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            ) {
                Icon(AppIcons.Close, contentDescription = "Cerrar", tint = Color.White)
            }
        }
    }
}

private fun openTrailer(videoId: String) {
    runCatching {
        java.awt.Desktop.getDesktop().browse(URI("https://www.youtube.com/watch?v=$videoId"))
    }
}
