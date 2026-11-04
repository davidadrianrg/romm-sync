package com.davidadrianrg.atril.data

import kotlinx.coroutines.flow.first
import android.content.Context
import com.davidadrianrg.atril.data.local.RomSyncDatabase
import com.davidadrianrg.atril.data.local.SettingsDataStore
import com.davidadrianrg.atril.data.metadata.MetadataExportManager
import com.davidadrianrg.atril.data.repository.RomRepository
import com.davidadrianrg.atril.data.repository.SettingsRepository
import com.davidadrianrg.atril.data.sync.SaveSyncManager
import com.davidadrianrg.atril.data.update.AppUpdateChecker
import com.davidadrianrg.atril.data.update.ApkInstallHelper
import com.davidadrianrg.atril.download.DownloadManager

/**
 * Manual dependency injection container.
 *
 * Initialized once in [com.davidadrianrg.atril.RomMSyncApplication.onCreate].
 * All ViewModels receive their dependencies from here via ViewModelFactory.
 *
 * This avoids the annotation-processing complexity of Hilt while keeping
 * a clean, testable architecture.
 */
class AppContainer(private val appContext: Context) {

    private val database: RomSyncDatabase = RomSyncDatabase.getDatabase(appContext)

    val settingsDataStore = SettingsDataStore(appContext)

    val settingsRepository = SettingsRepository(settingsDataStore)

    /** DAOs expuestos para pantallas que leen directamente de Room (Estantería). */
    val romDao = database.romDao()
    val platformDao = database.platformDao()

    val romRepository = RomRepository(
        platformDao = database.platformDao(),
        romDao = database.romDao(),
    )

    val downloadManager = DownloadManager(appContext)

    /** Presentación por juego en el launcher + caché del medio físico. */
    val physicalMediaRepository = com.davidadrianrg.atril.data.repository.PhysicalMediaRepository(
        dao = database.romPresentationDao(),
        cacheDir = java.io.File(appContext.filesDir, "physical-media"),
    )

    /** Juegos añadidos a mano (fuera de RomM) y su scrapeo de carátulas. */
    val localGamesRepository = com.davidadrianrg.atril.data.localgames.LocalGamesRepository(
        store = com.davidadrianrg.atril.core.localgames.LocalGameStore(java.io.File(appContext.filesDir, "local_games.json")),
        coversDir = java.io.File(appContext.filesDir, "local-covers"),
        credentials = { settingsRepository.scraperCredentials.first() },
    )

    /** Lanza juegos descargados en el emulador que toque. */
    val emulatorLauncher = com.davidadrianrg.atril.launch.EmulatorLauncher(appContext)

    val saveSyncManager = SaveSyncManager(appContext)

    /** Versionado local de saves (restaurable desde el detalle de juego). */
    val saveBackupManager = com.davidadrianrg.atril.core.sync.SaveBackupManager(
        java.io.File(appContext.filesDir, "save-backups"),
    )

    val saveBackupRestorer = com.davidadrianrg.atril.data.sync.SaveBackupRestorer(
        context = appContext,
        settingsDataStore = settingsDataStore,
        romDao = database.romDao(),
        platformDao = database.platformDao(),
        backupManager = saveBackupManager,
    )

    val metadataExportManager = MetadataExportManager(appContext)

    val appUpdateChecker = AppUpdateChecker()

    val apkInstallHelper = ApkInstallHelper(appContext)

    /**
     * Called when server settings change to refresh the API client.
     */
    fun configureApi(serverUrl: String, apiKey: String) {
        romRepository.configureApi(serverUrl, apiKey)
    }
}
