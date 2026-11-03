package com.davidadrianrg.atril.core.scrape

import com.davidadrianrg.atril.core.scrape.PhysicalMediaScrape.TaskState
import com.squareup.moshi.Moshi
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class PhysicalMediaScrapeTest {

    @Test
    fun `payload asks RomM to update one rom with ss only`() {
        val json = Moshi.Builder().addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()
            .adapter(com.davidadrianrg.atril.core.remote.dto.RomScrapeRequest::class.java)
            .toJson(PhysicalMediaScrape.payload(42))
        assertEquals("""{"apis":["ss"],"roms_ids":[42],"type":"update"}""", json.sorted())
    }

    // Orden de claves no garantizado por moshi-reflect/codegen: lo normalizamos.
    private fun String.sorted(): String {
        val map = Moshi.Builder().build().adapter(Any::class.java).fromJson(this) as Map<*, *>
        return map.toSortedMap(compareBy { it.toString() }).entries.joinToString(",", "{", "}") { (k, v) ->
            "\"$k\":" + when (v) {
                is String -> "\"$v\""
                is List<*> -> v.joinToString(",", "[", "]") { x -> if (x is String) "\"$x\"" else (x as Double).toInt().toString() }
                else -> v.toString()
            }
        }
    }

    @Test
    fun `parses task states`() {
        fun st(s: String) = PhysicalMediaScrape.parseTask("""{"task_id":"abc","status":"$s"}""").state
        assertEquals(TaskState.QUEUED, st("queued"))
        assertEquals(TaskState.RUNNING, st("started"))
        assertEquals(TaskState.FINISHED, st("finished"))
        assertEquals(TaskState.FAILED, st("failed"))
        assertEquals(TaskState.FAILED, st("canceled"))
        assertEquals(TaskState.UNKNOWN, st("weird"))
    }

    @Test
    fun `parses task id and error and terminal flag`() {
        val t = PhysicalMediaScrape.parseTask("""{"task_id":"t-1","status":"failed","error":"boom"}""")
        assertEquals("t-1", t.taskId)
        assertEquals("boom", t.error)
        assertTrue(t.isTerminal)
        assertFalse(PhysicalMediaScrape.parseTask("""{"id":"x","status":"queued"}""").isTerminal)
        assertEquals("x", PhysicalMediaScrape.parseTask("""{"id":"x","status":"queued"}""").taskId)
    }

    @Test
    fun `garbage body is unknown not a crash`() {
        val t = PhysicalMediaScrape.parseTask("not json")
        assertNull(t.taskId)
        assertEquals(TaskState.UNKNOWN, t.state)
    }

    @Test
    fun `classifies auth and network errors`() {
        fun http(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody()))
        assertEquals(PhysicalMediaScrape.Outcome.Unauthorized, PhysicalMediaScrape.classify(http(401)))
        assertEquals(PhysicalMediaScrape.Outcome.Forbidden, PhysicalMediaScrape.classify(http(403)))
        assertTrue(PhysicalMediaScrape.classify(java.io.IOException("down")) is PhysicalMediaScrape.Outcome.Network)
        assertTrue(PhysicalMediaScrape.classify(http(500)) is PhysicalMediaScrape.Outcome.TaskFailed)
    }

    @Test
    fun `every outcome has a user message`() {
        listOf(
            PhysicalMediaScrape.Outcome.NoAsset,
            PhysicalMediaScrape.Outcome.Unauthorized,
            PhysicalMediaScrape.Outcome.Forbidden,
            PhysicalMediaScrape.Outcome.TimedOut,
            PhysicalMediaScrape.Outcome.TaskFailed(null),
            PhysicalMediaScrape.Outcome.Network("x"),
        ).forEach { assertFalse(PhysicalMediaScrape.describe(it).startsWith("scrape.")) }
    }

    // ── run(): errores contra un RomMApiService simulado (sin red real) ──

    /** API falsa: cada método suspend responde con lo que devuelva [handler]. */
    private fun fakeApi(handler: (String) -> Any?): com.davidadrianrg.atril.core.remote.RomMApiService =
        java.lang.reflect.Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(com.davidadrianrg.atril.core.remote.RomMApiService::class.java),
        ) { _, method, args ->
            try {
                handler(method.name)
            } catch (e: Throwable) {
                // Un Proxy envuelve las excepciones comprobadas (IOException);
                // las entregamos por la continuación, como hace Retrofit.
                @Suppress("UNCHECKED_CAST")
                (args.last() as kotlin.coroutines.Continuation<Any?>).resumeWith(Result.failure(e))
                kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
            }
        } as com.davidadrianrg.atril.core.remote.RomMApiService

    private fun body(json: String) = json.toResponseBody()

    private fun runScrape(api: com.davidadrianrg.atril.core.remote.RomMApiService) =
        kotlinx.coroutines.runBlocking {
            PhysicalMediaScrape.run(api, romId = 42, pollIntervalMs = 1, maxWaitMs = 20)
        }

    private fun http(code: Int) = HttpException(Response.error<Any>(code, "".toResponseBody()))

    @Test
    fun `401 on start is Unauthorized`() {
        val api = fakeApi { throw http(401) }
        assertEquals(PhysicalMediaScrape.Outcome.Unauthorized, runScrape(api))
    }

    @Test
    fun `403 on start is Forbidden`() {
        val api = fakeApi { throw http(403) }
        assertEquals(PhysicalMediaScrape.Outcome.Forbidden, runScrape(api))
    }

    @Test
    fun `network failure on start is Network`() {
        val api = fakeApi { throw java.io.IOException("connection reset") }
        val out = runScrape(api)
        assertTrue(out is PhysicalMediaScrape.Outcome.Network)
        assertEquals("connection reset", (out as PhysicalMediaScrape.Outcome.Network).message)
    }

    @Test
    fun `401 while polling the task is Unauthorized`() {
        val api = fakeApi { name ->
            if (name == "startRomScrape") body("""{"task_id":"t1","status":"queued"}""") else throw http(401)
        }
        assertEquals(PhysicalMediaScrape.Outcome.Unauthorized, runScrape(api))
    }

    @Test
    fun `failed task reports its error`() {
        val api = fakeApi { name ->
            if (name == "startRomScrape") body("""{"task_id":"t1","status":"queued"}""")
            else body("""{"task_id":"t1","status":"failed","error":"ss disabled"}""")
        }
        val out = runScrape(api)
        assertEquals(PhysicalMediaScrape.Outcome.TaskFailed("ss disabled"), out)
    }

    @Test
    fun `task that never finishes times out`() {
        val api = fakeApi { name ->
            if (name == "startRomScrape") body("""{"task_id":"t1","status":"queued"}""")
            else body("""{"task_id":"t1","status":"started"}""")
        }
        assertEquals(PhysicalMediaScrape.Outcome.TimedOut, runScrape(api))
    }

    @Test
    fun `progress callback sees the task states`() {
        val seen = mutableListOf<TaskState>()
        val api = fakeApi { name ->
            if (name == "startRomScrape") body("""{"task_id":"t1","status":"queued"}""")
            else body("""{"task_id":"t1","status":"failed"}""")
        }
        kotlinx.coroutines.runBlocking {
            PhysicalMediaScrape.run(api, 42, pollIntervalMs = 1, maxWaitMs = 20) { seen += it }
        }
        assertEquals(listOf(TaskState.QUEUED, TaskState.FAILED), seen)
    }
}
