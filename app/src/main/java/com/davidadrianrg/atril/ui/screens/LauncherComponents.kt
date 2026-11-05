package com.davidadrianrg.atril.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import com.davidadrianrg.atril.ui.input.focusRing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.davidadrianrg.atril.core.i18n.tr
import com.davidadrianrg.atril.ui.navigation.Screen
import com.davidadrianrg.atril.ui.theme.LocalLauncherColors
import com.davidadrianrg.atril.ui.viewmodel.ShelfItem
import java.io.File

// ── Paleta Warm Archive del launcher ────────────────────────────────
private fun argb(v: Long) = Color(v.toInt())
internal val LauncherBgTop: Color @Composable get() = argb(LocalLauncherColors.current.launcherBgTop)
internal val LauncherBgBottom: Color @Composable get() = argb(LocalLauncherColors.current.launcherBgBottom)
internal val LauncherCream: Color @Composable get() = MaterialTheme.colorScheme.onBackground
internal val LauncherAmber: Color @Composable get() = MaterialTheme.colorScheme.primary
internal val LauncherInk: Color @Composable get() = MaterialTheme.colorScheme.onPrimary
internal val LauncherPlastic: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
internal val LauncherPanel: Color @Composable get() = MaterialTheme.colorScheme.background.copy(alpha = 0.95f)

private val CardShape = RoundedCornerShape(18.dp)

/** Proporción de la carta (portada vertical). */
internal const val CARD_ASPECT = 0.70f

/**
 * El objeto del juego en el carrusel: carta por defecto o la imagen real del
 * medio físico. El cambio entre ambos se anima (fundido + escala).
 */
@Composable
internal fun LauncherGameObject(item: ShelfItem, modifier: Modifier = Modifier) {
    val physicalFile = item.physicalMediaFile?.takeIf { item.showPhysical }
    AnimatedContent(
        targetState = physicalFile,
        transitionSpec = {
            (fadeIn(tween(320)) + scaleIn(tween(320), initialScale = 0.88f)) togetherWith
                (fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 1.06f))
        },
        contentAlignment = Alignment.Center,
        label = "cardPhysical",
        modifier = modifier,
    ) { file ->
        if (file != null) {
            PhysicalMediaArt(file, item.rom.name, Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GameCard(item, Modifier.fillMaxHeight(0.92f).aspectRatio(CARD_ASPECT))
            }
        }
    }
}

/** Carta coleccionable: portada, plataforma, título e insignias. */
@Composable
internal fun GameCard(item: ShelfItem, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .shadow(22.dp, CardShape, ambientColor = LauncherAmber.copy(alpha = 0.25f), spotColor = Color.Black)
            .clip(CardShape)
            .background(LauncherPlastic)
            .border(
                1.dp,
                Brush.verticalGradient(listOf(LauncherAmber.copy(alpha = 0.55f), LauncherAmber.copy(alpha = 0.08f))),
                CardShape,
            ),
    ) {
        LauncherCoverArt(item, Modifier.fillMaxSize())
        // Brillo superior, como el laminado de una carta.
        Box(
            Modifier
                .fillMaxSize()
                .background(
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
                item.platformLabel.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.4.sp,
                color = LauncherAmber,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.rom.name,
                style = MaterialTheme.typography.titleMedium,
                color = LauncherCream,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (item.discCount > 1) CardBadge(tr("launcher.badge.discs", item.discCount))
            if (item.isManualDownloaded) CardBadge(tr("launcher.badge.manual"), subtle = true)
        }
    }
}

@Composable
private fun CardBadge(text: String, subtle: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = if (subtle) LauncherCream else LauncherInk,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (subtle) Color(0xCC0B0907) else LauncherAmber)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

@Composable
internal fun LauncherCoverArt(item: ShelfItem, modifier: Modifier = Modifier) {
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
                style = MaterialTheme.typography.titleMedium,
                color = LauncherCream,
                textAlign = TextAlign.Center,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

/** La imagen real del medio físico, con halo cálido y sombra de suelo. */
@Composable
internal fun PhysicalMediaArt(path: String, name: String, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize(0.95f)
                .background(Brush.radialGradient(listOf(LauncherAmber.copy(alpha = 0.18f), Color.Transparent))),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.66f)
                .height(26.dp)
                .drawBehind {
                    drawOval(
                        brush = Brush.radialGradient(
                            listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent),
                            center = Offset(size.width / 2, size.height / 2),
                            radius = size.width / 2,
                        ),
                        topLeft = Offset.Zero,
                        size = Size(size.width, size.height),
                    )
                },
        )
        AsyncImage(
            model = File(path),
            contentDescription = tr("launcher.display.physical") + ": " + name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 6.dp, bottom = 16.dp),
        )
    }
}

