@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.davidadrianrg.atril.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.foundation.Image
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.davidadrianrg.atril.RomMSyncApplication
import com.davidadrianrg.atril.core.i18n.tr
import androidx.compose.ui.geometry.isSpecified
import com.davidadrianrg.atril.core.library.CoverRatio
import com.davidadrianrg.atril.domain.model.DownloadStatus
import com.davidadrianrg.atril.domain.model.formatBytesForUi
import com.davidadrianrg.atril.domain.model.Platform
import com.davidadrianrg.atril.domain.model.Rom
import com.davidadrianrg.atril.domain.model.RomWithStatus
import com.davidadrianrg.atril.ui.components.FilledTonalIconButton
import com.davidadrianrg.atril.ui.viewmodel.LibraryEvent
import com.davidadrianrg.atril.ui.viewmodel.LibraryViewModel
import com.davidadrianrg.atril.ui.viewmodel.RomSort

private enum class RomFilter(val labelKey: String) {
    ALL("library.filter.all"),
    MISSING("library.filter.missing"),
    DOWNLOADED("library.filter.downloaded"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onOpenDownloads: () -> Unit = {}) {
    val context = LocalContext.current
    val container = (context.applicationContext as RomMSyncApplication).container
    val windowInfo = com.davidadrianrg.atril.ui.components.rememberWindowInfo()

    val viewModel: LibraryViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                LibraryViewModel(
                    romRepository = container.romRepository,
                    downloadManager = container.downloadManager,
                    aspectRatioCalculator = com.davidadrianrg.atril.util.CoverAspectRatioCalculator(
                        context = context.applicationContext,
                        imageLoader = coil.Coil.imageLoader(context.applicationContext),
                    ),
                )
            }
        }
    )

    val settings by container.settingsRepository.settings.collectAsState(
        initial = com.davidadrianrg.atril.data.local.ServerConfig("", "", "", 2)
    )
    val platforms by container.romRepository.getVisiblePlatforms().collectAsState(initial = emptyList())
    // La sección de sync del detalle de juego solo aparece si la función está
    // activada en Configuración.
    val saveSyncEnabled by container.settingsRepository.saveSyncEnabled.collectAsState(initial = true)
    val romsWithStatus by viewModel.romsWithStatus.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedPlatformId by viewModel.selectedPlatformId.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(RomFilter.ALL) }
    var platformMenuExpanded by remember { mutableStateOf(false) }

    // ── Server-side search with debounce ──────────────────────────────
    LaunchedEffect(selectedPlatformId) {
        snapshotFlow { searchQuery }
            .debounce(400)
            .distinctUntilChanged()
            .collect { query ->
                if (selectedPlatformId != null) {
                    viewModel.searchRoms(query, settings.serverUrl, settings.apiKey)
                }
            }
    }

    // Bottom sheet state for long-press game details
    var selectedRom by remember { mutableStateOf<Rom?>(null) }
    val insertion = remember { InsertionState() }
    var insertJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    InsertionDialogLayer(insertion) { insertJob?.cancel() }

    val manualIds by viewModel.manualDownloadedIds.collectAsState()
    // ROM pendiente de confirmación de borrado
    var romToDelete by remember { mutableStateOf<Rom?>(null) }
    val sheetState = rememberModalBottomSheetState()
    // En pantallas compactas el sheet a media altura no deja ver los menús:
    // lo llevamos a pantalla completa con skipPartiallyExpanded.
    val isCompactSheet = com.davidadrianrg.atril.ui.components.rememberWindowInfo().isCompact

    // Snackbar for download feedback
    val snackbarHostState = remember { SnackbarHostState() }

    // Collect one-shot events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is LibraryEvent.DownloadStarted -> {
                    snackbarHostState.showSnackbar(
                        message = tr("app.snackbar.downloading", event.romName),
                        duration = SnackbarDuration.Short,
                    )
                }
                is LibraryEvent.BatchDownloadStarted -> {
                    snackbarHostState.showSnackbar(
                        message = tr("app.snackbar.downloads_queued", event.count),
                        duration = SnackbarDuration.Short,
                    )
                }
                is LibraryEvent.DownloadDeleted -> {
                    snackbarHostState.showSnackbar(
                        message = tr("app.snackbar.deleted", event.romName),
                        duration = SnackbarDuration.Short,
                    )
                }
                is LibraryEvent.RomMoved -> {
                    snackbarHostState.showSnackbar(
                        message = tr("android.library.rom_moved", event.romName, event.targetPath),
                        duration = SnackbarDuration.Short,
                    )
                }
                is LibraryEvent.Error -> {
                    snackbarHostState.showSnackbar(
                        message = event.message,
                        duration = SnackbarDuration.Long,
                    )
                }
            }
        }
    }

    // Filter ROMs by download status only (search is server-side)
    val platformDownloaded by viewModel.platformDownloadedCards.collectAsState()
    val filteredRoms by remember(romsWithStatus, selectedFilter, platformDownloaded) {
        derivedStateOf {
            when (selectedFilter) {
                RomFilter.ALL -> romsWithStatus
                RomFilter.MISSING -> romsWithStatus.filter { it.status == DownloadStatus.NOT_DOWNLOADED }
                // Descargados de TODA la plataforma, no solo las páginas cargadas.
                RomFilter.DOWNLOADED -> platformDownloaded.ifEmpty {
                    romsWithStatus.filter { it.status == DownloadStatus.DOWNLOADED }
                }
            }
        }
    }

    // Missing across the WHOLE platform (all server pages), not only the pages
    // scrolled so far. Fallback to the local list while the full fetch runs.
    val platformMissing by viewModel.platformMissingRoms.collectAsState()
    val localMissing = remember(filteredRoms) {
        filteredRoms.filter { it.status == DownloadStatus.NOT_DOWNLOADED }.map { it.rom }
    }
    val missingRoms: List<Rom> = platformMissing.ifEmpty { localMissing }

    LaunchedEffect(selectedPlatformId, settings.serverUrl, settings.apiKey) {
        if (selectedPlatformId != null) viewModel.loadAllPlatformRoms()
    }

    // State for the batch download confirmation dialog.
    var showBatchDialog by remember { mutableStateOf(false) }

    // State for the "upload game" dialog.
    var showUploadGame by remember { mutableStateOf(false) }

    /**
     * Destino inicial de los chips del diálogo batch: el default de la
     * plataforma seleccionada (si tiene) o "Interna" como antes.
     */
    val batchDefaultRoot: String? = remember(selectedPlatformId, settings.dualRomsPathsEnabled) {
        if (!settings.dualRomsPathsEnabled) return@remember null
        val platform = platforms.find { it.id == selectedPlatformId }
        when (platform?.downloadStorage) {
            com.davidadrianrg.atril.domain.model.PlatformDownloadStorage.INTERNAL -> null
            com.davidadrianrg.atril.domain.model.PlatformDownloadStorage.SD -> settings.secondaryRomsPath
            else -> null
        }
    }

    // ── Modo dos rutas: destino pendiente de elección ────────────────
    // ROM individual esperando que el usuario elija interna/SD.
    var romPendingDestination by remember { mutableStateOf<Rom?>(null) }
    // Destino elegido en el diálogo batch (null = interna; ruta = SD).
    var batchSelectedRoot by remember { mutableStateOf<String?>(null) }

    // Al abrir el diálogo batch, arranca con el destino por defecto de la
    // plataforma (o Interna si la plataforma está en "Preguntar").
    LaunchedEffect(showBatchDialog) {
        if (showBatchDialog) {
            batchSelectedRoot = batchDefaultRoot
        }
    }

    /** Encola la descarga de un juego (con destino explícito o el principal). */
    val enqueueSingleDownload: (Rom, String?) -> Unit = { rom, rootPath ->
        if (settings.isConfigured) {
            // Grupo completo del juego: multi-disc llega de la API como varias
            // ROMs (mismo igdbId) y deben descargarse todas juntas.
            val group = romsWithStatus.firstOrNull { it.rom.id == rom.id }?.groupRomIds ?: listOf(rom.id)
            val groupRoms = romsWithStatus.map { it.rom }.filter { it.id in group }
            val romsToEnqueue = groupRoms.ifEmpty { listOf(rom) }
            romsToEnqueue.forEach { r ->
                container.downloadManager.enqueueDownload(
                    rom = r,
                    serverUrl = settings.serverUrl,
                    romsRootPath = rootPath,
                )
            }
            viewModel.onDownloadEnqueued(rom.name)
        }
    }

    /**
     * Inicia la descarga de un juego resolviendo el destino: si el modo dos
     * rutas está activo y la plataforma tiene un destino por defecto
     * (Interna/SD), descarga directo ahí; si no (Preguntar), abre el diálogo.
     */
    val startDownload: (Rom) -> Unit = { rom ->
        if (settings.isConfigured) {
            if (settings.dualRomsPathsEnabled) {
                val platform = platforms.find { it.id == rom.platformId }
                when (platform?.downloadStorage) {
                    com.davidadrianrg.atril.domain.model.PlatformDownloadStorage.INTERNAL ->
                        enqueueSingleDownload(rom, settings.romsRootPath)
                    com.davidadrianrg.atril.domain.model.PlatformDownloadStorage.SD ->
                        enqueueSingleDownload(rom, settings.secondaryRomsPath)
                    else -> romPendingDestination = rom
                }
            } else {
                enqueueSingleDownload(rom, null)
            }
        }
    }

    // Densidad de la rejilla adaptada al tamaño de ventana: en portátiles
    // compactos (4" 16:9/4:3) buscamos ~3 columnas en horizontal y 2 en
    // vertical; en móviles normales subimos el tamaño mínimo de tarjeta.
    val minCardSize = when {
        windowInfo.isCompactLandscape -> 96.dp
        windowInfo.isCompactPortrait -> 120.dp
        windowInfo.isLandscape -> 110.dp
        else -> 140.dp
    }

    val isLandscape = windowInfo.isLandscape

    // Aspect ratio de los covers segun la plataforma seleccionada
    val coverAspectRatio = remember(selectedPlatformId, platforms) {
        val platform = platforms.find { it.id == selectedPlatformId }
        platform?.measuredAspectRatio ?: parseAspectRatio(platform?.aspectRatio)
    }

    // Mide el aspect ratio real de los covers una vez por plataforma, reusando
    // las carátulas ya cargadas en la rejilla.
    LaunchedEffect(selectedPlatformId, romsWithStatus, platforms) {
        val platformId = selectedPlatformId ?: return@LaunchedEffect
        val platform = platforms.find { it.id == platformId } ?: return@LaunchedEffect
        val coverUrls = romsWithStatus.mapNotNull {
            it.rom.coverUrlLarge ?: it.rom.coverUrlSmall
        }
        viewModel.ensureAspectRatioMeasured(
            platformId = platformId,
            alreadyMeasured = platform.measuredAspectRatio != null,
            coverUrls = coverUrls,
        )
    }

    // No pintar la rejilla hasta tener el ratio real medido (si no, las portadas
    // aparecen con 2:3 y saltan al reajustarse). Con tope de 4 s para no bloquear.
    var ratioTimedOut by remember(selectedPlatformId) { mutableStateOf(false) }
    LaunchedEffect(selectedPlatformId) { kotlinx.coroutines.delay(4_000); ratioTimedOut = true }
    val ratioPending = !ratioTimedOut && selectedPlatformId != null &&
        platforms.find { it.id == selectedPlatformId }?.measuredAspectRatio == null &&
        romsWithStatus.any { (it.rom.coverUrlLarge ?: it.rom.coverUrlSmall) != null }

    // ── Infinite scroll state ──────────────────────────────────────────
    val gridState = rememberLazyStaggeredGridState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()

    // Detect when user scrolls near the bottom -> trigger loadMore
    val reachedBottom by remember {
        derivedStateOf {
            val lastVisibleIndex = gridState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: -1
            val totalItems = filteredRoms.size
            totalItems > 0 && lastVisibleIndex >= totalItems - 8
        }
    }
    LaunchedEffect(reachedBottom, selectedPlatformId) {
        if (reachedBottom && hasMore && !isLoadingMore && selectedPlatformId != null) {
            viewModel.loadMore(settings.serverUrl, settings.apiKey)
        }
    }

    // Toolbar unificada: una sola fila en horizontal (portátiles) y hasta dos
    // en vertical, con alturas reducidas en ventanas compactas.
    val compact = windowInfo.isCompact
    val fieldShape = RoundedCornerShape(12.dp)

    val scope = rememberCoroutineScope()
    val showScrollToTop by remember {
        derivedStateOf { gridState.firstVisibleItemIndex > 6 }
    }

    // Estado del header colapsable. La conexión nestedScroll se instala en la
    // Column que contiene TANTO el header como la rejilla: los deltas de
    // scroll de un contenedor lazy suben hacia sus ancestros, y la rejilla es
    // hermana del header, no su hija.
    val headerState = com.davidadrianrg.atril.ui.components.rememberCollapsingHeaderState()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            androidx.compose.animation.AnimatedVisibility(
                visible = showScrollToTop,
                enter = androidx.compose.animation.fadeIn() +
                    androidx.compose.animation.scaleIn(initialScale = 0.6f),
                exit = androidx.compose.animation.fadeOut() +
                    androidx.compose.animation.scaleOut(targetScale = 0.6f),
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        scope.launch { gridState.animateScrollToItem(0) }
                    },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = CircleShape,
                ) {
                    Icon(Icons.Filled.ExpandLess, contentDescription = tr("library.back_to_top"))
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
                .nestedScroll(headerState.connection),
        ) {
            // Header colapsable: en pantallas pequeñas recupera ~140dp para
            // la rejilla al hacer scroll hacia abajo. El contenido queda
            // anclado arriba, así que el selector de plataforma es lo último
            // en ocultarse (y siempre accesible tras expandir).
            com.davidadrianrg.atril.ui.components.CollapsingHeader(state = headerState) {
                Column {
            // ── Fila 1: selector de plataforma + acciones ────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (compact) 4.dp else 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ExposedDropdownMenuBox(
                    expanded = platformMenuExpanded,
                    onExpandedChange = { platformMenuExpanded = it },
                    modifier = if (isLandscape) Modifier.width(340.dp) else Modifier.weight(1f),
                ) {
                    OutlinedTextField(
                        value = platforms.find { it.id == selectedPlatformId }?.name
                            ?: tr("android.library.select_platform"),
                        onValueChange = {},
                        readOnly = true,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(platformMenuExpanded) },
                        singleLine = true,
                        shape = fieldShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (compact) 46.dp else 56.dp)
                            .menuAnchor(),
                    )
                    ExposedDropdownMenu(
                        expanded = platformMenuExpanded,
                        onDismissRequest = { platformMenuExpanded = false },
                    ) {
                        platforms.forEach { platform ->
                            DropdownMenuItem(
                                text = { Text("${platform.name} (${platform.romCount})") },
                                onClick = {
                                    searchQuery = ""
                                    viewModel.selectPlatform(platform.id, settings.serverUrl, settings.apiKey)
                                    platformMenuExpanded = false
                                },
                            )
                        }
                    }
                }
                if (isLandscape) {
                    LibrarySearchField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        compact = compact,
                        modifier = Modifier.weight(1f),
                    )
                }
                // Descargar faltantes (batch)
                if (selectedPlatformId != null && missingRoms.isNotEmpty()) {
                    FilledTonalIconButton(
                        onClick = { showBatchDialog = true },
                        size = if (compact) 46.dp else 52.dp,
                        badge = "${missingRoms.size}",
                    ) {
                        Icon(
                            Icons.Outlined.Download,
                            contentDescription = tr("library.download_missing", missingRoms.size),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            // ── Fila 2: búsqueda (solo en vertical; en horizontal va al lado) ──
            if (!isLandscape) {
                LibrarySearchField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    compact = compact,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
            }

            // ── Fila 3: filtros + orden + región + aleatorio ────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RomFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = {
                            Text(
                                tr(filter.labelKey),
                                style = if (compact) MaterialTheme.typography.labelSmall
                                        else MaterialTheme.typography.labelMedium,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    )
                }
                Spacer(Modifier.weight(1f))

                val sort by viewModel.sort.collectAsState()
                val regionFilter by viewModel.regionFilter.collectAsState()
                val availableRegions by viewModel.availableRegions.collectAsState()

                // Juego aleatorio de la vista actual
                if (romsWithStatus.isNotEmpty()) {
                    IconButton(onClick = { selectedRom = romsWithStatus.random().rom }) {
                        Icon(Icons.Filled.Casino, contentDescription = tr("library.random_game"))
                    }
                }

                // Cola de descargas (ya no es una pestaña propia)
                com.davidadrianrg.atril.ui.components.DownloadQueueButton(onClick = onOpenDownloads)

                // Subir juego al servidor
                IconButton(onClick = { showUploadGame = true }) {
                    Icon(Icons.Outlined.Upload, contentDescription = tr("library.upload.title"))
                }

                // Filtro por región
                if (availableRegions.isNotEmpty()) {
                    var regionMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { regionMenu = true }) {
                            Icon(
                                Icons.Filled.Public,
                                contentDescription = tr("library.filter_region"),
                                tint = if (regionFilter != null) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DropdownMenu(expanded = regionMenu, onDismissRequest = { regionMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(tr("library.all_regions")) },
                                onClick = { viewModel.setRegionFilter(null); regionMenu = false },
                            )
                            availableRegions.forEach { r ->
                                DropdownMenuItem(
                                    text = { Text(if (r == regionFilter) "● $r" else r) },
                                    onClick = { viewModel.setRegionFilter(r); regionMenu = false },
                                )
                            }
                        }
                    }
                }

                // Orden
                var sortMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { sortMenu = true }) {
                        Icon(
                            Icons.Filled.Sort,
                            contentDescription = tr("android.library.sort"),
                            tint = if (sort != RomSort.NAME_ASC) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        RomSort.entries.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(if (s == sort) "● " + tr(s.labelKey) else tr(s.labelKey)) },
                                onClick = { viewModel.setSort(s); sortMenu = false },
                            )
                        }
                    }
                }
            }
                }
            }

            // ── Grid of games ───────────────────────────────────────────
            if ((isLoading && romsWithStatus.isEmpty()) || ratioPending) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.size(12.dp))
                        Text(
                            tr("library.loading"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                return@Column
            }

            if (selectedPlatformId == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    com.davidadrianrg.atril.ui.components.EmptyState(
                        icon = Icons.Outlined.GridView,
                        title = tr("android.library.select_platform_hint"),
                        description = tr("android.library.select_platform_desc"),
                    )
                }
                return@Column
            }

            // ── Empty state ─────────────────────────────────────────────
            if (filteredRoms.isEmpty() && !isLoading && !isLoadingMore) {
                val (icon, title, message) = when {
                    searchQuery.isNotBlank() -> Triple(
                        Icons.Filled.Search,
                        tr("library.empty.no_results.title"),
                        tr("android.library.empty.no_results.description", searchQuery),
                    )
                    selectedFilter == RomFilter.MISSING -> Triple(
                        Icons.Outlined.DownloadDone,
                        tr("library.empty.all_downloaded.title"),
                        tr("android.library.empty.all_downloaded.description"),
                    )
                    selectedFilter == RomFilter.DOWNLOADED -> Triple(
                        Icons.Outlined.Download,
                        tr("library.empty.no_downloads.title"),
                        tr("android.library.empty.no_downloads.description"),
                    )
                    else -> Triple(
                        Icons.Outlined.SportsEsports,
                        tr("library.empty.no_games.title"),
                        tr("android.library.empty.no_games.description"),
                    )
                }
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    com.davidadrianrg.atril.ui.components.EmptyState(
                        icon = icon,
                        title = title,
                        description = message,
                    )
                }
                return@Column
            }

            PullToRefreshBox(
                isRefreshing = isLoading && romsWithStatus.isNotEmpty(),
                onRefresh = {
                    if (selectedPlatformId != null) {
                        viewModel.refresh(settings.serverUrl, settings.apiKey)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                // Mampostería real: cada carátula conserva su ratio y las columnas se rellenan sin
                // huecos (la rejilla uniforme dejaba filas altas con tarjetas cortas arriba).
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Adaptive(minSize = minCardSize),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalItemSpacing = 14.dp,
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) {
                    items(filteredRoms, key = { it.rom.id }) { romStatus ->
                        RomCard(
                            romWithStatus = romStatus,
                            coverAspectRatio = coverAspectRatio,
                            onDownload = { startDownload(romStatus.rom) },
                            onLongPress = { selectedRom = romStatus.rom },
                        )
                    }
                    // Loading more footer
                    if (isLoadingMore) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Bottom sheet: game details ─────────────────────────────────────
    if (selectedRom != null) {
        val rom = selectedRom!!
        val romStatus = romsWithStatus.firstOrNull { it.rom.id == rom.id }
        val isDownloaded = romStatus != null && romStatus.status == DownloadStatus.DOWNLOADED
        val isDownloading = romStatus != null && romStatus.status == DownloadStatus.DOWNLOADING
        val syncGroupIds = romStatus?.groupRomIds ?: listOf(rom.id)
        val retroArchBasePath by container.settingsRepository.retroArchBasePath.collectAsState(
            initial = com.davidadrianrg.atril.data.local.SettingsDataStore.DEFAULT_RETROARCH_PATH,
        )
        val syncConfig by viewModel.observeRomSyncConfig(rom.id)
            .collectAsState(initial = null)
        val isMovingRom by viewModel.isMovingRom.collectAsState()
        val movingProgress by viewModel.movingProgress.collectAsState()
        // Volumen actual del juego: true si su ruta local está bajo la ruta
        // secundaria (SD).
        val isOnSd = remember(syncConfig?.localPath, settings.secondaryRomsPath) {
            val p = syncConfig?.localPath ?: ""
            val sd = settings.secondaryRomsPath.trimEnd('/')
            p.isNotBlank() && sd.isNotBlank() && (p == sd || p.startsWith("$sd/"))
        }
        // Ruta de saves que hereda de la plataforma (la que se sobreescribe por juego)
        val platform = platforms.find { it.id == rom.platformId }
        val platformSavesPath = remember(platform, retroArchBasePath) {
            platform?.savesPathOverride?.takeIf { it.isNotBlank() }
                ?: com.davidadrianrg.atril.core.sync.platform.SaveHandlerRegistry.getDefaultSavesPath(
                    emulatorId = platform?.emulatorId
                        ?: com.davidadrianrg.atril.core.sync.platform.SaveHandlerRegistry
                            .getDefaultEmulator(rom.platformSlug).id,
                    platformSlug = rom.platformSlug,
                    retroArchBase = retroArchBasePath,
                )
        }
        ModalBottomSheet(
            onDismissRequest = { selectedRom = null },
            sheetState = if (isCompactSheet) {
                rememberModalBottomSheetState(skipPartiallyExpanded = true)
            } else {
                sheetState
            },
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            RomDetailSheet(
                rom = rom,
                platformName = platform?.name,
                isDownloaded = isDownloaded,
                isDownloading = isDownloading,
                discCount = romStatus?.discCount ?: 1,
                syncEnabled = saveSyncEnabled,
                savesPathOverride = syncConfig?.savesPathOverride,
                excludedFromSync = syncConfig?.excludedFromSync ?: false,
                platformSavesPath = platformSavesPath,
                onSavesPathOverrideChange = { path -> viewModel.setRomSavesPathOverride(rom.id, path) },
                onExcludedFromSyncChange = { excluded -> viewModel.setRomExcludedFromSync(rom.id, excluded) },
                onDownload = {
                    startDownload(rom)
                    selectedRom = null
                },
                onDelete = {
                    romToDelete = selectedRom
                    selectedRom = null
                },
                onClose = { selectedRom = null },
                serverUrl = settings.serverUrl,
                apiKey = settings.apiKey,
                isManualDownloaded = romStatus?.groupRomIds?.all { it in manualIds } == true,
                onMarkDownloaded = {
                    viewModel.markDownloadedManually(rom, romStatus?.groupRomIds ?: listOf(rom.id))
                    selectedRom = null
                },
                onUnmarkDownloaded = {
                    viewModel.unmarkDownloadedManually(rom, romStatus?.groupRomIds ?: listOf(rom.id))
                    selectedRom = null
                },
                showMoveOption = settings.dualRomsPathsEnabled,
                isOnSd = isOnSd,
                isMoving = isMovingRom,
                moveProgress = movingProgress?.second,
                onMove = { toSd ->
                    val targetRoot = if (toSd) settings.secondaryRomsPath else settings.romsRootPath
                    viewModel.moveDownloadedRom(rom, targetRoot)
                },
                launcherControls = if (isDownloaded) {
                    val localPath = syncConfig?.localPath.orEmpty()
                    val platformLabel = platform?.name ?: rom.platformSlug.uppercase()
                    rememberLauncherDetailControls(
                        rom = rom,
                        platformLabel = platformLabel,
                        platformEmulatorId = platform?.emulatorId,
                        launchable = localPath.isNotBlank(),
                        onPlay = {
                            // Misma secuencia que en Play: preparar → overlay de inserción → lanzar.
                            selectedRom = null // la hoja es otra ventana: cerrarla para ver el overlay
                            insertJob = scope.launch {
                                val plan = container.emulatorLauncher.prepare(
                                    com.davidadrianrg.atril.launch.LaunchRequest(
                                        platformSlug = rom.platformSlug,
                                        platformLabel = platformLabel,
                                        localPath = localPath,
                                        knownFileNames = rom.files.map { it.filename },
                                        overrideFamilyId = container.settingsRepository.launchEmulatorOverrides
                                            .first()[com.davidadrianrg.atril.core.launch.LaunchPlatforms.canonical(rom.platformSlug)],
                                        saveEmulatorId = platform?.emulatorId,
                                    ),
                                )
                                val error = when (plan) {
                                    is com.davidadrianrg.atril.launch.LaunchPlan.Failed -> plan.message
                                    is com.davidadrianrg.atril.launch.LaunchPlan.Ready ->
                                        insertion.run(
                                            InsertionSpec(
                                                rom.name,
                                                rom.coverUrlLarge ?: rom.coverUrlSmall,
                                                com.davidadrianrg.atril.core.launch.LaunchPlatforms.isOptical(rom.platformSlug),
                                            ),
                                        ) { container.emulatorLauncher.start(context, plan) }
                                }
                                error?.let { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show() }
                            }
                        },
                    )
                } else {
                    null
                },
            )
        }
    }

    // ── Batch download confirmation dialog ────────────────────────────────
    if (showUploadGame) {
        com.davidadrianrg.atril.ui.components.UploadGameDialog(
            platforms = platforms,
            onDismiss = { showUploadGame = false },
        )
    }

    if (showBatchDialog && missingRoms.isNotEmpty()) {
        val platformName = platforms.find { it.id == selectedPlatformId }?.name
            ?: tr("android.library.platform_fallback")
        AlertDialog(
            onDismissRequest = { showBatchDialog = false },
            icon = {
                Icon(
                    Icons.Filled.DownloadDone,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            title = { Text(tr("android.library.download_missing_title")) },
            text = {
                Column {
                    Text(
                        tr("android.library.batch.confirm", missingRoms.size, platformName),
                    )
                    if (settings.dualRomsPathsEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            tr("android.library.destination"),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = batchSelectedRoot == null,
                                onClick = { batchSelectedRoot = null },
                                label = { Text(tr("android.library.storage_internal")) },
                            )
                            FilterChip(
                                selected = batchSelectedRoot != null,
                                onClick = { batchSelectedRoot = settings.secondaryRomsPath },
                                label = { Text(tr("android.library.storage_sd")) },
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.enqueueBatchDownload(
                            roms = missingRoms,
                            serverUrl = settings.serverUrl,
                            romsRootPath = batchSelectedRoot,
                        )
                        showBatchDialog = false
                    },
                ) {
                    Text(tr("common.download"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDialog = false }) {
                    Text(tr("common.cancel"))
                }
            },
        )
    }

    // ── Dual-path destination picker (single ROM) ─────────────────────────
    romPendingDestination?.let { rom ->
        AlertDialog(
            onDismissRequest = { romPendingDestination = null },
            icon = {
                Icon(
                    Icons.Filled.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            title = { Text(tr("android.library.where_to_download")) },
            text = {
                Column {
                    Text(rom.name, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        settings.romsRootPath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        settings.secondaryRomsPath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        enqueueSingleDownload(rom, null)
                        romPendingDestination = null
                    },
                ) {
                    Text(tr("android.library.storage_internal_memory"))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        enqueueSingleDownload(rom, settings.secondaryRomsPath)
                        romPendingDestination = null
                    },
                ) {
                    Text(tr("android.library.storage_sd"))
                }
            },
        )
    }

    // ── Delete download confirmation dialog ───────────────────────────────
    romToDelete?.let { rom ->
        AlertDialog(
            onDismissRequest = { romToDelete = null },
            icon = {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = { Text(tr("game.delete_download")) },
            text = {
                val discs = romsWithStatus.firstOrNull { it.rom.id == rom.id }?.discCount ?: 1
                Text(
                    if (discs > 1) {
                        tr("android.library.delete_confirm_multi", rom.name, discs)
                    } else {
                        tr("android.library.delete_confirm_single", rom.name)
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val groupIds = romsWithStatus.firstOrNull { it.rom.id == rom.id }?.groupRomIds ?: listOf(rom.id)
                        viewModel.deleteDownloadedRom(rom, groupIds)
                        romToDelete = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(tr("game.delete.confirm"))
                }
            },
            dismissButton = {
                TextButton(onClick = { romToDelete = null }) {
                    Text(tr("common.cancel"))
                }
            },
        )
    }
}

// ── Game card with long-press ──────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RomCard(
    romWithStatus: RomWithStatus,
    coverAspectRatio: Float,
    onDownload: (RomWithStatus) -> Unit,
    onLongPress: (RomWithStatus) -> Unit,
) {
    val context = LocalContext.current
    val coverUrl = romWithStatus.rom.coverUrlLarge ?: romWithStatus.rom.coverUrlSmall

    // El painter expone el tamaño intrínseco de la imagen una vez decodificada:
    // cada card adopta la proporción REAL de su cover (masonry), con la mediana
    // de la plataforma como placeholder mientras carga o si la decodificación falla.
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(coverUrl)
            .crossfade(true)
            .build(),
    )
    val intrinsic = painter.intrinsicSize
    val cardAspectRatio = if (intrinsic != Size.Unspecified && intrinsic.width > 0f && intrinsic.height > 0f) {
        intrinsic.width / intrinsic.height
    } else {
        coverAspectRatio
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(cardAspectRatio)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                ),
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
            )
            .combinedClickable(
                onClick = {},
                onLongClick = { onLongPress(romWithStatus) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Placeholder mientras decodifica el cover
        if (painter.state is coil.compose.AsyncImagePainter.State.Loading ||
            painter.state is coil.compose.AsyncImagePainter.State.Error
        ) {
            Icon(
                Icons.Outlined.SportsEsports,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(32.dp),
            )
        }
        Image(
            painter = painter,
            contentDescription = romWithStatus.rom.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Scrim degradado inferior para legibilidad del título
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.78f),
                    ),
                ),
        )

        // Título superpuesto en la parte inferior
        Text(
            text = romWithStatus.rom.name,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 8.dp, vertical = 8.dp),
        )

        // Badge de descargado
        if (romWithStatus.status == DownloadStatus.DOWNLOADED) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(26.dp)
                    .background(MaterialTheme.colorScheme.secondary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = tr("common.downloaded"),
                    tint = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // Badge multi-disc: nº de discos agrupados en esta card (esquina
        // superior izquierda). Los juegos de un disco no muestran nada.
        if (romWithStatus.discCount > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(
                        MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f),
                        RoundedCornerShape(10.dp),
                    )
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(
                    Icons.Outlined.Album,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    "${romWithStatus.discCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
        }

        // Botón de descarga (solo si no descargado)
        if (romWithStatus.status == DownloadStatus.NOT_DOWNLOADED) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(34.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                        CircleShape,
                    )
                    .combinedClickable(onClick = { onDownload(romWithStatus) }),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Download,
                    contentDescription = tr("common.download"),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // Overlay de descarga en curso
        if (romWithStatus.status == DownloadStatus.DOWNLOADING) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(30.dp),
                    strokeWidth = 3.dp,
                    color = Color.White,
                )
            }
        }
    }
}

