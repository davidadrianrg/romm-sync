package com.davidadrianrg.atril.core.sync

import com.davidadrianrg.atril.core.sync.SyncIntegrity.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SyncIntegrityTest {

    private fun tmp(content: ByteArray): File = File.createTempFile("save", ".srm").apply {
        deleteOnExit()
        writeBytes(content)
    }

    @Test
    fun `md5 matches known vectors`() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", SyncIntegrity.md5(tmp(ByteArray(0))))
        assertEquals("900150983cd24fb0d6963f7d28e17f72", SyncIntegrity.md5("abc".toByteArray()))
        assertEquals("900150983cd24fb0d6963f7d28e17f72", SyncIntegrity.md5(tmp("abc".toByteArray())))
    }

    @Test
    fun `md5 of a large file streams without loading it whole`() {
        val big = tmp(ByteArray(5 * 1024 * 1024) { (it % 251).toByte() })
        assertEquals(32, SyncIntegrity.md5(big).length)
    }

    @Test
    fun `verify accepts the matching md5 in any case`() {
        val f = tmp("abc".toByteArray())
        assertEquals(Verdict.Match, SyncIntegrity.verify(f, "900150983cd24fb0d6963f7d28e17f72"))
        assertEquals(Verdict.Match, SyncIntegrity.verify(f, "900150983CD24FB0D6963F7D28E17F72"))
    }

    @Test
    fun `verify flags a corrupted or truncated download`() {
        val f = tmp("ab".toByteArray()) // truncado respecto a "abc"
        val v = SyncIntegrity.verify(f, "900150983cd24fb0d6963f7d28e17f72")
        assertTrue(v is Verdict.Mismatch)
        assertEquals("900150983cd24fb0d6963f7d28e17f72", (v as Verdict.Mismatch).expected)
    }

    private fun zip(vararg entries: Pair<String, String?>): File = File.createTempFile("save", ".zip").apply {
        deleteOnExit()
        java.util.zip.ZipOutputStream(outputStream()).use { z ->
            for ((name, content) in entries) {
                z.putNextEntry(java.util.zip.ZipEntry(name))
                content?.let { z.write(it.toByteArray()) }
                z.closeEntry()
            }
        }
    }

    @Test
    fun `contentHash of a zip matches RomM hash_zip_contents`() {
        // Desordenado y con una entrada de directorio, que RomM ignora.
        val f = zip("b/c.txt" to "x", "b/" to null, "a.txt" to "abc")
        val expected = SyncIntegrity.md5(
            ("a.txt:900150983cd24fb0d6963f7d28e17f72\n" +
                "b/c.txt:9dd4e461268c8034f5c8564e155c67a6").toByteArray(),
        )
        assertEquals(expected, SyncIntegrity.contentHash(f))
        assertEquals(Verdict.Match, SyncIntegrity.verify(f, expected))
        // RomM antiguos: MD5 crudo del zip
        assertEquals(Verdict.Match, SyncIntegrity.verify(f, SyncIntegrity.md5(f)))
        assertTrue(SyncIntegrity.verify(f, "900150983cd24fb0d6963f7d28e17f72") is Verdict.Mismatch)
    }

    @Test
    fun `contentHash of a plain save is its md5`() {
        assertEquals("900150983cd24fb0d6963f7d28e17f72", SyncIntegrity.contentHash(tmp("abc".toByteArray())))
    }

    @Test
    fun `verify never blocks on unknown hash formats`() {
        val f = tmp("abc".toByteArray())
        assertEquals(Verdict.Unverifiable, SyncIntegrity.verify(f, null))
        assertEquals(Verdict.Unverifiable, SyncIntegrity.verify(f, ""))
        // SHA-1 (40 hex): formato que RomM no usa, no se puede comparar
        assertEquals(Verdict.Unverifiable, SyncIntegrity.verify(f, "a9993e364706816aba3e25717850c26c9cd0d89d"))
    }
}
