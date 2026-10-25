package es.davidrg.rommsync.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import es.davidrg.rommsync.core.i18n.tr
import es.davidrg.rommsync.core.remote.NetworkModule
import es.davidrg.rommsync.core.upload.RomUploader
import es.davidrg.rommsync.data.storage.SafFolder
import es.davidrg.rommsync.domain.model.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Subida de un juego al servidor: plataforma destino + fichero suelto o
 * CARPETA (multi-fichero real: el primer fichero crea la ROM, el resto se sube
 * dentro y RomM la deja como ROM-carpeta).
 */
@Composable
fun UploadGameDialog(
    platforms: List<Platform>,
    serverUrl: String,
    apiKey: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var platformId by remember { mutableStateOf(platforms.firstOrNull()?.id) }
    var singleFile by remember { mutableStateOf<File?>(null) }
    var folderFiles by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var folderName by remember { mutableStateOf("") }
    var selection by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf<Int?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val busy = progress != null

    // Copias temporales en cache: se borran al cerrar el diálogo y al acabar.
    val tempFiles = remember { mutableStateListOf<File>() }
    fun trackTemp(f: File) { tempFiles.add(f) }
    fun discardTemp(f: File?) {
        if (f != null) {
            runCatching { f.delete() }
            tempFiles.remove(f)
        }
    }
    fun cleanupTemps() {
        tempFiles.forEach { runCatching { it.delete() } }
        tempFiles.clear()
    }
    DisposableEffect(Unit) {
        onDispose { cleanupTemps() }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val name = displayName(context, uri) ?: "rom_${System.currentTimeMillis()}"
            val tmp = File(context.cacheDir, name)
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tmp.outputStream().use { input.copyTo(it) }
                    } ?: error("no stream")
                }.isSuccess
            }
            if (ok) {
                discardTemp(singleFile)
                trackTemp(tmp)
                singleFile = tmp
                folderFiles = emptyList()
                folderName = ""
                selection = "${name} (${tmp.length() / (1024 * 1024)} MB)"
                error = null
            } else {
                tmp.delete()
                error = tr("library.upload.error", "read")
            }
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val docs = withContext(Dispatchers.IO) {
                SafFolder.listFiles(uri, context).filter { it.isFile && it.length() > 0 }
            }
            if (docs.isEmpty()) {
                error = tr("library.upload.error", "empty folder")
                return@launch
            }
            discardTemp(singleFile)
            singleFile = null
            folderFiles = docs
            folderName = SafFolder.folderName(uri, context)
            val totalMb = docs.sumOf { it.length() } / (1024 * 1024)
            selection = tr("library.upload.folder_summary", docs.size, totalMb)
            error = null
        }
    }

    suspend fun uploadFolder(uploader: RomUploader, pid: Int) {
        val entries = folderFiles
        val total = entries.sumOf { it.length() }.coerceAtLeast(1)
        var done = 0L

        val first = entries.first()
        val firstName = first.name ?: error("name")
        var romId = uploader.findRomId(pid, firstName)
        if (romId == null) {
            val tmp = SafFolder.copyToCache(context, first, context.cacheDir) ?: error("read")
            trackTemp(tmp)
            uploader.upload(tmp, pid) { p ->
                scope.launch(Dispatchers.Main.immediate) {
                    progress = ((done + first.length() * p / 100) * 100 / total).toInt()
                }
            }.getOrThrow()
            discardTemp(tmp)
            done += first.length()
            romId = uploader.awaitRomId(pid, firstName) ?: error(tr("library.upload.timeout"))
        } else {
            done += first.length()
        }

        for (doc in entries.drop(1)) {
            val tmp = SafFolder.copyToCache(context, doc, context.cacheDir) ?: error("read")
            trackTemp(tmp)
            uploader.uploadIntoRom(tmp, romId, pid) { p ->
                scope.launch(Dispatchers.Main.immediate) {
                    progress = ((done + doc.length() * p / 100) * 100 / total).toInt()
                }
            }.getOrThrow()
            discardTemp(tmp)
            done += doc.length()
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
        if (serverUrl.isBlank() || apiKey.isBlank()) {
            error = tr("common.server_not_configured")
            return
        }
        scope.launch {
            error = null
            message = null
            progress = 0
            val uploader = RomUploader(NetworkModule.createApiService(serverUrl, apiKey))
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
                cleanupTemps()
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
                            label = { Text(platform.name, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
                OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !busy) {
                    Text(tr("library.upload.pick"))
                }
                OutlinedButton(onClick = { folderPicker.launch(null) }, enabled = !busy) {
                    Text(tr("library.upload.folder"))
                }
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
