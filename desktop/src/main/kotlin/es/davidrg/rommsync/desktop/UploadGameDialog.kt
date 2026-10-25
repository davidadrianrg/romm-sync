package es.davidrg.rommsync.desktop

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import es.davidrg.rommsync.core.upload.FolderZipper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser

/**
 * Subida de un juego (ROM) al servidor desde desktop: fichero suelto o carpeta
 * (se empaqueta en zip → ROM multi-fichero en RomM) + escaneo rápido al acabar.
 */
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
    var zipProgress by remember { mutableStateOf<Int?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val busy = progress != null || zipProgress != null

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
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
                    enabled = !busy,
                    onClick = {
                        val picked = pickFile(tr("library.upload.pick")) ?: return@OutlinedButton
                        file = picked
                        error = null
                    },
                ) {
                    Text(file?.let { "${it.name} (${it.length() / (1024 * 1024)} MB)" } ?: tr("library.upload.pick"))
                }
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        val folder = pickDirectory(tr("library.upload.folder")) ?: return@OutlinedButton
                        scope.launch {
                            zipProgress = 0
                            val zip = File(
                                System.getProperty("java.io.tmpdir"),
                                "${folder.name}-${System.currentTimeMillis()}.zip",
                            )
                            val ok = withContext(Dispatchers.IO) {
                                FolderZipper.zip(folder, zip) { p ->
                                    scope.launch(Dispatchers.Main.immediate) { zipProgress = p }
                                }
                            }
                            zipProgress = null
                            if (ok && zip.length() > 0) {
                                file = zip
                                error = null
                            } else {
                                error = tr("library.upload.error", "zip")
                                zip.delete()
                            }
                        }
                    },
                ) {
                    Text(tr("library.upload.folder"))
                }
                zipProgress?.let { p ->
                    LinearProgressIndicator(progress = { p / 100f }, modifier = Modifier.fillMaxWidth())
                    Text(
                        tr("library.upload.zipping", p),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
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
                enabled = !busy && file != null && platformId != null,
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
                        result.onSuccess {
                            val scanned = state.triggerQuickScan(pid)
                            message = if (scanned) {
                                tr("library.upload.ok_scan", picked.name)
                            } else {
                                tr("library.upload.ok", picked.name)
                            }
                            picked.delete()
                            file = null
                        }.onFailure { error = tr("library.upload.error", it.message ?: "") }
                    }
                },
            ) { Text(tr("library.upload.start")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(tr("common.cancel")) }
        },
    )
}

/** Selector de carpeta nativo (JFileChooser, solo directorios). */
private fun pickDirectory(title: String): File? {
    val chooser = JFileChooser()
    chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    chooser.dialogTitle = title
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}
