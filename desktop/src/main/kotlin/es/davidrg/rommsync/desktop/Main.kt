package es.davidrg.rommsync.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import es.davidrg.rommsync.desktop.theme.RomMSyncDesktopTheme
import java.awt.Dimension
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import javax.swing.UIManager
import kotlin.system.exitProcess

fun main() = application {
    // Look & feel del sistema para que JFileChooser (selectores de carpeta)
    // no se vea como Metal/AWT clásico en Linux.
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }

    val scope = rememberCoroutineScope()
    val state = remember { DesktopAppState(scope) }

    Window(
        onCloseRequest = ::exitApplication,
        title = "RomM Sync",
        icon = painterResource("romm-sync-icon.png"),
        state = rememberWindowState(width = 1280.dp, height = 832.dp),
    ) {
        window.minimumSize = Dimension(980, 640)

        // Atajos globales vía KeyEventDispatcher de AWT: reciben TODOS los key
        // eventos con independencia del foco de Compose (los campos de texto
        // incluidos). Nunca consumimos el evento (return false).
        DisposableEffect(state) {
            val dispatcher = KeyEventDispatcher { e ->
                if (e.id == KeyEvent.KEY_PRESSED) {
                    val sections = Section.entries
                    when {
                        (e.keyCode == KeyEvent.VK_1 || e.keyCode == KeyEvent.VK_2 ||
                            e.keyCode == KeyEvent.VK_3 || e.keyCode == KeyEvent.VK_4 ||
                            e.keyCode == KeyEvent.VK_5) && e.isControlDown -> {
                            val idx = e.keyCode - KeyEvent.VK_1
                            if (idx in sections.indices) state.navigate(sections[idx])
                            true
                        }
                        e.keyCode == KeyEvent.VK_F5 -> {
                            state.refreshCurrentView()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher)
            onDispose { KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(dispatcher) }
        }

        RomMSyncDesktopTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                DesktopApp(state)
            }
        }
    }
}
