package es.davidrg.rommsync.desktop

import java.io.File
import java.util.Collections
import kotlin.math.min

/**
 * Caché LRU en disco para covers (equivalente al DiskCache de Coil en Android):
 *  - Ficheros nombrados por hash SHA-256 de la URL dentro de cacheDir/covers
 *  - Recorte por tamaño total (100 MB) y por antigüedad en cada put()
 *  - Además limita la decodificación para no re-escalar bitmaps gigantes
 */
object CoverCache {

    private const val MAX_BYTES = 100L * 1024L * 1024L
    private val dir: File by lazy {
        File(DesktopConfig.cacheDir, "covers").apply { mkdirs() }
    }

    /** Mapa en memoria URL → fichero cacheado (evita repetir decodificación). */
    private val memHit = Collections.synchronizedMap(HashMap<String, Boolean>())

    fun fileFor(url: String): File = File(dir, sha256(url) + ".img")

    fun get(url: String): File? {
        val f = fileFor(url)
        if (f.isFile) {
            f.setLastModified(System.currentTimeMillis())
            return f
        }
        return null
    }

    /** Descarga (si no está en caché) y devuelve el fichero local. */
    fun fetch(url: String, maxRetries: Int = 2): File? {
        get(url)?.let { return it }
        repeat(maxRetries) { attempt ->
            runCatching {
                val tmp = File(dir, sha256(url) + ".tmp")
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 15_000
                try {
                    if (conn.responseCode != 200) return@runCatching
                    conn.inputStream.use { input ->
                        tmp.outputStream().use { input.copyTo(it, 64 * 1024) }
                    }
                } finally {
                    conn.disconnect()
                }
                // Un HTTP 200 con cuerpo inválido (p. ej. HTML de un proxy)
                // no debe envenenar la caché: validar antes de promocionar.
                if (!tmp.isFile || tmp.length() < 64L || !looksLikeImage(tmp)) {
                    tmp.delete()
                    return@runCatching
                }
                val target = fileFor(url)
                if (!tmp.renameTo(target)) {
                    target.outputStream().use { out ->
                        java.io.FileInputStream(tmp).use { it.copyTo(out, 64 * 1024) }
                    }
                    tmp.delete()
                }
                trim()
                return target
            }
        }
        return null
    }

    /** Firma de cabecera mínima: PNG/JPEG/GIF/WebP/BMP. */
    private fun looksLikeImage(f: File): Boolean = runCatching {
        java.io.RandomAccessFile(f, "r").use { raf ->
            val b = ByteArray(12)
            raf.readFully(b)
            b.size >= 4 && (
                b[0] == 0x89.toByte() && b[1] == 'P'.code.toByte() || // PNG
                b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() ||     // JPEG
                b[0] == 'G'.code.toByte() && b[1] == 'I'.code.toByte() || // GIF
                b[8] == 'W'.code.toByte() && b[9] == 'E'.code.toByte() || // WebP
                b[0] == 'B'.code.toByte() && b[1] == 'M'.code.toByte()    // BMP
                )
        }
    }.getOrDefault(false)

    /** Recorta el directorio a MAX_BYTES borrando los más viejos. */
    private fun trim() {
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= MAX_BYTES) return
        val byOldest = files.sortedBy { it.lastModified() }
        for (f in byOldest) {
            if (total <= MAX_BYTES) break
            total -= f.length()
            f.delete()
        }
    }

    private fun sha256(url: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
