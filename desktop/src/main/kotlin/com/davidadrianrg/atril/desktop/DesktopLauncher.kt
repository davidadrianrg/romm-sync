package com.davidadrianrg.atril.desktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.davidadrianrg.atril.core.i18n.tr
import com.davidadrianrg.atril.core.launch.LaunchPlatforms
import com.davidadrianrg.atril.core.library.DiscEntry
import com.davidadrianrg.atril.core.library.MultiDiscGrouper
import com.davidadrianrg.atril.core.library.PhysicalMedia
import com.davidadrianrg.atril.desktop.theme.LocalLauncherColors
import com.davidadrianrg.atril.core.remote.dto.RomDto
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

// Colores del lanzador: siguen al tema activo (Warm Archive / Midnight).
private fun argb(v: Long) = Color(v.toInt())
private val PlayBgTop: Color @Composable get() = argb(LocalLauncherColors.current.launcherBgTop)
private val PlayBgBottom: Color @Composable get() = argb(LocalLauncherColors.current.launcherBgBottom)
private val PlayCream: Color @Composable get() = MaterialTheme.colorScheme.onBackground
private val PlayAmber: Color @Composable get() = MaterialTheme.colorScheme.primary
private val PlayInk: Color @Composable get() = MaterialTheme.colorScheme.onPrimary
private val PlayPlastic: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh

private data class LauncherEntry(
    val card: GameCard,
    val platformLabel: String,
    val physicalUrl: String?,
    /** Juego añadido a mano (NO está en RomM); null para los de RomM. */
    val local: com.davidadrianrg.atril.core.localgames.LocalGame? = null,
) {
    /** Plataforma tal cual se muestra en la carta/pie: los locales llevan la marca «Local». */
    val displayPlatform: String get() = if (local != null) "$platformLabel · ${tr("local.badge")}" else platformLabel
}

/** Id sintético y NEGATIVO para un juego local: nunca choca con un id de RomM. */
private fun com.davidadrianrg.atril.core.localgames.LocalGame.syntheticRomId(): Int =
    -((id.hashCode().toLong() and 0x7FFFFFFF).toInt().coerceAtLeast(1))

private fun com.davidadrianrg.atril.core.localgames.LocalGame.toLauncherEntry(platformName: String): LauncherEntry {
    val rep = RomDto(
        id = syntheticRomId(),
        name = title,
        fileName = File(path).name,
        platformId = 0,
        platformSlug = platformSlug,
        fileSizeBytes = File(path).takeIf { it.isFile }?.length() ?: 0L,
        urlCover = coverPath?.let { "file://$it" },
    )
    return LauncherEntry(
        card = GameCard(rep, 1, listOf(rep), downloaded = true),
        platformLabel = platformName,
        physicalUrl = null,
        local = this,
    )
}

/** Objeto que se está insertando en la ranura antes de abrir el juego. */
private data class Insertion(
    val entry: LauncherEntry,
    val physical: Boolean,
)

/**
 * Launcher de escritorio: misma idea que el PlayScreen de Android, adaptada a
 * ventana de escritorio. Muestra los juegos descargados como cartas o como su
 * medio físico real y ofrece el detalle sin salir de la pantalla.
 */
