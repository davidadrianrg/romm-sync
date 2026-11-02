package com.davidadrianrg.atril.desktop

import java.awt.SystemTray
import java.awt.TrayIcon
import java.util.Collections
import java.util.concurrent.TimeUnit

/**
 * Notificaciones de escritorio de Atril.
 *
 * En Linux se usa `notify-send` con `--print-id`, lo que permite cerrar las
 * notificaciones de descarga cuando el usuario limpia la cola
 * ([dismissAll]). En el resto de plataformas, o si `notify-send` no existe,
 * se usa el `TrayIcon` de AWT (sin capacidad de retirar el aviso).
 */
object DesktopNotifier {

    private val notificationIds = Collections.synchronizedSet(mutableSetOf<Long>())

    private val isLinux: Boolean =
        System.getProperty("os.name", "").lowercase().contains("linux")

    private val hasNotifySend: Boolean by lazy {
        isLinux && commandExists("notify-send")
    }

    fun notify(title: String, message: String) {
        if (hasNotifySend && notifySend(title, message)) return
        fallbackTray(title, message)
    }

    /**
     * Cierra las notificaciones que Atril haya publicado. Se llama al limpiar
     * o cancelar la cola de descargas para que no quede un aviso huérfano.
     * Es un no-op en el fallback de AWT.
     */
    fun dismissAll() {
        if (!hasNotifySend) return
        val ids = synchronized(notificationIds) {
            notificationIds.toList().also { notificationIds.clear() }
        }
        ids.forEach { id ->
            runCatching {
                val process = ProcessBuilder(
                    "gdbus",
                    "call",
                    "--session",
                    "--dest", "org.freedesktop.Notifications",
                    "--object-path", "/org/freedesktop/Notifications",
                    "--method", "org.freedesktop.Notifications.CloseNotification",
                    id.toString(),
                ).redirectErrorStream(true).start()
                process.waitFor(500, TimeUnit.MILLISECONDS)
                process.destroy()
            }
        }
    }

    private fun notifySend(title: String, message: String): Boolean = runCatching {
        val process = ProcessBuilder(
            "notify-send",
            "--app-name=Atril",
            "--print-id",
            title,
            message,
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        process.waitFor(1500, TimeUnit.MILLISECONDS)
        if (process.exitValue() != 0) return@runCatching false
        output.lineSequence()
            .mapNotNull { it.trim().toLongOrNull() }
            .firstOrNull()
            ?.let { notificationIds.add(it) }
        true
    }.getOrDefault(false)

    private fun fallbackTray(title: String, message: String) {
        runCatching {
            val tray = SystemTray.getSystemTray()
            val icon: TrayIcon? = tray.trayIcons.firstOrNull()
            icon?.displayMessage(title, message, TrayIcon.MessageType.INFO)
        }
    }

    private fun commandExists(name: String): Boolean = runCatching {
        val p = ProcessBuilder("which", name)
            .redirectErrorStream(true)
            .start()
        p.waitFor(800, TimeUnit.MILLISECONDS) && p.exitValue() == 0
    }.getOrDefault(false)
}
