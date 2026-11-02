package com.davidadrianrg.atril.data.sync

import android.content.Context
import com.davidadrianrg.atril.core.sync.SaveBackupManager
import com.davidadrianrg.atril.data.local.SettingsDataStore
import com.davidadrianrg.atril.data.local.dao.PlatformDao
import com.davidadrianrg.atril.data.local.dao.RomDao
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
            appContext = context,
        ).restoreBackup(romId, fileName, backupFile)
}
