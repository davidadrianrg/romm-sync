package com.davidadrianrg.atril.desktop

import com.davidadrianrg.atril.core.i18n.tr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Una sync que falla entera (p. ej. servidor caído) no trae fallos por archivo: debe verse como error. */
class SyncFailureStateTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before
    fun requireIsolatedConfig() {
        // syncSaves() escribe en DesktopConfig: nunca contra la configuración real.
        assertTrue(System.getProperty("java.util.prefs.userRoot").orEmpty().contains("test-home"))
        DesktopConfig.deviceId = null
    }

    @After
    fun tearDown() {
        scope.cancel()
        DesktopConfig.serverUrl = ""
        DesktopConfig.apiKey = ""
        DesktopConfig.deviceId = null
    }

    private fun test(block: suspend () -> Unit) = runBlocking { withTimeout(20_000) { block() } }

    private suspend fun DesktopAppState.syncAndWait() {
        syncSaves()
        syncing.first { !it }
    }

    @Test
    fun `whole-sync failure is exposed as an error, without per-file failures`() = test {
        DesktopConfig.serverUrl = "http://127.0.0.1:1/" // nadie escucha: falla el registro del dispositivo
        DesktopConfig.apiKey = "k"
        val state = DesktopAppState(scope)

        state.syncAndWait()

        assertEquals(tr("sync.error.device_registration_detail"), state.syncError.value)
        assertTrue(state.lastFailed.value.isEmpty())
    }

    @Test
    fun `retrying clears the error once the sync no longer fails`() = test {
        DesktopConfig.serverUrl = ""
        DesktopConfig.apiKey = ""
        val state = DesktopAppState(scope)
        state.syncAndWait()
        assertEquals(tr("common.server_not_configured"), state.syncError.value)

        // Con dispositivo ya registrado y sin juegos descargados el ciclo termina sin error ni red.
        DesktopConfig.serverUrl = "http://127.0.0.1:1/"
        DesktopConfig.apiKey = "k"
        DesktopConfig.deviceId = "device"
        state.syncAndWait()

        assertNull(state.syncError.value)
    }
}
