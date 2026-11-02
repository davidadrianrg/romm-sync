package es.davidrg.rommsync.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import es.davidrg.rommsync.RomMSyncApplication
import es.davidrg.rommsync.core.i18n.tr
import es.davidrg.rommsync.core.launch.LaunchPlatforms
import es.davidrg.rommsync.data.local.ServerConfig
import es.davidrg.rommsync.data.repository.LauncherDisplayMode
import es.davidrg.rommsync.domain.model.Rom
import es.davidrg.rommsync.launch.EmulatorOption
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Controles del launcher para la hoja de detalle (compartidos por Jugar y
 * Biblioteca): Jugar, Carta / Medio físico, estado de la caché y emulador.
 */
internal data class LauncherDetailControls(
    val platformLabel: String,
    val onPlay: (() -> Unit)?,
    val launchable: Boolean,
    val displayMode: LauncherDisplayMode,
    val onDisplayModeChange: (LauncherDisplayMode) -> Unit,
    val hasRemotePhysicalMedia: Boolean,
    val physicalMediaCached: Boolean,
    val physicalMediaDownloading: Boolean,
    val onDownloadPhysicalMedia: () -> Unit,
    val emulatorOptions: List<EmulatorOption>,
    /** Override de la plataforma; null = automático. */
    val selectedEmulatorId: String?,
    val automaticEmulatorName: String?,
    val onEmulatorChange: (String?) -> Unit,
)

/**
 * Construye [LauncherDetailControls] para [rom] leyendo directamente del
 * contenedor (Room + DataStore), así el launcher y la Biblioteca ven siempre
 * el mismo estado.
 */
@Composable
internal fun rememberLauncherDetailControls(
    rom: Rom,
    platformLabel: String,
    platformEmulatorId: String?,
    launchable: Boolean,
    onPlay: (() -> Unit)?,
): LauncherDetailControls {
    val context = LocalContext.current
    val container = (context.applicationContext as RomMSyncApplication).container
    val repo = container.physicalMediaRepository
    val presentation by repo.observe().collectAsState(initial = emptyMap())
    val downloading by repo.downloading.collectAsState()
    val defaultMode by container.settingsRepository.launcherDefaultDisplay.collectAsState(initial = "card")
    val overrides by container.settingsRepository.launchEmulatorOverrides.collectAsState(initial = emptyMap())
    val settings by container.settingsRepository.settings.collectAsState(initial = ServerConfig("", "", "", 2))
    val platformKey = LaunchPlatforms.canonical(rom.platformSlug)
    val override = overrides[platformKey]

    val row = presentation[rom.id]
    val cached = row?.physicalMediaPath?.let { File(it).isFile } == true
    val mode = LauncherDisplayMode.fromId(row?.displayMode)
        ?: LauncherDisplayMode.fromId(defaultMode)
        ?: LauncherDisplayMode.CARD

    val options by produceState(emptyList<EmulatorOption>(), rom.platformSlug) {
        value = withContext(Dispatchers.IO) { container.emulatorLauncher.options(rom.platformSlug) }
    }
    val automaticName by produceState<String?>(null, rom.platformSlug, platformEmulatorId, options) {
        value = withContext(Dispatchers.IO) {
            container.emulatorLauncher.currentFamily(rom.platformSlug, null, platformEmulatorId)?.displayName
        }
    }
    val scope = rememberCoroutineScope()

    return LauncherDetailControls(
        platformLabel = platformLabel,
        onPlay = onPlay,
        launchable = launchable,
        displayMode = mode,
        onDisplayModeChange = { repo.updateDisplayMode(listOf(rom.id), it) },
        hasRemotePhysicalMedia = rom.physicalMediaUrl != null,
        physicalMediaCached = cached,
        physicalMediaDownloading = rom.id in downloading,
        onDownloadPhysicalMedia = {
            val url = rom.physicalMediaUrl
            if (url == null) {
                Toast.makeText(context, tr("launcher.physical.none"), Toast.LENGTH_LONG).show()
            } else {
                val appContext = context.applicationContext
                repo.enqueueDownload(rom.id, url, settings.apiKey) { result ->
                    val msg = result.fold(
                        onSuccess = { tr("launcher.physical.downloaded") },
                        onFailure = { tr("launcher.physical.download_failed", it.message ?: "") },
                    )
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(appContext, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        },
        emulatorOptions = options,
        selectedEmulatorId = override,
        automaticEmulatorName = automaticName,
        onEmulatorChange = { id ->
            scope.launch { container.settingsRepository.setLaunchEmulatorOverride(platformKey, id) }
        },
    )
}

/** Sección del detalle con los controles del launcher. */
@Composable
internal fun LauncherControlsSection(controls: LauncherDetailControls) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        controls.onPlay?.let { onPlay ->
            Button(
                onClick = onPlay,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(tr("launcher.play"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (!controls.launchable) {
                Text(
                    tr("launcher.not_launchable"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Text(
            tr("launcher.display.title"),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
                .padding(4.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(
                LauncherDisplayMode.CARD to tr("launcher.display.card"),
                LauncherDisplayMode.PHYSICAL to tr("launcher.display.physical"),
            ).forEach { (mode, label) ->
                val selected = controls.displayMode == mode
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .selectable(selected = selected, role = Role.RadioButton) {
                            controls.onDisplayModeChange(mode)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        val status = when {
            controls.physicalMediaCached -> tr("launcher.physical.cached")
            controls.hasRemotePhysicalMedia && controls.displayMode == LauncherDisplayMode.PHYSICAL ->
                tr("launcher.physical.needs_download")
            controls.hasRemotePhysicalMedia -> tr("launcher.physical.available")
            else -> tr("launcher.physical.none")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (controls.physicalMediaCached) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        if (controls.hasRemotePhysicalMedia) {
            when {
                controls.physicalMediaDownloading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(tr("launcher.physical.downloading"), style = MaterialTheme.typography.bodySmall)
                }
                controls.physicalMediaCached -> TextButton(onClick = controls.onDownloadPhysicalMedia) {
                    Text(tr("launcher.physical.redownload"))
                }
                else -> FilledTonalButton(
                    onClick = controls.onDownloadPhysicalMedia,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(tr("launcher.physical.download"))
                }
            }
        }

        EmulatorPicker(controls)
    }
}

@Composable
private fun EmulatorPicker(controls: LauncherDetailControls) {
    Text(
        tr("launcher.emulator.title", controls.platformLabel),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
    )
    if (controls.emulatorOptions.isEmpty()) {
        Text(
            tr("launcher.emulator.none"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    var open by remember { mutableStateOf(false) }
    val autoLabel = tr("launcher.emulator.auto") + (controls.automaticEmulatorName?.let { " ($it)" } ?: "")
    fun label(option: EmulatorOption) = option.family.displayName +
        if (option.installed) "" else " · " + tr("launcher.emulator.not_installed")
    val current = controls.emulatorOptions.firstOrNull { it.family.id == controls.selectedEmulatorId }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(current?.let(::label) ?: autoLabel, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(autoLabel) },
                onClick = {
                    open = false
                    controls.onEmulatorChange(null)
                },
            )
            controls.emulatorOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    onClick = {
                        open = false
                        controls.onEmulatorChange(option.family.id)
                    },
                )
            }
        }
    }
}