/** Pie del launcher: título, metadatos y acciones Jugar / Detalles. */
@Composable
internal fun LauncherCaption(
    item: ShelfItem,
    meta: List<String>,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
    playBusy: Boolean,
    modifier: Modifier = Modifier,
    alignStart: Boolean = false,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (alignStart) Alignment.Start else Alignment.CenterHorizontally,
    ) {
        Text(
            item.rom.name,
            style = MaterialTheme.typography.headlineSmall,
            color = LauncherCream,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (alignStart) TextAlign.Start else TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            meta.joinToString("  ·  "),
            style = MaterialTheme.typography.labelMedium,
            color = LauncherCream.copy(alpha = 0.62f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (alignStart) TextAlign.Start else TextAlign.Center,
        )
        if (!item.launchable) {
            Spacer(Modifier.height(4.dp))
            Text(
                tr(if (item.isLocalSource) "local.file_missing" else "launcher.not_launchable"),
                style = MaterialTheme.typography.labelSmall,
                color = LauncherAmber.copy(alpha = 0.8f),
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PlayPill(enabledLook = item.launchable && !playBusy, onClick = onPlay)
            Row(
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(50))
                    .border(1.dp, LauncherCream.copy(alpha = 0.22f), RoundedCornerShape(50))
                    .clickable(role = Role.Button, onClick = onDetails)
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = LauncherCream, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("launcher.details"), style = MaterialTheme.typography.labelLarge, color = LauncherCream)
            }
        }
    }
}

/**
 * Botón Jugar. Nunca está "muerto": si el juego no se puede lanzar se ve
 * atenuado pero al tocarlo explica por qué.
 */
@Composable
private fun PlayPill(enabledLook: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .shadow(if (enabledLook) 10.dp else 0.dp, RoundedCornerShape(50), spotColor = LauncherAmber)
            .clip(RoundedCornerShape(50))
            .background(if (enabledLook) LauncherAmber else LauncherAmber.copy(alpha = 0.32f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 18.dp, end = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = LauncherInk)
        Spacer(Modifier.width(6.dp))
        Text(
            tr("launcher.play"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = LauncherInk,
        )
    }
}

/** Píldora de filtro de plataforma (sustituye al FilterChip de Material). */
@Composable
internal fun LauncherPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) LauncherAmber else Color(0x22F2E6D2))
            .border(1.dp, if (selected) Color.Transparent else LauncherCream.copy(alpha = 0.14f), RoundedCornerShape(50))
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) LauncherInk else LauncherCream.copy(alpha = 0.78f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Menú overlay del launcher: sustituye al rail / barra inferior permanentes.
 * En horizontal entra por la izquierda; en vertical sube desde abajo (al
 * alcance del pulgar). Se cierra tocando fuera, con el botón o con Atrás.
 */
@Composable
internal fun LauncherMenuOverlay(
    visible: Boolean,
    items: List<Screen>,
    landscape: Boolean,
    onNavigate: (Screen) -> Unit,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(visible, enter = fadeIn(tween(220)), exit = fadeOut(tween(180))) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = tr("launcher.menu.close"),
                    onClick = onDismiss,
                ),
        )
    }
    Box(Modifier.fillMaxSize(), contentAlignment = if (landscape) Alignment.CenterStart else Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = visible,
            enter = if (landscape) {
                slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { -it } + fadeIn(tween(200))
            } else {
                slideInVertically(tween(280, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(200))
            },
            exit = if (landscape) {
                slideOutHorizontally(tween(220)) { -it } + fadeOut(tween(180))
            } else {
                slideOutVertically(tween(220)) { it } + fadeOut(tween(180))
            },
        ) {
            val shape = if (landscape) {
                RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp)
            } else {
                RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            }
            Column(
                modifier = (if (landscape) Modifier.width(300.dp).fillMaxHeight() else Modifier.fillMaxWidth())
                    .clip(shape)
                    .background(LauncherPanel)
                    .border(1.dp, LauncherAmber.copy(alpha = 0.18f), shape)
                    // Consume los toques: que no lleguen al scrim.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            if (landscape) {
                                WindowInsetsSides.Start + WindowInsetsSides.Vertical
                            } else {
                                WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                            },
                        ),
                    )
                    // En horizontal la lista puede no caber: que se desplace.
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        AppMark()
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Atril",
                            style = MaterialTheme.typography.titleLarge,
                            color = LauncherCream,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            tr("launcher.tagline"),
                            style = MaterialTheme.typography.labelSmall.copy(lineHeight = 16.sp),
                            color = LauncherCream.copy(alpha = 0.55f),
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = tr("launcher.menu.close"), tint = LauncherCream)
                    }
                }
                Spacer(Modifier.height(12.dp))
                items.forEach { screen ->
                    val selected = screen == Screen.Play
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .focusRing()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                            .clickable(role = Role.Button) { onNavigate(screen) }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (selected) screen.selectedIcon else screen.icon,
                            contentDescription = null,
                            tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else LauncherCream.copy(alpha = 0.8f),
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            screen.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else LauncherCream,
                        )
                    }
                }
            }
        }
    }
}

