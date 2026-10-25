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
import es.davidrg.rommsync.core.i18n.tr
import es.davidrg.rommsync.core.remote.NetworkModule
import es.davidrg.rommsync.core.upload.RomUploader
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
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

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
                            label = { Text(platform.name, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
                OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = progress == null) {
                    Text(if (fileName.isBlank()) tr("library.upload.pick") else fileName)
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
                    if (serverUrl.isBlank() || apiKey.isBlank()) {
                        error = tr("common.server_not_configured")
                        return@TextButton
                    }
                    scope.launch {
                        error = null
                        message = null
                        progress = 0
                        val api = NetworkModule.createApiService(serverUrl, apiKey)
                        val result = RomUploader(api).upload(picked, pid) { p ->
                            scope.launch(Dispatchers.Main.immediate) { progress = p }
                        }
                        progress = null
                        result.onSuccess {
                            message = tr("library.upload.ok", picked.name)
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
            TextButton(onClick = onDismiss, enabled = progress == null) { Text(tr("common.cancel")) }
        },
    )
}
