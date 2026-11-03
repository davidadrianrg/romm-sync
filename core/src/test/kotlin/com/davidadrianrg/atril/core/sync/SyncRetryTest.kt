package com.davidadrianrg.atril.core.sync

import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class SyncRetryTest {

    private fun http(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody()))

    @Test
    fun `classifies transient and permanent errors`() {
        assertTrue(SyncRetry.isTransient(IOException("reset")))
        assertTrue(SyncRetry.isTransient(http(500)))
        assertTrue(SyncRetry.isTransient(http(503)))
        assertTrue(SyncRetry.isTransient(http(429)))
        assertTrue(SyncRetry.isTransient(http(408)))
        assertFalse(SyncRetry.isTransient(http(401)))
        assertFalse(SyncRetry.isTransient(http(403)))
        assertFalse(SyncRetry.isTransient(http(404)))
        assertFalse(SyncRetry.isTransient(IllegalStateException("bug")))
        assertFalse(SyncRetry.isTransient(kotlinx.coroutines.CancellationException("x")))
    }

    @Test
    fun `backoff grows exponentially`() {
        assertEquals(800L, SyncRetry.backoffMs(1))
        assertEquals(1600L, SyncRetry.backoffMs(2))
        assertEquals(3200L, SyncRetry.backoffMs(3))
    }

    @Test
    fun `recovers after transient failures`() {
        var calls = 0
        val out = runBlocking {
            SyncRetry.withRetry(attempts = 3, baseDelayMs = 1) {
                calls++
                if (calls < 3) throw IOException("flaky") else "ok"
            }
        }
        assertEquals("ok", out)
        assertEquals(3, calls)
    }

    @Test
    fun `gives up after the last attempt and rethrows`() {
        var calls = 0
        try {
            runBlocking { SyncRetry.withRetry(attempts = 3, baseDelayMs = 1) { calls++; throw http(503) } }
            fail("should throw")
        } catch (e: HttpException) {
            assertEquals(503, e.code())
        }
        assertEquals(3, calls)
    }

    @Test
    fun `does not retry permanent errors`() {
        var calls = 0
        try {
            runBlocking { SyncRetry.withRetry(attempts = 3, baseDelayMs = 1) { calls++; throw http(401) } }
            fail("should throw")
        } catch (e: HttpException) {
            assertEquals(401, e.code())
        }
        assertEquals(1, calls)
    }

    @Test
    fun `reports each retry`() {
        val seen = mutableListOf<Int>()
        runBlocking {
            SyncRetry.withRetry(attempts = 3, baseDelayMs = 1, onRetry = { a, _ -> seen += a }) { n ->
                if (n < 3) throw IOException("x") else Unit
            }
        }
        assertEquals(listOf(1, 2), seen)
    }
}
