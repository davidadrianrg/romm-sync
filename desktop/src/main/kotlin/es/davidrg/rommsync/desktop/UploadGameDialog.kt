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
import es.davidrg.rommsync.core.upload.RomUploader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import javax.swing.JFileChooser

/**
 * Subida de un juego desde desktop: fichero suelto o carpeta (multi-fichero
 * real: el primer fichero crea la ROM y el resto se sube dentro). Nunca toca
 * los ficheros originales del usuario.
 */
@Composable
fun UploadGameDialogDesktop(
    state: DesktopAppState,
    platforms: List<PlatformDto>,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var platformId by remember { mutableStateOf(platforms.firstOrNull()?.id) }
    var singleFile by remember { mutableStateOf<File?>(null) }
    var folderFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var folderName by remember { mutableStateOf("") }
    var selection by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf<Int?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val busy = progress != null

    suspend fun uploadFolder(uploader: RomUploader, pid: Int) {
        val entries = folderFiles
        val total = entries.sumOf { it.length() }.coerceAtLeast(1)
        var done = 0L

        val first = entries.first()
        var romId = uploader.findRomId(pid, first.name)
        if (romId == null) {
            uploader.upload(first, pid) { p ->
                scope.launch(Dispatchers.Main.immediate) {
                    progress = ((done + first.length() * p / 100) * 100 / total).toInt()
                }
            }.getOrThrow()
            done += first.length()
            romId = uploader.awaitRomId(pid, first.name) ?: error(tr("library.upload.timeout"))
        } else {
            done += first.length()
        }

        for (file in entries.drop(1)) {
            uploader.uploadIntoRom(file, romId, pid) { p ->
                scope.launch(Dispatchers.Main.immediate) {
                    progress = ((done + file.length() * p / 100) * 100 / total).toInt()
                }
            }.getOrThrow()
            done += file.length()
        }

        progress = 100
        val scanned = uploader.triggerQuickScan(pid)
        message = if (scanned) {
            tr("library.upload.ok_scan", folderName)
        } else {
            tr("library.upload.ok", folderName)
        }
    }

    fun runUpload() {
        val pid = platformId ?: return
        val uploader: RomUploader = state.romUploader() ?: run {
            error = tr("common.server_not_configured")
            return
        }
        scope.launch {
            error = null
            message = null
            progress = 0
            try {
                if (folderFiles.isNotEmpty()) {
                    uploadFolder(uploader, pid)
                } else {
                    val picked = singleFile ?: return@launch
                    uploader.upload(picked, pid) { p ->
                        scope.launch(Dispatchers.Main.immediate) { progress = p }
                    }.getOrThrow()
                    val scanned = uploader.triggerQuickScan(pid)
                    message = if (scanned) {
                        tr("library.upload.ok_scan", picked.name)
                    } else {
                        tr("library.upload.ok", picked.name)
                    }
                }
                selection = ""
                singleFile = null
                folderFiles = emptyList()
                folderName = ""
            } catch (e: Exception) {
                error = tr("library.upload.error", e.message ?: "")
            } finally {
                progress = null
            }
        }
    }

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
                        singleFile = picked
                        folderFiles = emptyList()
                        folderName = ""
                        selection = "${picked.name} (${picked.length() / (1024 * 1024)} MB)"
                        error = null
                    },
                ) { Text(tr("library.upload.pick")) }
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        val folder = pickDirectory(tr("library.upload.folder")) ?: return@OutlinedButton
                        val files = folder.walkTopDown()
                            .filter { it.isFile && it.length() > 0 }
                            .sortedBy { it.name.lowercase() }
                            .toList()
                        if (files.isEmpty()) {
                            error = tr("library.upload.error", "empty folder")
                            return@OutlinedButton
                        }
                        singleFile = null
                        folderFiles = files
                        folderName = folder.name
                        selection = tr(
                            "library.upload.folder_summary",
                            files.size,
                            files.sumOf { it.length() } / (1024 * 1024),
                        )
                        error = null
                    },
                ) { Text(tr("library.upload.folder")) }
                if (selection.isNotBlank()) {
                    Text(
                        selection,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                progress?.let { p ->
                    LinearProgressIndicator(progress = { p / 100f }, modifier = Modifier.fillMaxWidth())
                    Text(
                        if (p >= 100) tr("library.upload.processing") else tr("library.upload.uploading", p),
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
                    tr("library.upload.folder_hint"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && (singleFile != null || folderFiles.isNotEmpty()) && platformId != null,
                onClick = { runUpload() },
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
