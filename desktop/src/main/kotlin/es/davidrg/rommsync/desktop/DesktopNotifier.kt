package es.davidrg.rommsync.desktop

import java.awt.SystemTray
import java.awt.TrayIcon

/**
 * Notificaciones de escritorio vía el icono de bandeja de AWT (la única vía
 * sin dependencias extra en un runtime jlink). Si no hay bandeja disponible
 * (GNOME sin extensión), es un no-op silencioso.
 */
object DesktopNotifier {

    fun notify(title: String, message: String) {
        runCatching {
            val tray = SystemTray.getSystemTray()
            val icon: TrayIcon? = tray.trayIcons.firstOrNull()
            icon?.displayMessage(title, message, TrayIcon.MessageType.INFO)
        }
    }
}
