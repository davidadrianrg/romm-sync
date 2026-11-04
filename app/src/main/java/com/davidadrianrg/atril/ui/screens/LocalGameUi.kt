package com.davidadrianrg.atril.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.davidadrianrg.atril.core.i18n.tr
import com.davidadrianrg.atril.core.localgames.LocalGameNames
import com.davidadrianrg.atril.core.localgames.ScrapeStatus
import com.davidadrianrg.atril.ui.viewmodel.ShelfItem

/** Alta de un juego local: título (editable) + plataforma tras elegir el fichero. */
@Composable
internal fun AddLocalGameDialog(
    path: String,
    platforms: List<Pair<String, String>>,
    initialSlug: String?,
    onConfirm: (title: String, platformSlug: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember(path) { mutableStateOf(LocalGameNames.titleFromFileName(path)) }
    var slug by remember(path) { mutableStateOf(initialSlug ?: platforms.firstOrNull()?.first) }
    var open by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("local.add.title")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    tr("local.path", path.substringAfterLast('/')),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(tr("local.add.name")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(tr("local.add.platform"), style = MaterialTheme.typography.labelLarge)
                Column {
                    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            platforms.firstOrNull { it.first == slug }?.second ?: tr("local.add.platform_hint"),
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        platforms.forEach { (s, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { slug = s; open = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = slug != null, onClick = { onConfirm(title, slug!!) }) { Text(tr("local.add.confirm")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("common.cancel")) } },
    )
}

/**
 * Detalle de un juego local. Deja claro que NO es de RomM, y ofrece lo que sí
 * tiene sentido: jugar, renombrar, buscar/elegir carátula y quitarlo de Play.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocalGameSheet(
    item: ShelfItem,
    scraping: Boolean,
    onPlay: () -> Unit,
    onRename: (String) -> Unit,
    onRetryScrape: () -> Unit,
    onPickCover: () -> Unit,
    onRemove: () -> Unit,
    onClose: () -> Unit,
) {
    val game = item.localGame ?: return
    var title by remember(game.id) { mutableStateOf(game.title) }
    var confirmRemove by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        sheetMaxWidth = 900.dp,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(item.platformLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(tr("local.add.name")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    if (title.trim() != game.title && title.isNotBlank()) {
                        TextButton(onClick = { onRename(title) }) { Text(tr("local.save")) }
                    }
                },
            )
            Text(tr("local.source_note"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(tr("local.path", game.path), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Button(onClick = onPlay, enabled = item.launchable, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(tr("local.play"), fontWeight = FontWeight.Bold)
            }

            Text(
                when {
                    scraping -> tr("local.scrape.running")
                    game.scrapeStatus == ScrapeStatus.DONE -> tr("local.scrape.status.done", game.scrapeSource ?: "")
                    game.scrapeStatus == ScrapeStatus.NOT_FOUND -> tr("local.scrape.status.not_found")
                    game.scrapeStatus == ScrapeStatus.FAILED -> tr("local.scrape.status.failed")
                    else -> tr("local.scrape.status.none")
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onRetryScrape, enabled = !scraping, modifier = Modifier.weight(1f)) {
                    Text(tr("local.scrape.retry"))
                }
                OutlinedButton(onClick = onPickCover, modifier = Modifier.weight(1f)) { Text(tr("local.cover.choose")) }
            }
            TextButton(onClick = { confirmRemove = true }) {
                Text(tr("local.remove"), color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(tr("local.remove")) },
            text = { Text(tr("local.remove.confirm", game.title)) },
            confirmButton = {
                TextButton(onClick = { confirmRemove = false; onRemove() }) {
                    Text(tr("common.delete"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(tr("common.cancel")) } },
        )
    }
}
