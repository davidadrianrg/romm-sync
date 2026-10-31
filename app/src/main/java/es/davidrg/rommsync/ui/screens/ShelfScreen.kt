package es.davidrg.rommsync.ui.screens

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import es.davidrg.rommsync.RomMSyncApplication
import es.davidrg.rommsync.core.i18n.tr
import es.davidrg.rommsync.data.local.ServerConfig
import es.davidrg.rommsync.ui.viewmodel.ShelfItem
import es.davidrg.rommsync.ui.viewmodel.ShelfViewModel

// ── Paleta de la estantería ──────────────────────────────────────────
private val ShelfBgTop = Color(0xFF1D130C)
private val ShelfBgBottom = Color(0xFF0A0705)
private val WoodLight = Color(0xFF9A6636)
private val WoodMid = Color(0xFF5E3A1E)
private val WoodDark = Color(0xFF351E0E)
private val ShelfCream = Color(0xFFF4E4C9)
private val ShelfAmber = Color(0xFFD9A15B)

/**
 * Estantería retro: todos los juegos descargados, agrupados por juego y con
 * la carátula apoyada sobre una balda de madera. El detalle se abre dentro
 * de la propia pestaña (no navega a Biblioteca).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelfScreen() {
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
    val saveSyncEnabled by container.settingsRepository.saveSyncEnabled
        .collectAsState(initial = true)

    var selectedRomId by remember { mutableStateOf<Int?>(null) }
    var pendingDelete by remember { mutableStateOf<ShelfItem?>(null) }
    var platformFilter by remember { mutableStateOf<String?>(null) }
    val selected = items.firstOrNull { it.rom.id == selectedRomId }

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

    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(ShelfBgTop, ShelfBgBottom))),
    ) {
        Column(Modifier.fillMaxSize()) {
            ShelfHeader(count = visibleItems.size)

            if (platformsInShelf.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    item {
                        ShelfFilterChip(
                            label = tr("shelf.filter.all"),
                            selected = platformFilter == null,
                            onClick = { platformFilter = null },
                        )
                    }
                    items(platformsInShelf, key = { it.first }) { (slug, label) ->
                        ShelfFilterChip(
                            label = label,
                            selected = platformFilter == slug,
                            onClick = { platformFilter = slug },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            when {
                loading && items.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = ShelfAmber) }

                items.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        tr("shelf.empty"),
                        style = MaterialTheme.typography.bodyLarge,
                        color = ShelfCream.copy(alpha = 0.65f),
                        textAlign = TextAlign.Center,
                    )
                }

                else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                    val columns = ((maxWidth - 24.dp) / 104.dp).toInt().coerceAtLeast(2)
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = 12.dp, end = 12.dp, top = 4.dp, bottom = 28.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(visibleItems.chunked(columns)) { rowItems ->
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.Bottom,
                                ) {
                                    rowItems.forEach { item ->
                                        ShelfCard(
                                            item = item,
                                            modifier = Modifier.weight(1f),
                                            onClick = { selectedRomId = item.rom.id },
                                        )
                                    }
                                    repeat(columns - rowItems.size) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                WoodPlank()
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Detalle del juego dentro de la propia estantería ──────────────
    selected?.let { item ->
        ModalBottomSheet(
            onDismissRequest = { selectedRomId = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
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
            )
        }
    }

    // ── Confirmación de borrado ───────────────────────────────────────
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
private fun ShelfHeader(count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                tr("nav.shelf"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = ShelfCream,
            )
            Text(
                tr("shelf.subtitle"),
                style = MaterialTheme.typography.bodySmall,
                color = ShelfCream.copy(alpha = 0.55f),
            )
        }
        if (count > 0) {
            Text(
                tr("shelf.count", count.toString()),
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF241407),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(ShelfAmber)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

/** Chip de filtro por plataforma, con la paleta de la estantería. */
@Composable
private fun ShelfFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
            containerColor = Color(0x331D130C),
            labelColor = ShelfCream,
            selectedContainerColor = ShelfAmber,
            selectedLabelColor = Color(0xFF241407),
        ),
    )
}

/** Balda de madera con brillo superior y sombra inferior. */
@Composable
private fun WoodPlank() {
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(Color(0xFFB07C46)),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(WoodLight, WoodMid, WoodDark),
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(Color.Black.copy(alpha = 0.45f)),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfCard(
    item: ShelfItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val cover = item.rom.coverUrlLarge ?: item.rom.coverUrlSmall
    Box(
        modifier = modifier
            .shadow(8.dp, RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF241A12))
            .border(1.dp, Color(0x55FFD9A0), RoundedCornerShape(4.dp))
            .combinedClickable(onClick = onClick, onLongClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.70f)
                .background(Color(0xFF2A2018)),
        ) {
            if (cover != null) {
                AsyncImage(
                    model = cover,
                    contentDescription = item.rom.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    item.rom.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ShelfCream,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Center).padding(10.dp),
                )
            }

            // Degradado inferior para que el título se lea sobre la portada
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f)),
                        ),
                    ),
            )
            Text(
                item.rom.name,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )

            // Badge de plataforma
            Text(
                item.platformLabel,
                style = MaterialTheme.typography.labelSmall,
                color = ShelfCream,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(5.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xCC1A110A))
                    .border(1.dp, Color(0x66D9A15B), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )

            if (item.discCount > 1) {
                Text(
                    "×${item.discCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF241407),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(ShelfAmber)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }

            if (item.isManualDownloaded) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .width(7.dp)
                        .height(7.dp)
                        .clip(RoundedCornerShape(50))
                        .background(ShelfAmber),
                )
            }
        }
    }
}
