package com.davidadrianrg.atril.data.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.davidadrianrg.atril.data.local.SettingsDataStore

/**
 * Reanuda la vigilancia de saves tras reiniciar el dispositivo si el usuario
 * la tenía activada. En Android 15 el arranque de una FGS dataSync desde
 * BOOT_COMPLETED puede estar restringido: el fallo se ignora y la vigilancia
 * se reanuda la próxima vez que se abra la app.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val settings = SettingsDataStore(context)
        if (settings.getSaveSyncWatchEnabledBlocking() &&
            settings.getSaveSyncEnabledBlocking()
        ) {
            runCatching { SaveSyncWatchService.start(context) }
        }
    }
}
