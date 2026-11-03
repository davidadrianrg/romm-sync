package com.davidadrianrg.atril.download

import java.util.concurrent.ConcurrentHashMap

/**
 * Distingue una cancelación explícita del usuario de otras paradas del worker
 * (red perdida, restricciones, proceso muerto). Solo en la primera se borra el
 * fichero parcial; en las demás se conserva para reanudar con `Range`.
 *
 * WorkManager no dice por qué se detuvo un worker antes de la API 31 (`minSdk`
 * es 26), así que la marca vive en memoria dentro del proceso de la app.
 */
internal object DownloadCancelRegistry {
    private val cancelledRoms = ConcurrentHashMap.newKeySet<Int>()

    @Volatile
    private var cancelAllAtMs = 0L

    /** El usuario canceló la descarga de [romId]. */
    fun markCancelled(romId: Int) {
        cancelledRoms.add(romId)
    }

    /** El usuario canceló toda la cola; afecta a los workers ya iniciados. */
    fun markAllCancelled(nowMs: Long = System.currentTimeMillis()) {
        cancelAllAtMs = nowMs
    }

    /** Un worker nuevo empieza: descarta una marca vieja de [romId]. */
    fun clear(romId: Int) {
        cancelledRoms.remove(romId)
    }

    /**
     * ¿Fue una cancelación del usuario para el worker de [romId] iniciado en
     * [workStartedAtMs]? Consume la marca individual.
     */
    fun consumeUserCancel(romId: Int, workStartedAtMs: Long): Boolean {
        val single = cancelledRoms.remove(romId)
        return single || cancelAllAtMs >= workStartedAtMs
    }
}
