package es.davidrg.rommsync

import android.app.Application
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import es.davidrg.rommsync.data.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.File

class RomMSyncApplication : Application(), Configuration.Provider, ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        restorePeriodicSync()
        startForegroundFastSync()
    }

    /**
     * Sincronización rápida (1 y 5 minutos) mientras la app está en primer
     * plano: WorkManager no permite periodos menores de 15 minutos, así que
     * estos intervalos se cubren con un temporizador en proceso. Al salir de
     * la app queda el trabajo periódico de WorkManager (mínimo 15 min).
     */
    private fun startForegroundFastSync() {
        appScope.launch {
            combine(
                container.settingsDataStore.saveSyncEnabled,
                container.settingsDataStore.saveSyncIntervalMinutes,
            ) { enabled, minutes -> enabled to minutes }
                .collectLatest { (enabled, minutes) ->
                    if (!enabled || minutes !in 1..14) return@collectLatest
                    while (true) {
                        delay(minutes * 60_000L)
                        container.saveSyncManager.triggerSync()
                    }
                }
        }
    }

    /**
     * Si el usuario tenia configurada una sincronización periódica, se
     * re-registra al arrancar la app. WorkManager persiste los trabajos
     * periódicos entre reinicios del dispositivo, pero re-enqueue con
     * KEEP garantiza que siempre exista.
     */
    private fun restorePeriodicSync() {
        // Si el usuario desactivó el sync de saves, no re-registramos el
        // trabajo periódico en el arranque (y limpiamos el que pudiera quedar
        // de una versión anterior).
        if (!container.settingsDataStore.getSaveSyncEnabledBlocking()) {
            container.saveSyncManager.cancelAllSyncWork()
            return
        }
        val interval = container.settingsDataStore.getSyncIntervalMinutesBlocking()
        if (interval > 0) {
            container.saveSyncManager.schedulePeriodicSync(interval)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    /**
     * Single ImageLoader for the whole app.
     *
     * Tuning:
     *  - memoryCache: 25 % of the app's available memory (Coil computes maxSize
     *    from the runtime when we pass the percentage).
     *  - diskCache:   100 MB at cacheDir/image_cache.
     *  - crossfade:   smooth fade-in for covers.
     *  - HARDWARE & ENABLED policies keep covers cached as long as possible.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, "image_cache"))
                    .maxSizeBytes(100L * 1024L * 1024L) // 100 MB
                    .build()
            }
            .crossfade(true)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .build()
}
