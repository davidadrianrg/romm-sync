package es.davidrg.rommsync.ui.components

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import es.davidrg.rommsync.core.i18n.tr
import es.davidrg.rommsync.core.remote.NetworkModule
import es.davidrg.rommsync.core.remote.dto.SaveDto
import es.davidrg.rommsync.core.remote.dto.StateDto
import es.davidrg.rommsync.core.upload.UploadParts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Guardados y save states que YA están en el servidor para este juego, con su
 * fecha, más subida manual de un fichero (para juegos/plataformas que no se
 * pueden automatizar).
 */
@Composable
fun ServerAssetsSection(
    romId: Int,
    serverUrl: String,
    apiKey: String,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saves by remember { mutableStateOf<List<SaveDto>>(emptyList()) }
    var states by remember { mutableStateOf<List<StateDto>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun reload() {
        if (serverUrl.isBlank() || apiKey.isBlank()) return
        scope.launch {
            loading = true
            runCatching {
                val api = NetworkModule.createApiService(serverUrl, apiKey)
                saves = api.getSaves(romId)
                states = api.getStates(romId)
            }
            loading = false
        }
    }

    LaunchedEffect(romId, serverUrl, apiKey) { reload() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            uploading = true
            val result = runCatching {
                val name = displayName(context, uri) ?: "save_${System.currentTimeMillis()}.sav"
                val tmp = File(context.cacheDir, name)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tmp.outputStream().use { input.copyTo(it) }
                }
                val api = NetworkModule.createApiService(serverUrl, apiKey)
                api.uploadSave(
                    file = UploadParts.saveFile(tmp),
                    romId = romId,
                    emulator = null,
                    deviceId = null,
                    overwrite = true,
                )
                tmp.delete()
            }
            uploading = false
            message = result.fold(
                { tr("saves.server.upload_ok") },
                { tr("saves.server.upload_error", it.message ?: "") },
            )
            if (result.isSuccess) reload()
        }
    }

    Spacer(modifier = Modifier.size(20.dp))
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                tr("saves.server.title"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (loading || uploading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = { reload() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = tr("saves.server.refresh"))
                }
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
            AssetRow(
                label = tr("saves.server.save"),
                fileName = save.fileName,
                meta = listOfNotNull(save.emulator, formatServerDate(save.updatedAt)).joinToString(" · "),
            )
        }
        states.forEach { state ->
            AssetRow(
                label = tr("saves.server.state"),
                fileName = state.fileName,
                meta = listOfNotNull(state.emulator, formatServerDate(state.updatedAt)).joinToString(" · "),
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        FilledTonalButton(
            onClick = { picker.launch(arrayOf("*/*")) },
            enabled = !uploading && serverUrl.isNotBlank() && apiKey.isNotBlank(),
        ) {
            Text(tr("saves.server.manual"))
        }
        Text(
            tr("saves.server.manual_hint"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (uploading) {
            Text(
                tr("saves.server.uploading"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun AssetRow(label: String, fileName: String, meta: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(fileName, style = MaterialTheme.typography.bodySmall)
            Text(
                meta.ifBlank { label },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** "2026-09-25T16:24:05" → "2026-09-25 16:24". */
internal fun formatServerDate(raw: String?): String {
    val clean = raw?.replace('T', ' ')?.trim() ?: return ""
    return if (clean.length >= 16) clean.take(16) else clean
}

internal fun displayName(context: Context, uri: Uri): String? =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