/** Marca de la app ("ATRIL") en versalitas ámbar. */
@Composable
internal fun AppMark() {
    Text(
        "ATRIL",
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 2.4.sp,
        fontWeight = FontWeight.SemiBold,
        color = LauncherAmber,
    )
}

/**
 * Animación de inserción: el medio físico baja en vertical (sin giros) hacia una
 * consola en el borde inferior y se hunde en su ranura; la ranura se ilumina y se
 * lanza el juego. [progress] va de 0 a 1 y lo controla quien lanza (cancelable).
 */
@Composable
internal fun InsertionOverlay(
    title: String,
    isOptical: Boolean,
    model: Any?,
    progress: Float,
    onCancel: () -> Unit,
) {
    val pose = com.davidadrianrg.atril.core.launch.InsertionMotion.pose(progress)
    val appear = pose.appear
    val glow = (pose.glow + 0.5f * pose.impact).coerceAtMost(1f)

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF050507).copy(alpha = 0.94f * appear))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = tr("common.cancel"),
                onClick = onCancel,
            )
            .semantics { contentDescription = tr("launcher.inserting", title) },
    ) {
        val mediaSize = minOf(maxWidth, maxHeight) * 0.40f
        val consoleH = maxHeight * 0.17f
        val slotWidth = mediaSize * if (isOptical) 1.02f else 0.94f

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 16.dp)
                .graphicsLayer { alpha = appear },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                tr("launcher.inserting", title),
                style = MaterialTheme.typography.titleLarge,
                color = LauncherCream,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Text(
                tr("launcher.cancel_hint"),
                style = MaterialTheme.typography.labelMedium,
                color = LauncherCream.copy(alpha = 0.5f),
            )
        }

        // Sombra del medio sobre la consola: crece al acercarse y desaparece al hundirse.
        // Va antes del medio para quedar DEBAJO de él (no oscurecer su borde inferior).
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .offset(y = -(consoleH) + 2.dp)
                .size(width = slotWidth * pose.shadowScale, height = 14.dp)
                .graphicsLayer { alpha = pose.shadowAlpha }
                .background(
                    androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color.Black, Color.Transparent)),
                    RoundedCornerShape(50),
                ),
        )

        // Medio: baja en vertical y se hunde en la ranura (la consola lo tapa).
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(mediaSize)
                .graphicsLayer {
                    val h = mediaSize.toPx()
                    val startBottom = maxHeight.toPx() * 0.60f
                    val rest = maxHeight.toPx() - consoleH.toPx() + 4.dp.toPx() // borde de la ranura
                    val bottom = startBottom + (rest - startBottom) * pose.drop + h * 0.78f * pose.seat
                    translationY = bottom - h
                    scaleX = pose.scale
                    scaleY = pose.scale
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                    alpha = appear
                },
        )

        SlotConsole(
            slotWidth = slotWidth,
            glow = glow,
            impact = pose.impact,
            accent = LauncherAmber,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .height(consoleH)
                .graphicsLayer { translationY = (1f - pose.console) * consoleH.toPx() },
        )
    }
}

