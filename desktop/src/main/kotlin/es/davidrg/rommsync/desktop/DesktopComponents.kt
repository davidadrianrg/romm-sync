package es.davidrg.rommsync.desktop

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.io.File
import javax.swing.JFileChooser

/**
 * Estado vacío con icono "respirando" — paridad con el EmptyState de Android
 * (círculo con degradado radial detrás del icono, alpha oscilante 1,8s).
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val transition = rememberInfiniteTransition(label = "emptyBreath")
            val breath by transition.animateFloat(
                initialValue = 0.55f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
                label = "emptyBreathAlpha",
            )
            Box(
                Modifier.size(96.dp).alpha(breath).background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                            Color.Transparent,
                        ),
                    ),
                    shape = CircleShape,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (!description.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Cabecera estándar de sección: título + subtítulo opcional + acciones a la derecha. */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    actions: @Composable () -> Unit = {},
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        actions()
    }
}

/** Card de sección de ajustes (equivalente al SettingsSection de Android). */
@Composable
fun SettingsSection(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShapeDefault,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

private val RoundedCornerShapeDefault = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)

/**
 * Selector nativo de directorios (JFileChooser). Se llama desde un onClick
 * (hilo EDT de Swing, que es donde Compose Desktop ejecuta la UI), así que
 * el diálogo modal es seguro. Devuelve la ruta elegida o null.
 */
fun pickDirectory(title: String, startDir: String?): String? {
    val chooser = JFileChooser().apply {
        dialogTitle = title
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        isAcceptAllFileFilterUsed = false
        if (!startDir.isNullOrBlank() && File(startDir).isDirectory) currentDirectory = File(startDir)
    }
    val result = chooser.showOpenDialog(null)
    return if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile.absolutePath else null
}

/** Formatea bytes como "1,2 GB" / "850 MB" / "120 KB" (paridad con Android). */
fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "—"
    val gb = bytes / 1024.0 / 1024.0 / 1024.0
    val mb = bytes / 1024.0 / 1024.0
    return when {
        gb >= 1.0 -> String.format("%.1f GB", gb)
        mb >= 1.0 -> String.format("%.0f MB", mb)
        else -> String.format("%.0f KB", bytes / 1024.0)
    }
}

fun formatSpeed(bps: Long): String = "${formatBytes(bps)}/s"

/** Formatea un epoch-millis como "28 sep 2026 18:45" (locale ES). */
fun formatTimestamp(epochMillis: Long): String {
    val fmt = java.text.SimpleDateFormat("d MMM yyyy HH:mm", java.util.Locale("es", "ES"))
    return fmt.format(java.util.Date(epochMillis))
}

/** Ancho de contenido máximo de las pantallas de formulario (Ajustes). */
val SettingsMaxWidth: Dp = 560.dp
