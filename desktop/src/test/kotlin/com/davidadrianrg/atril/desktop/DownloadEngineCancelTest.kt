package com.davidadrianrg.atril.desktop

import com.davidadrianrg.atril.core.download.PathMapper
import com.davidadrianrg.atril.core.remote.RomMApiService
import com.davidadrianrg.atril.core.remote.dto.RomDto
import java.io.File
import java.lang.reflect.Proxy
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.BufferedSource
import okio.buffer
import okio.source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Response

class DownloadEngineCancelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val rom = RomDto(id = 1, name = "Test", fileName = "test.bin", platformId = 1, platformSlug = "testplat")

    /**
     * Cuerpo de [size] bytes servido despacio: el motor notifica progreso como mucho
     * cada 500 ms, así que hace falta tiempo para que haya un segundo informe en el
     * que se detecte la cancelación.
     */
    private fun slowBody(size: Long): ResponseBody = object : ResponseBody() {
        override fun contentType() = "application/octet-stream".toMediaType()
        override fun contentLength() = size
        override fun source(): BufferedSource = object : java.io.InputStream() {
            var left = size
            override fun read(): Int = if (left-- > 0) 0 else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (left <= 0) return -1
                Thread.sleep(5)
                val n = minOf(len.toLong(), left, 8192L).toInt()
                left -= n
                return n
            }
        }.source().buffer()
    }

    /** API falsa: solo downloadRom; devuelve [bodySize] bytes (206 si hay Range). */
    private fun fakeApi(bodySize: Int, slow: Boolean = true): RomMApiService =
        Proxy.newProxyInstance(
            RomMApiService::class.java.classLoader,
            arrayOf(RomMApiService::class.java),
        ) { _, method, args ->
            check(method.name == "downloadRom") { "inesperado: ${method.name}" }
            val range = args[2] as String?
            val body = if (slow) slowBody(bodySize.toLong())
            else ByteArray(bodySize).toResponseBody("application/octet-stream".toMediaType())
            if (range != null) {
                val raw = okhttp3.Response.Builder()
                    .code(206).message("Partial Content").protocol(Protocol.HTTP_1_1)
                    .request(Request.Builder().url("http://localhost/").build())
                    .build()
                Response.success(body, raw)
            } else {
                Response.success(body)
            }
        } as RomMApiService

    private fun targetFile(root: File) = File(PathMapper.getPlatformDir(root.absolutePath, "testplat"), "test.bin")

    /** Descarga y cancela el Job tras el primer progreso. */
    private fun downloadAndCancel(engine: DownloadEngine) = runBlocking {
        lateinit var job: Job
        job = launch(start = CoroutineStart.LAZY) { engine.download(rom) { job.cancel() } }
        job.start()
        job.join()
        assertTrue(job.isCancelled)
    }

    @Test
    fun userCancelDeletesTheFreshPartial() {
        val root = tmp.newFolder("roms")
        val engine = DownloadEngine(fakeApi(4_000_000), root, tmp.newFolder("cache"), discardPartialOnCancel = { true })
        downloadAndCancel(engine)
        assertFalse(targetFile(root).exists())
    }

    @Test
    fun nonUserCancelKeepsThePartialForResume() {
        val root = tmp.newFolder("roms")
        val engine = DownloadEngine(fakeApi(4_000_000), root, tmp.newFolder("cache"), discardPartialOnCancel = { false })
        downloadAndCancel(engine)
        assertTrue(targetFile(root).isFile)
        assertTrue(targetFile(root).length() > 0)
    }

    @Test
    fun userCancelWhileResumingDeletesTheOldPartialToo() {
        val root = tmp.newFolder("roms")
        val target = targetFile(root).also { it.parentFile.mkdirs(); it.writeBytes(ByteArray(1024)) }
        val engine = DownloadEngine(fakeApi(4_000_000), root, tmp.newFolder("cache"), discardPartialOnCancel = { true })
        downloadAndCancel(engine)
        assertFalse(target.exists())
    }

    @Test
    fun completedDownloadIsNeverDeleted() {
        val root = tmp.newFolder("roms")
        val engine = DownloadEngine(fakeApi(100_000, slow = false), root, tmp.newFolder("cache"), discardPartialOnCancel = { true })
        val result = runBlocking { engine.download(rom) { } }
        assertFalse(result.failed)
        assertEquals(100_000L, targetFile(root).length())
    }
}
