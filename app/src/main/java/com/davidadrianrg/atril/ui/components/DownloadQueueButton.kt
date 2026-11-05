package com.davidadrianrg.atril.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgeDefaults
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.davidadrianrg.atril.RomMSyncApplication
import com.davidadrianrg.atril.core.i18n.tr
import com.davidadrianrg.atril.ui.viewmodel.DownloadQueueViewModel

/** Ámbar de insignia compartido con el launcher (mismo tono que el contador de Play). */
internal val QueueBadgeAmber = Color(0xFFE8B56A)
internal val QueueBadgeInk = Color(0xFF2A1D08)

/**
 * Botón único de la cola de descargas: icono de descarga + insignia ámbar con las descargas
 * activas (sin insignia si no hay ninguna; el hueco del botón es siempre el mismo).
 * Sustituye al icono suelto de Biblioteca y al de Play.
 */
@Composable
fun DownloadQueueButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    val container = (LocalContext.current.applicationContext as RomMSyncApplication).container
    val vm: DownloadQueueViewModel = viewModel(
        factory = viewModelFactory { initializer { DownloadQueueViewModel(container.downloadManager) } },
    )
    val tasks by vm.downloads.collectAsState()
    val active = tasks.count { it.isRunning || (!it.isCompleted && !it.isFailed) }
    IconButton(onClick = onClick, modifier = modifier) {
        BadgedBox(
            badge = {
                if (active > 0) {
                    Badge(containerColor = QueueBadgeAmber, contentColor = QueueBadgeInk) {
                        Text(if (active > 99) "99+" else active.toString())
                    }
                }
            },
        ) {
            Icon(
                Icons.Outlined.Download,
                contentDescription = if (active > 0) tr("downloads.in_progress", active) else tr("nav.downloads"),
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
