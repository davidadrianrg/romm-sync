package com.davidadrianrg.atril.core.auth

import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * RomM "device authorization" flow (RFC 8628 style):
 *
 *  1. `POST /api/auth/device/init` → device_code + user_code + verification path.
 *  2. The user opens `origin + verification_path_complete` (QR) on an already
 *     authenticated RomM web session and approves the device.
 *  3. We poll `POST /api/auth/device/token` until we get an access token.
 *
 * Pure JVM so Android and desktop share it. The token is never logged.
 */
object DeviceAuth {
    /** Minimal scopes the app needs: library, saves/states, device registration. */
    val DEFAULT_SCOPES: List<String> = listOf(
        "me.read", "roms.read", "platforms.read", "collections.read",
        "assets.read", "assets.write", "devices.read", "devices.write",
        "firmware.read",
    )

    const val CLIENT = "atril"
}

data class DeviceAuthRequest(
    val clientDeviceIdentifier: String,
    val name: String,
    val platform: String,
    val clientVersion: String?,
    val scopes: List<String> = DeviceAuth.DEFAULT_SCOPES,
)

/** Result of `init`: what to show the user and how to poll. */
data class DeviceCode(
    val deviceCode: String,
    val userCode: String,
    /** Absolute URL to encode in the QR (server origin + path with `?user_code=`). */
    val verificationUrl: String,
    val expiresInSeconds: Int,
    val intervalSeconds: Int,
)

class DeviceToken(val accessToken: String, val deviceId: String, val scopes: List<String>) {
    // Keep the secret out of logs / crash reports.
    override fun toString() = "DeviceToken(deviceId=$deviceId, scopes=$scopes, accessToken=***)"
}

sealed interface DevicePoll {
    data object Pending : DevicePoll
    data object SlowDown : DevicePoll
    data object Denied : DevicePoll
    data object Expired : DevicePoll
    data class Approved(val token: DeviceToken) : DevicePoll
    data class Failure(val kind: DeviceAuthError, val detail: String? = null) : DevicePoll
}

enum class DeviceAuthError { NETWORK, UNAUTHORIZED, SERVER, INVALID_URL, UNSUPPORTED }

sealed interface DeviceAuthOutcome {
    data class Success(val token: DeviceToken) : DeviceAuthOutcome
    data object Denied : DeviceAuthOutcome
    data object Expired : DeviceAuthOutcome
    data class Failed(val kind: DeviceAuthError, val detail: String? = null) : DeviceAuthOutcome
}