@Composable
fun DesktopPlayScreen(
    state: DesktopAppState,
    onOpenLibrary: () -> Unit,
    onOpenDownloads: () -> Unit,
    onNavigate: (Section) -> Unit,
) {
    val version by state.downloadedVersion.collectAsState()
    val selectedGame by state.selectedGame.collectAsState()
    val tasks by state.tasks.collectAsState()
    val connected by state.connected.collectAsState()
    val activeDownloads = tasks.count { it.active }

    var remoteEntries by remember { mutableStateOf<List<LauncherEntry>>(emptyList()) }
    val localGames by state.localGames.games.collectAsState()
    val platformsState by state.platforms.collectAsState()
    val entries = remember(remoteEntries, localGames, platformsState) {
        val names = platformsState.associate { it.slug to (it.displayName ?: it.name) }
        (remoteEntries + localGames.map { it.toLauncherEntry(names[it.platformSlug] ?: it.platformSlug.uppercase()) })
            .sortedBy { it.card.rep.name.lowercase() }
    }
    var pendingAddPath by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var platformFilter by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var launchJob by remember { mutableStateOf<Job?>(null) }
    var insertion by remember { mutableStateOf<Insertion?>(null) }
    val insertProgress = remember { Animatable(0f) }

    fun play(entry: LauncherEntry) {
        if (launchJob?.isActive == true) return
        launchJob = scope.launch {
            try {
                insertion = Insertion(
                    entry = entry,
                    physical = entry.physicalUrl != null && state.isPhysicalMedia(entry.card.rep.id),
                )
                insertProgress.snapTo(0f)
                insertProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = com.davidadrianrg.atril.core.launch.InsertionMotion.DURATION_MS, easing = LinearEasing),
                )
                val game = entry.local
                if (game != null) state.launchLocalGame(game) else state.openLocalGame(entry.card)
            } finally {
                insertion = null
                insertProgress.snapTo(0f)
            }
        }
    }

    LaunchedEffect(version, connected) {
        loading = true
        // Pinta primero lo que ya está en library.properties: la red solo
        // enriquece portada/medio físico y nunca debe dejar la ventana vacía.
        val local = localLauncherEntries(state)
        remoteEntries = local
        loading = false
        val enriched = runCatching { loadLauncherEntries(state) }.getOrDefault(local)
        if (enriched.isNotEmpty()) remoteEntries = enriched
    }

    val platforms = remember(entries) {
        entries.map { entry ->
            (entry.card.rep.platformSlug ?: "unknown") to entry.platformLabel
        }.distinctBy { it.first }.sortedBy { it.second }
    }
    LaunchedEffect(platforms) {
        if (platformFilter != null && platforms.none { it.first == platformFilter }) {
            platformFilter = null
        }
    }
    val visible = entries.filter { entry ->
        platformFilter == null || (entry.card.rep.platformSlug ?: "unknown") == platformFilter
    }
    val pagerState = rememberPagerState(pageCount = { visible.size })
    LaunchedEffect(visible.size) {
        if (visible.isEmpty()) return@LaunchedEffect
        if (pagerState.currentPage > visible.lastIndex) pagerState.scrollToPage(visible.lastIndex)
    }
    val current = visible.getOrNull(pagerState.currentPage.coerceIn(0, (visible.size - 1).coerceAtLeast(0)))

    val selected = selectedGame
    if (selected != null) {
        Surface(Modifier.fillMaxSize().background(PlayBgBottom)) {
            val local = localGames.firstOrNull { it.syntheticRomId() == selected.rep.id }
            if (local != null) LocalGameDetailPanel(local, state) else GameDetailPanel(selected, state)
        }
        return
    }

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(PlayBgTop, PlayBgBottom))),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.62f)
                .background(
                    Brush.radialGradient(
                        listOf(PlayAmber.copy(alpha = 0.10f), Color.Transparent),
                    ),
                ),
        )

        Column(Modifier.fillMaxSize()) {
            PlayHeader(
                count = visible.size,
                activeDownloads = activeDownloads,
                menuOpen = menuOpen,
                onMenuOpen = { menuOpen = true },
                onMenuDismiss = { menuOpen = false },
                onOpenDownloads = onOpenDownloads,
                onNavigate = onNavigate,
                onAddLocal = { scope.launch { pickLocalGameFile()?.let { pendingAddPath = it } } },
            )

            if (platforms.size > 1) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PlayPill(
                        label = tr("shelf.filter.all"),
                        selected = platformFilter == null,
                        onClick = { platformFilter = null },
                    )
                    platforms.forEach { (slug, label) ->
                        PlayPill(
                            label = label,
                            selected = platformFilter == slug,
                            onClick = { platformFilter = slug },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            when {
                loading && entries.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = PlayAmber) }

                visible.isEmpty() -> EmptyLauncher(onOpenLibrary, onAddLocal = { scope.launch { pickLocalGameFile()?.let { pendingAddPath = it } } })

                else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                    val landscape = maxWidth > 900.dp

                    Row(Modifier.fillMaxSize()) {
                        BoxWithConstraints(
                            Modifier
                                .weight(if (landscape) 1.35f else 1f)
                                .fillMaxHeight(),
                        ) {
                            // La carta crece con la ventana: antes 320dp fijos
                            // dejaban un desierto negro en pantallas grandes.
                            val pageWidth = if (landscape) {
                                minOf(720.dp, maxWidth * 0.64f, maxHeight * 0.90f * 0.70f)
                            } else {
                                minOf(420.dp, maxWidth * 0.68f, maxHeight * 0.52f * 0.70f)
                            }.coerceAtLeast(220.dp)
                            HorizontalPager(
                                state = pagerState,
                                pageSize = PageSize.Fixed(pageWidth),
                                contentPadding = PaddingValues(
                                    horizontal = ((maxWidth - pageWidth) / 2).coerceAtLeast(0.dp),
                                ),
                                pageSpacing = 18.dp,
                                // Los vecinos asoman por los lados: se desvanecen en
                                // el borde en vez de cortarse en seco junto al texto.
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                    .drawWithContent {
                                        drawContent()
                                        drawRect(
                                            brush = Brush.horizontalGradient(
                                                0f to Color.Transparent,
                                                0.06f to Color.Black,
                                                0.94f to Color.Black,
                                                1f to Color.Transparent,
                                            ),
                                            blendMode = BlendMode.DstIn,
                                        )
                                    },
                            ) { page ->
                                val entry = visible[page]
                                val isCurrent = page == pagerState.currentPage
                                val scale by animateFloatAsState(
                                    if (isCurrent) 1f else 0.80f,
                                    label = "playObjectScale",
                                )
                                val alpha by animateFloatAsState(
                                    if (isCurrent) 1f else 0.48f,
                                    label = "playObjectAlpha",
                                )
                                val physical = entry.physicalUrl != null && state.isPhysicalMedia(entry.card.rep.id)
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .padding(vertical = 10.dp)
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                            this.alpha = alpha
                                        }
                                        .clickable {
                                            if (isCurrent) state.openGame(entry.card)
                                            else scope.launch { pagerState.scrollToPage(page) }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (physical) {
                                        PhysicalMediaArt(entry.physicalUrl!!, entry.card.rep.name)
                                    } else {
                                        LauncherCard(entry, state, Modifier.fillMaxHeight(0.90f).aspectRatio(0.70f))
                                    }
                                }
                            }
                        }

                        if (landscape) {
                            current?.let { entry ->
                                PlayCaption(
                                    entry = entry,
                                    state = state,
                                    onPlay = { play(entry) },
                                    onDetails = { state.openGame(entry.card) },
                                    onTogglePhysical = { state.togglePhysicalMedia(entry.card.rep.id) },
                                    modifier = Modifier
                                        .weight(0.9f)
                                        .fillMaxHeight()
                                        .padding(end = 28.dp),
                                )
                            }
                        }
                    }

                    if (!landscape) {
                        current?.let { entry ->
                            PlayCaption(
                                entry = entry,
                                state = state,
                                onPlay = { play(entry) },
                                onDetails = { state.openGame(entry.card) },
                                onTogglePhysical = { state.togglePhysicalMedia(entry.card.rep.id) },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 16.dp),
                            )
                        }
                    }
                }
            }
        }

        pendingAddPath?.let { path ->
            val choices = platformsState.map { it.slug to (it.displayName ?: it.name) }
                .distinctBy { it.first }.sortedBy { it.second.lowercase() }
            AddLocalGameDialog(
                path = path,
                platforms = choices,
                initialSlug = com.davidadrianrg.atril.core.localgames.LocalGamePaths
                    .guessPlatform(path, choices.map { it.first }),
                onConfirm = { title, slug -> pendingAddPath = null; state.addLocalGame(title, slug, path) },
                onDismiss = { pendingAddPath = null },
            )
        }

        insertion?.let { ins ->
            InsertionOverlay(
                insertion = ins,
                state = state,
                progress = insertProgress.value,
                onCancel = { launchJob?.cancel() },
            )
        }
    }
}

