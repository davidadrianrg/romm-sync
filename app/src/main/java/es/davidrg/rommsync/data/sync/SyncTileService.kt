package es.davidrg.rommsync.data.sync

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Tile de Ajustes Rápidos "Sincronizar saves": dispara un ciclo de sync
 * único (WorkManager) sin abrir la app. El propio worker guarda de seguridad
 * (save sync desactivado → no-op), así que el tile es siempre seguro.
 */
class SyncTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            subtitle = "Sincronizar"
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        SaveSyncManager(applicationContext).triggerSync()
    }
}
