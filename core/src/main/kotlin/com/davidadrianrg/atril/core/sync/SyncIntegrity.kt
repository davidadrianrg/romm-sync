package com.davidadrianrg.atril.core.sync

import java.io.File
import java.security.MessageDigest

/**
 * Hashes y verificación de integridad de saves.
 *
 * RomM guarda y compara `content_hash` como **MD5** hexadecimal (32 chars) del
 * blob subido; si el cliente envía otro algoritmo (p. ej. SHA-1) el servidor
 * nunca detecta «contenido idéntico» y cae al respaldo por fechas, lo que
 * provoca subidas innecesarias o falsos conflictos.
 */
object SyncIntegrity {

    fun md5(file: File): String = digest(file, "MD5")

    fun md5(bytes: ByteArray): String = hex(MessageDigest.getInstance("MD5").digest(bytes))

    private fun digest(file: File, algorithm: String): String {
        val md = MessageDigest.getInstance(algorithm)
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                md.update(buffer, 0, read)
            }
        }
        return hex(md.digest())
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    /**
     * `content_hash` tal como lo calcula RomM (`hash_save_content`): para un zip,
     * MD5 de las líneas `nombre:md5(entrada)` (entradas no-directorio ordenadas,
     * unidas por `\n`); para cualquier otro fichero, MD5 del blob.
     */
    fun contentHash(file: File): String = zipContentsMd5(file) ?: md5(file)

    private fun zipContentsMd5(file: File): String? {
        val zip = try {
            java.util.zip.ZipFile(file)
        } catch (_: java.io.IOException) {
            return null // no es un zip
        }
        return zip.use { zf ->
            val lines = zf.entries().asSequence()
                .filterNot { it.isDirectory || it.name.endsWith("/") }
                .sortedBy { it.name }
                .map { entry ->
                    val md = MessageDigest.getInstance("MD5")
                    zf.getInputStream(entry).use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            md.update(buffer, 0, read)
                        }
                    }
                    "${entry.name}:${hex(md.digest())}"
                }
                .toList()
            md5(lines.joinToString("\n").toByteArray(Charsets.UTF_8))
        }
    }

    sealed interface Verdict {
        /** El fichero coincide con el hash esperado. */
        data object Match : Verdict

        /** No hay hash MD5 utilizable del servidor: no se puede verificar (se acepta). */
        data object Unverifiable : Verdict

        data class Mismatch(val expected: String, val actual: String) : Verdict
    }

    private val md5Hex = Regex("^[0-9a-fA-F]{32}$")

    /**
     * Compara [file] con el `content_hash` anunciado por el servidor. Solo se
     * verifica si es un MD5 válido; cualquier otro formato → [Verdict.Unverifiable]
     * (nunca bloqueamos una descarga por un formato que no entendemos).
     */
    fun verify(file: File, expectedHash: String?): Verdict {
        val expected = expectedHash?.trim()?.takeIf { md5Hex.matches(it) } ?: return Verdict.Unverifiable
        val actual = contentHash(file)
        if (actual.equals(expected, ignoreCase = true)) return Verdict.Match
        // RomM anteriores guardaban el MD5 crudo también para zips.
        val raw = md5(file)
        if (raw != actual && raw.equals(expected, ignoreCase = true)) return Verdict.Match
        return Verdict.Mismatch(expected.lowercase(), actual)
    }
}
