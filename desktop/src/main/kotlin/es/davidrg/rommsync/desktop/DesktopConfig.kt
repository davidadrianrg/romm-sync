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

    /** Slugs de plataformas ocultas por el usuario (CSV). */
    var hiddenPlatforms: String
        get() = prefs.get("hidden_platforms", "")
        set(v) = prefs.put("hidden_platforms", v)

    /** Directorio de datos de ES-DE (donde vive gamelists/). */
    var esdeDataDir: String
        get() = prefs.get("esde_data_dir", File(System.getProperty("user.home", "."), "ES-DE").absolutePath)
        set(v) = prefs.put("esde_data_dir", v)

    /** Minutos entre sincronizaciones automáticas de saves (0 = desactivado). */
    var autoSyncMinutes: Int
        get() = prefs.getInt("auto_sync_minutes", 0)
        set(v) = prefs.putInt("auto_sync_minutes", v)

    /** Versión de la app desktop (para el comprobador de actualizaciones). */
    const val appVersion: String = "0.6.2"
}
