package com.davidadrianrg.atril

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import com.davidadrianrg.atril.data.local.SettingsDataStore
import com.davidadrianrg.atril.data.update.UpdateNotificationManager
import com.davidadrianrg.atril.ui.navigation.RomMSyncApp
import com.davidadrianrg.atril.ui.theme.RomMSyncTheme
import com.davidadrianrg.atril.util.LocaleController
import com.davidadrianrg.atril.util.hasNotificationPermission
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* La app seguirá funcionando aunque se deniegue. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Idioma guardado ("" = sistema) antes de componer la primera pantalla.
        val languageTag = runBlocking {
            SettingsDataStore(applicationContext).getLanguageBlocking()
        }
        LocaleController.apply(languageTag)

        val openUpdates = intent?.getBooleanExtra(UpdateNotificationManager.EXTRA_OPEN_UPDATES, false) == true

        setContent {
            val revision by LocaleController.revision.collectAsState()
            key(revision) {
                RomMSyncTheme {
                    RomMSyncApp(startInUpdates = openUpdates)
                }
            }
        }

        // Android 13+: sin este permiso no se pueden mostrar las notificaciones
        // de actualización ni las de sync. Se pide una vez al abrir la app.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasNotificationPermission(this)
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
