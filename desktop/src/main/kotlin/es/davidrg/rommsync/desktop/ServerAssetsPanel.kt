package es.davidrg.rommsync.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import es.davidrg.rommsync.core.i18n.tr
import es.davidrg.rommsync.core.remote.dto.SaveDto
import es.davidrg.rommsync.core.remote.dto.StateDto
import kotlinx.coroutines.launch
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** Guardados/estados del servidor para un juego + subida manual (desktop). */
@Composable
fun ServerAssetsPanel(romId: Int, state: DesktopAppState) {
    var saves by remember { mutableStateOf<List<SaveDto>>(emptyList()) }
    var states by remember { mutableStateOf<List<StateDto>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            loading = true
            saves = state.serverSaves(romId)
            states = state.serverStates(romId)
            loading = false
        }
    }

    LaunchedEffect(romId) { reload() }

    Spacer(Modifier.height(20.dp))
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                tr("saves.server.title"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (loading || uploading) {
                CircularProgressIndicator(Modifier.height(16.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = { reload() }) { Text(tr("saves.server.refresh")) }
            }
        }

        if (!loading && saves.isEmpty() && states.isEmpty()) {
            Text(
                tr("saves.server.none"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        saves.forEach { save ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(save.fileName, style = MaterialTheme.typography.bodySmall)
                    Text(
                        listOfNotNull(save.emulator, formatDate(save.updatedAt)).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(tr("saves.server.save"), style = MaterialTheme.typography.labelSmall)
            }
        }
        states.forEach { st ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(st.fileName, style = MaterialTheme.typography.bodySmall)
                    Text(
                        listOfNotNull(st.emulator, formatDate(st.updatedAt)).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(tr("saves.server.state"), style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(Modifier.height(4.dp))
        FilledTonalButton(
            enabled = !uploading,
            onClick = {
                val picked = pickFile(tr("saves.server.manual")) ?: return@FilledTonalButton
                scope.launch {
                    uploading = true
                    val result = state.uploadServerSave(romId, picked)
                    uploading = false
                    message = result.fold(
                        { tr("saves.server.upload_ok") },
                        { tr("saves.server.upload_error", it.message ?: "") },
                    )
                    if (result.isSuccess) reload()
                }
            },
        ) { Text(tr("saves.server.manual")) }
        Text(
            tr("saves.server.manual_hint"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** "2026-09-25T16:24:05" → "2026-09-25 16:24". */
private fun formatDate(raw: String?): String {
    val clean = raw?.replace('T', ' ')?.trim() ?: return ""
    return if (clean.length >= 16) clean.take(16) else clean
}

/** Selector de fichero AWT (modal, mismo look que el resto del desktop). */
internal fun pickFile(title: String): File? {
    val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
    dialog.isVisible = true
    val dir = dialog.directory ?: return null
    val name = dialog.file ?: return null
    return File(dir, name)
}