@Composable
private fun InsertionOverlay(
    insertion: Insertion,
    state: DesktopAppState,
    progress: Float,
    onCancel: () -> Unit,
) {
    val entry = insertion.entry
    val optical = LaunchPlatforms.isOptical(entry.card.rep.platformSlug ?: "")
    val pose = com.davidadrianrg.atril.core.launch.InsertionMotion.pose(progress, optical)
    val appear = pose.appear
    val glow = (pose.glow + 0.5f * pose.impact).coerceAtMost(1.4f)

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.86f * appear))
            .clickable(onClick = onCancel),
    ) {
        val mediaSize = minOf(maxWidth * 0.38f, maxHeight * 0.42f).coerceAtLeast(160.dp)
        val slotY = maxHeight * 0.25f

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 28.dp)
                .graphicsLayer { alpha = appear },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                tr("launcher.inserting", entry.card.rep.name),
                style = MaterialTheme.typography.titleLarge,
                color = PlayCream,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Text(
                tr("launcher.cancel_hint"),
                style = MaterialTheme.typography.labelMedium,
                color = PlayCream.copy(alpha = 0.5f),
            )
        }

        Box(
            Modifier
                .align(Alignment.Center)
                .offset(y = slotY)
                .size(width = mediaSize * 1.08f, height = 20.dp)
                .graphicsLayer { alpha = appear }
                .shadow(
                    (6 + 22 * glow).dp,
                    RoundedCornerShape(10.dp),
                    spotColor = PlayAmber,
                    ambientColor = PlayAmber,
                )
                .clip(RoundedCornerShape(10.dp))
                .background(PlayBgBottom)
                .border(
                    1.5.dp,
                    PlayAmber.copy(alpha = (0.35f + 0.65f * glow).coerceIn(0f, 1f)),
                    RoundedCornerShape(10.dp),
                ),
        )

        // Sombra proyectada sobre la ranura: crece y se oscurece al acercarse.
        Box(
            Modifier
                .align(Alignment.Center)
                .offset(y = slotY - 6.dp)
                .size(width = mediaSize * 1.08f * pose.shadowScale, height = 18.dp)
                .graphicsLayer { alpha = pose.shadowAlpha }
                .background(
                    Brush.radialGradient(listOf(Color.Black, Color.Transparent)),
                    RoundedCornerShape(50),
                ),
        )

        Box(
            Modifier
                .align(Alignment.Center)
                .size(mediaSize)
                .graphicsLayer {
                    val travelPx = slotY.toPx() - mediaSize.toPx() * 0.18f
                    translationY = -mediaSize.toPx() * (0.16f * (1f - pose.travel) + pose.arc) +
                        travelPx * pose.travel
                    scaleX = pose.scale
                    scaleY = pose.scale * pose.squashY
                    rotationX = pose.rotationX
                    rotationY = pose.rotationY
                    rotationZ = pose.rotationZ
                    cameraDistance = 12f * 1000f
                    alpha = pose.alpha
                    transformOrigin = TransformOrigin(0.5f, if (optical) 0.5f else 1f)
                },
            contentAlignment = Alignment.Center,
        ) {
            if (insertion.physical && entry.physicalUrl != null) {
                PhysicalMediaArt(entry.physicalUrl, entry.card.rep.name)
            } else {
                LauncherCard(
                    entry = entry,
                    state = state,
                    modifier = Modifier.fillMaxHeight().aspectRatio(0.70f),
                )
            }
        }
    }
}

