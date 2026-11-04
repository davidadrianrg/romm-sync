package com.davidadrianrg.atril.core.auth

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
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
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val json = "application/json".toMediaType()

    val isValid: Boolean get() = base != null

    /** Starts the flow. Returns null in [DeviceAuthOutcome.Failed] form via [Result]. */
    suspend fun init(req: DeviceAuthRequest): Result<DeviceCode> = withContext(Dispatchers.IO) {
        val url = base?.newBuilder()?.addEncodedPathSegments("api/auth/device/init")?.build()
            ?: return@withContext Result.failure(DeviceAuthException(DeviceAuthError.INVALID_URL))
        val body = moshi.adapter(InitBody::class.java).toJson(
            InitBody(req.clientDeviceIdentifier, req.name, DeviceAuth.CLIENT, req.platform, req.clientVersion, req.scopes),
        ).toRequestBody(json)
        try {
            http.newCall(Request.Builder().url(url).post(body).build()).execute().use { r ->
                val text = r.body?.string().orEmpty()
                if (!r.isSuccessful) {
                    return@withContext Result.failure(DeviceAuthException(classify(r.code), "HTTP ${r.code}"))
                }
                val parsed = moshi.adapter(InitResponse::class.java).fromJson(text)
                    ?: return@withContext Result.failure(DeviceAuthException(DeviceAuthError.SERVER, "bad init body"))
                Result.success(
                    DeviceCode(
                        deviceCode = parsed.deviceCode,
                        userCode = parsed.userCode,
                        verificationUrl = origin + parsed.verificationPathComplete,
                        expiresInSeconds = parsed.expiresIn,
                        intervalSeconds = parsed.interval.coerceAtLeast(1),
                    ),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Result.failure(DeviceAuthException(DeviceAuthError.NETWORK, e.javaClass.simpleName))
        } catch (e: Exception) {
            Result.failure(DeviceAuthException(DeviceAuthError.SERVER, e.javaClass.simpleName))
        }
    }

    /** One poll of the token endpoint. */
    suspend fun pollOnce(deviceCode: String): DevicePoll = withContext(Dispatchers.IO) {
        val url = base?.newBuilder()?.addEncodedPathSegments("api/auth/device/token")?.build()
            ?: return@withContext DevicePoll.Failure(DeviceAuthError.INVALID_URL)
        val body = moshi.adapter(TokenBody::class.java).toJson(TokenBody(deviceCode)).toRequestBody(json)
        try {
            http.newCall(Request.Builder().url(url).post(body).build()).execute().use { r ->
                val text = r.body?.string().orEmpty()
                if (r.isSuccessful) {
                    val t = moshi.adapter(TokenResponse::class.java).fromJson(text)
                        ?: return@withContext DevicePoll.Failure(DeviceAuthError.SERVER, "bad token body")
                    return@withContext DevicePoll.Approved(DeviceToken(t.accessToken, t.deviceId, t.scopes))
                }
                parsePollError(r.code, text)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            DevicePoll.Failure(DeviceAuthError.NETWORK, e.javaClass.simpleName)
        } catch (e: Exception) {
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
        val detail = runCatching {
            moshi.adapter(ErrorBody::class.java).fromJson(text)?.detail
        }.getOrNull()?.lowercase()
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

    // ── wire types ─────────────────────────────────────────────
    private data class InitBody(
        @Json(name = "client_device_identifier") val clientDeviceIdentifier: String,
        val name: String,
        val client: String,
        val platform: String?,
        @Json(name = "client_version") val clientVersion: String?,
        @Json(name = "requested_scopes") val requestedScopes: List<String>,
    )

    private data class InitResponse(
        @Json(name = "device_code") val deviceCode: String,
        @Json(name = "user_code") val userCode: String,
        @Json(name = "verification_path_complete") val verificationPathComplete: String,
        @Json(name = "expires_in") val expiresIn: Int,
        val interval: Int,
    )

    private data class TokenBody(@Json(name = "device_code") val deviceCode: String)

    private data class TokenResponse(
        @Json(name = "access_token") val accessToken: String,
        @Json(name = "device_id") val deviceId: String,
        val scopes: List<String> = emptyList(),
    )

    private data class ErrorBody(val detail: String? = null)

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
