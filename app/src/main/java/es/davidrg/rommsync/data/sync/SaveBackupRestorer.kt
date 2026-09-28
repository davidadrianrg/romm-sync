package es.davidrg.rommsync.data.sync

import android.content.Context
import es.davidrg.rommsync.core.sync.SaveBackupManager
import es.davidrg.rommsync.data.local.SettingsDataStore
import es.davidrg.rommsync.data.local.dao.PlatformDao
import es.davidrg.rommsync.data.local.dao.RomDao
import java.io.File

/**
 * Fachada para la UI del historial de copias de seguridad de saves: lista
 * las versiones de un ROM y las restaura reutilizando la lógica del
 * [SyncCoordinator] (resolución de handler + extractDownload).
 */
class SaveBackupRestorer(
    private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val romDao: RomDao,
    private val platformDao: PlatformDao,
    val backupManager: SaveBackupManager,
) {

    fun versions(romId: Int): List<SaveBackupManager.BackupVersion> =
        backupManager.versions(romId)

    suspend fun restore(romId: Int, fileName: String, backupFile: File): Boolean =
        SyncCoordinator(
            settingsDataStore = settingsDataStore,
            romDao = romDao,
            platformDao = platformDao,
            cacheDir = File(context.cacheDir, "restore"),
            backupManager = backupManager,
        ).restoreBackup(romId, fileName, backupFile)
}