@Composable
private fun PlayHeader(
    count: Int,
    activeDownloads: Int,
    menuOpen: Boolean,
    onMenuOpen: () -> Unit,
    onMenuDismiss: () -> Unit,
    onOpenDownloads: () -> Unit,
    onNavigate: (Section) -> Unit,
    onAddLocal: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            RoundIconButton(AppIcons.Menu, tr("launcher.menu.open"), onMenuOpen)
            DropdownMenu(expanded = menuOpen, onDismissRequest = onMenuDismiss) {
                DropdownMenuItem(
                    text = { Text(tr("nav.library")) },
                    onClick = { onMenuDismiss(); onNavigate(Section.LIBRARY) },
                )
                DropdownMenuItem(
                    text = { Text(tr("nav.saves")) },
                    onClick = { onMenuDismiss(); onNavigate(Section.SAVES) },
                )
                DropdownMenuItem(
                    text = { Text(tr("nav.settings")) },
                    onClick = { onMenuDismiss(); onNavigate(Section.SETTINGS) },
                )
                DropdownMenuItem(
                    text = { Text(tr("nav.platforms")) },
                    onClick = { onMenuDismiss(); onNavigate(Section.PLATFORMS) },
                )
                DropdownMenuItem(
                    text = { Text(tr("nav.downloads")) },
                    onClick = { onMenuDismiss(); onOpenDownloads() },
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "ATRIL",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 2.4.sp,
                fontWeight = FontWeight.SemiBold,
                color = PlayAmber,
            )
            Text(
                tr("nav.play"),
                style = MaterialTheme.typography.headlineMedium,
                color = PlayCream,
            )
        }
        if (count > 0) {
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = PlayInk,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(PlayAmber)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        RoundIconButton(icon = AppIcons.Add, description = tr("local.add"), onClick = onAddLocal)
        Spacer(Modifier.width(8.dp))
        RoundIconButton(
            icon = AppIcons.Download,
            description = tr("nav.downloads"),
            onClick = onOpenDownloads,
            badge = activeDownloads.takeIf { it > 0 },
        )
    }
}

