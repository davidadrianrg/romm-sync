package es.davidrg.rommsync.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import es.davidrg.rommsync.core.i18n.tr
import es.davidrg.rommsync.data.local.SettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

/**
 * Gestor de sincronización de saves. Expone métodos para la UI:
 * - Disparar sync manual.
 * - Programar sync periódico.
 * - Observar el estado del worker activo.
 */
class SaveSyncManager(
    private val context: Context,
    private val settings: SettingsDataStore = SettingsDataStore(context),
) {

    private val workManager = WorkManager.getInstance(context)

    /**
     * Encola un ciclo de sync como trabajo único (evita duplicados si se
     * pulsa varias veces).
     */
    fun triggerSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<SaveSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniqueWork(
            SaveSyncWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Resuelve un conflicto forzando la dirección elegida por el usuario.
     *
     * @param resolution "local" para subir la versión local sobrescribiendo
     *   el servidor, "server" para descargar la del servidor sobrescribiendo
     *   la local.
     */
    fun triggerConflictResolution(romId: Int, fileName: String, resolution: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<SaveSyncWorker>()
            .setConstraints(constraints)
            .setInputData(
                Data.Builder()
                    .putInt(SaveSyncWorker.KEY_CONFLICT_ROM_ID, romId)
                    .putString(SaveSyncWorker.KEY_CONFLICT_FILE_NAME, fileName)
                    .putString(SaveSyncWorker.KEY_CONFLICT_RESOLUTION, resolution)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniqueWork(
            SaveSyncWorker.CONFLICT_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    /**
     * Programa un sync periódico. Si intervalMinutes es 0 o negativo, cancela
     * cualquier sync periódico existente.
     *
     * @param replace si es true, reemplaza el trabajo existente (para cuando el
     * usuario cambia el intervalo). Si es false, usa KEEP (para restaurar al
     * arrancar sin reiniciar el timer).
     * @param wifiOnly solo con redes sin límite de datos (null = leer ajuste).
     * @param chargingOnly solo con el dispositivo cargando (null = leer ajuste).
     */
    fun schedulePeriodicSync(
        intervalMinutes: Int,
        replace: Boolean = false,
        wifiOnly: Boolean? = null,
        chargingOnly: Boolean? = null,
    ) {
        if (intervalMinutes <= 0) {
            workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
            return
        }

        // null = usar el valor persistido en ajustes (wifi/charging only)
        val wifi = wifiOnly ?: settings.getSyncWifiOnlyBlocking()
        val charging = chargingOnly ?: settings.getSyncChargingOnlyBlocking()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifi) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresCharging(charging)
            .build()

        val request = PeriodicWorkRequestBuilder<SaveSyncWorker>(
            // WorkManager impone un mínimo de 15 min para trabajo periódico.
            // Para 1/5 min la app usa un temporizador en primer plano.
            intervalMinutes.coerceAtLeast(15).toLong(), TimeUnit.MINUTES,
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Observa el estado del worker de sync para actualizar la UI.
     *
     * Cuando el ciclo termina con éxito, persiste también el JSON de
     * conflictos detectados para que la UI de resolución los cargue.
     */
    fun observeSyncState(): Flow<SyncState> {
        return workManager.getWorkInfosForUniqueWorkFlow(SaveSyncWorker.WORK_NAME).map { infos ->
            val info = infos.firstOrNull()
            when (info?.state) {
                WorkInfo.State.RUNNING -> SyncState.Running
                WorkInfo.State.SUCCEEDED -> {
                    val message = info.outputData.getString(SaveSyncWorker.KEY_MESSAGE) ?: ""
                    // Señales explícitas del resultado: la UI no debe deducir el
                    // estado leyendo el texto del mensaje (es traducible).
                    val allSynced = info.outputData.getBoolean(SaveSyncWorker.KEY_ALL_SYNCED, false)
                    val hasWarnings = info.outputData.getBoolean(SaveSyncWorker.KEY_HAS_WARNINGS, false)
                    val conflictsJson = info.outputData.getString(SaveSyncWorker.KEY_CONFLICTS_JSON)
                    if (conflictsJson != null) {
                        kotlinx.coroutines.runBlocking {
                            SettingsDataStore(context).setLastSyncConflictsJson(conflictsJson)
                        }
                    }
                    val failedJson = info.outputData.getString(SaveSyncWorker.KEY_FAILED_JSON)
                    if (failedJson != null) {
                        kotlinx.coroutines.runBlocking {
                            SettingsDataStore(context).setLastSyncFailedJson(failedJson)
                        }
                    }
                    SyncState.Success(message, allSynced = allSynced, hasWarnings = hasWarnings)
                }
                WorkInfo.State.FAILED -> {
                    val message = info.outputData.getString(SaveSyncWorker.KEY_MESSAGE) ?: tr("common.error")
                    SyncState.Failed(message)
                }
                WorkInfo.State.ENQUEUED -> SyncState.Pending
                else -> SyncState.Idle
            }
        }
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "save_sync_periodic"
    }

    /**
     * Cancela la sincronización en curso (o en cola) sin tocar la
     * programación periódica.
     */
    fun cancelCurrentSync() {
        workManager.cancelUniqueWork(SaveSyncWorker.WORK_NAME)
    }

    /**
     * Cancela todos los trabajos de sync (periódico, único y resolución de
     * conflictos). Se llama cuando el usuario desactiva el sync de saves.
     */
    fun cancelAllSyncWork() {
        workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
        workManager.cancelUniqueWork(SaveSyncWorker.WORK_NAME)
        workManager.cancelUniqueWork(SaveSyncWorker.CONFLICT_WORK_NAME)
    }
}

sealed class SyncState {
    data object Idle : SyncState()
    data object Pending : SyncState()
    data object Running : SyncState()
    data class Success(
        val message: String,
        /** No hubo nada que hacer (ni subidas, ni bajadas, ni fallos): "todo al día". */
        val allSynced: Boolean = false,
        /** El resultado trae avisos (p.ej. rutas de saves ilegibles). */
        val hasWarnings: Boolean = false,
    ) : SyncState()
    data class Failed(val message: String) : SyncState()
}
