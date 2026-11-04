package com.davidadrianrg.atril.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.davidadrianrg.atril.ui.input.focusRing
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.davidadrianrg.atril.RomMSyncApplication
import com.davidadrianrg.atril.core.i18n.tr
import com.davidadrianrg.atril.data.local.ServerConfig
import com.davidadrianrg.atril.domain.model.formatBytesForUi
import com.davidadrianrg.atril.launch.LaunchPlan
import com.davidadrianrg.atril.ui.navigation.Screen
import com.davidadrianrg.atril.ui.viewmodel.ShelfItem
import com.davidadrianrg.atril.ui.viewmodel.ShelfViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Juego cuyo medio físico se está "insertando" antes de lanzar. */
private data class Insertion(val item: ShelfItem, val mediaPath: String)

/**
 * Launcher de juegos descargados, a pantalla completa y sin chrome permanente:
 * la navegación vive en un menú overlay. Cada juego se muestra como carta o,
 * si el usuario lo eligió y está descargado, como su medio físico real.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayScreen(
    onOpenDownloads: () -> Unit = {},
    onOpenLibrary: () -> Unit = {},
    menuItems: List<Screen> = emptyList(),
    onNavigate: (Screen) -> Unit = {},
) {
    val context = LocalContext.current
    val container = (context.applicationContext as RomMSyncApplication).container
    val viewModel: ShelfViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                ShelfViewModel(
                    romRepository = container.romRepository,
                    romDao = container.romDao,
                    platformDao = container.platformDao,
                    settingsRepository = container.settingsRepository,
                    physicalMediaRepository = container.physicalMediaRepository,
                    emulatorLauncher = container.emulatorLauncher,
                    localGames = container.localGamesRepository,
                )
            }
        }
    )

    val items by viewModel.items.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val settings by container.settingsRepository.settings.collectAsState(
        initial = ServerConfig("", "", "", 2)
    )
    val landscape = com.davidadrianrg.atril.ui.components.rememberWindowInfo().isLandscape
    val saveSyncEnabled by container.settingsRepository.saveSyncEnabled
        .collectAsState(initial = true)

    var selectedRomId by remember { mutableStateOf<Int?>(null) }
    var pendingDelete by remember { mutableStateOf<ShelfItem?>(null) }
    var platformFilter by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // ── Juegos locales (fuera de RomM) ──
    var pendingAddPath by remember { mutableStateOf<String?>(null) }
    var coverTargetId by remember { mutableStateOf<String?>(null) }
    var scrapingId by remember { mutableStateOf<String?>(null) }
    val knownPlatforms = remember { runCatching { container.platformDao.getAllPlatformsBlocking() }.getOrDefault(emptyList()) }
    val platformChoices = remember(knownPlatforms) {
        knownPlatforms.map { it.slug to it.name }.distinctBy { it.first }.sortedBy { it.second.lowercase() }
    }
    val pickGame = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val path = com.davidadrianrg.atril.core.localgames.LocalGamePaths.fromPickedUri(uri.toString())
            if (path != null) pendingAddPath = path else scope.launch { snackbar.showSnackbar(tr("local.pick_failed")) }
        }
    }
    val pickCover = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri ->
        val id = coverTargetId
        if (uri != null && id != null) {
            context.contentResolver.openInputStream(uri)?.let { viewModel.setLocalCover(id, it) }
        }
    }

    val platformsInShelf = remember(items) {
        items.map { it.rom.platformSlug to it.platformLabel }
            .distinctBy { it.first }
            .sortedBy { it.second }
    }
    LaunchedEffect(platformsInShelf) {
        if (platformFilter != null && platformsInShelf.none { it.first == platformFilter }) {
            platformFilter = null
        }
    }
    val visibleItems = items.filter {
        platformFilter == null || it.rom.platformSlug == platformFilter
    }
    val pagerState = rememberPagerState(pageCount = { visibleItems.size })
    LaunchedEffect(platformFilter) { pagerState.scrollToPage(0) }
    val current = visibleItems.getOrNull(pagerState.currentPage)

    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg -> snackbar.showSnackbar(msg) }
    }

    // ── Jugar: preparar → (animación de inserción) → lanzar ─────────────
    var launchJob by remember { mutableStateOf<Job?>(null) }
    var insertion by remember { mutableStateOf<Insertion?>(null) }
    val insertProgress = remember { Animatable(0f) }
    var liftingRomId by remember { mutableStateOf<Int?>(null) }
    val cardLift = remember { Animatable(0f) }

    // Los avisos van en su propia corrutina: si colgaran del job de
    // lanzamiento, el botón Jugar quedaría "ocupado" mientras se muestran.
    fun notify(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    fun play(item: ShelfItem) {
        if (launchJob?.isActive == true) return
        launchJob = scope.launch {
            try {
                when (val plan = viewModel.prepareLaunch(item)) {
                    is LaunchPlan.Failed -> notify(plan.message)
                    is LaunchPlan.Ready -> {
                        plan.fallbackFrom?.let { preferred ->
                            notify(tr("launcher.fallback", preferred.displayName, plan.family.displayName))
                        }
                        val media = item.physicalMediaFile?.takeIf { item.showPhysical }
                        if (media != null) {
                            insertion = Insertion(item, media)
                            insertProgress.snapTo(0f)
                            insertProgress.animateTo(1f, tween(com.davidadrianrg.atril.core.launch.InsertionMotion.DURATION_MS, easing = LinearEasing))
                        } else {
                            // Carta: un gesto contenido (la carta se eleva) y a jugar.
                            liftingRomId = item.rom.id
                            cardLift.snapTo(0f)
                            cardLift.animateTo(1f, tween(220))
                        }
                        val error = viewModel.startLaunch(context, plan)
                        if (error != null) notify(error)
                    }
                }
            } finally {
                // Nunca dejar un overlay invisible bloqueando la pantalla.
                insertion = null
                liftingRomId = null
                cardLift.snapTo(0f)
            }
        }
    }

    // Start / Menu del mando abre o cierra el menú del launcher.
    LaunchedEffect(Unit) {
        com.davidadrianrg.atril.ui.input.PadEvents.menu.collect { if (insertion == null) menuOpen = !menuOpen }
    }
    // Foco inicial en el carrusel: ←/→ (D-pad, stick o teclado) cambian de juego.
    val carouselFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(visibleItems.isNotEmpty()) { runCatching { carouselFocus.requestFocus() } }

    BackHandler(enabled = insertion != null) { launchJob?.cancel() }
    BackHandler(enabled = menuOpen && insertion == null) { menuOpen = false }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(LauncherBgTop, LauncherBgBottom))),
    ) {
        // Halo cálido detrás del objeto central.
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.6f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(LauncherAmber.copy(alpha = 0.09f), Color.Transparent),
                    ),
                ),
        )

        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            LauncherHeader(
                count = visibleItems.size,
                onAddLocal = { pickGame.launch(arrayOf("*/*")) },
                onOpenDownloads = onOpenDownloads,
                onOpenMenu = { menuOpen = true },
                showMenu = menuItems.isNotEmpty(),
            )

            if (platformsInShelf.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    item {
                        LauncherPill(
                            label = tr("shelf.filter.all"),
                            selected = platformFilter == null,
                            onClick = { platformFilter = null },
                        )
                    }
                    items(platformsInShelf, key = { it.first }) { (slug, label) ->
                        LauncherPill(
                            label = label,
                            selected = platformFilter == slug,
                            onClick = { platformFilter = slug },
                        )
                    }
                }
            }

            when {
                loading && items.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = LauncherAmber) }

                visibleItems.isEmpty() -> EmptyLauncher(onOpenLibrary, onAddLocal = { pickGame.launch(arrayOf("*/*")) })

                else -> {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        BoxWithConstraints(
                            Modifier
                                .weight(if (landscape) 1.3f else 1f)
                                .fillMaxHeight(),
                        ) {
                            val heroWidth = if (landscape) {
                                (maxHeight * 0.66f).coerceAtMost(maxWidth * 0.56f)
                            } else {
                                (maxWidth * 0.66f).coerceAtMost(maxHeight * 0.62f)
                            }

                            HorizontalPager(
                                state = pagerState,
                                pageSize = PageSize.Fixed(heroWidth),
                                contentPadding = PaddingValues(
                                    horizontal = ((maxWidth - heroWidth) / 2).coerceAtLeast(0.dp),
                                ),
                                pageSpacing = 16.dp,
                                // En horizontal los vecinos asoman junto al texto del
                                // juego actual: se desvanecen en el borde (igual que en
                                // escritorio) en lugar de cortarse contra el título.
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(if (landscape) Modifier.fadeEdges() else Modifier)
                                    .focusRequester(carouselFocus)
                                    .focusable()
                                    .onKeyEvent { ev ->
                                        if (ev.type != KeyEventType.KeyDown || insertion != null || menuOpen) return@onKeyEvent false
                                        when (ev.key) {
                                            Key.DirectionLeft -> {
                                                scope.launch { pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0)) }
                                                true
                                            }
                                            Key.DirectionRight -> {
                                                scope.launch {
                                                    pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(visibleItems.lastIndex))
                                                }
                                                true
                                            }
                                            // A del mando (→ DPAD_CENTER) / Enter: el pager tiene el foco
                                            // y no es clickable; abre la ficha como el toque en la carátula.
                                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter ->
                                                current?.let { selectedRomId = it.rom.id; true } ?: false
                                            else -> false
                                        }
                                    },
                            ) { page ->
                                val item = visibleItems[page]
                                val selected = page == pagerState.currentPage
                                val scale by animateFloatAsState(if (selected) 1f else 0.8f, label = "objectScale")
                                val alpha by animateFloatAsState(if (selected) 1f else 0.5f, label = "objectAlpha")
                                val lift = if (liftingRomId == item.rom.id) cardLift.value else 0f
                                val hidden = insertion?.item?.rom?.id == item.rom.id
                                LauncherGameObject(
                                    item = item,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(vertical = 8.dp)
                                        .graphicsLayer {
                                            scaleX = scale * (1f + 0.05f * lift)
                                            scaleY = scale * (1f + 0.05f * lift)
                                            translationY = -10.dp.toPx() * lift
                                            this.alpha = if (hidden) 0f else alpha
                                        }
                                        .focusRing()
                                        .clickable(
                                            onClickLabel = if (selected) tr("launcher.details") else null,
                                        ) {
                                            if (selected) {
                                                selectedRomId = item.rom.id
                                            } else {
                                                // El toque en un vecino lo trae al centro.
                                                scope.launch { pagerState.animateScrollToPage(page) }
                                            }
                                        },
                                )
                            }

                            if (!landscape) {
                                LauncherArrow(
                                    icon = Icons.Filled.ChevronLeft,
                                    description = tr("common.previous"),
                                    enabled = pagerState.currentPage > 0,
                                    modifier = Modifier.align(Alignment.CenterStart),
                                    onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                                )
                                LauncherArrow(
                                    icon = Icons.Filled.ChevronRight,
                                    description = tr("common.next"),
                                    enabled = pagerState.currentPage < visibleItems.lastIndex,
                                    modifier = Modifier.align(Alignment.CenterEnd),
                                    onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                                )
                            }
                        }

                        if (landscape) {
                            current?.let { item ->
                                Box(
                                    Modifier.weight(0.9f).fillMaxHeight().padding(end = 20.dp),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    Caption(item, viewModel, saveSyncEnabled, launchJob?.isActive == true, alignStart = true,
                                        onPlay = { play(item) }, onDetails = { selectedRomId = item.rom.id })
                                }
                            }
                        }
                    }

                    if (!landscape) {
                        current?.let { item ->
                            Caption(
                                item, viewModel, saveSyncEnabled, launchJob?.isActive == true,
                                alignStart = false,
                                onPlay = { play(item) },
                                onDetails = { selectedRomId = item.rom.id },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                            )
                        }
                    }
                }
            }
        }

        // Gesto de borde: arrastrar desde la izquierda abre el menú.
        if (menuItems.isNotEmpty() && !menuOpen && insertion == null) {
            EdgeSwipeZone(
                onOpen = { menuOpen = true },
                // Justo después de la zona del gesto Atrás del sistema; en
                // vertical, en la parte baja (las flechas ocupan el centro).
                modifier = Modifier
                    .align(if (landscape) Alignment.CenterStart else Alignment.BottomStart)
                    .windowInsetsPadding(WindowInsets.systemGestures.only(WindowInsetsSides.Start))
                    .fillMaxHeight(if (landscape) 0.7f else 0.3f),
            )
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = Color(0xFF2A241C),
                contentColor = LauncherCream,
                actionColor = LauncherAmber,
                shape = RoundedCornerShape(14.dp),
            )
        }

        insertion?.let { ins ->
            InsertionOverlay(
                item = ins.item,
                mediaPath = ins.mediaPath,
                progress = insertProgress.value,
                onCancel = { launchJob?.cancel() },
            )
        }

        LauncherMenuOverlay(
            visible = menuOpen,
            items = menuItems,
            landscape = landscape,
            onNavigate = { screen ->
                menuOpen = false
                if (screen != Screen.Play) onNavigate(screen)
            },
            onDismiss = { menuOpen = false },
        )
    }

    // ── Detalle del juego ──────────────────────────────────────────────
    val selected = visibleItems.firstOrNull { it.rom.id == selectedRomId }
    selected?.takeIf { it.isLocalSource }?.let { item ->
        LocalGameSheet(
            item = item,
            scraping = scrapingId == item.localGame?.id,
            onPlay = { selectedRomId = null; play(item) },
            onRename = { viewModel.renameLocal(item.localGame!!.id, it) },
            onRetryScrape = {
                val id = item.localGame!!.id
                scrapingId = id
                viewModel.scrapeLocal(id)
                scope.launch { kotlinx.coroutines.delay(1500); if (scrapingId == id) scrapingId = null }
            },
            onPickCover = { coverTargetId = item.localGame!!.id; pickCover.launch("image/*") },
            onRemove = { selectedRomId = null; viewModel.removeLocal(item.localGame!!.id) },
            onClose = { selectedRomId = null },
        )
    }
    selected?.takeIf { !it.isLocalSource }?.let { item ->
        ModalBottomSheet(
            onDismissRequest = { selectedRomId = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            sheetMaxWidth = 1600.dp,
            // Let the scrolling content run to the screen edge; the sheet
            // body pads its own bottom for the gesture bar (no dead band).
            // Keep the top inset so the drag handle never sits under the
            // status bar when the sheet is fully expanded in landscape.
            contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Top) },
        ) {
            RomDetailSheet(
                rom = item.rom,
                isDownloaded = true,
                isDownloading = false,
                discCount = item.discCount,
                syncEnabled = saveSyncEnabled,
                savesPathOverride = item.savesPathOverride,
                excludedFromSync = item.excludedFromSync,
                platformSavesPath = item.platformSavesPath,
                onSavesPathOverrideChange = { path ->
                    viewModel.setSavesPathOverride(item.rom.id, path)
                },
                onExcludedFromSyncChange = { excluded ->
                    viewModel.setExcludedFromSync(item.rom.id, excluded)
                },
                onDownload = {},
                onDelete = { pendingDelete = item },
                onClose = { selectedRomId = null },
                serverUrl = settings.serverUrl,
                apiKey = settings.apiKey,
                isManualDownloaded = item.isManualDownloaded,
                wide = landscape,
                launcherControls = rememberLauncherDetailControls(
                    rom = item.rom,
                    platformLabel = item.platformLabel,
                    platformEmulatorId = item.platformEmulatorId,
                    launchable = item.launchable,
                    onPlay = {
                        selectedRomId = null
                        play(item)
                    },
                ),
            )
        }
    }

    // ── Alta de un juego local tras elegir el fichero ──
    pendingAddPath?.let { path ->
        AddLocalGameDialog(
            path = path,
            platforms = platformChoices,
            initialSlug = com.davidadrianrg.atril.core.localgames.LocalGamePaths
                .guessPlatform(path, platformChoices.map { it.first }),
            onConfirm = { title, slug ->
                pendingAddPath = null
                viewModel.addLocalGame(title, slug, path)
            },
            onDismiss = { pendingAddPath = null },
        )
    }

    // ── Confirmación de borrado ────────────────────────────────────────
    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(tr("game.delete_download")) },
            text = {
                Text(
                    if (item.discCount > 1) {
                        tr("android.library.delete_confirm_multi", item.rom.name, item.discCount.toString())
                    } else {
                        tr("android.library.delete_confirm_single", item.rom.name)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(item)
                    pendingDelete = null
                    selectedRomId = null
                }) { Text(tr("common.delete")) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(tr("common.cancel")) }
            },
        )
    }
}

