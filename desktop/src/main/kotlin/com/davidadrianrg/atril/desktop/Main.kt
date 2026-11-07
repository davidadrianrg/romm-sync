package com.davidadrianrg.atril.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.davidadrianrg.atril.core.i18n.I18n
import com.davidadrianrg.atril.core.i18n.tr
import com.davidadrianrg.atril.desktop.theme.RomMSyncDesktopTheme
import java.awt.Dimension
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.SystemTray
import java.awt.event.KeyEvent
import java.util.Locale
import javax.swing.UIManager
import kotlin.system.exitProcess

fun main() {
    // En WMs de tiling AWT necesita _JAVA_AWT_WM_NONREPARENTING antes de abrir ventana.
    TilingWmWorkaround.relaunchIfNeeded()
    // El idioma guardado debe estar fijado antes de componer el primer frame.
    val languageTag = DesktopConfig.languageTag
    I18n.locale = if (languageTag.isBlank()) Locale.getDefault() else Locale.forLanguageTag(languageTag)
    application {
    // Look & feel del sistema para que JFileChooser (selectores de carpeta)
    // no se vea como Metal/AWT clásico en Linux.
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }

    val scope = rememberCoroutineScope()
    val state = remember { DesktopAppState(scope) }
    val traySupported = SystemTray.isSupported()

    // Ventana para el close-to-tray: "Mostrar" del menú de bandeja la revive.
    var appWindow: java.awt.Window? = null

    Window(
        onCloseRequest = {
            if (DesktopConfig.closeToTray && traySupported) {
                // Ocultar en vez de salir: los downloads y el auto-sync siguen
                // corriendo en background; se sale desde el menú de bandeja.
                appWindow?.isVisible = false
            } else {
                exitApplication()
            }
        },
        // Esc / botón B del mando: cerrar el detalle abierto. Va en onKeyEvent (fase de
        // burbuja) y no en el KeyEventDispatcher global para que los diálogos abiertos sobre
        // el detalle (confirmar borrado, visor de capturas…) se cierren primero con su Esc.
        onKeyEvent = { ev ->
            if (ev.type == androidx.compose.ui.input.key.KeyEventType.KeyDown &&
                ev.key == androidx.compose.ui.input.key.Key.Escape &&
                state.selectedGame.value != null
            ) {
                state.closeGame()
                true
            } else {
                false
            }
        },
        title = "Atril",
        icon = painterResource("atril-icon.png"),
        state = rememberWindowState(width = 1280.dp, height = 832.dp),
    ) {
        window.minimumSize = Dimension(980, 640)

        DisposableEffect(window) {
            appWindow = window
            onDispose { if (appWindow == window) appWindow = null }
        }

        // Atajos globales vía KeyEventDispatcher de AWT: reciben TODOS los key
        // eventos con independencia del foco de Compose (los campos de texto
        // incluidos). Nunca consumimos el evento (return false).
        DisposableEffect(state) {
            val dispatcher = KeyEventDispatcher { e ->
                if (e.id == KeyEvent.KEY_PRESSED) {
                    val sections = listOfNotNull(Section.PLAY, Section.LIBRARY, Section.SAVES.takeIf { state.saveSyncEnabled.value }, Section.SETTINGS)
                    when {
                        (e.keyCode == KeyEvent.VK_1 || e.keyCode == KeyEvent.VK_2 ||
                            e.keyCode == KeyEvent.VK_3 || e.keyCode == KeyEvent.VK_4) && e.isControlDown -> {
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

        // Mando (evdev, sin dependencias): si no hay mando o no hay permiso, no hace nada.
        DisposableEffect(Unit) {
            val pad = GamepadInput { GamepadInput.dispatchAsKey(it) }
            pad.start()
            // Autoprueba (solo desarrollo): ATRIL_PAD_SELFTEST="right,menu,back" inyecta esas acciones
            // por la misma vía que el mando (evdev → tecla AWT), sin necesitar hardware.
            System.getenv("ATRIL_PAD_SELFTEST")?.takeIf { it.isNotBlank() }?.let { script ->
                Thread {
                    Thread.sleep(((System.getenv("ATRIL_PAD_SELFTEST_DELAY_MS")?.toLongOrNull()) ?: 6000L))
                    script.split(',').forEach { name ->
                        runCatching { com.davidadrianrg.atril.core.input.PadAction.valueOf(name.trim().uppercase()) }
                            .getOrNull()?.let { GamepadInput.dispatchAsKey(it) }
                        Thread.sleep(2500)
                    }
                }.apply { isDaemon = true; start() }
            }
            onDispose { pad.stop() }
        }

        val theme by state.theme.collectAsState()
        RomMSyncDesktopTheme(theme) {
            Surface(modifier = Modifier.fillMaxSize()) {
                DesktopApp(state)
            }
        }
    }

    // Bandeja del sistema (KDE/SteamOS la soportan de serie; en GNOME puede
    // requerir extensión — si no hay soporte, simplemente no aparece).
    if (traySupported) {
        Tray(
            icon = painterResource("atril-icon.png"),
            tooltip = "Atril",
        ) {
            if (state.saveSyncEnabled.collectAsState().value) {
                Item(tr("window.tray.sync_saves")) { state.syncSaves() }
            }
            Item(tr("window.tray.show")) {
                appWindow?.isVisible = true
                appWindow?.toFront()
            }
            Item(tr("window.tray.quit")) { exitApplication() }
        }
    }
}
}
