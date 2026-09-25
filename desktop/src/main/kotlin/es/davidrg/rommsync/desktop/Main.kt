package es.davidrg.rommsync.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import es.davidrg.rommsync.core.remote.NetworkModule
import java.io.File
import es.davidrg.rommsync.core.remote.dto.RomDto
import kotlinx.coroutines.launch
import kotlin.system.exitProcess

fun main() = application {
    val scope = rememberCoroutineScope()
    var serverUrl by remember { mutableStateOf(DesktopConfig.serverUrl) }
    var apiKey by remember { mutableStateOf(DesktopConfig.apiKey) }
    var romsRoot by remember { mutableStateOf(DesktopConfig.romsRoot) }
    var roms by remember { mutableStateOf<List<RomDto>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    Window(onCloseRequest = ::exitApplication, title = "RomM Sync") {
        MaterialTheme(darkColorScheme()) {
            Surface(modifier = Modifier.fillMaxSize()) {
                AppContent(
                    serverUrl = serverUrl, onServerUrl = { serverUrl = it },
                    apiKey = apiKey, onApiKey = { apiKey = it },
                    romsRoot = romsRoot, onRomsRoot = { romsRoot = it },
                    roms = roms, loading = loading, status = status,
                    onConnect = {
                        DesktopConfig.serverUrl = serverUrl
                        DesktopConfig.apiKey = apiKey
                        DesktopConfig.romsRoot = romsRoot
                        scope.launch {
                            loading = true
                            status = null
                            try {
                                val api = NetworkModule.createApiService(serverUrl, apiKey)
                                val resp = api.getRoms(mapOf("limit" to "500"))
                                roms = resp.items
                                status = "${roms.size} ROMs cargados"
                            } catch (e: Exception) {
                                status = "Error: ${e.message}"
                            } finally {
                                loading = false
                            }
                        }
                    },
                    onSync = {
                        scope.launch {
                            status = "Sincronizando saves…"
                            try {
                                val coordinator = DesktopSyncCoordinator(
                                    config = DesktopConfig,
                                    library = DesktopLibrary(File(DesktopConfig.configDir, "library.properties")),
                                    cacheDir = DesktopConfig.cacheDir,
                                )
                                val result = coordinator.runSync()
                                status = buildString {
                                    if (result.error != null) append(result.error)
                                    else append(result.message ?: "Sync completado")
                                    if (result.uploaded > 0) append(" · ${result.uploaded} subidos")
                                    if (result.downloaded > 0) append(" · ${result.downloaded} descargados")
                                    if (result.conflicts > 0) append(" · ${result.conflicts} conflictos")
                                }
                            } catch (e: Exception) {
                                status = "Error: ${e.message}"
                            }
                        }
                    },
                    onDownload = { rom ->
                        scope.launch {
                            status = "Descargando ${rom.name}…"
                            try {
                                val api = NetworkModule.createApiService(serverUrl, apiKey)
                                val handler = DownloadEngine(
                                    api = api,
                                    romsRoot = File(romsRoot),
                                    cacheDir = DesktopConfig.cacheDir,
                                    library = DesktopLibrary(File(DesktopConfig.configDir, "library.properties")),
                                )
                                val result = handler.download(rom)
                                status = result
                            } catch (e: Exception) {
                                status = "Error: ${e.message}"
                            }
                        }
                    },
                )
            }
        }
    }
}
