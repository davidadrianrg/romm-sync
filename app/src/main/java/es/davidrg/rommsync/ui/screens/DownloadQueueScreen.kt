package es.davidrg.rommsync.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import es.davidrg.rommsync.RomMSyncApplication
import es.davidrg.rommsync.core.i18n.tr
import es.davidrg.rommsync.domain.model.DownloadTask
import es.davidrg.rommsync.ui.viewmodel.DownloadQueueViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadQueueScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val container = (context.applicationContext as RomMSyncApplication).container

    val viewModel: DownloadQueueViewModel = viewModel(
        factory = viewModelFactory {
            initializer { DownloadQueueViewModel(container.downloadManager) }
        }
    )

    val downloads by viewModel.downloads.collectAsState()
    val compact = es.davidrg.rommsync.ui.components.rememberWindowInfo().isCompact

    val activeCount = downloads.count { it.isRunning || (!it.isCompleted && !it.isFailed) }
    val hasCompletedOrFailed = downloads.any { it.isCompleted || it.isFailed }
    val hasRunning = downloads.any { it.isRunning }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = tr("common.back"),
                        )
                    }
                },
                title = {
                    Column {
                        Text(tr("android.downloads.queue_title"))
                        if (downloads.isNotEmpty()) {
                            Text(
                                text = if (activeCount > 0) tr("downloads.in_progress", activeCount)
                                       else tr("downloads.in_list", downloads.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                // Altura mínima (no fija): el título de dos líneas + botones de
                // acción necesitan crecer sin recortarse en pantallas compactas.
                modifier = Modifier.heightIn(min = if (compact) 48.dp else 64.dp),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                actions = {
                    if (hasCompletedOrFailed) {
                        TextButton(onClick = { viewModel.clearCompleted() }) {
                            Icon(
                                Icons.Filled.CleaningServices,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(tr("android.downloads.clear_completed"))
                        }
                    }
                    if (hasRunning) {
                        TextButton(
                            onClick = { viewModel.cancelAll() },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) { Text(tr("downloads.cancel_all")) }
                    }
                },
            )
        }
    ) { padding ->
        if (downloads.isEmpty()) {
            EmptyDownloads(modifier = Modifier
                .fillMaxSize()
                .padding(padding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
        ) {
            items(downloads, key = { it.workId }) { task ->
                DownloadCard(
                    task = task,
                    onCancel = { viewModel.cancelDownload(task.romId) },
                    onRetry = { viewModel.retryDownload(task) },
                )
            }
        }
    }
}

@Composable
private fun EmptyDownloads(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        es.davidrg.rommsync.ui.components.EmptyState(
            icon = Icons.Outlined.CloudDownload,
            title = tr("downloads.empty.title"),
            description = tr("android.downloads.empty_description"),
        )
    }
}

@Composable
private fun DownloadCard(
    task: DownloadTask,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusBadge(task)

            Spacer(modifier = Modifier.size(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.romName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${task.platformSlug.uppercase()} • ${task.fileName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (task.isRunning) {
                    Spacer(modifier = Modifier.height(8.dp))
                    if (task.isIndeterminate) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            task.progressText ?: tr("android.downloads.packaging_zip"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        val animatedProgress by animateFloatAsState(
                            targetValue = task.progress / 100f,
                            label = "progress",
                        )
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                task.byteDetail.ifEmpty { "${task.progress}%" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "${task.progress}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else if (task.isCompleted) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        tr("common.download_completed"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                } else if (task.isFailed) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = task.errorMessage ?: tr("android.downloads.error"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Action
            if (task.isRunning) {
                IconButton(onClick = onCancel) {
                    Icon(
                        Icons.Filled.Cancel,
                        contentDescription = tr("common.cancel"),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            } else if (task.isFailed) {
                TextButton(onClick = onRetry) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(tr("downloads.retry"))
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(task: DownloadTask) {
    val (bg, fg) = when {
        task.isCompleted -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        task.isFailed -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    }
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(bg, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        when {
            task.isRunning && task.isIndeterminate -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.5.dp,
                    color = fg,
                )
            }
            task.isRunning -> {
                Icon(
                    Icons.Filled.Downloading,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(24.dp),
                )
            }
            task.isCompleted -> {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = tr("android.downloads.completed"),
                    tint = fg,
                    modifier = Modifier.size(24.dp),
                )
            }
            task.isFailed -> {
                Icon(
                    Icons.Filled.ErrorOutline,
                    contentDescription = tr("common.error"),
                    tint = fg,
                    modifier = Modifier.size(24.dp),
                )
            }
            else -> {
                Icon(
                    Icons.Filled.DownloadDone,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
