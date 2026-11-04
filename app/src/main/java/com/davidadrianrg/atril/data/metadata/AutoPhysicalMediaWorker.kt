package com.davidadrianrg.atril.data.metadata

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.davidadrianrg.atril.RomMSyncApplication
import com.davidadrianrg.atril.core.i18n.tr
import com.davidadrianrg.atril.core.scrape.AutoPhysicalMedia
import com.davidadrianrg.atril.core.scrape.PhysicalMediaScrape
import com.davidadrianrg.atril.data.local.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Pide a RomM (no a ScreenScraper) el medio físico de un juego recién descargado.
 * Trabajo aparte: nunca bloquea ni hace fallar la descarga del ROM. El resultado
 * se avisa con un toast; el botón de reintento del detalle del launcher sigue disponible.
 */
class AutoPhysicalMediaWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val romId = inputData.getInt(KEY_ROM_ID, -1)
        if (romId < 0) return@withContext Result.failure()
        val name = inputData.getString(KEY_ROM_NAME).orEmpty()

        val store = SettingsDataStore(applicationContext)
        val serverUrl = store.getServerUrlBlocking()
        val apiKey = store.getApiKeyBlocking()
        val container = (applicationContext as RomMSyncApplication).container
        container.configureApi(serverUrl, apiKey)

        // Re-evalúa las condiciones: el usuario pudo apagar la opción o desconectar RomM.
        val repo = container.romRepository
        val hasMedia = runCatching {
            com.davidadrianrg.atril.core.remote.NetworkModule.createApiService(serverUrl, apiKey).getRom(romId).physicalMediaPath
        }.getOrNull()
            ?.isNotBlank() == true
        if (!AutoPhysicalMedia.shouldRequest(
                enabled = store.getAutoPhysicalMediaBlocking() && store.getLauncherDefaultDisplayBlocking() == "physical",
                romMConnected = serverUrl.isNotBlank() && apiKey.isNotBlank(),
                isLocalGame = false, // los juegos locales nunca pasan por la descarga de RomM
                alreadyHasPhysicalMedia = hasMedia,
                requestInProgress = false,
            )
        ) return@withContext Result.success()

        val outcome = repo.requestPhysicalScrape(romId) { }
        Log.i(TAG, "rom $romId: ${outcome.javaClass.simpleName}")
        if (outcome is PhysicalMediaScrape.Outcome.Network && runAttemptCount < 2) return@withContext Result.retry()
        val ok = outcome is PhysicalMediaScrape.Outcome.Found
        val msg = if (ok) tr("scrape.auto.done", name) else tr("scrape.auto.failed", name, PhysicalMediaScrape.describe(outcome))
        Handler(Looper.getMainLooper()).post { Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show() }
        Result.success()
    }

    companion object {
        private const val TAG = "AutoPhysicalMedia"
        const val KEY_ROM_ID = "rom_id"
        const val KEY_ROM_NAME = "rom_name"

        fun enqueue(context: Context, romId: Int, romName: String) {
            val request = OneTimeWorkRequestBuilder<AutoPhysicalMediaWorker>()
                .setInputData(Data.Builder().putInt(KEY_ROM_ID, romId).putString(KEY_ROM_NAME, romName).build())
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            // KEEP: una petición en curso para el mismo juego no se duplica.
            WorkManager.getInstance(context)
                .enqueueUniqueWork("auto_physical_$romId", ExistingWorkPolicy.KEEP, request)
        }
    }
}
