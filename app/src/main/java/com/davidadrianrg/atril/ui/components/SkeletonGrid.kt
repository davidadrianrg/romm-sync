package com.davidadrianrg.atril.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.davidadrianrg.atril.core.i18n.tr

/**
 * Esqueleto de carga de la rejilla (tarjetas que "respiran") en vez de un spinner solitario.
 *
 * Usa las mismas medidas que la rejilla real para que las columnas no salten al terminar de cargar.
 * [count] solo acota: la rejilla es perezosa y compone las tarjetas visibles, así que debe sobrar
 * para llenar una tablet.
 */
@Composable
fun SkeletonGrid(minCardSize: Dp, cardAspectRatio: Float, modifier: Modifier = Modifier, count: Int = 60) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    // Las tarjetas no tienen texto: sin esto, TalkBack no anuncia que la biblioteca está cargando.
    val loadingLabel = tr("library.loading")
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minCardSize),
        modifier = modifier.fillMaxSize().semantics { contentDescription = loadingLabel },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        userScrollEnabled = false,
    ) {
        items(count) {
            Box(
                Modifier
                    .aspectRatio(cardAspectRatio)
                    // En la capa gráfica: el pulso no recompone todas las tarjetas en cada fotograma.
                    .graphicsLayer { alpha = pulse }
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
        }
    }
}
