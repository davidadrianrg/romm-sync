package com.davidadrianrg.atril.core.sync

import com.davidadrianrg.atril.core.remote.NetworkModule
import com.davidadrianrg.atril.core.sync.platform.LocalSave
import com.davidadrianrg.atril.core.sync.platform.SaveHandler
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy

/**
 * Prueba real SOLO DE LECTURA contra un RomM, con el save del juego de prueba
 * (Gex). Se omite salvo que existan ATRIL_E2E_URL, ATRIL_E2E_KEY,
 * ATRIL_E2E_SAVE_ID y ATRIL_E2E_MD5 (el `content_hash` que anuncia el servidor).
 * Nunca guarda credenciales en el repo.
 */
class SyncTransfersRealServerTest {

    private fun env(name: String) = System.getenv(name)?.takeIf { it.isNotBlank() }

    private class Capture { var calls = 0; var bytes: ByteArray? = null }

    private fun handler(c: Capture): SaveHandler = Proxy.newProxyInstance(
        javaClass.classLoader, arrayOf(SaveHandler::class.java),
    ) { _, m, args ->
        if (m.name == "extractDownload") { c.calls++; c.bytes = (args[0] as File).readBytes(); true } else null
    } as SaveHandler

    @Test
    fun `real download is verified against the server md5`() {
        val url = env("ATRIL_E2E_URL"); val key = env("ATRIL_E2E_KEY")
        val saveId = env("ATRIL_E2E_SAVE_ID")?.toIntOrNull(); val md5 = env("ATRIL_E2E_MD5")
        assumeTrue("e2e env not set", url != null && key != null && saveId != null && md5 != null)
        val api = NetworkModule.createApiService(url!!, key!!)
        val cache = createTempDir("e2e")
        val device = env("ATRIL_E2E_DEVICE") ?: "e2e"

        // 1) descarga íntegra: coincide el MD5 anunciado y se entrega al handler
        val ok = Capture()
        val good = runBlocking {
            SyncTransfers.download(api, saveId!!, device, 2, "Gex.chd", "3do", "Gex [x].srm", "/tmp", handler(ok), cache, md5, log = ::println)
        }
        assertTrue("download should succeed", good)
        assertEquals(1, ok.calls)
        assertEquals(md5, SyncIntegrity.md5(ok.bytes!!))

        // 2) hash esperado erróneo: NO se entrega nada al handler (save local intacto)
        val bad = Capture()
        val rejected = runBlocking {
            SyncTransfers.download(
                api, saveId!!, device, 2, "Gex.chd", "3do", "Gex [x].srm", "/tmp", handler(bad), cache,
                "00000000000000000000000000000000", attempts = 2, baseDelayMs = 1, log = ::println,
            )
        }
        assertFalse("corrupt/mismatching download must be rejected", rejected)
        assertEquals(0, bad.calls)
        assertEquals("sin temporales", 0, cache.listFiles()!!.size)
    }
}