/**
 * Consola oscura del borde inferior con la ranura: bisel metálico, raíles
 * laterales, sombra interior y un LED que se ilumina al encajar. Se dibuja
 * por ENCIMA del medio, así el cartucho se hunde «dentro» de la ranura.
 */
@Composable
private fun SlotConsole(
    slotWidth: androidx.compose.ui.unit.Dp,
    glow: Float,
    impact: Float,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val g = glow.coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = impact * 2.5f * density }
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(Color(0xFF2A2A2C), Color(0xFF161617), Color(0xFF0C0C0D)),
                ),
            ),
    ) {
        // Filo metálico del borde superior de la consola.
        Box(
            Modifier.fillMaxWidth().height(2.dp).background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(Color(0xFF3A3A3D), Color(0xFF8E8E93), Color(0xFF3A3A3D)),
                ),
            ),
        )
        // Boca de la ranura (hueco oscuro con sombra interior).
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 2.dp)
                .size(width = slotWidth, height = 18.dp)
                .shadow((2 + 14 * g).dp, RoundedCornerShape(bottomStart = 9.dp, bottomEnd = 9.dp), spotColor = accent, ambientColor = accent)
                .clip(RoundedCornerShape(bottomStart = 9.dp, bottomEnd = 9.dp))
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.Black, Color(0xFF050505), Color(0xFF1B1B1D)),
                    ),
                )
                .border(
                    1.dp,
                    accent.copy(alpha = 0.18f + 0.7f * g),
                    RoundedCornerShape(bottomStart = 9.dp, bottomEnd = 9.dp),
                ),
        )
        // Raíles laterales de la ranura.
        listOf(-1, 1).forEach { side ->
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(x = (slotWidth / 2 + 6.dp) * side, y = 4.dp)
                    .size(width = 4.dp, height = 12.dp)
                    .background(Color(0xFF5A5A5F), RoundedCornerShape(2.dp)),
            )
        }
        // LED central.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = 24.dp)
                .size(7.dp)
                .shadow((1 + 10 * g).dp, androidx.compose.foundation.shape.CircleShape, spotColor = accent, ambientColor = accent)
                .background(
                    androidx.compose.ui.graphics.lerp(Color(0xFF3A2A12), accent, g),
                    androidx.compose.foundation.shape.CircleShape,
                ),
        )
    }
}


/** Qué se está insertando (título, imagen —fichero de medio físico o URL de carátula— y tipo). */
internal data class InsertionSpec(val title: String, val model: Any?, val isOptical: Boolean)

/**
 * Secuencia única para jugar desde cualquier pantalla: preparar → overlay de inserción
 * (~1,3 s, sin adelantar) → lanzar. El overlay se mantiene hasta que el intent de
 * lanzamiento se haya enviado y siempre se cierra (también si falla o se cancela).
 */
@androidx.compose.runtime.Stable
internal class InsertionState {
    var active by androidx.compose.runtime.mutableStateOf<InsertionSpec?>(null)
        private set
    val progress = androidx.compose.animation.core.Animatable(0f)

    /** Devuelve el mensaje de error del lanzamiento (null si fue bien). */
    suspend fun run(spec: InsertionSpec, launch: suspend () -> String?): String? {
        active = spec
        progress.snapTo(0f)
        try {
            progress.animateTo(
                1f,
                androidx.compose.animation.core.tween(
                    com.davidadrianrg.atril.core.launch.InsertionMotion.DURATION_MS,
                    easing = androidx.compose.animation.core.LinearEasing,
                ),
            )
            return launch()
        } finally {
            active = null
            progress.snapTo(0f)
        }
    }
}

/** Overlay a pantalla completa en una ventana propia: queda por encima de hojas/diálogos. */
@Composable
internal fun InsertionDialogLayer(state: InsertionState, onCancel: () -> Unit) {
    val spec = state.active ?: return
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onCancel,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false,
        ),
    ) {
        InsertionOverlay(spec.title, spec.isOptical, spec.model, state.progress.value, onCancel)
    }
}
