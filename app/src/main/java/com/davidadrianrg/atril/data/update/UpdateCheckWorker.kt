package com.davidadrianrg.atril.data.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.davidadrianrg.atril.data.local.SettingsDataStore
import java.util.concurrent.TimeUnit

/**
 * Comprueba GitHub en segundo plano una vez al día y publica una notificación
 * si hay una versión nueva. También se ejecuta una comprobación inmediata al
 * abrir la app desde `RomMSyncApplication`.
 */
class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val result = AppUpdateChecker().check()
        UpdateNotificationManager.notifyIfAvailable(
            context = applicationContext,
            result = result,
            settings = SettingsDataStore(applicationContext),
        )
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "atril_update_check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(24, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
