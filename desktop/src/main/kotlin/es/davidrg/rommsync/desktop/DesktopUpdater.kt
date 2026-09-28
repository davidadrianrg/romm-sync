package es.davidrg.rommsync.desktop

import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Descarga e instalación in-place de actualizaciones para el AppImage:
 *  - Detecta el AppImage en ejecución (ruta del jar en el AppDir extraído o
 *    la ruta del propio AppImage si se ejecuta directamente).
 *  - Descarga el asset de la arquitectura correcta (aarch64/x86_64) a un
 *    fichero temporal junto al actual, le da permisos de ejecución y hace
 *    reemplazo atómico (rename).
 *  - El usuario reinicia la app cuando quiera; el AppImage nuevo arranca solo.
 */
object DesktopUpdater {

    data class UpdateInfo(
        val available: Boolean,
        val latestVersion: String,
        val downloadUrl: String?,
        val assetName: String?,
        val currentVersion: String = DesktopConfig.appVersion,
        /** Cuerpo del release (changelog en markdown tal cual lo escribe GitHub). */
        val releaseNotes: String = "",
    )

    fun check(): UpdateInfo? {
        val conn = URL("https://api.github.com/repos/davidadrianrg/romm-sync/releases/latest").openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val tag = Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1) ?: return null
            val latest = tag.removePrefix("v")
            val arch = currentArch()
            val asset = Regex("\"browser_download_url\"\\s*:\\s*\"([^\"]+${Regex.escape(arch)}[^\"]*)\"").find(body)?.groupValues?.get(1)
            val name = asset?.substringAfterLast('/')
            val available = isNewer(latest, DesktopConfig.appVersion)
            return UpdateInfo(available, latest, asset, name, releaseNotes = extractBody(body))
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Extrae el campo "body" del JSON de release. El valor es un string JSON
     * escapado (\n, \", \\, \t); se des-escapa lo básico para mostrarlo tal
     * cual en la UI.
     */
    private fun extractBody(json: String): String {
        val raw = Regex("\"body\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").find(json)?.groupValues?.get(1) ?: return ""
        return raw
            .replace("\\r\\n", "\n")
            .replace("\\n", "\n")
            .replace("\\t", "    ")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .trim()
    }

    /**
     * Descarga el AppImage nuevo y reemplaza el actual atómicamente.
     * @return mensaje descriptivo del resultado.
     */
    fun downloadAndInstall(
        info: UpdateInfo,
        url: String,
        onProgress: (bytesRead: Long, total: Long) -> Unit = { _, _ -> },
    ): String {
        val current = currentAppImage() ?: return "No se encontró el AppImage en ejecución — descarga manual: $url"
        val tmp = File(current.parentFile, ".${info.latestVersion}.download")
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        try {
            if (conn.responseCode != 200) return "Error HTTP ${conn.responseCode} descargando $url"
            val total = conn.contentLengthLong
            var read = 0L
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        onProgress(read, total)
                    }
                }
            }
            // Verificación mínima: el AppImage empieza con ELF magic
            val magic = java.io.RandomAccessFile(tmp, "r").use { raf ->
                val b = ByteArray(4); raf.readFully(b); b
            }
            if (!magic.contentEquals(byteArrayOf(0x7f, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte()))) {
                tmp.delete()
                return "El archivo descargado no es un AppImage válido"
            }
            if (!tmp.setExecutable(true)) return "No se pudo dar permiso de ejecución a la actualización"
            // Reemplazo atómico: renombrar sobre el original
            val backup = File(current.parentFile, ".${current.name}.old")
            backup.delete()
            if (!current.renameTo(backup)) return "No se pudo mover el AppImage actual"
            if (!tmp.renameTo(current)) {
                backup.renameTo(current)
                return "No se pudo instalar la actualización (permisos)"
            }
            backup.delete()
            return "Actualizado a v${info.latestVersion} — reinicia la app para aplicar"
        } finally {
            conn.disconnect()
            tmp.delete()
        }
    }

    /** Ruta del AppImage/jar en ejecución, si es detectable. */
    fun currentAppImage(): File? {
        // 0) AppImage en ejecución: el runtime exporta $APPIMAGE con la ruta
        //    del propio .AppImage (fiable incluso con FUSE montado en /tmp/.mount_xxx).
        System.getenv("APPIMAGE")?.let { env ->
            val f = File(env)
            if (f.isFile) return f
        }
        // 1) AppImage extraído (extract-and-run) o instalado: la ruta del jar
        //    contiene squashfs-root o termina en .AppImage
        val jarLoc = DesktopUpdater::class.java.protectionDomain.codeSource?.location
        val jarFile = jarLoc?.toURI()?.let { runCatching { File(it) }.getOrNull() }
        if (jarFile != null && jarFile.isFile) {
            // Subir buscando el .AppImage (cuando se monta squashfs, la raíz
            // del AppDir aparece como /tmp/.mount_xxx/usr/bin/romm-sync.jar)
            var d = jarFile.parentFile
            while (d != null) {
                d.listFiles()?.forEach { f -> if (f.name.endsWith(".AppImage") && f.isFile) return f }
                d = d.parentFile
            }
        }
        // 2) Fallback: buscar en el directorio de trabajo
        return runCatching {
            File(".").canonicalFile.listFiles()?.firstOrNull { it.name.endsWith(".AppImage") && it.isFile }
        }.getOrNull()
    }

    /**
     * Relanza la app tras una actualización in-place del AppImage:
     * arranca el binario nuevo en un proceso detached y termina este.
     */
    fun relaunch() {
        val app = currentAppImage() ?: return
        runCatching {
            ProcessBuilder(app.absolutePath)
                .directory(app.parentFile)
                .start()
        }
        kotlin.system.exitProcess(0)
    }

    fun currentArch(): String = System.getProperty("os.arch", "x86_64").let {
        when (it) {
            "aarch64", "arm64" -> "aarch64"
            else -> "x86_64"
        }
    }

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
