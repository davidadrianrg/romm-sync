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

    /** Descargas simultáneas máximas (1-5, igual que Android). */
    var maxConcurrentDownloads: Int
        get() = prefs.getInt("max_concurrent_downloads", 2).coerceIn(1, 5)
        set(v) = prefs.putInt("max_concurrent_downloads", v.coerceIn(1, 5))

    /** Timestamp (epoch ms) de la última sincronización de saves completada. */
    var lastSyncAt: Long
        get() = prefs.getLong("last_sync_at", 0L)
        set(v) = prefs.putLong("last_sync_at", v)

    /** Resumen de la última sincronización de saves (legible, en español). */
    var lastSyncSummary: String
        get() = prefs.get("last_sync_summary", "")
        set(v) = prefs.put("last_sync_summary", v)

    /** Política de resolución de conflictos de saves (id de ConflictPolicy). */
    var conflictPolicy: String
        get() = prefs.get("conflict_policy", "ask")
        set(v) = prefs.put("conflict_policy", v)

    /** Cerrar a la bandeja del sistema en vez de salir (sync en background). */
    var closeToTray: Boolean
        get() = prefs.getBoolean("close_to_tray", false)
        set(v) = prefs.putBoolean("close_to_tray", v)

    /** Versión de actualización omitida por el usuario ("" = ninguna). */
    var skippedVersion: String
        get() = prefs.get("skipped_version", "")
        set(v) = prefs.put("skipped_version", v)

    /** Versión de la app desktop (para el comprobador de actualizaciones). */
    const val appVersion: String = "0.8.0"
}
