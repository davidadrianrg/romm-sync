package es.davidrg.rommsync.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.davidrg.rommsync.core.i18n.tr
import es.davidrg.rommsync.core.remote.dto.PlatformDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/** Subida de un juego (ROM) al servidor desde desktop (chunked + progreso). */
@Composable
fun UploadGameDialogDesktop(
    state: DesktopAppState,
    platforms: List<PlatformDto>,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var platformId by remember { mutableStateOf(platforms.firstOrNull()?.id) }
    var file by remember { mutableStateOf<File?>(null) }
    var progress by remember { mutableStateOf<Int?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (progress == null) onDismiss() },
        title = { Text(tr("library.upload.title")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("library.upload.platform"), style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    platforms.forEach { platform ->
                        FilterChip(
                            selected = platformId == platform.id,
                            onClick = { platformId = platform.id },
                            label = {
                                Text(
                                    platform.displayName ?: platform.name,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                    }
                }
                OutlinedButton(
                    enabled = progress == null,
                    onClick = {
                        val picked = pickFile(tr("library.upload.pick")) ?: return@OutlinedButton
                        file = picked
                        error = null
                    },
                ) {
                    Text(file?.let { "${it.name} (${it.length() / (1024 * 1024)} MB)" } ?: tr("library.upload.pick"))
                }
                progress?.let { p ->
                    LinearProgressIndicator(progress = { p / 100f }, modifier = Modifier.fillMaxWidth())
                    Text(
                        tr("library.upload.uploading", p),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                message?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
                Text(
                    tr("library.upload.note"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = progress == null && file != null && platformId != null,
                onClick = {
                    val picked = file ?: return@TextButton
                    val pid = platformId ?: return@TextButton
                    scope.launch {
                        error = null
                        message = null
                        progress = 0
                        val result = state.uploadGameFile(picked, pid) { p ->
                            scope.launch(Dispatchers.Main.immediate) { progress = p }
                        }
                        progress = null
                        result.onSuccess { message = tr("library.upload.ok", picked.name) }
                            .onFailure { error = tr("library.upload.error", it.message ?: "") }
                    }
                },
            ) { Text(tr("library.upload.start")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = progress == null) { Text(tr("common.cancel")) }
        },
    )
}
