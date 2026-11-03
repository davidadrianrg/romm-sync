package com.davidadrianrg.atril.core.sync

import com.davidadrianrg.atril.core.remote.RomMApiService
import com.davidadrianrg.atril.core.sync.platform.LocalSave
import com.davidadrianrg.atril.core.sync.platform.SaveHandler
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.File
import java.io.IOException
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

class SyncTransfersTest {

    private val good = "SAVEDATA-0123456789".toByteArray()
    private val goodMd5 = SyncIntegrity.md5(good)

    private fun http(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody()))

    /** Proxy cuyas excepciones se entregan por la continuación (como Retrofit). */
    private inline fun <reified T> fake(crossinline handler: (String, Array<Any?>) -> Any?): T =
        Proxy.newProxyInstance(javaClass.classLoader, arrayOf(T::class.java)) { _, m, args ->
            try {
                handler(m.name, args ?: emptyArray())
            } catch (e: Throwable) {
                @Suppress("UNCHECKED_CAST")
                (args!!.last() as Continuation<Any?>).resumeWith(Result.failure(e))
                COROUTINE_SUSPENDED
            }
        } as T

    private class Recorder {
        var extractCalls = 0
        var extractedBytes: ByteArray? = null
    }

    private fun handler(rec: Recorder): SaveHandler = fake { name, args ->
        when (name) {
            "extractDownload" -> {
                rec.extractCalls++
                rec.extractedBytes = (args[0] as File).readBytes()
                true
            }
            "prepareForUpload" -> (args[0] as LocalSave).file
            else -> null
        }
    }

    private fun cache(): File = createTempDir("synccache")

    private fun download(api: RomMApiService, rec: Recorder, cache: File, hash: String?) = runBlocking {
        SyncTransfers.download(
            api = api, saveId = 1, deviceId = "d", romId = 7, romFileName = "g.chd", platformSlug = "3do",
            fileName = "Gex [2026-11-03_06-57-36].srm", savesBasePath = "/saves", handler = handler(rec),
            cacheDir = cache, expectedHash = hash, attempts = 3, baseDelayMs = 1,
        )
    }

    @Test
    fun `good download is verified and extracted, temp is cleaned`() {
        val rec = Recorder(); val cache = cache()
        val api = fake<RomMApiService> { _, _ -> good.toResponseBody() }
        assertTrue(download(api, rec, cache, goodMd5))
        assertEquals(1, rec.extractCalls)
        assertTrue(good.contentEquals(rec.extractedBytes))
        assertEquals(0, cache.listFiles()!!.size)
    }

    @Test
    fun `corrupt download never reaches the local save`() {
        val rec = Recorder(); val cache = cache(); var calls = 0
        val api = fake<RomMApiService> { _, _ -> calls++; "SAVEDATA-0123".toResponseBody() } // truncado
        assertFalse(download(api, rec, cache, goodMd5))
        assertEquals("sin sobrescribir el save local", 0, rec.extractCalls)
        assertEquals("reintenta 3 veces", 3, calls)
        assertEquals("temporal borrado", 0, cache.listFiles()!!.size)
    }

    @Test
    fun `truncated first attempt is retried and then succeeds`() {
        val rec = Recorder(); val cache = cache(); var calls = 0
        val api = fake<RomMApiService> { _, _ -> calls++; (if (calls == 1) "SAVE".toByteArray() else good).toResponseBody() }
        assertTrue(download(api, rec, cache, goodMd5))
        assertEquals(2, calls)
        assertEquals(1, rec.extractCalls)
        assertTrue(good.contentEquals(rec.extractedBytes))
    }

    @Test
    fun `server error is retried`() {
        val rec = Recorder(); val cache = cache(); var calls = 0
        val api = fake<RomMApiService> { _, _ -> calls++; if (calls < 3) throw http(503) else good.toResponseBody() }
        assertTrue(download(api, rec, cache, goodMd5))
        assertEquals(3, calls)
    }

    @Test
    fun `401 is not retried`() {
        val rec = Recorder(); val cache = cache(); var calls = 0
        val api = fake<RomMApiService> { _, _ -> calls++; throw http(401) }
        assertFalse(download(api, rec, cache, goodMd5))
        assertEquals(1, calls)
        assertEquals(0, rec.extractCalls)
    }

    @Test
    fun `unknown hash format does not block the download`() {
        val rec = Recorder(); val cache = cache()
        val api = fake<RomMApiService> { _, _ -> good.toResponseBody() }
        assertTrue(download(api, rec, cache, null))
        assertEquals(1, rec.extractCalls)
    }

    @Test
    fun `network failure fails cleanly after retries and leaves no temp file`() {
        val rec = Recorder(); val cache = cache(); var calls = 0
        val api = fake<RomMApiService> { _, _ -> calls++; throw IOException("down") }
        assertFalse(download(api, rec, cache, goodMd5))
        assertEquals(3, calls)
        assertEquals(0, cache.listFiles()!!.size)
    }

    private fun upload(api: RomMApiService) = runBlocking {
        val f = File.createTempFile("save", ".srm").apply { deleteOnExit(); writeBytes(good) }
        SyncTransfers.upload(
            api, LocalSave(7, "Gex.srm", f, 0L, "x"), 7, "d", handler(Recorder()), attempts = 3, baseDelayMs = 1,
        )
    }

    @Test
    fun `upload retries transient errors`() {
        var calls = 0
        val api = fake<RomMApiService> { _, _ -> calls++; if (calls < 2) throw IOException("reset") else "".toResponseBody() }
        assertTrue(upload(api))
        assertEquals(2, calls)
    }

    @Test
    fun `upload 403 fails at once`() {
        var calls = 0
        val api = fake<RomMApiService> { _, _ -> calls++; throw http(403) }
        assertFalse(upload(api))
        assertEquals(1, calls)
    }
}