@Composable
private fun Caption(
    item: ShelfItem,
    viewModel: ShelfViewModel,
    syncEnabled: Boolean,
    busy: Boolean,
    alignStart: Boolean,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // El nombre del emulador consulta PackageManager: fuera del hilo principal.
    val emulatorName by produceState<String?>(null, item.rom.id, item.launchOverride, item.platformEmulatorId) {
        value = withContext(Dispatchers.IO) { viewModel.currentEmulatorName(item) }
    }
    val meta = buildList {
        add(item.platformLabel)
        if (!item.isLocalSource || item.fileSizeBytes > 0) add(formatBytesForUi(item.fileSizeBytes))
        if (item.discCount > 1) add(tr("launcher.badge.discs", item.discCount))
        emulatorName?.let { add(it) }
        // Los juegos locales no se sincronizan con RomM: no tiene sentido decirlo.
        if (syncEnabled && item.excludedFromSync && !item.isLocalSource) add(tr("play.excluded"))
    }
    LauncherCaption(
        item = item,
        meta = meta,
        onPlay = onPlay,
        onDetails = onDetails,
        playBusy = busy,
        alignStart = alignStart,
        modifier = modifier,
    )
}

@Composable
private fun LauncherHeader(
    count: Int,
    onAddLocal: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenMenu: () -> Unit,
    showMenu: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showMenu) {
            RoundIconButton(Icons.Filled.Menu, tr("launcher.menu.open"), onOpenMenu)
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            AppMark()
            Text(
                tr("nav.play"),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = LauncherCream,
            )
        }
        if (count > 0) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = LauncherInk,
                modifier = Modifier
                    .semantics { contentDescription = tr("launcher.games_count", count) }
                    .clip(RoundedCornerShape(50))
                    .background(LauncherAmber)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        RoundIconButton(Icons.Filled.Add, tr("local.add"), onAddLocal)
        Spacer(Modifier.width(8.dp))
        RoundIconButton(Icons.Outlined.Download, tr("nav.downloads"), onOpenDownloads)
    }
}

