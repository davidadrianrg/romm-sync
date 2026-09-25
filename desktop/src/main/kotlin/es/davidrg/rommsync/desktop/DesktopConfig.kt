package es.davidrg.rommsync.desktop

import java.io.File
import java.util.prefs.Preferences

/** Configuración persistente de la app desktop (java.util.prefs, sin Android). */
object DesktopConfig {
    private val prefs = Preferences.userNodeForPackage(DesktopConfig::class.java)

    var serverUrl: String
        get() = prefs.get("server_url", "")
        set(v) = prefs.put("server_url", v)

    var apiKey: String
        get() = prefs.get("api_key", "")
        set(v) = prefs.put("api_key", v)

    var romsRoot: String
        get() = prefs.get("roms_root", defaultRomsRoot())
        set(v) = prefs.put("roms_root", v)

    var deviceId: String?
        get() = prefs.get("device_id", null)
        set(v) = if (v == null) prefs.remove("device_id") else prefs.put("device_id", v)

    val configDir: File
        get() = File(System.getProperty("user.home"), ".config/romm-sync").also { it.mkdirs() }

    val cacheDir: File
        get() = File(System.getProperty("user.home"), ".cache/romm-sync").also { it.mkdirs() }

    private fun defaultRomsRoot(): String =
        File(System.getProperty("user.home", "."), "ROMs").absolutePath
}
