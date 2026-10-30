package es.davidrg.rommsync.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.ui.graphics.vector.ImageVector
import es.davidrg.rommsync.core.i18n.tr

enum class Screen(
    val route: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Config(
        route = "config",
        icon = Icons.Outlined.Settings,
        selectedIcon = Icons.Filled.Settings,
    ),
    Platforms(
        route = "platforms",
        icon = Icons.Outlined.Storage,
        selectedIcon = Icons.Filled.Storage,
    ),
    Library(
        route = "library",
        icon = Icons.Outlined.SportsEsports,
        selectedIcon = Icons.Filled.SportsEsports,
    ),
    Shelf(
        route = "shelf",
        icon = Icons.Outlined.GridView,
        selectedIcon = Icons.Filled.GridView,
    ),
    Downloads(
        route = "downloads",
        icon = Icons.Outlined.Download,
        selectedIcon = Icons.Filled.Download,
    ),
    Sync(
        route = "sync",
        icon = Icons.Outlined.Sync,
        selectedIcon = Icons.Filled.Sync,
    ),
    ;

    /** Título de la barra de navegación (se traduce al leerlo). */
    val title: String get() = tr("nav.${name.lowercase()}")
}
