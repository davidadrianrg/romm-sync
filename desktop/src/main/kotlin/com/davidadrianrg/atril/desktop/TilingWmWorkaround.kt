package com.davidadrianrg.atril.desktop

import java.io.File
import kotlin.system.exitProcess

/**
 * Java AWT sobre X11/Xwayland asume un gestor de ventanas "reparenting". En
 * compositores de tiling (sway, i3, Hyprland...) eso hace que la ventana no
 * propague su tamaño real al área de dibujo: la UI se queda en el tamaño
 * inicial (1280x832) aunque la ventana esté a pantalla completa y el launcher
 * no rellena la ventana.
 *
 * El remedio oficial es la variable de entorno `_JAVA_AWT_WM_NONREPARENTING=1`,
 * pero AWT la lee con `getenv` nativo al inicializar, así que no puede fijarse
 * desde dentro del proceso ni desde el lanzador de jpackage (AppImage). Por eso
 * se relanza la JVM una sola vez con la variable puesta.
 */
internal object TilingWmWorkaround {
    const val ENV_VAR = "_JAVA_AWT_WM_NONREPARENTING"

    private val tilingDesktops = setOf("sway", "i3", "hyprland", "river", "niri", "bspwm", "dwm", "awesome", "qtile")

    /** Solo en Linux con sesión gráfica, en un WM de tiling y si aún no está la variable. */
    fun needsRelaunch(osName: String, env: Map<String, String>): Boolean {
        if (!osName.contains("linux", ignoreCase = true)) return false
        if (env[ENV_VAR] != null) return false
        if (env["DISPLAY"].isNullOrBlank() && env["WAYLAND_DISPLAY"].isNullOrBlank()) return false
        val tilingSocket = !env["SWAYSOCK"].isNullOrBlank() ||
            !env["I3SOCK"].isNullOrBlank() ||
            !env["HYPRLAND_INSTANCE_SIGNATURE"].isNullOrBlank() ||
            !env["NIRI_SOCKET"].isNullOrBlank()
        val desktops = env["XDG_CURRENT_DESKTOP"].orEmpty()
            .split(':')
            .map { it.trim().lowercase() }
        return tilingSocket || desktops.any { it in tilingDesktops }
    }

    /**
     * Si hace falta, relanza esta misma JVM con la variable y termina con su
     * código de salida. Si no se puede reconstruir la línea de comandos, sigue
     * con normalidad (peor caso: el comportamiento anterior).
     */
    fun relaunchIfNeeded() {
        if (!needsRelaunch(System.getProperty("os.name").orEmpty(), System.getenv())) return
        // ProcessHandle.info().arguments() sale vacío en algunos JDK; /proc es fiable en Linux.
        val commandLine = runCatching {
            File("/proc/self/cmdline").readBytes().toString(Charsets.UTF_8)
                .split('\u0000')
                .filter { it.isNotEmpty() }
        }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return
        val child = runCatching {
            ProcessBuilder(commandLine)
                .inheritIO()
                .apply { environment()[ENV_VAR] = "1" }
                .start()
        }.getOrNull() ?: return
        Runtime.getRuntime().addShutdownHook(Thread { child.destroy() })
        exitProcess(child.waitFor())
    }
}
