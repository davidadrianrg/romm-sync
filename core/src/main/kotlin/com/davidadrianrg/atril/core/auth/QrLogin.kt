package com.davidadrianrg.atril.core.auth

/** UI-facing state of the QR login, shared by Android and desktop. */
sealed interface QrLoginState {
    data object Idle : QrLoginState
    data object Starting : QrLoginState
    data class Waiting(val code: DeviceCode, val qr: QrMatrix) : QrLoginState
    data object Success : QrLoginState
    data object Denied : QrLoginState
    data object Expired : QrLoginState
    data class Failed(val kind: DeviceAuthError) : QrLoginState
}

/**
 * Whole flow: init → QR → poll → [onApproved] (persist the token) → Success.
 * Cancel by cancelling the calling coroutine. [onApproved] gets the normalised
 * server origin and the token; it must not log the token.
 */
suspend fun runQrLogin(
    serverUrl: String,
    request: DeviceAuthRequest,
    onState: (QrLoginState) -> Unit,
    onApproved: suspend (origin: String, token: DeviceToken) -> Unit,
    client: DeviceAuthClient = DeviceAuthClient(serverUrl),
) {
    if (!client.isValid) {
        onState(QrLoginState.Failed(DeviceAuthError.INVALID_URL))
        return
    }
    onState(QrLoginState.Starting)
    val code = client.init(request).getOrElse {
        onState(QrLoginState.Failed((it as? DeviceAuthException)?.kind ?: DeviceAuthError.SERVER))
        return
    }
    val qr = try {
        QrCode.encode(code.verificationUrl)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Throwable) {
        onState(QrLoginState.Failed(DeviceAuthError.SERVER))
        return
    }
    onState(QrLoginState.Waiting(code, qr))
    when (val out = client.awaitApproval(code)) {
        is DeviceAuthOutcome.Success -> {
            onApproved(DeviceAuthClient.normalizeOrigin(serverUrl), out.token)
            onState(QrLoginState.Success)
        }
        DeviceAuthOutcome.Denied -> onState(QrLoginState.Denied)
        DeviceAuthOutcome.Expired -> onState(QrLoginState.Expired)
        is DeviceAuthOutcome.Failed -> onState(QrLoginState.Failed(out.kind))
    }
}