class DeviceAuthClient(
    serverUrl: String,
    private val http: OkHttpClient = OkHttpClient(),
) {
    private val origin: String = normalizeOrigin(serverUrl)
    private val base = origin.toHttpUrlOrNull()
    // Sin reflexión: en release R8 renombra los campos de las clases de datos y el Moshi
    // reflexivo lanza un Error (no una Exception) que tumbaba la app al pulsar «QR».
    private val anyAdapter = Moshi.Builder().build().adapter(Any::class.java)
    private val json = "application/json".toMediaType()

    val isValid: Boolean get() = base != null

    /** Starts the flow. Returns null in [DeviceAuthOutcome.Failed] form via [Result]. */
    suspend fun init(req: DeviceAuthRequest): Result<DeviceCode> = withContext(Dispatchers.IO) {
        val url = base?.newBuilder()?.addEncodedPathSegments("api/auth/device/init")?.build()
            ?: return@withContext Result.failure(DeviceAuthException(DeviceAuthError.INVALID_URL))
        val body = anyAdapter.toJson(
            mapOf(
                "client_device_identifier" to req.clientDeviceIdentifier,
                "name" to req.name,
                "client" to DeviceAuth.CLIENT,
                "platform" to req.platform,
                "client_version" to req.clientVersion,
                "requested_scopes" to req.scopes,
            ),
        ).toRequestBody(json)
        try {
            http.newCall(Request.Builder().url(url).post(body).build()).execute().use { r ->
                val text = r.body?.string().orEmpty()
                if (!r.isSuccessful) {
                    return@withContext Result.failure(DeviceAuthException(classify(r.code), "HTTP ${r.code}"))
                }
                val m = parseMap(text)
                val deviceCode = m?.str("device_code")
                val userCode = m?.str("user_code")
                val path = m?.str("verification_path_complete")
                if (deviceCode == null || userCode == null || path == null) {
                    return@withContext Result.failure(DeviceAuthException(DeviceAuthError.SERVER, "bad init body"))
                }
                Result.success(
                    DeviceCode(
                        deviceCode = deviceCode,
                        userCode = userCode,
                        verificationUrl = origin + path,
                        expiresInSeconds = m.int("expires_in") ?: 600,
                        intervalSeconds = (m.int("interval") ?: 5).coerceAtLeast(1),
                    ),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Result.failure(DeviceAuthException(DeviceAuthError.NETWORK, e.javaClass.simpleName))
        } catch (e: Throwable) {
            Result.failure(DeviceAuthException(DeviceAuthError.SERVER, e.javaClass.simpleName))
        }
    }

    /** One poll of the token endpoint. */
    suspend fun pollOnce(deviceCode: String): DevicePoll = withContext(Dispatchers.IO) {
        val url = base?.newBuilder()?.addEncodedPathSegments("api/auth/device/token")?.build()
            ?: return@withContext DevicePoll.Failure(DeviceAuthError.INVALID_URL)
        val body = anyAdapter.toJson(mapOf("device_code" to deviceCode)).toRequestBody(json)
        try {
            http.newCall(Request.Builder().url(url).post(body).build()).execute().use { r ->
                val text = r.body?.string().orEmpty()
                if (r.isSuccessful) {
                    val t = parseMap(text)
                    val token = t?.str("access_token")
                    val deviceId = t?.str("device_id")
                    if (token == null || deviceId == null) {
                        return@withContext DevicePoll.Failure(DeviceAuthError.SERVER, "bad token body")
                    }
                    val scopes = (t["scopes"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                    return@withContext DevicePoll.Approved(DeviceToken(token, deviceId, scopes))
                }
                parsePollError(r.code, text)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            DevicePoll.Failure(DeviceAuthError.NETWORK, e.javaClass.simpleName)
        } catch (e: Throwable) {
            DevicePoll.Failure(DeviceAuthError.SERVER, e.javaClass.simpleName)
        }
    }

    /**
     * Polls until approved / denied / expired / failed. Respects [DeviceCode.intervalSeconds]
     * (+5 s on `slow_down`) and [DeviceCode.expiresInSeconds]. Transient network errors are
     * tolerated up to [maxNetworkErrors] consecutive times. [sleep] and [now] are injectable.
     */
    suspend fun awaitApproval(
        code: DeviceCode,
        maxNetworkErrors: Int = 3,
        sleep: suspend (Long) -> Unit = { delay(it) },
        now: () -> Long = System::currentTimeMillis,
    ): DeviceAuthOutcome {
        val deadline = now() + code.expiresInSeconds * 1000L
        var intervalMs = code.intervalSeconds * 1000L
        var netErrors = 0
        while (true) {
            if (now() >= deadline) return DeviceAuthOutcome.Expired
            sleep(intervalMs)
            when (val p = pollOnce(code.deviceCode)) {
                is DevicePoll.Approved -> return DeviceAuthOutcome.Success(p.token)
                DevicePoll.Denied -> return DeviceAuthOutcome.Denied
                DevicePoll.Expired -> return DeviceAuthOutcome.Expired
                DevicePoll.Pending -> netErrors = 0
                DevicePoll.SlowDown -> { netErrors = 0; intervalMs += 5_000L }
                is DevicePoll.Failure -> {
                    if (p.kind == DeviceAuthError.NETWORK && ++netErrors <= maxNetworkErrors) continue
                    return DeviceAuthOutcome.Failed(p.kind, p.detail)
                }
            }
        }
    }

    private fun parsePollError(code: Int, text: String): DevicePoll {
        val detail = parseMap(text)?.str("detail")?.lowercase()
        return when (detail) {
            "authorization_pending" -> DevicePoll.Pending
            "slow_down" -> DevicePoll.SlowDown
            "access_denied" -> DevicePoll.Denied
            "expired_token" -> DevicePoll.Expired
            else -> DevicePoll.Failure(classify(code), "HTTP $code")
        }
    }

    private fun classify(code: Int) = when (code) {
        401, 403 -> DeviceAuthError.UNAUTHORIZED
        404, 405 -> DeviceAuthError.UNSUPPORTED
        else -> DeviceAuthError.SERVER
    }

    private fun parseMap(text: String): Map<*, *>? =
        runCatching { anyAdapter.fromJson(text) as? Map<*, *> }.getOrNull()

    private fun Map<*, *>.str(key: String): String? = (this[key] as? String)?.takeIf { it.isNotBlank() }

    private fun Map<*, *>.int(key: String): Int? = (this[key] as? Number)?.toInt()

    companion object {
        /**
         * `host:port[/subpath]`, with or without scheme/trailing slash → base URL without
         * query/fragment. The path is kept: the API client resolves `api/...` relative to it,
         * so a RomM behind a reverse-proxy subpath works the same as with a manual token.
         */
        fun normalizeOrigin(input: String): String {
            var s = input.trim().trimEnd('/')
            if (s.isEmpty()) return ""
            if (!s.contains("://")) s = "https://$s"
            val u = s.toHttpUrlOrNull() ?: return s
            return u.newBuilder().query(null).fragment(null).build()
                .toString().trimEnd('/')
        }
    }
}

class DeviceAuthException(val kind: DeviceAuthError, detail: String? = null) : Exception(detail ?: kind.name)
