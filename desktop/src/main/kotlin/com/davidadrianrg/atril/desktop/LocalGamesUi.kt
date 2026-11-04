package com.davidadrianrg.atril.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.davidadrianrg.atril.core.i18n.tr
import com.davidadrianrg.atril.core.localgames.LocalGame
import com.davidadrianrg.atril.core.localgames.LocalGameNames
import com.davidadrianrg.atril.core.localgames.ScrapeStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** Selector de archivo nativo (AWT). Devuelve la ruta absoluta o null si se cancela. */
internal suspend fun pickLocalGameFile(title: String = tr("local.add.title")): String? = withContext(Dispatchers.IO) {
    val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
    dialog.isVisible = true
    val name = dialog.file
    val dir = dialog.directory
    if (name == null || dir == null) null else File(dir, name).absolutePath
}

/** Alta de un juego local: título editable + plataforma, tras elegir el fichero. */
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.widthIn(min = 360.dp)) {
                Text(
                    tr("local.path", path),
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
                        Text(platforms.firstOrNull { it.first == slug }?.second ?: tr("local.add.platform_hint"))
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
 * Detalle de un juego local en escritorio. Deja claro que NO es de RomM y ofrece
 * lo que tiene sentido: jugar, renombrar, buscar/elegir carátula y quitarlo.
 */
@Composable
internal fun LocalGameDetailPanel(game: LocalGame, state: DesktopAppState, onPlay: () -> Unit = { state.launchLocalGame(game) }) {
    val scope = rememberCoroutineScope()
    var title by remember(game.id) { mutableStateOf(game.title) }
    var confirmRemove by remember { mutableStateOf(false) }
    val exists = remember(game.path) { File(game.path).isFile }
    val cover = game.coverPath?.let { produceCover("file://$it") }

    Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(Modifier.width(280.dp)) {
            TextButton(onClick = { state.closeGame() }) { Text(tr("common.back")) }
            Spacer(Modifier.height(8.dp))
            if (cover != null) {
                Image(cover, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f))
            } else {
                Text(
                    game.title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).padding(16.dp),
                )
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(tr("local.badge"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(tr("local.add.name")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    if (title.isNotBlank() && title.trim() != game.title) {
                        TextButton(onClick = { scope.launch { state.localGames.rename(game.id, title) } }) { Text(tr("local.save")) }
                    }
                },
            )
            Text(tr("local.source_note"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(tr("local.path", game.path), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!exists) {
                Text(tr("local.file_missing"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Button(onClick = onPlay, enabled = exists) { Text(tr("local.play")) }

            Text(
                when (game.scrapeStatus) {
                    ScrapeStatus.DONE -> tr("local.scrape.status.done", game.scrapeSource ?: "")
                    ScrapeStatus.NOT_FOUND -> tr("local.scrape.status.not_found")
                    ScrapeStatus.FAILED -> tr("local.scrape.status.failed")
                    ScrapeStatus.NONE -> tr("local.scrape.status.none")
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { state.scrapeLocalGame(game.id) }) { Text(tr("local.scrape.retry")) }
                OutlinedButton(onClick = {
                    scope.launch {
                        pickLocalGameFile(tr("local.cover.choose"))?.let { f ->
                            runCatching { File(f).inputStream() }.getOrNull()?.let { state.localGames.setManualCover(game.id, it) }
                        }
                    }
                }) { Text(tr("local.cover.choose")) }
            }
            TextButton(onClick = { confirmRemove = true }) {
                Text(tr("local.remove"), color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(tr("local.remove")) },
            text = { Text(tr("local.remove.confirm", game.title)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    state.closeGame()
                    scope.launch { state.localGames.remove(game.id) }
                }) { Text(tr("common.delete"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(tr("common.cancel")) } },
        )
    }
}