/** Aspect ratio por defecto (2:3) cuando la plataforma no lo especifica. */
private const val DEFAULT_COVER_ASPECT_RATIO = 0.667f

/**
 * Parsea un string de aspect ratio como "2 / 3" o "3 / 4" a un Float.
 * Devuelve el ratio por defecto (2:3) si no se puede parsear.
 */
private fun parseAspectRatio(ratioStr: String?): Float {
    if (ratioStr.isNullOrBlank()) return DEFAULT_COVER_ASPECT_RATIO
    val parts = ratioStr.split("/").map { it.trim() }
    if (parts.size != 2) return DEFAULT_COVER_ASPECT_RATIO
    val w = parts[0].toFloatOrNull() ?: return DEFAULT_COVER_ASPECT_RATIO
    val h = parts[1].toFloatOrNull() ?: return DEFAULT_COVER_ASPECT_RATIO
    if (h <= 0f) return DEFAULT_COVER_ASPECT_RATIO
    return w / h
}

// ── Bottom sheet: game detail ──────────────────────────────────────────
@Composable
internal fun RomDetailSheet(
    rom: Rom,
    platformName: String? = null,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    discCount: Int = 1,
    syncEnabled: Boolean,
    savesPathOverride: String?,
    excludedFromSync: Boolean,
    platformSavesPath: String,
    onSavesPathOverrideChange: (String?) -> Unit,
    onExcludedFromSyncChange: (Boolean) -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    serverUrl: String = "",
    apiKey: String = "",
    isManualDownloaded: Boolean = false,
    onMarkDownloaded: () -> Unit = {},
    onUnmarkDownloaded: () -> Unit = {},
    showMoveOption: Boolean = false,
    isOnSd: Boolean = false,
    isMoving: Boolean = false,
    moveProgress: Pair<Long, Long>? = null,
    onMove: (toSd: Boolean) -> Unit = {},
    wide: Boolean = false,
    /** Controles del launcher (Jugar, carta / medio físico, emulador). Solo juegos descargados. */
    launcherControls: LauncherDetailControls? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    var showFolderPicker by remember { mutableStateOf(false) }
    var showMatchDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
            .padding(top = 8.dp, bottom = 48.dp)
            .navigationBarsPadding(),
    ) {
        // Cover + title row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Respeta el ratio real de la carátula (cuadrada, apaisada…): alto fijo y
            // ancho derivado, sin recortar ni deformar.
            var coverRatio by remember(rom.id) { mutableStateOf(CoverRatio.DEFAULT) }
            AsyncImage(
                model = rom.coverUrlLarge ?: rom.coverUrlSmall,
                contentDescription = rom.name,
                contentScale = ContentScale.Fit,
                onSuccess = { st ->
                    val sz = st.painter.intrinsicSize
                    if (sz.isSpecified) coverRatio = CoverRatio.of(sz.width.toInt(), sz.height.toInt())
                },
                // Ancho acotado: una carátula apaisada (ratio hasta 2.5) no debe
                // comerse la columna del título, sobre todo en horizontal.
                modifier = Modifier
                    .sizeIn(
                        maxWidth = if (wide) 216.dp else 140.dp,
                        maxHeight = if (wide) 216.dp else 140.dp,
                    )
                    .aspectRatio(coverRatio)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = rom.name,
                    style = if (wide) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = platformName ?: rom.platformSlug.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                val metaParts = buildList {
                    if (rom.regions.isNotEmpty()) addAll(rom.regions)
                    rom.revision?.takeIf { it.isNotBlank() }?.let { add("Rev $it") }
                }
                if (metaParts.isNotEmpty()) {
                    Text(
                        text = metaParts.joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // En horizontal aprovechamos el ancho: la sinopsis acompaña
                // a la portada en vez de dejar media pantalla vacía.
                if (wide) {
                    rom.summary?.takeIf { it.isNotBlank() }?.let { summary ->
                        Spacer(Modifier.size(10.dp))
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = if (expanded) Int.MAX_VALUE else 6,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (summary.length > 120) {
                            TextButton(
                                onClick = { expanded = !expanded },
                                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                            ) {
                                Icon(
                                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    if (expanded) " " + tr("game.summary.show_less")
                                    else " " + tr("game.summary.show_more"),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = tr("game.viewer.close"))
            }
        }

        // Summary with expandable text (en horizontal ya va junto a la portada)
        if (!wide) rom.summary?.takeIf { it.isNotBlank() }?.let { summary ->
            Spacer(modifier = Modifier.size(16.dp))
            val maxLines = if (expanded) Int.MAX_VALUE else 3
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            // Show "ver más/menos" button only if text is long enough
            if (summary.length > 120) {
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.padding(top = 0.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                ) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        if (expanded) " " + tr("game.summary.show_less") else " " + tr("game.summary.show_more"),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.size(24.dp))

        launcherControls?.let { controls ->
            LauncherControlsSection(controls)
            Spacer(modifier = Modifier.size(32.dp))
        }

        // ── Multimedia: capturas y tráiler ──────────────────────────
        val hasMedia = rom.screenshots.isNotEmpty() || rom.youtubeVideoId != null
        if (hasMedia) {
            Text(
                text = tr("game.media.title"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            if (rom.screenshots.isNotEmpty()) {
                var selectedShot by remember { mutableStateOf<Int?>(null) }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(rom.screenshots.size) { index ->
                        AsyncImage(
                            model = rom.screenshots[index],
                            contentDescription = tr("android.library.screenshot_n", index + 1),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 128.dp, height = 72.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { selectedShot = index },
                        )
                    }
                }
                if (selectedShot != null) {
                    ScreenshotViewerDialog(
                        screenshots = rom.screenshots,
                        initialIndex = selectedShot!!,
                        onDismiss = { selectedShot = null },
                    )
                }
                if (rom.screenshots.size > 1) {
                    Text(
                        text = tr("android.library.screenshot_tap_hint"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            rom.youtubeVideoId?.takeIf { it.isNotBlank() }?.let { videoId ->
                if (rom.screenshots.isNotEmpty()) Spacer(modifier = Modifier.size(10.dp))
                val context = LocalContext.current
                FilledTonalButton(
                    onClick = {
                        openTrailer(context, videoId)
                    },
                ) {
                    Icon(
                        Icons.Filled.PlayCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Text("  " + tr("game.media.watch_trailer"))
                }
            }
            Spacer(modifier = Modifier.size(28.dp))
        }

        // Metadata rows
        DetailRow(tr("game.info.file"), rom.fileName)
        DetailRow(tr("game.info.size"), formatFileSize(rom.fileSizeBytes))
        rom.fileNameNoTags?.let { DetailRow(tr("game.info.clean_name"), it) }
        rom.fileExtension?.let { DetailRow(tr("game.info.extension"), it) }
        if (rom.isMulti) DetailRow(tr("game.info.multi_file"), tr("game.info.multi_file_value", rom.files.size))
        rom.igdbId?.let { DetailRow("IGDB ID", it.toString()) }

        // Idiomas y géneros como chips visuales
        if (rom.languages.isNotEmpty() || rom.genres.isNotEmpty()) {
            Spacer(modifier = Modifier.size(10.dp))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                rom.languages.forEach { lang ->
                    AssistChipLike("${rom.platformSlug.uppercase()} · $lang".substringAfter(" · "))
                }
                rom.genres.forEach { genre ->
                    AssistChipLike(genre)
                }
            }
        }

        Spacer(modifier = Modifier.size(32.dp))

        // ── RomM: guardados del servidor y emparejado (plegable) ──
        if (serverUrl.isNotBlank() && apiKey.isNotBlank()) DetailGroup(tr("game.group.romm")) {
            RomMStatusRows(serverUrl)

            // ── Buscar coincidencia (si el escaneo no reconoció el juego) ──
            Spacer(modifier = Modifier.size(20.dp))
            OutlinedButton(
                onClick = { showMatchDialog = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(tr("library.match.title")) }
            if (showMatchDialog) {
                com.davidadrianrg.atril.ui.components.MatchRomDialog(
                    romId = rom.id,
                    initialName = rom.name,
                    serverUrl = serverUrl,
                    apiKey = apiKey,
                    onDismiss = { showMatchDialog = false },
                )
            }
        }

        // Action button
        when {
            isDownloaded -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.DownloadDone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            "  " + tr("common.downloaded"),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    if (isManualDownloaded) {
                        OutlinedButton(
                            onClick = onUnmarkDownloaded,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(tr("library.unmark_downloaded"))
                        }
                    }
                    // ── Mover entre almacenamientos (modo dos rutas) ─────
                    // Solo para juegos descargados con el modo dual activo.
                    if (showMoveOption) {
                        OutlinedButton(
                            onClick = { onMove(!isOnSd) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isMoving,
                        ) {
                            if (isMoving) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    Icons.Filled.SwapHoriz,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Text(
                                if (isMoving) "  " + tr("android.library.moving")
                                else if (isOnSd) "  " + tr("android.library.move_to_internal")
                                else "  " + tr("android.library.move_to_sd")
                            )
                        }
                        // Barra de progreso de la copia (bytes + %)
                        if (isMoving && moveProgress != null && moveProgress.second > 0L) {
                            val (copied, total) = moveProgress
                            val pct = (copied * 100f / total).coerceIn(0f, 100f)
                            Spacer(modifier = Modifier.size(8.dp))
                            LinearProgressIndicator(
                                progress = { pct / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                            )
                            Spacer(modifier = Modifier.size(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    "${formatBytesForUi(copied)} / ${formatBytesForUi(total)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "${pct.toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            isDownloading -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        "  " + tr("android.library.downloading"),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    androidx.compose.material3.Button(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text("  " + tr("common.download"))
                    }
                    OutlinedButton(
                        onClick = onMarkDownloaded,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.DownloadDone, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text("  " + tr("library.mark_downloaded"))
                    }
                }
            }
        }

        // ── Configuración de sincronización de partidas ────────────────
        // Solo tiene sentido para juegos descargados (son los que se sincronizan)
        // y si la función de sync no está desactivada en Configuración.
        if ((isDownloaded && syncEnabled) || (serverUrl.isNotBlank() && apiKey.isNotBlank())) {
            Spacer(modifier = Modifier.size(16.dp))
            DetailGroup(tr("game.group.saves")) {
                if (isDownloaded && syncEnabled) {

            // Toggle de exclusión
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        tr("game.sync.exclude"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        tr("android.library.sync_exclude_hint"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                androidx.compose.material3.Switch(
                    checked = excludedFromSync,
                    onCheckedChange = onExcludedFromSyncChange,
                )
            }

            // Ruta de saves específica (deshabilitada si está excluido)
            if (!excludedFromSync) {
                Spacer(modifier = Modifier.size(16.dp))
                val hasOverride = !savesPathOverride.isNullOrBlank()
                val displayedPath = savesPathOverride?.takeIf { it.isNotBlank() } ?: platformSavesPath
                var manualPath by remember(savesPathOverride, platformSavesPath) {
                    mutableStateOf(displayedPath)
                }

                Text(
                    tr("android.library.saves_location"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    if (hasOverride) tr("android.library.saves_path_custom")
                    else tr("android.library.saves_path_inherited"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.size(8.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = manualPath,
                    onValueChange = { manualPath = it },
                    label = { Text(tr("android.library.saves_path_label")) },
                    placeholder = { Text(tr("android.library.saves_path_placeholder")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        androidx.compose.material3.IconButton(
                            onClick = { showFolderPicker = true },
                        ) {
                            Icon(
                                Icons.Filled.Folder,
                                contentDescription = tr("common.select_folder"),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                )
                Spacer(modifier = Modifier.size(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    androidx.compose.material3.FilledTonalButton(
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
                        androidx.compose.material3.Button(
                            onClick = { onSavesPathOverrideChange(manualPath.trim()) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(tr("game.sync.save_path"))
                        }
                    } else if (hasOverride) {
                        TextButton(onClick = { onSavesPathOverrideChange(null) }) {
                            Text(tr("game.sync.reset"))
                        }
                    }
                }
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    tr("android.library.saves_path_hint"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── Historial de copias de seguridad (versionado de saves) ──
            val sheetContext = androidx.compose.ui.platform.LocalContext.current
            val restorer = remember {
                (sheetContext.applicationContext as com.davidadrianrg.atril.RomMSyncApplication)
                    .container.saveBackupRestorer
            }
            val backups = remember(rom.id) { restorer.versions(rom.id) }
            if (backups.isNotEmpty()) {
                Spacer(modifier = Modifier.size(16.dp))
                Text(
                    tr("game.backups.title", backups.size),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    tr("game.backups.hint"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val restoreScope = androidx.compose.runtime.rememberCoroutineScope()
                var restoring by remember { mutableStateOf(false) }
                backups.take(5).forEach { v ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                v.fileName,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "${
                                    java.text.SimpleDateFormat(
                                        "d MMM HH:mm",
                                        java.util.Locale.getDefault(),
                                    ).format(java.util.Date(v.timestamp))
                                } · ${formatFileSize(v.sizeBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(
                            enabled = !restoring,
                            onClick = {
                                restoreScope.launch {
                                    restoring = true
                                    restorer.restore(v.romId, v.fileName, v.backupFile)
                                    restoring = false
                                }
                            },
                        ) { Text(if (restoring) "…" else tr("game.backups.restore")) }
                    }
                }
            }
            }
                if (serverUrl.isNotBlank() && apiKey.isNotBlank()) {
                    com.davidadrianrg.atril.ui.components.ServerAssetsSection(
                        romId = rom.id,
                        serverUrl = serverUrl,
                        apiKey = apiKey,
                    )
                }
            }
        }

        // ── Eliminar descarga: siempre al final y con confirmación (el diálogo lo abre onDelete) ──
        if (isDownloaded) {
            Spacer(modifier = Modifier.size(32.dp))
            androidx.compose.material3.HorizontalDivider()
            Spacer(modifier = Modifier.size(20.dp))
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
            ) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text("  " + tr("game.delete_download"))
            }
        }

    }

    if (showFolderPicker) {
        com.davidadrianrg.atril.ui.components.FolderPickerDialog(
            initialPath = savesPathOverride?.takeIf { it.isNotBlank() } ?: platformSavesPath,
            onDismiss = { showFolderPicker = false },
            onSelect = { path ->
                onSavesPathOverrideChange(path)
                showFolderPicker = false
            },
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/**
 * Visor de capturas a pantalla completa: pager horizontal con zoom por doble
 * toque (pinch-zoom quedará para una iteración futura), contador "n / total",
 * cierre por botón o toque fuera. Las imágenes se cargan a resolución completa
 * desde el servidor de RomM (con la caché de disco de Coil).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScreenshotViewerDialog(
    screenshots: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = initialIndex) { screenshots.size }
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f)),
        ) {
            HorizontalPager(state = pagerState) { page ->
                var zoomed by remember(page) { mutableStateOf(false) }
                AsyncImage(
                    model = screenshots[page],
                    contentDescription = tr("android.library.screenshot_n", page + 1),
                    contentScale = if (zoomed) ContentScale.FillBounds else ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .combinedClickable(
                            onClick = { zoomed = !zoomed },
                            onLongClick = { zoomed = !zoomed },
                        ),
                )
            }
            // Contador de página
            Text(
                text = "${pagerState.currentPage + 1} / ${screenshots.size}",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
            )
            // Botón cerrar
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = tr("game.viewer.close"),
                    tint = Color.White,
                )
            }
        }
    }
}

/** Abre el tráiler del juego en YouTube (app o navegador). */
private fun openTrailer(context: Context, videoId: String) {
    val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId"))
    val webIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://www.youtube.com/watch?v=$videoId"),
    )
    runCatching {
        context.startActivity(appIntent)
    }.onFailure {
        runCatching { context.startActivity(webIntent) }
    }
}
/** Chip compacto para metadatos tipo idioma/género. */
@Composable
private fun AssistChipLike(text: String) {
    Box(
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                RoundedCornerShape(8.dp),
            )
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "—"
    val units = arrayOf("B", "KB", "MB", "GB")
    var size = bytes.toDouble()
    var unitIndex = 0
    while (size >= 1024 && unitIndex < units.lastIndex) {
        size /= 1024
        unitIndex++
    }
    return if (unitIndex == 0) "${bytes}B" else String.format("%.1f %s", size, units[unitIndex])
}

@Composable
private fun LibrarySearchField(
    value: String,
    onValueChange: (String) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(tr("android.library.search_all_placeholder")) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        textStyle = MaterialTheme.typography.bodyMedium,
        modifier = modifier.height(56.dp),
    )
}
