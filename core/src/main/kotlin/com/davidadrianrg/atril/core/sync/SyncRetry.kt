package com.davidadrianrg.atril.core.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException

/**
 * Reintentos con backoff exponencial para subidas/descargas de saves.
 * Solo se reintentan fallos transitorios (red, timeouts, 5xx, 408, 429);
 * 4xx (401/403/404…) y errores de lógica fallan a la primera.
 */
object SyncRetry {

    const val DEFAULT_ATTEMPTS = 3
    const val DEFAULT_BASE_DELAY_MS = 800L

    fun isTransient(t: Throwable): Boolean = when (t) {
        is CancellationException -> false
        is HttpException -> t.code() == 408 || t.code() == 429 || t.code() in 500..599
        is IOException -> true
        else -> false
    }

    /** Espera antes del reintento número [attempt] (1 = tras el primer fallo). */
    fun backoffMs(attempt: Int, baseDelayMs: Long = DEFAULT_BASE_DELAY_MS): Long =
        baseDelayMs * (1L shl (attempt - 1).coerceIn(0, 10))

    /**
     * Ejecuta [block] hasta [attempts] veces. Devuelve su resultado o relanza el
     * último error si no era transitorio / se agotaron los intentos.
     */
    suspend fun <T> withRetry(
        attempts: Int = DEFAULT_ATTEMPTS,
        baseDelayMs: Long = DEFAULT_BASE_DELAY_MS,
        onRetry: (attempt: Int, error: Throwable) -> Unit = { _, _ -> },
        block: suspend (attempt: Int) -> T,
    ): T {
        var attempt = 1
        while (true) {
            try {
                return block(attempt)
            } catch (e: Throwable) {
                if (attempt >= attempts || !isTransient(e)) throw e
                onRetry(attempt, e)
                delay(backoffMs(attempt, baseDelayMs))
                attempt++
            }
        }
    }
}
