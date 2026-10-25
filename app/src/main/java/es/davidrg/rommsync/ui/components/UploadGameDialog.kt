package es.davidrg.rommsync.ui.components

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import es.davidrg.rommsync.core.i18n.tr
import es.davidrg.rommsync.core.remote.NetworkModule
import es.davidrg.rommsync.core.upload.RomUploader
import es.davidrg.rommsync.data.storage.SafFolderZipper
import es.davidrg.rommsync.domain.model.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Subida de un juego (ROM) al servidor: elegir plataforma destino + fichero,
 * subida chunked con progreso. El juego aparece en la biblioteca tras el
 * siguiente escaneo de RomM.
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
    var file by remember { mutableStateOf<File?>(null) }
    var fileName by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf<Int?>(null) }
    var zipProgress by remember { mutableStateOf<Int?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val busy = progress != null || zipProgress != null

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
                file = tmp
                fileName = "${name} (${tmp.length() / (1024 * 1024)} MB)"
                error = null
            } else {
                error = tr("library.upload.error", "read")
            }
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            zipProgress = 0
            val folderName = DocumentFile.fromTreeUri(context, uri)?.name ?: "upload"
            val zip = File(context.cacheDir, "$folderName.zip")
            val ok = withContext(Dispatchers.IO) {
                SafFolderZipper.zip(uri, zip, context) { p ->
                    scope.launch(Dispatchers.Main.immediate) { zipProgress = p }
                }
            }
            zipProgress = null
            if (ok && zip.length() > 0) {
                file = zip
                fileName = "${zip.name} (${zip.length() / (1024 * 1024)} MB)"
                error = null
            } else {
                error = tr("library.upload.error", "zip")
                zip.delete()
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
                    Text(if (fileName.isBlank()) tr("library.upload.pick") else fileName)
                }
                OutlinedButton(onClick = { folderPicker.launch(null) }, enabled = !busy) {
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
                    if (serverUrl.isBlank() || apiKey.isBlank()) {
                        error = tr("common.server_not_configured")
                        return@TextButton
                    }
                    scope.launch {
                        error = null
                        message = null
                        progress = 0
                        val api = NetworkModule.createApiService(serverUrl, apiKey)
                        val uploader = RomUploader(api)
                        val result = uploader.upload(picked, pid) { p ->
                            scope.launch(Dispatchers.Main.immediate) { progress = p }
                        }
                        progress = null
                        result.onSuccess {
                            val scanned = uploader.triggerQuickScan(pid)
                            message = if (scanned) {
                                tr("library.upload.ok_scan", picked.name)
                            } else {
                                tr("library.upload.ok", picked.name)
                            }
                            picked.delete()
                            file = null
                            fileName = ""
                        }.onFailure {
                            error = tr("library.upload.error", it.message ?: "")
                        }
                    }
                },
            ) { Text(tr("library.upload.start")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(tr("common.cancel")) }
        },
    )
}
