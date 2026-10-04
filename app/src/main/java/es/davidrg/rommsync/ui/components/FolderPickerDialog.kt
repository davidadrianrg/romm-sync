package es.davidrg.rommsync.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.davidrg.rommsync.util.hasAllFilesAccess
import java.io.File

/**
 * Límite superior de navegación. No se permite subir por encima de `/storage`,
 * que es la raíz que agrupa el almacenamiento interno y las tarjetas SD.
 */
private const val NAV_ROOT = "/storage"
private const val DEFAULT_START = "/storage/emulated/0"

/**
 * Explorador de carpetas propio basado en [java.io.File].
 *
 * A diferencia del Storage Access Framework (que en Android 11+ no permite
 * navegar a `Android/data/...`), este diálogo usa acceso directo al sistema de
 * ficheros, disponible gracias a `MANAGE_EXTERNAL_STORAGE`. Así el usuario puede
 * llegar a las carpetas internas de los emuladores donde viven los saves.
 *
 * Si el permiso aún no está concedido se muestra un aviso con botón para
 * pedirlo, y al volver de Ajustes se relista el directorio actual. También se
 * distingue "carpeta ilegible" (`listFiles() == null`) de "carpeta vacía", y
 * se muestran los ficheros (en gris, no navegables) junto a las carpetas.
 *
 * @param initialPath ruta inicial; si no existe, se sube al primer ancestro
 *   existente.
 * @param onDismiss se invoca al cancelar.
 * @param onSelect se invoca con la ruta absoluta de la carpeta elegida.
 */
@Composable
fun FolderPickerDialog(
    initialPath: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val context = LocalContext.current
    var refreshTick by remember { mutableStateOf(0) }
    var hasAccess by remember { mutableStateOf(hasAllFilesAccess()) }
    var currentDir by remember {
        mutableStateOf(existingAncestorOrDefault(initialPath))
    }

    // Al volver de la pantalla de permisos de Ajustes, el callback del launcher
    // reevalúa el permiso y fuerza un relistado del directorio actual.
    val grantLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        hasAccess = hasAllFilesAccess()
        refreshTick++
    }

    val listing = remember(currentDir.path, refreshTick) { currentDir.listFiles() }
    val subDirs = remember(listing) {
        listing?.filter { it.isDirectory }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    }
    val files = remember(listing) {
        listing?.filter { it.isFile }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    }

    val canGoUp = currentDir.path != NAV_ROOT && currentDir.parentFile != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seleccionar carpeta") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    currentDir.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (!hasAccess) {
                    Spacer(modifier = Modifier.size(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                "Sin permiso «Acceso a todos los archivos»: " +
                                    "Android/data puede aparecer vacío o ilegible.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                onClick = {
                                    grantLauncher.launch(
                                        Intent(
                                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                            Uri.parse("package:${context.packageName}"),
                                        ),
                                    )
                                },
                            ) { Text("Conceder") }
                        }
                    }
                }

                Spacer(modifier = Modifier.size(8.dp))
                HorizontalDivider()

                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    if (listing == null) {
                        item {
                            Text(
                                "No se pudo leer esta carpeta. Comprueba el permiso " +
                                    "«Acceso a todos los archivos» en Ajustes.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                    }
                    if (canGoUp) {
                        item {
                            FolderRow(
                                name = "..",
                                icon = { UpIcon() },
                                onClick = {
                                    currentDir.parentFile?.let { parent ->
                                        if (parent.path.startsWith(NAV_ROOT)) {
                                            currentDir = parent
                                        }
                                    }
                                },
                            )
                        }
                    }
                    items(subDirs, key = { "d:${it.path}" }) { dir ->
                        FolderRow(
                            name = dir.name,
                            icon = { FolderIcon() },
                            onClick = { currentDir = dir },
                        )
                    }
                    // Ficheros visibles (no navegables) para poder comprobar el
                    // contenido real de la carpeta, igual que hace ZArchiver.
                    items(files, key = { "f:${it.path}" }) { file ->
                        FileRow(name = file.name)
                    }
                    if (listing != null && subDirs.isEmpty() && files.isEmpty()) {
                        item {
                            Text(
                                "Carpeta vacía",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(currentDir.path) }) {
                Text("Usar esta carpeta")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}

@Composable
private fun FolderRow(
    name: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        icon()
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Fila de fichero: visible pero no navegable (el diálogo selecciona carpetas). */
@Composable
private fun FileRow(name: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Filled.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FolderIcon() {
    Icon(
        Icons.Filled.Folder,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(22.dp),
    )
}

@Composable
private fun UpIcon() {
    Icon(
        Icons.Filled.KeyboardArrowUp,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(22.dp),
    )
}

/**
 * Devuelve la propia ruta si existe como directorio; si no, sube hasta el
 * primer ancestro existente. Como último recurso devuelve el almacenamiento
 * interno principal.
 */
private fun existingAncestorOrDefault(path: String): File {
    var file = File(path)
    while (!(file.exists() && file.isDirectory)) {
        file = file.parentFile ?: return File(DEFAULT_START)
    }
    return file
}
