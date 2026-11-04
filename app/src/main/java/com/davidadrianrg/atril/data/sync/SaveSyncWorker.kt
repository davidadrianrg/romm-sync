package com.davidadrianrg.atril.data.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.davidadrianrg.atril.core.i18n.tr
import com.davidadrianrg.atril.data.local.RomSyncDatabase
import com.davidadrianrg.atril.data.local.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WorkManager CoroutineWorker que ejecuta un ciclo de sincronización de saves.
 *
 * Puede ser disparado manualmente por el usuario desde la UI o programado
 * periódicamente (futuro).
 */
class SaveSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val dataStore = SettingsDataStore(applicationContext)
        val database = RomSyncDatabase.getDatabase(applicationContext)

        // Guard de seguridad: si el usuario desactivó el sync de saves, el
        // worker termina inmediatamente sin tocar servidor ni disco. Cubre
        // trabajos periódicos ya encolados antes del cambio de ajuste.
        if (!dataStore.getSaveSyncEnabledBlocking()) {
            Log.i(TAG, "Save sync disabled in settings, skipping run")
            return@withContext Result.success()
        }

        // Modo solo-local / RomM desconectado: el trabajo periódico encolado
        // antes no debe mostrar "conectando" ni reintentar contra la nada.
        if (dataStore.getServerUrlBlocking().isEmpty() || dataStore.getApiKeyBlocking().isEmpty()) {
            Log.i(TAG, "RomM not configured, skipping save sync")
            return@withContext Result.success()
        }

        // Foreground notification
        createNotificationChannel()
        try {
            setForeground(createForegroundInfo())
        } catch (e: Exception) {
            Log.w(TAG, "Could not start foreground (notification permission denied?)", e)
        }

        val coordinator = SyncCoordinator(
            settingsDataStore = dataStore,
            romDao = database.romDao(),
            platformDao = database.platformDao(),
            cacheDir = applicationContext.cacheDir,
            syncedHashStore = SyncedHashStore(applicationContext),
            backupManager = com.davidadrianrg.atril.core.sync.SaveBackupManager(
                java.io.File(applicationContext.filesDir, "save-backups"),
            ),
            conflictPolicy = com.davidadrianrg.atril.core.sync.ConflictPolicy.fromId(
                dataStore.getConflictPolicyBlocking(),
            ),
            appContext = applicationContext,
        )

        // Modo resolución de conflicto único (disparado desde la UI de conflictos)
        val conflictRomId = inputData.getInt(KEY_CONFLICT_ROM_ID, -1)
        val conflictFileName = inputData.getString(KEY_CONFLICT_FILE_NAME)
        val conflictResolution = inputData.getString(KEY_CONFLICT_RESOLUTION)
        if (conflictRomId >= 0 && !conflictFileName.isNullOrBlank() && !conflictResolution.isNullOrBlank()) {
            val result = coordinator.runConflictResolution(conflictRomId, conflictFileName, conflictResolution)
            return@withContext if (result.isSuccess) {
                Result.success(workDataOf(KEY_MESSAGE to (result.message ?: tr("sync.conflict.resolved"))))
            } else {
                if (runAttemptCount < 3) Result.retry()
                else Result.failure(workDataOf(KEY_MESSAGE to (result.error ?: tr("android.sync.error.unknown"))))
            }
        }

        val result = coordinator.runSync()

        if (result.isSuccess) {
            Log.i(TAG, "Sync completed: ${result.message}")
            val message = result.message ?: tr("android.sync.completed_title")
            notifySyncResult(
                success = true,
                message = message,
                conflicts = result.conflicts,
                failed = result.failedDetails.size,
                allSynced = result.allSynced,
            )
            Result.success(workDataOf(
                KEY_MESSAGE to message,
                KEY_UPLOADED to result.uploaded,
                KEY_DOWNLOADED to result.downloaded,
                KEY_CONFLICTS to result.conflicts,
                KEY_CONFLICTS_JSON to serializeConflicts(result.conflictDetails),
                KEY_FAILED_JSON to serializeFailures(result.failedDetails),
                // Señales explícitas para la UI (nada de deducir estado del texto).
                KEY_ALL_SYNCED to result.allSynced,
                KEY_HAS_WARNINGS to result.hasWarnings,
            ))
        } else {
            Log.w(TAG, "Sync failed: ${result.error}")
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure(workDataOf(KEY_MESSAGE to (result.error ?: tr("android.sync.error.unknown"))))
            }
        }
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(tr("sync.in_progress"))
            .setContentText(tr("android.notif.connecting"))
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                tr("android.notif.channel.name"),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = tr("android.notif.channel.description")
            }
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Notifica el resultado final del sync cuando la app está en segundo
     * plano. Solo si hubo actividad (uploads/downloads/conflictos) o error:
     * un "todo al día" silencioso no merece interrumpir.
     *
     * "No hubo nada que hacer" llega como señal explícita en allSynced
     * (calculada en SyncResult desde los contadores del ciclo), nunca leyendo
     * el texto del mensaje, que es traducible.
     */
    private fun notifySyncResult(
        success: Boolean,
        message: String,
        conflicts: Int,
        failed: Int = 0,
        allSynced: Boolean = false,
    ) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        // Nada que sincronizar: no interrumpir con una notificación.
        if (success && allSynced) return

        val title = when {
            !success -> tr("android.notif.title.failed")
            conflicts > 0 -> tr("android.notif.title.conflicts", conflicts)
            failed > 0 -> tr("android.notif.title.failures", failed)
            else -> tr("android.notif.title.success")
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(SYNC_RESULT_NOTIFICATION_ID, notification) }
    }

    companion object {
        const val TAG = "SaveSyncWorker"
        const val WORK_NAME = "save_sync"
        const val CONFLICT_WORK_NAME = "save_sync_conflict"
        const val KEY_MESSAGE = "sync_message"
        private const val SYNC_RESULT_NOTIFICATION_ID = 4202
        const val KEY_UPLOADED = "sync_uploaded"
        const val KEY_DOWNLOADED = "sync_downloaded"
        const val KEY_CONFLICTS = "sync_conflicts"
        const val KEY_CONFLICTS_JSON = "sync_conflicts_json"
        const val KEY_FAILED_JSON = "sync_failed_json"
        const val KEY_ALL_SYNCED = "sync_all_synced"
        const val KEY_HAS_WARNINGS = "sync_has_warnings"
        const val KEY_CONFLICT_ROM_ID = "conflict_rom_id"
        const val KEY_CONFLICT_FILE_NAME = "conflict_file_name"
        const val KEY_CONFLICT_RESOLUTION = "conflict_resolution"

        private const val CHANNEL_ID = "save_sync"
        private const val NOTIFICATION_ID = 2001

        /** Adaptador Moshi para serializar conflictos en el WorkInfo output. */
        private val conflictsAdapter by lazy {
            val moshi = com.squareup.moshi.Moshi.Builder()
                .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                .build()
            moshi.adapter<List<ConflictInfo>>(
                com.squareup.moshi.Types.newParameterizedType(
                    List::class.java, ConflictInfo::class.java,
                ),
            )
        }

        /** Adaptador Moshi para serializar operaciones fallidas. */
        private val failuresAdapter by lazy {
            val moshi = com.squareup.moshi.Moshi.Builder()
                .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                .build()
            moshi.adapter<List<FailedOpInfo>>(
                com.squareup.moshi.Types.newParameterizedType(
                    List::class.java, FailedOpInfo::class.java,
                ),
            )
        }

        private fun serializeConflicts(conflicts: List<ConflictInfo>): String = try {
            conflictsAdapter.toJson(conflicts)
        } catch (_: Exception) {
            "[]"
        }

        private fun serializeFailures(failures: List<FailedOpInfo>): String = try {
            failuresAdapter.toJson(failures)
        } catch (_: Exception) {
            "[]"
        }
    }
}
