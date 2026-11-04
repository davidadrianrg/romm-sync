package com.davidadrianrg.atril.core.auth

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DeviceAuthTest {
    private lateinit var server: MockWebServer
    private lateinit var client: DeviceAuthClient

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        client = DeviceAuthClient(server.url("/").toString())
    }
    @After fun tearDown() = server.shutdown()

    private val req = DeviceAuthRequest("dev-1", "Atril (Pixel)", "android", "1.0")
    private val initOk = """{"device_code":"DC","user_code":"ABCD1234","verification_path":"/pair/device",
        "verification_path_complete":"/pair/device?user_code=ABCD1234","expires_in":600,"interval":5}"""
    private fun err(code: Int, detail: String) =
        MockResponse().setResponseCode(code).setBody("""{"detail":"$detail"}""")

    @Test fun initBuildsPayloadAndAbsoluteUrl() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody(initOk))
        val code = client.init(req).getOrThrow()
        assertEquals("DC", code.deviceCode)
        assertEquals("ABCD1234", code.userCode)
        assertEquals(server.url("/").toString().trimEnd('/') + "/pair/device?user_code=ABCD1234", code.verificationUrl)
        assertEquals(5, code.intervalSeconds)
        val r = server.takeRequest()
        assertEquals("/api/auth/device/init", r.path)
        val body = r.body.readUtf8()
        assertTrue(body, body.contains("\"client_device_identifier\":\"dev-1\""))
        assertTrue(body, body.contains("\"client\":\"atril\""))
        assertTrue(body, body.contains("\"requested_scopes\":[\"me.read\""))
        assertNull(r.getHeader("Authorization"))
    }

    @Test fun initClassifiesErrors() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals(DeviceAuthError.UNSUPPORTED, (client.init(req).exceptionOrNull() as DeviceAuthException).kind)
        server.enqueue(MockResponse().setResponseCode(403))
        assertEquals(DeviceAuthError.UNAUTHORIZED, (client.init(req).exceptionOrNull() as DeviceAuthException).kind)
        server.enqueue(MockResponse().setResponseCode(500))
        assertEquals(DeviceAuthError.SERVER, (client.init(req).exceptionOrNull() as DeviceAuthException).kind)
        server.shutdown()
        assertEquals(DeviceAuthError.NETWORK, (client.init(req).exceptionOrNull() as DeviceAuthException).kind)
    }

    @Test fun pollMapsRfc8628Errors() = runBlocking {
        server.enqueue(err(400, "authorization_pending")); assertEquals(DevicePoll.Pending, client.pollOnce("x"))
        server.enqueue(err(400, "slow_down")); assertEquals(DevicePoll.SlowDown, client.pollOnce("x"))
        server.enqueue(err(400, "access_denied")); assertEquals(DevicePoll.Denied, client.pollOnce("x"))
        server.enqueue(err(400, "expired_token")); assertEquals(DevicePoll.Expired, client.pollOnce("x"))
        server.enqueue(MockResponse().setResponseCode(401))
        assertEquals(DeviceAuthError.UNAUTHORIZED, (client.pollOnce("x") as DevicePoll.Failure).kind)
    }

    @Test fun approvedTokenIsParsedAndNotPrinted() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"access_token":"rmm_secret","device_id":"d-9","scopes":["roms.read"],"expires_at":null}"""))
        val p = client.pollOnce("DC") as DevicePoll.Approved
        assertEquals("rmm_secret", p.token.accessToken)
        assertEquals("d-9", p.token.deviceId)
        assertFalse(p.token.toString().contains("rmm_secret"))
        assertTrue(server.takeRequest().body.readUtf8().contains("\"device_code\":\"DC\""))
    }

    private val code get() = DeviceCode("DC", "U", "u", 600, 5)

    @Test fun awaitPollsUntilApprovedHonouringInterval() = runBlocking {
        server.enqueue(err(400, "authorization_pending"))
        server.enqueue(err(400, "slow_down"))
        server.enqueue(MockResponse().setBody("""{"access_token":"t","device_id":"d","scopes":[]}"""))
        val sleeps = mutableListOf<Long>()
        val out = client.awaitApproval(code, sleep = { sleeps += it })
        assertTrue(out is DeviceAuthOutcome.Success)
        assertEquals(listOf(5000L, 5000L, 10000L), sleeps) // slow_down adds 5 s
    }

    @Test fun awaitReportsDeniedAndExpired() = runBlocking {
        server.enqueue(err(400, "access_denied"))
        assertEquals(DeviceAuthOutcome.Denied, client.awaitApproval(code, sleep = {}))
        server.enqueue(err(400, "expired_token"))
        assertEquals(DeviceAuthOutcome.Expired, client.awaitApproval(code, sleep = {}))
    }

    @Test fun awaitExpiresLocallyAtDeadline() = runBlocking {
        var t = 0L
        server.enqueue(err(400, "authorization_pending"))
        val out = client.awaitApproval(DeviceCode("DC", "U", "u", 6, 5), sleep = { t += it }, now = { t })
        assertEquals(DeviceAuthOutcome.Expired, out)
    }

    @Test fun awaitToleratesTransientNetworkThenFails() = runBlocking {
        server.shutdown()
        val out = client.awaitApproval(code, maxNetworkErrors = 2, sleep = {})
        assertEquals(DeviceAuthOutcome.Failed(DeviceAuthError.NETWORK, "ConnectException"), out)
    }

    @Test fun normalizeOrigin() {
        assertEquals("https://romm.x.org", DeviceAuthClient.normalizeOrigin("romm.x.org/"))
        assertEquals("http://10.0.0.2:8080", DeviceAuthClient.normalizeOrigin(" http://10.0.0.2:8080/?x=1 "))
        assertEquals("http://10.0.0.2:8080/romm", DeviceAuthClient.normalizeOrigin("http://10.0.0.2:8080/romm/?x=1#f"))
        assertEquals("", DeviceAuthClient.normalizeOrigin("  "))
    }

    @Test fun subpathServerUsesSubpathForApiAndQr() = runBlocking {
        val sub = DeviceAuthClient(server.url("/romm/").toString())
        server.enqueue(MockResponse().setResponseCode(201).setBody(initOk))
        val code = sub.init(req).getOrThrow()
        assertEquals("/romm/api/auth/device/init", server.takeRequest().path)
        assertEquals(server.url("/romm").toString() + "/pair/device?user_code=ABCD1234", code.verificationUrl)
    }

    @Test fun qrMatrixIsSquareAndHasFinderPattern() {
        val m = QrCode.encode("https://romm.example/pair/device?user_code=ABCD1234")
        assertTrue(m.size in 21..80)
        assertTrue(m[0, 0] && m[6, 0] && m[0, 6]) // finder corner
        assertFalse(m[7, 0]) // separator
    }
}

class QrLoginFlowTest {
    private val req = DeviceAuthRequest("dev-1", "Atril", "linux", "1.0")
    private val initOk = """{"device_code":"DC","user_code":"U1","verification_path":"/pair/device",
        "verification_path_complete":"/pair/device?user_code=U1","expires_in":600,"interval":1}"""

    @Test fun invalidUrlFailsWithoutNetwork() = runBlocking {
        val states = mutableListOf<QrLoginState>()
        runQrLogin("", req, { states += it }, { _, _ -> fail("no token expected") })
        assertEquals(listOf<QrLoginState>(QrLoginState.Failed(DeviceAuthError.INVALID_URL)), states)
    }

    @Test fun approvalPersistsTokenThenReportsSuccess() = runBlocking {
        val server = MockWebServer().also { it.start() }
        try {
            server.enqueue(MockResponse().setResponseCode(201).setBody(initOk))
            server.enqueue(MockResponse().setBody("""{"access_token":"rmm_x","device_id":"d","scopes":[]}"""))
            val states = mutableListOf<QrLoginState>()
            var saved: Pair<String, String>? = null
            val url = server.url("/").toString()
            runQrLogin(url, req, { states += it }, { o, t -> saved = o to t.accessToken },
                client = DeviceAuthClient(url))
            // poll uses real delay(1s); acceptable for one test
            assertEquals(url.trimEnd('/') to "rmm_x", saved)
            assertTrue(states.first() is QrLoginState.Starting)
            assertTrue(states[1] is QrLoginState.Waiting)
            assertEquals(QrLoginState.Success, states.last())
        } finally { server.shutdown() }
    }

    @Test fun denialDoesNotPersist() = runBlocking {
        val server = MockWebServer().also { it.start() }
        try {
            server.enqueue(MockResponse().setResponseCode(201).setBody(initOk))
            server.enqueue(MockResponse().setResponseCode(400).setBody("""{"detail":"access_denied"}"""))
            val states = mutableListOf<QrLoginState>()
            val url = server.url("/").toString()
            runQrLogin(url, req, { states += it }, { _, _ -> fail("must not save") }, client = DeviceAuthClient(url))
            assertEquals(QrLoginState.Denied, states.last())
        } finally { server.shutdown() }
    }
}

class ReleaseSafetyTest {
    private lateinit var server: MockWebServer
    @Before fun up() { server = MockWebServer().also { it.start() } }
    @After fun down() = server.shutdown()

    @Test fun garbageInitBodyIsAFailureNotACrash() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("<html>not json</html>"))
        val r = DeviceAuthClient(server.url("/").toString()).init(DeviceAuthRequest("d", "n", "linux", "1"))
        assertEquals(DeviceAuthError.SERVER, (r.exceptionOrNull() as DeviceAuthException).kind)
    }

    @Test fun initBodyMissingFieldsIsAFailure() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{"device_code":"x"}"""))
        val r = DeviceAuthClient(server.url("/").toString()).init(DeviceAuthRequest("d", "n", "linux", "1"))
        assertTrue(r.isFailure)
    }

    @Test fun tokenBodyWithoutTokenIsAFailureNotApproval() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"device_id":"d"}"""))
        assertTrue(DeviceAuthClient(server.url("/").toString()).pollOnce("x") is DevicePoll.Failure)
    }

    /** R8 renames fields of plain data classes, which breaks reflective Moshi with an Error (app crash). */
    @Test fun authAndMediaCodeDoNotUseReflectiveMoshi() {
        val roots = listOf("src/main/kotlin/com/davidadrianrg/atril/core/auth", "src/main/kotlin/com/davidadrianrg/atril/core/media")
        roots.forEach { r ->
            java.io.File(r).walkTopDown().filter { it.extension == "kt" }.forEach { f ->
                assertFalse("${f.name} must not use KotlinJsonAdapterFactory (R8-unsafe)", f.readText().contains("KotlinJsonAdapterFactory"))
            }
        }
    }
}