@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    badge: Int? = null,
) {
    Box {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .clip(CircleShape)
                .background(PlayCream.copy(alpha = 0.08f)),
        ) {
            Icon(icon, contentDescription = description, tint = PlayCream)
        }
        if (badge != null) {
            Text(
                badge.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun PlayPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) PlayAmber else Color(0x22F2E6D2))
            .border(
                1.dp,
                if (selected) Color.Transparent else PlayCream.copy(alpha = 0.14f),
                RoundedCornerShape(50),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) PlayInk else PlayCream.copy(alpha = 0.78f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LauncherCard(entry: LauncherEntry, state: DesktopAppState, modifier: Modifier) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .shadow(22.dp, shape, ambientColor = PlayAmber.copy(alpha = 0.25f), spotColor = Color.Black)
            .clip(shape)
            .background(PlayPlastic)
            .border(
                1.dp,
                Brush.verticalGradient(listOf(PlayAmber.copy(alpha = 0.55f), PlayAmber.copy(alpha = 0.08f))),
                shape,
            ),
    ) {
        PlayCover(entry.card.rep, state.config.serverUrl, Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.10f),
                    0.28f to Color.Transparent,
                ),
            ),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color(0xE80B0907), Color(0xF50B0907))),
                )
                .padding(start = 14.dp, end = 14.dp, top = 28.dp, bottom = 12.dp),
        ) {
            Text(
                entry.displayPlatform.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.4.sp,
                color = PlayAmber,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                entry.card.rep.name,
                style = MaterialTheme.typography.titleMedium,
                color = PlayCream,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (entry.card.discCount > 1) CardBadge(tr("launcher.badge.discs", entry.card.discCount))
            if (state.isManualDownloaded(entry.card)) CardBadge(tr("launcher.badge.manual"), subtle = true)
        }
    }
}