@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .clip(CircleShape)
            .background(LauncherCream.copy(alpha = 0.08f)),
    ) {
        Icon(icon, contentDescription = description, tint = LauncherCream)
    }
}

@Composable
private fun EmptyLauncher(onOpenLibrary: () -> Unit, onAddLocal: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.SportsEsports,
                contentDescription = null,
                tint = LauncherAmber.copy(alpha = 0.8f),
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                tr("play.empty"),
                style = MaterialTheme.typography.titleMedium,
                color = LauncherCream,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                tr("play.empty_hint"),
                style = MaterialTheme.typography.bodySmall,
                color = LauncherCream.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            androidx.compose.material3.FilledTonalButton(onClick = onOpenLibrary) {
                Text(tr("play.open_library"))
            }
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.TextButton(onClick = onAddLocal) { Text(tr("local.add")) }
        }
    }
}

/** Franja invisible en el borde izquierdo: arrastrar hacia dentro abre el menú. */
@Composable
private fun EdgeSwipeZone(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val threshold = with(LocalDensity.current) { 48.dp.toPx() }
    var dragged by remember { mutableFloatStateOf(0f) }
    Box(
        modifier
            .width(20.dp)
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    dragged = (dragged + delta).coerceAtLeast(0f)
                    if (dragged > threshold) {
                        dragged = 0f
                        onOpen()
                    }
                },
                onDragStopped = { dragged = 0f },
            ),
    )
}

@Composable
private fun LauncherArrow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .padding(horizontal = 6.dp)
            .clip(CircleShape)
            .background(LauncherCream.copy(alpha = if (enabled) 0.08f else 0.02f)),
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = LauncherCream.copy(alpha = if (enabled) 0.9f else 0.2f),
        )
    }
}

/** Desvanece los bordes laterales del contenido (máscara DstIn sobre capa offscreen). */
private fun Modifier.fadeEdges(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Transparent,
                0.12f to Color.Black,
                0.80f to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
