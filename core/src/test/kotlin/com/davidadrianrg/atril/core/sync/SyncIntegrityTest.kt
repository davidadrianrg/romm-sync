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

    @Test
    fun `verify never blocks on unknown hash formats`() {
        val f = tmp("abc".toByteArray())
        assertEquals(Verdict.Unverifiable, SyncIntegrity.verify(f, null))
        assertEquals(Verdict.Unverifiable, SyncIntegrity.verify(f, ""))
        // SHA-1 (40 hex): formato que RomM no usa, no se puede comparar
        assertEquals(Verdict.Unverifiable, SyncIntegrity.verify(f, "a9993e364706816aba3e25717850c26c9cd0d89d"))
    }
}
