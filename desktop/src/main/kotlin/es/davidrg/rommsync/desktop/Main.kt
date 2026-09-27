package es.davidrg.rommsync.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlin.system.exitProcess

fun main() = application {
    val scope = rememberCoroutineScope()
    val state = remember { DesktopAppState(scope) }

    Window(
        onCloseRequest = ::exitApplication,
        title = "RomM Sync",
    ) {
        MaterialTheme(darkColorScheme()) {
            Surface(modifier = Modifier.fillMaxSize()) {
                DesktopApp(state)
            }
        }
    }
}
