package com.davidadrianrg.atril.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.davidadrianrg.atril.RomMSyncApplication
import com.davidadrianrg.atril.ui.components.rememberWindowInfo
import com.davidadrianrg.atril.ui.screens.ConfigScreen
import com.davidadrianrg.atril.ui.screens.DownloadQueueScreen
import com.davidadrianrg.atril.ui.screens.LibraryScreen
import com.davidadrianrg.atril.ui.screens.PlatformsScreen
import com.davidadrianrg.atril.ui.screens.PlayScreen
import com.davidadrianrg.atril.ui.screens.SyncScreen
import com.davidadrianrg.atril.util.hasAllFilesAccess

@Composable
fun RomMSyncApp(startInUpdates: Boolean = false) {
    val context = LocalContext.current
    val container = (context.applicationContext as RomMSyncApplication).container

    val settings by container.settingsRepository.settings.collectAsState(
        initial = com.davidadrianrg.atril.data.local.ServerConfig("", "", "", 2)
    )

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val needsOnboarding = !settings.canStart || !hasAllFilesAccess()
    val windowInfo = rememberWindowInfo()

    // La pestaña Sync solo existe si la función de sincronizar saves está
    // activada en Configuración (por defecto sí).
    val saveSyncEnabled by container.settingsRepository.saveSyncEnabled.collectAsState(initial = true)
    val navItems = if (saveSyncEnabled) bottomNavItems else bottomNavItems.filter { it != Screen.Sync }

    // Si el usuario desactiva el sync mientras está en esa pestaña, lo
    // devolvemos a Biblioteca para no dejar un destino inalcanzable.
    LaunchedEffect(saveSyncEnabled) {
        if (!saveSyncEnabled && currentDestination?.route == Screen.Sync.route) {
            navigateTo(Screen.Play.route, navController)
        }
    }

    // El launcher (Jugar) es inmersivo: sin rail ni barra inferior; su menú es
    // un overlay propio. El resto de secciones conserva la navegación fija:
    // rail en horizontal (libera altura) y barra inferior en vertical.
    // Un único árbol para que NavHost no cambie de posición al alternar.
    // Android: todas las secciones son inmersivas (sin rail ni barra inferior): hamburguesa +
    // menú superpuesto. Solo Play ocupa la pantalla completa (sin insets propios).
    val immersive = !needsOnboarding && currentDestination?.route == Screen.Play.route
    val contentInsets = if (immersive) WindowInsets(0) else WindowInsets.safeDrawing

    // Menú unificado: el mismo overlay del launcher en todas las pestañas (Start/Select del
    // mando o el botón de menú); en Play lo gestiona la propia pantalla.
    var appMenuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(immersive) { if (immersive) appMenuOpen = false }
    // El colector vive todo el tiempo: lee el estado actual (en el primer frame needsOnboarding
    // es true porque los ajustes aún no han cargado, y capturarlo dejaba el menú siempre cerrado).
    val menuAllowed by androidx.compose.runtime.rememberUpdatedState(!immersive && !needsOnboarding)
    LaunchedEffect(Unit) {
        com.davidadrianrg.atril.ui.input.PadEvents.menu.collect { if (menuAllowed) appMenuOpen = !appMenuOpen }
    }
    androidx.activity.compose.BackHandler(enabled = appMenuOpen) { appMenuOpen = false }

    androidx.compose.runtime.CompositionLocalProvider(
        com.davidadrianrg.atril.ui.components.LocalOpenAppMenu provides { appMenuOpen = true },
    ) {
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            AppNavHost(
                navController = navController,
                needsOnboarding = needsOnboarding,
                startInUpdates = startInUpdates,
                menuItems = navItems,
                onNavigate = { route -> navigateTo(route, navController) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    // Los insets se aplican aquí UNA vez y se marcan como consumidos: si no,
                    // los Scaffold internos (safeDrawing) los suman otra vez y dejan una banda
                    // vacía de ~100 px sobre la barra de gestos en Ajustes/Biblioteca/Sync.
                    // Se consume TODO safeDrawing (no solo contentInsets): el lado que cubre la
                    // barra inferior (Bottom, vertical) o el rail (Start, horizontal) ya lo
                    // gestionan ellos, y sin consumirlo los Scaffold internos lo vuelven a sumar.
                    .padding(contentInsets.asPaddingValues())
                    .consumeWindowInsets(if (immersive) WindowInsets(0) else WindowInsets.safeDrawing),
            )
        }
    }
        com.davidadrianrg.atril.ui.screens.LauncherMenuOverlay(
            visible = appMenuOpen && !immersive,
            items = navItems,
            landscape = windowInfo.isLandscape,
            onNavigate = { screen ->
                appMenuOpen = false
                navigateTo(screen.route, navController)
            },
            onDismiss = { appMenuOpen = false },
        )
    }
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    needsOnboarding: Boolean,
    startInUpdates: Boolean,
    menuItems: List<Screen>,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = when {
            needsOnboarding -> Screen.Config.route
            startInUpdates -> Screen.Config.route
            else -> Screen.Play.route
        },
        modifier = modifier,
        enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 8 } },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { it / 8 } },
    ) {
        composable(Screen.Play.route) {
            PlayScreen(
                onOpenDownloads = { navController.navigate(Screen.Downloads.route) },
                onOpenLibrary = { navController.navigate(Screen.Library.route) },
                menuItems = menuItems,
                onNavigate = { screen -> onNavigate(screen.route) },
            )
        }
        composable(Screen.Library.route) {
            LibraryScreen(
                onOpenDownloads = { navController.navigate(Screen.Downloads.route) },
            )
        }
        composable(Screen.Platforms.route) { PlatformsScreen(onBack = { navController.popBackStack() }) }
        composable(Screen.Downloads.route) { DownloadQueueScreen(onBack = { navController.popBackStack() }) }
        composable(Screen.Sync.route) { SyncScreen() }
        composable(Screen.Config.route) {
            ConfigScreen(
                onOpenPlatforms = { navController.navigate(Screen.Platforms.route) },
                initialCategory = if (startInUpdates && !needsOnboarding) {
                    com.davidadrianrg.atril.core.settings.SettingsCategory.ABOUT
                } else {
                    com.davidadrianrg.atril.core.settings.SettingsCategory.CONNECTION
                },
            )
        }
    }
}

private fun isSelected(route: String, currentDestination: NavDestination?): Boolean {
    // Las pantallas anidadas mantienen resaltada su sección de origen.
    if (route == Screen.Config.route && currentDestination?.route == Screen.Platforms.route) return true
    return currentDestination?.hierarchy?.any { it.route == route } == true
}

private fun navigateTo(route: String, navController: NavHostController) {
    // Jugar es el destino de inicio y siempre está en la base de la pila: basta
    // con volver a él. Con popUpTo(inicio){saveState} + restoreState la pestaña
    // no navegaba (se quedaba en Biblioteca o restauraba el estado anterior).
    if (route == Screen.Play.route && navController.popBackStack(route, inclusive = false)) return
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

// Order: Library (home) → Platforms → Downloads → Sync → Config
private val bottomNavItems = listOf(
    Screen.Play,
    Screen.Library,
    Screen.Sync,
    Screen.Config,
)
