package es.davidrg.rommsync.ui.screens

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import es.davidrg.rommsync.RomMSyncApplication
import es.davidrg.rommsync.core.i18n.tr
import es.davidrg.rommsync.data.local.ServerConfig
import es.davidrg.rommsync.domain.model.formatBytesForUi
import es.davidrg.rommsync.ui.viewmodel.ShelfItem
import es.davidrg.rommsync.ui.viewmodel.ShelfViewModel

// ── Launcher: materias físicas, luz cálida y poco chrome ─────────────
private val LauncherBgTop = Color(0xFF17130D)
private val LauncherBgBottom = Color(0xFF080706)
private val LauncherCream = Color(0xFFF2E6D2)
private val LauncherAmber = Color(0xFFE7B46A)
private val LauncherPlastic = Color(0xFF211B14)
private val LauncherPlasticLight = Color(0xFF342A1E)

/**
 * Launcher de juegos descargados. Un objeto físico por juego (cartucho o
 * caja de disco), centrado, con los vecinos asomando. Sustituye la antigua
 * pestaña Estantería y es la entrada principal de la app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayScreen(
    onOpenDownloads: () -> Unit = {},
    onOpenLibrary: () -> Unit = {},
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
                )
            }
        }
    )

    val items by viewModel.items.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val settings by container.settingsRepository.settings.collectAsState(
        initial = ServerConfig("", "", "", 2)
    )
    val landscape = es.davidrg.rommsync.ui.components.rememberWindowInfo().isLandscape
    val saveSyncEnabled by container.settingsRepository.saveSyncEnabled
        .collectAsState(initial = true)

    var selectedRomId by remember { mutableStateOf<Int?>(null) }
    var pendingDelete by remember { mutableStateOf<ShelfItem?>(null) }
    var platformFilter by remember { mutableStateOf<String?>(null) }

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
    val scope = rememberCoroutineScope()
    LaunchedEffect(platformFilter) { pagerState.scrollToPage(0) }
    val current = visibleItems.getOrNull(pagerState.currentPage)

    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(LauncherBgTop, LauncherBgBottom))),
    ) {
        // Halo cálido detrás del objeto central.
        Box(
            Modifier
                .fillMaxWidth()
                .height(420.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(LauncherAmber.copy(alpha = 0.09f), Color.Transparent),
                    ),
                ),
        )

        Column(Modifier.fillMaxSize()) {
            LauncherHeader(
                count = visibleItems.size,
                onOpenDownloads = onOpenDownloads,
            )

            if (platformsInShelf.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    item {
                        LauncherChip(
                            label = tr("shelf.filter.all"),
                            selected = platformFilter == null,
                            onClick = { platformFilter = null },
                        )
                    }
                    items(platformsInShelf, key = { it.first }) { (slug, label) ->
                        LauncherChip(
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

                visibleItems.isEmpty() -> Box(
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
                    }
                }

                else -> {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        BoxWithConstraints(
                            Modifier
                                .weight(if (landscape) 1.35f else 1f)
                                .fillMaxHeight(),
                        ) {
                            val heroWidth = if (landscape) {
                                (maxHeight * 0.82f).coerceAtMost(maxWidth * 0.62f)
                            } else {
                                (maxWidth * 0.62f).coerceAtMost(maxHeight * 0.58f)
                            }

                            HorizontalPager(
                                state = pagerState,
                                pageSize = PageSize.Fixed(heroWidth),
                                contentPadding = PaddingValues(
                                    horizontal = ((maxWidth - heroWidth) / 2).coerceAtLeast(0.dp),
                                ),
                                pageSpacing = 14.dp,
                                modifier = Modifier.fillMaxSize(),
                            ) { page ->
                                val item = visibleItems[page]
                                LauncherObject(
                                    item = item,
                                    selected = page == pagerState.currentPage,
                                    onClick = {
                                        if (page == pagerState.currentPage) {
                                            selectedRomId = item.rom.id
                                        } else {
                                            // El toque en un vecino lo trae al centro.
                                            scope.launch { pagerState.animateScrollToPage(page) }
                                        }
                                    },
                                )
                            }

                            // En horizontal se navega deslizando o tocando al vecino;
                            // las flechas solo ayudan en vertical, donde no se ve el carrusel.
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
                                LauncherCaption(
                                    item = item,
                                    syncEnabled = saveSyncEnabled,
                                    modifier = Modifier.weight(0.85f),
                                    alignStart = true,
                                )
                            }
                        }
                    }

                    if (!landscape) {
                        current?.let { item ->
                            LauncherCaption(item = item, syncEnabled = saveSyncEnabled)
                        }
                    }
                }
            }
        }
    }

    // ── Detalle del juego ──────────────────────────────────────────────
    val selected = visibleItems.firstOrNull { it.rom.id == selectedRomId }
    selected?.let { item ->
        ModalBottomSheet(
            onDismissRequest = { selectedRomId = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            sheetMaxWidth = 1600.dp,
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
            )
        }
    }

    // ── Confirmación de borrado ────────────────────────────────────────
    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(tr("common.delete")) },
            text = {
                Text(
                    tr(
                        "android.library.delete_confirm_multi",
                        item.rom.name,
                        item.discCount.toString(),
                    ),
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
private fun LauncherHeader(count: Int, onOpenDownloads: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                tr("nav.play"),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = LauncherCream,
            )
            Text(
                tr("play.subtitle"),
                style = MaterialTheme.typography.bodySmall,
                color = LauncherCream.copy(alpha = 0.55f),
            )
        }
        if (count > 0) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF2A1A08),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(LauncherAmber)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.width(6.dp))
        IconButton(
            onClick = onOpenDownloads,
            modifier = Modifier
                .clip(CircleShape)
                .background(LauncherCream.copy(alpha = 0.07f)),
        ) {
            Icon(
                Icons.Outlined.Download,
                contentDescription = tr("nav.downloads"),
                tint = LauncherCream,
            )
        }
    }
}

@Composable
private fun LauncherChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color(0x33120E09),
            labelColor = LauncherCream.copy(alpha = 0.72f),
            selectedContainerColor = LauncherAmber,
            selectedLabelColor = Color(0xFF2A1A08),
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = LauncherCream.copy(alpha = 0.18f),
            selectedBorderColor = Color.Transparent,
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

@Composable
private fun LauncherObject(item: ShelfItem, selected: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 1f else 0.78f, label = "objectScale")
    val objectAlpha by animateFloatAsState(if (selected) 1f else 0.55f, label = "objectAlpha")
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = objectAlpha }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isOpticalPlatform(item.rom.platformSlug)) {
            JewelCase(item, Modifier.fillMaxHeight(0.9f).aspectRatio(0.82f))
        } else {
            Cartridge(item, Modifier.fillMaxHeight(0.9f).aspectRatio(0.70f))
        }
    }
}

@Composable
private fun Cartridge(item: ShelfItem, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .shadow(18.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.verticalGradient(listOf(LauncherPlasticLight, LauncherPlastic)))
            .border(1.dp, LauncherAmber.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
            .padding(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(7) {
                Box(
                    Modifier
                        .width(3.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(LauncherCream.copy(alpha = 0.12f)),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF0D0B08)),
        ) {
            CoverArt(item, Modifier.fillMaxSize())
            BadgeOverlay(item)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            item.platformLabel.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = LauncherAmber,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            item.rom.name,
            style = MaterialTheme.typography.titleSmall,
            color = LauncherCream,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun JewelCase(item: ShelfItem, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        // Disco asomando por detrás de la caja.
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 14.dp, y = (-10).dp)
                .size(86.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xFF8A96A3), Color(0xFF3B444D), Color(0xFF14181C)),
                    ),
                )
                .border(1.dp, LauncherCream.copy(alpha = 0.25f), CircleShape),
        ) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0B0A09)),
            )
        }

        Row(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .shadow(18.dp, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xCC1B2026))
                .border(1.dp, LauncherCream.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
        ) {
            // Lomo de la caja.
            Box(
                Modifier
                    .width(26.dp)
                    .fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(Color(0xFF2A3138), Color(0xFF11151A))))
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    item.platformLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = LauncherAmber,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
            Box(Modifier.weight(1f).fillMaxHeight().padding(7.dp)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF0D0B08)),
                ) {
                    CoverArt(item, Modifier.fillMaxSize())
                    BadgeOverlay(item)
                }
            }
        }
    }
}

@Composable
private fun CoverArt(item: ShelfItem, modifier: Modifier = Modifier) {
    val cover = item.rom.coverUrlLarge ?: item.rom.coverUrlSmall
    if (cover != null) {
        AsyncImage(
            model = cover,
            contentDescription = item.rom.name,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        Box(modifier.background(Color(0xFF1A1610)), contentAlignment = Alignment.Center) {
            Text(
                item.rom.name,
                style = MaterialTheme.typography.bodyMedium,
                color = LauncherCream,
                textAlign = TextAlign.Center,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@Composable
private fun BadgeOverlay(item: ShelfItem) {
    Row(
        modifier = Modifier.fillMaxSize().padding(6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        if (item.discCount > 1) {
            Text(
                "×${item.discCount}",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF2A1A08),
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(LauncherAmber)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        } else {
            Spacer(Modifier.width(1.dp))
        }
        if (item.isManualDownloaded) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(LauncherAmber),
            )
        }
    }
}

@Composable
private fun LauncherCaption(
    item: ShelfItem,
    syncEnabled: Boolean,
    modifier: Modifier = Modifier,
    alignStart: Boolean = false,
) {
    Column(
        modifier = if (alignStart) {
            modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        } else {
            modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
        },
        horizontalAlignment = if (alignStart) Alignment.Start else Alignment.CenterHorizontally,
    ) {
        Text(
            item.rom.name,
            style = MaterialTheme.typography.headlineSmall,
            color = LauncherCream,
            maxLines = if (alignStart) 2 else 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (alignStart) TextAlign.Start else TextAlign.Center,
        )
        Spacer(Modifier.height(3.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                item.platformLabel,
                style = MaterialTheme.typography.labelMedium,
                color = LauncherAmber,
            )
            Text("·", color = LauncherCream.copy(alpha = 0.3f))
            Text(
                formatBytesForUi(item.fileSizeBytes),
                style = MaterialTheme.typography.labelMedium,
                color = LauncherCream.copy(alpha = 0.55f),
            )
            if (item.discCount > 1) {
                Text("·", color = LauncherCream.copy(alpha = 0.3f))
                Text(
                    "×${item.discCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = LauncherCream.copy(alpha = 0.55f),
                )
            }
            if (syncEnabled && item.excludedFromSync) {
                Text("·", color = LauncherCream.copy(alpha = 0.3f))
                Text(
                    tr("play.excluded"),
                    style = MaterialTheme.typography.labelSmall,
                    color = LauncherCream.copy(alpha = 0.4f),
                )
            }
        }
    }
}

/** Plataformas que se presentan como caja de disco en lugar de cartucho. */
private fun isOpticalPlatform(slug: String): Boolean = slug.lowercase() in setOf(
    "3do", "psx", "ps1", "ps2", "ps3", "psp", "saturn", "segacd", "sega-cd",
    "dreamcast", "gamecube", "gc", "wii", "wiiu", "xbox", "xbox360", "xboxone",
)
