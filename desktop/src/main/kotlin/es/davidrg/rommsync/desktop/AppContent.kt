package es.davidrg.rommsync.desktop

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import es.davidrg.rommsync.core.remote.dto.RomDto

@Composable
fun AppContent(
    serverUrl: String, onServerUrl: (String) -> Unit,
    apiKey: String, onApiKey: (String) -> Unit,
    romsRoot: String, onRomsRoot: (String) -> Unit,
    roms: List<RomDto>,
    loading: Boolean,
    status: String?,
    onConnect: () -> Unit,
    onSync: () -> Unit,
    onDownload: (RomDto) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Conexión", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(serverUrl, onServerUrl, label = { Text("URL del servidor") })
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(apiKey, onApiKey, label = { Text("API Key") })
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(romsRoot, onRomsRoot, label = { Text("Carpeta raíz de ROMs") })
        Spacer(Modifier.height(8.dp))
        Row {
            Button(onClick = onConnect) { Text("Guardar y conectar") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onSync, enabled = !loading) { Text("Sincronizar saves") }
        }
        status?.let { s ->
            Spacer(Modifier.height(8.dp))
            Text(s, color = if (s.startsWith("Error")) MaterialTheme.colorScheme.error else Color.Unspecified)
        }
        if (loading) { Spacer(Modifier.height(8.dp)); CircularProgressIndicator() }
        Spacer(Modifier.height(12.dp))
        if (roms.isEmpty()) {
            Text("Sin ROMs cargados todavía.")
        } else {
            Text("${roms.size} ROMs", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            LazyColumn {
                items(roms) { rom ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(rom.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    rom.platformSlug ?: "?",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray,
                                )
                            }
                            Button(onClick = { onDownload(rom) }) { Text("Descargar") }
                        }
                    }
                }
            }
        }
    }
}