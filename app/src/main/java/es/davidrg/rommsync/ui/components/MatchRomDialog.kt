package es.davidrg.rommsync.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.davidrg.rommsync.core.i18n.tr
import es.davidrg.rommsync.core.remote.NetworkModule
import es.davidrg.rommsync.core.remote.RomMatcher
import es.davidrg.rommsync.core.remote.dto.SearchRomDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * "Buscar coincidencia": busca metadatos en los proveedores de RomM por nombre
 * o por ID de IGDB y aplica el resultado a este juego (PUT /api/roms/{id}).
 * Sirve cuando el escaneo no reconoció el juego.
 */
@Composable
fun MatchRomDialog(
    romId: Int,
    initialName: String,
    serverUrl: String,
    apiKey: String,
    onDismiss: () -> Unit,
    onApplied: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var term by remember { mutableStateOf(initialName) }
    var mode by remember { mutableStateOf(0) }
    var results by remember { mutableStateOf<List<SearchRomDto>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var applying by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun search() {
        if (term.isBlank()) return
        scope.launch {
            searching = true
            error = null
            message = null
            progressText = null
            val matcher = RomMatcher(NetworkModule.createApiService(serverUrl, apiKey))
            runCatching {
                when (mode) {
                    1 -> matcher.search(romId, term.trim(), byId = true)
                    2 -> matcher.searchAllPlatforms(term.trim()) { done, total ->
                        scope.launch(Dispatchers.Main.immediate) {
                            progressText = tr("library.match.searching_platforms", done, total)
                        }
                    }
                    else -> matcher.search(romId, term.trim(), byId = false)
                }
            }.onSuccess { results = it }
                .onFailure { error = tr("library.match.error", it.message ?: "") }
            progressText = null
            searching = false
        }
    }

    fun apply(match: SearchRomDto) {
        scope.launch {
            applying = true
            error = null
            runCatching {
                val api = NetworkModule.createApiService(serverUrl, apiKey)
                fun body(value: String?) = value?.toRequestBody("text/plain".toMediaType())
                api.updateRomMetadata(
                    romId = romId,
                    name = (match.name ?: error("name")).toRequestBody("text/plain".toMediaType()),
                    summary = body(match.summary),
                    slug = body(match.slug),
                    igdbId = body(match.igdbId?.toString()),
                    ssId = body(match.ssId?.toString()),
                    mobyId = body(match.mobyId?.toString()),
                )
            }.onSuccess {
                message = tr("library.match.applied")
                onApplied()
            }.onFailure { error = tr("library.match.error", it.message ?: "") }
            applying = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!applying) onDismiss() },
        title = { Text(tr("library.match.title")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = mode == 0,
                        onClick = { mode = 0 },
                        label = { Text(tr("library.match.by_name"), style = MaterialTheme.typography.labelSmall) },
                    )
                    FilterChip(
                        selected = mode == 1,
                        onClick = { mode = 1 },
                        label = { Text(tr("library.match.by_id"), style = MaterialTheme.typography.labelSmall) },
                    )
                    FilterChip(
                        selected = mode == 2,
                        onClick = { mode = 2 },
                        label = { Text(tr("library.match.no_filter"), style = MaterialTheme.typography.labelSmall) },
                    )
                }
                OutlinedTextField(
                    value = term,
                    onValueChange = { term = it },
                    label = { Text(tr("library.match.placeholder")) },
                    singleLine = true,
                    enabled = !searching && !applying,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (searching || applying) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.padding(4.dp), strokeWidth = 2.dp)
                        progressText?.let {
                            Text(
                                "  $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                message?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
                if (!searching && results.isEmpty() && message == null && error == null) {
                    Text(
                        tr("library.match.hint"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    results.forEach { match ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !applying) { apply(match) }
                                .padding(vertical = 6.dp),
                        ) {
                            Text(
                                match.name ?: "?",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                listOfNotNull(
                                    match.igdbId?.let { "IGDB $it" },
                                    match.ssId?.let { "SS $it" },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { search() }, enabled = !searching && !applying && term.isNotBlank()) {
                Text(tr("library.match.search"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !applying) { Text(tr("common.close")) }
        },
    )
}
