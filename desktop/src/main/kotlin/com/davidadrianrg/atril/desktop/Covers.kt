package com.davidadrianrg.atril.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.davidadrianrg.atril.core.library.CoverRatio
import com.davidadrianrg.atril.core.library.PlatformAbbreviation
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

/**
 * Portada con caché de disco (CoverCache) y decodificación en IO.
 * El ratio se recibe como parámetro: la biblioteca usa la MEDIANA de las
 * portadas de la vista para un grid uniforme (igual que Android; ver
 * references/compose-ui-notes.md — nunca sizing por card).
 */
@Composable
fun CoverImage(
    coverUrl: String?,
    pathCover: String?,
    serverUrl: String,
    aspectRatio: Float = 2f / 3f,
    contentScale: ContentScale = ContentScale.Crop,
    /** Usa el ratio real del bitmap (sin recortar) en vez de [aspectRatio]. */
    naturalRatio: Boolean = false,
    /** Plataforma que se muestra abreviada en lugar del icono si no hay portada. */
    placeholderSlug: String? = null,
) {
    val url = resolveMediaUrl(coverUrl, pathCover, serverUrl)
    if (url != null) {
        val bitmap = produceCover(url)
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = if (naturalRatio) ContentScale.Fit else contentScale,
                modifier = Modifier.fillMaxWidth().aspectRatio(
                    if (naturalRatio) com.davidadrianrg.atril.core.library.CoverRatio.of(bitmap.width, bitmap.height) else aspectRatio,
                ),
            )
        } else {
            CoverPlaceholder(aspectRatio, placeholderSlug)
        }
    } else {
        CoverPlaceholder(aspectRatio, placeholderSlug)
    }
}

/** Resuelve una URL absoluta o una ruta relativa del servidor RomM. */
fun resolveMediaUrl(absoluteUrl: String?, relativePath: String?, serverUrl: String): String? = when {
    // RomM devuelve la portada local con un timestamp que puede contener
    // espacios ("?ts=2026-10-26 15:01:56"); java.net.URL no los acepta. Se
    // prefiere el asset local sobre la URL externa de ScreenScraper: es más
    // rápido y no depende de un tercero.
    !relativePath.isNullOrBlank() && serverUrl.isNotBlank() ->
        serverUrl.trimEnd('/') + "/" + relativePath.removePrefix("/").replace(" ", "%20")
    !absoluteUrl.isNullOrBlank() -> absoluteUrl
    else -> null
}

@Composable
private fun CoverPlaceholder(aspectRatio: Float, slug: String?) {
    val abbreviation = slug?.let(PlatformAbbreviation::of).orEmpty()
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (abbreviation.isNotEmpty()) {
            Text(
                text = abbreviation,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                maxLines = 1,
            )
        } else {
            Icon(
                AppIcons.Gamepad,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@Composable
fun produceCover(url: String): ImageBitmap? {
    // Carga async: caché LRU en disco (CoverCache) + decodificación en IO.
    val bitmap by produceState<ImageBitmap?>(null, url) {
        withContext(Dispatchers.IO) {
            // Carátulas de juegos locales: ya están en disco, sin caché ni red.
            val cached = if (url.startsWith("file://")) File(url.removePrefix("file://")).takeIf { it.isFile } else CoverCache.fetch(url)
            if (cached != null) {
                runCatching {
                    FileInputStream(cached).use { input -> value = loadImageBitmap(input) }
                }
            }
        }
    }
    return bitmap
}

/**
 * Ratio (w/h) mediano de las portadas de una lista de juegos: se decodifican
 * en IO las primeras [sample] portadas (vía caché de disco) y se calcula la
 * mediana. Mientras no haya muestra suficiente devuelve el default 2:3.
 */
@Composable
fun rememberMedianCoverRatio(cards: List<GameCard>, serverUrl: String, sample: Int = 24): Float? {
    // null = aún midiendo: la biblioteca no pinta tarjetas hasta tener el ratio real,
    // para que no aparezcan con un tamaño y se reajusten segundos después.
    val ratio by produceState<Float?>(null, cards, serverUrl) {
        val urls = cards.take(sample).mapNotNull { resolveMediaUrl(it.rep.urlCover, it.rep.pathCoverLarge, serverUrl) }
        if (urls.isEmpty()) {
            value = CoverRatio.DEFAULT
            return@produceState
        }
        val measured = withContext(Dispatchers.IO) {
            // Tope de tiempo: con red lenta se usa lo medido hasta entonces.
            val out = java.util.concurrent.CopyOnWriteArrayList<Float>()
            kotlinx.coroutines.withTimeoutOrNull(4_000) {
                urls.forEach { url ->
                    runCatching {
                        CoverCache.fetch(url)?.let { f ->
                            FileInputStream(f).use { loadImageBitmap(it) }.let { bmp ->
                                out += CoverRatio.of(bmp.width, bmp.height)
                            }
                        }
                    }
                }
            }
            out.toList()
        }
        value = CoverRatio.median(measured).coerceIn(0.5f, 1.2f)
    }
    return ratio
}
