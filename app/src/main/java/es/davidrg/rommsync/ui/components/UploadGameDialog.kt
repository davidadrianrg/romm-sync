package es.davidrg.rommsync.ui.components

import android.content.Intent
import android.net.Uri
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import es.davidrg.rommsync.core.i18n.tr
import es.davidrg.rommsync.data.storage.SafFolder
import es.davidrg.rommsync.domain.model.Platform
import es.davidrg.rommsync.upload.UploadBus
import es.davidrg.rommsync.upload.UploadService

/**
 * Subida de un juego al servidor: plataforma destino + fichero suelto o
 * CARPETA (multi-fichero real: el primer fichero crea la ROM, el resto se sube
 * dentro y RomM la deja como ROM-carpeta).
 *
 * El trabajo lo hace [UploadService] en primer plano: el diálogo solo observa
 * el estado, así que cerrar la app o apagar la pantalla no cancela la subida.
 * Los ficheros se leen directamente del SAF, sin copiarlos al cache.
 */
@Composable
fun UploadGameDialog(
    platforms: List<Platform>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val uploadState by UploadBus.state.collectAsState()

    var platformId by remember { mutableStateOf(platforms.firstOrNull()?.id) }
    var singleUri by remember { mutableStateOf<Uri?>(null) }
    var singleName by remember { mutableStateOf("") }
    var singleSize by remember { mutableStateOf(0L) }
    var treeUri by remember { mutableStateOf<Uri?>(null) }
    var folderName by remember { mutableStateOf("") }
    var folderFiles by remember { mutableStateOf(0) }
    var folderBytes by remember { mutableStateOf(0L) }
    var selection by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { UploadBus.consumeResult() }

    val busy = uploadState.running
    val error = localError ?: uploadState.error
    val message = uploadState.message

    fun persistRead(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    fun clearSelection() {
        singleUri = null
        singleName = ""
        singleSize = 0L
        treeUri = null
        folderName = ""
        folderFiles = 0
        folderBytes = 0L
        selection = ""
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        persistRead(uri)
        val document = DocumentFile.fromSingleUri(context, uri)
        val name = document?.name ?: "rom_${System.currentTimeMillis()}"
        val size = document?.length() ?: 0L
        clearSelection()
        singleUri = uri
        singleName = name
        singleSize = size
        selection = "$name (${size / (1024 * 1024)} MB)"
        localError = null
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        persistRead(uri)
        val docs = SafFolder.listFiles(uri, context).filter { it.isFile && it.length() > 0 }
        if (docs.isEmpty()) {
            localError = tr("library.upload.error", "empty folder")
            return@rememberLauncherForActivityResult
        }
        clearSelection()
        treeUri = uri
        folderName = SafFolder.folderName(uri, context)
        folderFiles = docs.size
        folderBytes = docs.sumOf { it.length() }
        selection = tr(
            "library.upload.folder_summary",
            docs.size,
            folderBytes / (1024 * 1024),
        )
        localError = null
    }

    fun runUpload() {
        val pid = platformId ?: return
        localError = null
        if (singleUri == null && treeUri == null) return
        UploadService.start(
            context = context,
            platformId = pid,
            singleUri = singleUri,
            treeUri = treeUri,
            folderName = folderName,
            displayName = if (singleUri != null) singleName else folderName,
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
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
                            enabled = !busy,
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
                uploadState.progress?.let { p ->
                    LinearProgressIndicator(progress = { p / 100f }, modifier = Modifier.fillMaxWidth())
                    Text(
                        if (p >= 100) tr("library.upload.processing") else tr("library.upload.uploading", p),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (busy) {
                    uploadState.phase?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        tr("library.upload.background_hint"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                enabled = !busy && (singleUri != null || treeUri != null) && platformId != null,
                onClick = { runUpload() },
            ) { Text(tr("library.upload.start")) }
        },
        dismissButton = {
            if (busy) {
                TextButton(onClick = { UploadService.cancel(context) }) {
                    Text(tr("common.cancel"))
                }
            } else {
                TextButton(onClick = onDismiss) { Text(tr("common.close")) }
            }
        },
    )
}
