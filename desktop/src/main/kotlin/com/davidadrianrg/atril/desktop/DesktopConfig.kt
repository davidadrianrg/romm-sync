package com.davidadrianrg.atril.desktop

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

    /** Stable id the RomM server lists under "connected devices" for the QR login. */
    val clientDeviceId: String
        get() = prefs.get("client_device_id", null) ?: ("atril-desktop-" + java.util.UUID.randomUUID())
            .also { prefs.put("client_device_id", it) }

    var romsRoot: String
        get() = prefs.get("roms_root", defaultRomsRoot())
        set(v) = prefs.put("roms_root", v)

    var deviceId: String?
        get() = prefs.get("device_id", null)
        set(v) = if (v == null) prefs.remove("device_id") else prefs.put("device_id", v)

    /**
     * Config dir moved from `~/.config/romm-sync` to `~/.config/atril`.
     * Existing installs are migrated once so the library metadata and sync
     * hashes survive the rename. The old dir is kept as a fallback.
     */
    val configDir: File
        get() = dirWithLegacyMigration(".config/atril", ".config/romm-sync")

    val cacheDir: File
        get() = File(System.getProperty("user.home"), ".cache/atril").also { it.mkdirs() }

    private fun dirWithLegacyMigration(name: String, legacyName: String): File {
        val home = File(System.getProperty("user.home"))
        val target = File(home, name)
        val legacy = File(home, legacyName)
        if (!target.exists() && legacy.isDirectory) {
            runCatching { legacy.copyRecursively(target, overwrite = false) }
        }
        target.mkdirs()
        return target
    }

    private fun defaultRomsRoot(): String =
        File(System.getProperty("user.home", "."), "ROMs").absolutePath

    /** Slugs de plataformas ocultas por el usuario (CSV). */
    var hiddenPlatforms: String
        get() = prefs.get("hidden_platforms", "")
        set(v) = prefs.put("hidden_platforms", v)

    /** Última carpeta elegida como destino de una descarga manual de saves ("" = ruta de sync). */
    var saveDownloadDir: String
        get() = prefs.get("save_download_dir", "")
        set(v) = prefs.put("save_download_dir", v)

    /** Pedir a RomM el medio físico al descargar un juego que no lo tenga. */
    var autoPhysicalMedia: Boolean
        get() = prefs.getBoolean("auto_physical_media", false)
        set(v) = prefs.putBoolean("auto_physical_media", v)

    /** Descargar la media del juego (covers, screenshots, vídeo, manual) al descargarlo. */
    var mediaDownloadEnabled: Boolean
        get() = prefs.getBoolean("media_download_enabled", false)
        set(v) = prefs.putBoolean("media_download_enabled", v)

    /** Carpeta de media genérica; vacío = `<romsRoot>/downloaded_media`. */
    var mediaDownloadPath: String
        get() = prefs.get("media_download_path", "")
        set(v) = prefs.put("media_download_path", v.trim().trimEnd('/'))

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

    /** Claves de SteamGridDB / ScreenScraper para las carátulas de juegos locales. */
    fun scraperCredentials() = com.davidadrianrg.atril.core.localgames.ScraperCredentials(
        steamGridDbKey = prefs.get("scraper_sgdb_key", ""),
        ssUser = prefs.get("scraper_ss_user", ""),
        ssPassword = prefs.get("scraper_ss_password", ""),
    )

    fun saveScraperCredentials(c: com.davidadrianrg.atril.core.localgames.ScraperCredentials) {
        prefs.put("scraper_sgdb_key", c.steamGridDbKey.trim())
        prefs.put("scraper_ss_user", c.ssUser.trim())
        prefs.put("scraper_ss_password", c.ssPassword.trim())
    }

    /** Idioma: "" = sistema, "en", "es". */
    /** Id de tema ([com.davidadrianrg.atril.core.theme.AppTheme.id]); vacío = por defecto. */
    var themeId: String
        get() = prefs.get("theme", "")
        set(v) = prefs.put("theme", v)

    var languageTag: String
        get() = prefs.get("language", "")
        set(v) = prefs.put("language", v)

    /** IDs marcados como descargados a mano (CSV), sin fichero gestionado. */
    var manualDownloaded: String
        get() = prefs.get("manual_downloaded", "")
        set(v) = prefs.put("manual_downloaded", v)

    /** ROMs mostrados como medio físico en el launcher (CSV de romIds). */
    var physicalMediaIds: String
        get() = prefs.get("physical_media_ids", "")
        set(v) = prefs.put("physical_media_ids", v)

    /** Versión de la app desktop (para el comprobador de actualizaciones). */
    const val appVersion: String = "0.9.2"
}