@Composable
private fun CardBadge(text: String, subtle: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = if (subtle) PlayCream else PlayInk,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (subtle) MaterialTheme.colorScheme.background.copy(alpha = 0.8f) else PlayAmber)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

@Composable
private fun PlayCover(rep: RomDto, serverUrl: String, modifier: Modifier) {
    val url = resolveMediaUrl(rep.urlCover, rep.pathCoverLarge ?: rep.pathCoverSmall, serverUrl)
    val bitmap = if (url != null) produceCover(url) else null
    if (bitmap != null) {
        Image(bitmap, null, contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        Box(modifier.background(PlayBgTop), contentAlignment = Alignment.Center) {
            Text(
                rep.name,
                style = MaterialTheme.typography.titleMedium,
                color = PlayCream,
                textAlign = TextAlign.Center,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun PhysicalMediaArt(url: String, name: String) {
    val bitmap: ImageBitmap? = produceCover(url)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize(0.95f)
                .background(Brush.radialGradient(listOf(PlayAmber.copy(alpha = 0.18f), Color.Transparent))),
        )
        if (bitmap != null) {
            Image(
                bitmap,
                contentDescription = tr("launcher.display.physical") + ": " + name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(top = 6.dp, bottom = 16.dp),
            )
        } else {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium,
                color = PlayCream,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp),
            )
        }
    }
}

@Composable
private fun PlayCaption(
    entry: LauncherEntry,
    state: DesktopAppState,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
    onTogglePhysical: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val manual = state.isManualDownloaded(entry.card)
    val physicalAvailable = entry.physicalUrl != null
    val physical = physicalAvailable && state.isPhysicalMedia(entry.card.rep.id)
    Column(
        modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            entry.card.rep.name,
            style = MaterialTheme.typography.headlineSmall,
            color = PlayCream,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            buildList {
                add(entry.displayPlatform)
                add(formatBytes(entry.card.rep.fileSizeBytes))
                if (entry.card.discCount > 1) add(tr("launcher.badge.discs", entry.card.discCount))
                if (manual) add(tr("launcher.badge.manual"))
            }.joinToString("  ·  "),
            style = MaterialTheme.typography.labelMedium,
            color = PlayCream.copy(alpha = 0.62f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (manual) {
            Spacer(Modifier.height(4.dp))
            Text(
                tr("launcher.not_launchable"),
                style = MaterialTheme.typography.labelSmall,
                color = PlayAmber.copy(alpha = 0.8f),
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onPlay, enabled = !manual) {
                Icon(AppIcons.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(tr("launcher.play"))
            }
            OutlinedButton(onClick = onDetails) {
                Icon(AppIcons.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(tr("launcher.details"))
            }
        }
        if (physicalAvailable) {
            Spacer(Modifier.height(12.dp))
            FilterChip(
                selected = physical,
                onClick = onTogglePhysical,
                label = { Text(tr("launcher.display.physical")) },
            )
        }
        if (!physicalAvailable && !manual && entry.local == null) {
            Spacer(Modifier.height(12.dp))
            val scrape by state.scrapes.collectAsState()
            val ui = scrape[entry.card.rep.id]
            val running = ui is DesktopAppState.ScrapeUi.Running
            OutlinedButton(
                onClick = { state.requestPhysicalScrape(entry.card.rep.id) },
                enabled = !running && state.connected.value,
            ) {
                if (running) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = PlayAmber,
                    )
                } else {
                    Icon(AppIcons.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    if (ui is DesktopAppState.ScrapeUi.Finished && !ui.found) tr("scrape.retry")
                    else tr("scrape.action"),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                when (ui) {
                    is DesktopAppState.ScrapeUi.Running -> ui.text
                    is DesktopAppState.ScrapeUi.Finished -> ui.text
                    null -> tr("scrape.explain")
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (ui is DesktopAppState.ScrapeUi.Finished && !ui.found) {
                    MaterialTheme.colorScheme.error
                } else {
                    PlayCream.copy(alpha = 0.55f)
                },
                modifier = Modifier.widthIn(max = 420.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            tr("launcher.desktop.play_hint"),
            style = MaterialTheme.typography.bodySmall,
            color = PlayCream.copy(alpha = 0.45f),
        )
    }
}

@Composable
private fun EmptyLauncher(onOpenLibrary: () -> Unit, onAddLocal: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 520.dp),
        ) {
            EmptyState(
                icon = AppIcons.Gamepad,
                title = tr("play.empty"),
                description = tr("play.empty_hint"),
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onOpenLibrary) { Text(tr("play.open_library")) }
        }
    }
}

private fun localLauncherEntries(state: DesktopAppState): List<LauncherEntry> {
    val dtos = state.library.roms().associate { entry ->
        entry.romId to RomDto(
            id = entry.romId,
            name = entry.name,
            fileName = entry.fileName,
            platformId = 0,
            platformSlug = entry.platformSlug,
        )
    }
    val groups = MultiDiscGrouper.group(
        dtos.values.map { dto ->
            DiscEntry(
                id = dto.id,
                name = dto.name,
                platformSlug = dto.platformSlug ?: "unknown",
                igdbId = null,
                fileNames = listOf(dto.fileName),
            )
        },
    )
    return groups.mapNotNull { group ->
        val rep = dtos[group.representativeId] ?: return@mapNotNull null
        val slug = rep.platformSlug ?: "unknown"
        LauncherEntry(
            card = GameCard(rep, group.discCount, group.allIds.mapNotNull { dtos[it] }, downloaded = true),
            platformLabel = state.platforms.value.firstOrNull { it.slug == slug }?.let { it.displayName ?: it.name }
                ?: slug.uppercase(),
            physicalUrl = null,
        )
    }.sortedBy { it.card.rep.name.lowercase() }
}

private suspend fun loadLauncherEntries(state: DesktopAppState): List<LauncherEntry> = coroutineScope {
    val libraryEntries = state.library.roms()
    val platformMeta = libraryEntries.map { it.platformSlug }.distinct().associateWith { slug ->
        async { runCatching { state.fetchPlatformRomMeta(slug) }.getOrDefault(emptyMap()) }
    }.mapValues { (_, deferred) -> deferred.await() }

    val dtos = linkedMapOf<Int, RomDto>()
    libraryEntries.forEach { entry ->
        val dto = platformMeta[entry.platformSlug]?.get(entry.romId)
            ?: runCatching { state.fetchRomMeta(entry.romId) }.getOrNull()
            ?: RomDto(
                id = entry.romId,
                name = entry.name,
                fileName = entry.fileName,
                platformId = 0,
                platformSlug = entry.platformSlug,
            )
        dtos[entry.romId] = dto
    }
    state.manualDownloadedIds().forEach { id ->
        if (id !in dtos) {
            runCatching { state.fetchRomMeta(id) }.getOrNull()?.let { dtos[id] = it }
        }
    }

    val groups = MultiDiscGrouper.group(
        dtos.values.map { dto ->
            DiscEntry(
                id = dto.id,
                name = dto.name,
                platformSlug = dto.platformSlug ?: "unknown",
                igdbId = dto.igdbId,
                fileNames = dto.files.map { it.filename }.ifEmpty { listOf(dto.fileName) },
            )
        },
    )
    groups.mapNotNull { group ->
        val rep = dtos[group.representativeId] ?: return@mapNotNull null
        val groupRoms = group.allIds.mapNotNull { dtos[it] }
        val slug = rep.platformSlug ?: "unknown"
        val label = state.platforms.value.firstOrNull { it.slug == slug }?.let { it.displayName ?: it.name }
            ?: slug.uppercase()
        LauncherEntry(
            card = GameCard(rep, group.discCount, groupRoms, downloaded = true),
            platformLabel = label,
            physicalUrl = PhysicalMedia.resourceUrl(state.config.serverUrl, rep.physicalMediaPath),
        )
    }.sortedBy { it.card.rep.name.lowercase() }
}
