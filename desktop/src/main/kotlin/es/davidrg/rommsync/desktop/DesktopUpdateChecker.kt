package es.davidrg.rommsync.desktop

import java.net.HttpURLConnection
import java.net.URL

/**
 * Comprobador de actualizaciones vía GitHub Releases (igual que Android,
 * que consulta /repos/davidadrianrg/romm-sync/releases/latest).
 */
object DesktopUpdateChecker {

    private const val OWNER = "davidadrianrg"
    private const val REPO = "romm-sync"

    /** Devuelve un mensaje de estado para la UI. */
    fun check(): String {
        val conn = URL("https://api.github.com/repos/$OWNER/$REPO/releases/latest").openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (conn.responseCode != 200) return "Error HTTP ${conn.responseCode} buscando actualizaciones"
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val matchTag = Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").find(body)
            val matchUrl = Regex("\"html_url\"\\s*:\\s*\"([^\"]+)\"").find(body)
            val tag = matchTag?.groupValues?.get(1) ?: return "No se pudo leer la versión"
            val url = matchUrl?.groupValues?.get(1) ?: "https://github.com/$OWNER/$REPO/releases/latest"
            val latest = tag.removePrefix("v")
            val current = DesktopConfig.appVersion
            return if (isNewer(latest, current)) {
                "Nueva versión disponible: v$latest (tienes v$current) — $url"
            } else {
                "Estás en la última versión (v$current)"
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Compara semántico simple 0.6.0 vs 0.6.1 etc. */
    private fun isNewer(latest: String, current: String): Boolean {
        val l = latest.split('.').map { it.toIntOrNull() ?: 0 }
        val c = current.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(l.size, c.size)) {
            val li = l.getOrNull(i) ?: 0
            val ci = c.getOrNull(i) ?: 0
            if (li != ci) return li > ci
        }
        return false
    }
}
