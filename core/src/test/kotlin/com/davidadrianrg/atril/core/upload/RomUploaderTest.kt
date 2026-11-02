package com.davidadrianrg.atril.core.upload

import com.davidadrianrg.atril.core.remote.NetworkModule
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

class RomUploaderTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `retries a failed chunk instead of aborting the whole upload`() = runBlocking {
        val file = File.createTempFile("rom-upload-test", ".bin")
        try {
            RandomAccessFile(file, "rw").use { it.setLength(9L * 1024 * 1024) }

            server.enqueue(MockResponse().setBody("""{"upload_id":"u1"}"""))
            server.enqueue(MockResponse().setResponseCode(500)) // primer chunk falla
            server.enqueue(MockResponse().setBody("{}"))       // reintento OK
            server.enqueue(MockResponse().setBody("{}"))       // segundo chunk
            server.enqueue(MockResponse().setBody("{}"))       // complete

            val api = NetworkModule.createApiService(server.url("/").toString(), "test-key")
            val result = RomUploader(api).upload(file, platformId = 1)

            assertTrue(result.isSuccess)
            assertEquals(5, server.requestCount)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `reads the requested offset from a file source`() {
        val file = File.createTempFile("rom-source-test", ".bin")
        try {
            file.writeBytes(ByteArray(32) { it.toByte() })
            FileUploadSource(file).use { source ->
                assertEquals(32L, source.size)
                val chunk = source.read(offset = 16, length = 8)
                assertEquals(listOf<Byte>(16, 17, 18, 19, 20, 21, 22, 23), chunk.toList())
            }
        } finally {
            file.delete()
        }
    }
}
