package com.davidadrianrg.atril.data.metadata

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.davidadrianrg.atril.core.media.GameMediaDownloader
import com.davidadrianrg.atril.core.media.GameMediaPaths
import com.davidadrianrg.atril.core.remote.NetworkModule
import com.davidadrianrg.atril.data.local.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Baja la media de UN juego (covers, screenshots, vídeo, manual + metadata.json) a la
 * carpeta de media genérica cuando termina su descarga. Es un trabajo aparte: nunca
 * bloquea ni hace fallar la descarga del ROM, y un fallo de una imagen no corta el resto.
 */
class GameMediaWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val romId = inputData.getInt(KEY_ROM_ID, -1)
        if (romId < 0) return@withContext Result.failure()

        val store = SettingsDataStore(applicationContext)
        // Re-lee los ajustes: si el usuario apagó la opción o desconectó RomM, no hace nada.
        if (!store.getMediaDownloadEnabledBlocking()) return@withContext Result.success()
        val serverUrl = store.getServerUrlBlocking()
        val apiKey = store.getApiKeyBlocking()
        if (serverUrl.isBlank() || apiKey.isBlank()) return@withContext Result.success()

        val mediaRoot = GameMediaPaths.resolveRoot(
            store.getMediaDownloadPathBlocking(),
            store.getRomsRootPathBlocking(),
        )
        try {
            val dto = NetworkModule.createApiService(serverUrl, apiKey).getRom(romId)
            val report = GameMediaDownloader(OkHttpClient(), apiKey, serverUrl) { Log.w(TAG, it) }
                .run(dto, mediaRoot)
            if (report.failed.isNotEmpty()) {
                Log.w(TAG, "rom $romId: ${report.failed.size} media file(s) failed, ${report.downloaded} ok")
            }
            Result.success()
        } catch (e: IOException) {
            Log.w(TAG, "rom $romId: network error (${e.javaClass.simpleName})")
            if (runAttemptCount < 3) Result.retry() else Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "rom $romId: ${e.javaClass.simpleName}")
            Result.success() // best effort: nunca marca nada como fallido
        }
    }

    companion object {
        private const val TAG = "GameMediaWorker"
        const val KEY_ROM_ID = "rom_id"

        /** Encola la media de un juego recién descargado (sin RomM/ajuste activo no se llama). */
        fun enqueue(context: Context, romId: Int) {
            val request = OneTimeWorkRequestBuilder<GameMediaWorker>()
                .setInputData(Data.Builder().putInt(KEY_ROM_ID, romId).build())
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("game_media_$romId", ExistingWorkPolicy.KEEP, request)
        }
    }
}
