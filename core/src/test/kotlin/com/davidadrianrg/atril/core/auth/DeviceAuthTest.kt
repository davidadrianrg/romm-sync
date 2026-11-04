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
        assertEquals("http://10.0.0.2:8080", DeviceAuthClient.normalizeOrigin(" http://10.0.0.2:8080/app?x=1 "))
        assertEquals("", DeviceAuthClient.normalizeOrigin("  "))
    }

    @Test fun qrMatrixIsSquareAndHasFinderPattern() {
        val m = QrCode.encode("https://romm.example/pair/device?user_code=ABCD1234")
        assertTrue(m.size in 21..80)
        assertTrue(m[0, 0] && m[6, 0] && m[0, 6]) // finder corner
        assertFalse(m[7, 0]) // separator
    }
}
