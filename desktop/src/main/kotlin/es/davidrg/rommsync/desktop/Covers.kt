package es.davidrg.rommsync.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
) {
    val url = resolveMediaUrl(coverUrl, pathCover, serverUrl)
    if (url != null) {
        val bitmap = produceCover(url)
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxWidth().aspectRatio(aspectRatio),
            )
        } else {
            CoverPlaceholder(aspectRatio)
        }
    } else {
        CoverPlaceholder(aspectRatio)
    }
}

/** Resuelve una URL absoluta o una ruta relativa del servidor RomM. */
fun resolveMediaUrl(absoluteUrl: String?, relativePath: String?, serverUrl: String): String? = when {
    !absoluteUrl.isNullOrBlank() -> absoluteUrl
    !relativePath.isNullOrBlank() && serverUrl.isNotBlank() ->
        serverUrl.trimEnd('/') + "/" + relativePath.removePrefix("/")
    else -> null
}

@Composable
private fun CoverPlaceholder(aspectRatio: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            AppIcons.Gamepad,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(32.dp),
        )
    }
}

@Composable
fun produceCover(url: String): ImageBitmap? {
    // Carga async: caché LRU en disco (CoverCache) + decodificación en IO.
    val bitmap by produceState<ImageBitmap?>(null, url) {
        withContext(Dispatchers.IO) {
            val cached = CoverCache.fetch(url)
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
fun rememberMedianCoverRatio(cards: List<GameCard>, serverUrl: String, sample: Int = 24): Float {
    val ratio by produceState(2f / 3f, cards, serverUrl) {
        val urls = cards.take(sample).mapNotNull { resolveMediaUrl(it.rep.urlCover, it.rep.pathCoverLarge, serverUrl) }
        if (urls.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                val ratios = urls.mapNotNull { url ->
                    runCatching {
                        CoverCache.fetch(url)?.let { f ->
                            FileInputStream(f).use { loadImageBitmap(it) }.let { bmp ->
                                if (bmp.height > 0) bmp.width.toFloat() / bmp.height.toFloat() else null
                            }
                        }
                    }.getOrNull()
                }
                if (ratios.isNotEmpty()) {
                    val sorted = ratios.sorted()
                    value = sorted[sorted.size / 2].coerceIn(0.5f, 1.2f)
                }
            }
        }
    }
    return ratio
}
