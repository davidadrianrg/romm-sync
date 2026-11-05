package com.davidadrianrg.atril.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Botón circular con contenido centrado y contador opcional.
 * Pensado para toolbars compactas en portátiles: tamaño explícito (46dp en
 * ventanas compactas) y badge numérico en la esquina superior.
 */
@Composable
fun FilledTonalIconButton(
    onClick: () -> Unit,
    size: Dp = 48.dp,
    badge: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .size(size)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
        if (badge != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = (-6).dp)
                    // Pastilla que crece con el texto: un círculo fijo recortaba "207" o "1234".
                    .heightIn(min = (size * 0.46f).coerceAtLeast(16.dp))
                    .widthIn(min = (size * 0.46f).coerceAtLeast(16.dp))
                    .background(QueueBadgeAmber, CircleShape)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = QueueBadgeInk,
                    maxLines = 1,
                )
            }
        }
    }
}
