package com.davidadrianrg.atril.desktop

import com.davidadrianrg.atril.core.i18n.tr
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class LibraryLoadErrorTest {

    private var server: HttpServer? = null
    private var romsStatus = 200
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before
    fun requireIsolatedConfig() {
        // connect()/disconnect() escriben en DesktopConfig: nunca contra la configuración real.
        assertTrue(System.getProperty("java.util.prefs.userRoot").orEmpty().contains("test-home"))
        DesktopConfig.serverUrl = ""
        DesktopConfig.apiKey = ""
    }

    @After
    fun tearDown() {
        scope.cancel()
        server?.stop(0)
    }

    private fun startServer(port: Int = 0): Int {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
        s.createContext("/api/platforms") { ex ->
            val body = """[{"id":1,"slug":"gba","name":"Game Boy Advance"}]""".toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        s.createContext("/api/roms") { ex ->
            val body = """{"items":[]}""".toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(romsStatus, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        s.start()
        server = s
        return s.address.port
    }

    private fun DesktopAppState.url(port: Int) = "http://127.0.0.1:$port/"

    private suspend fun DesktopAppState.awaitIdle() {
        loadingPlatforms.first { !it }
        loadingRoms.first { !it }
    }

    private fun networkError() = tr("android.library.error_network")

    private fun test(block: suspend () -> Unit) = runBlocking { withTimeout(20_000) { block() } }

    @Test
    fun `failed retry keeps the error, and retry recovers once the server is back`() = test {
        val port = startServer()
        val state = DesktopAppState(scope)
        state.connect(state.url(port), "k", silent = true)
        state.connected.first { it }
        state.navigate(Section.LIBRARY)
        state.awaitIdle()
        assertNull(state.romsError.value)

        server!!.stop(0)
        state.refreshCurrentView() // "Reintentar" con el servidor caído
        state.romsError.first { it != null }
        state.connected.first { !it }
        state.awaitIdle()
        assertEquals(networkError(), state.romsError.value)

        startServer(port)
        state.refreshCurrentView()
        state.connected.first { it }
        state.awaitIdle()
        assertNull(state.romsError.value)
    }

    @Test
    fun `server down at startup reports the error and retry recovers`() = test {
        val port = startServer()
        server!!.stop(0)
        val state = DesktopAppState(scope)
        state.connect(state.url(port), "k", silent = true)
        state.romsError.first { it != null }
        assertEquals(networkError(), state.romsError.value)

        // Abrir la Biblioteca con el servidor aún caído no borra el error.
        state.awaitIdle()
        state.navigate(Section.LIBRARY)
        state.awaitIdle()
        assertEquals(networkError(), state.romsError.value)

        startServer(port)
        state.refreshCurrentView()
        state.connected.first { it }
        state.awaitIdle()
        assertNull(state.romsError.value)
    }

    @Test
    fun `http error while connected is reported and disconnect clears it`() = test {
        val port = startServer()
        romsStatus = 503
        val state = DesktopAppState(scope)
        state.connect(state.url(port), "k", silent = true)
        state.connected.first { it }
        state.navigate(Section.LIBRARY)
        state.romsError.first { it != null }
        assertEquals(tr("android.library.error_server"), state.romsError.value)
        assertTrue(state.connected.value)

        state.disconnect()
        assertNull(state.romsError.value)
        assertFalse(state.connected.value)
    }

    @Test
    fun `messages are localized by failure type`() {
        fun http(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody()))
        assertEquals(tr("android.library.error_auth"), libraryLoadErrorMessage(http(401)))
        assertEquals(tr("android.library.error_auth"), libraryLoadErrorMessage(http(403)))
        assertEquals(tr("android.library.error_not_found"), libraryLoadErrorMessage(http(404)))
        assertEquals(tr("android.library.error_server"), libraryLoadErrorMessage(http(503)))
        assertEquals(tr("android.library.error_unknown"), libraryLoadErrorMessage(http(418)))
        assertEquals(networkError(), libraryLoadErrorMessage(IOException("Failed to connect to /10.0.0.1")))
        assertEquals(tr("android.library.error_unknown"), libraryLoadErrorMessage(IllegalStateException("boom")))
    }
}
