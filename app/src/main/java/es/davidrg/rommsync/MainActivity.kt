package es.davidrg.rommsync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import es.davidrg.rommsync.data.local.SettingsDataStore
import es.davidrg.rommsync.ui.navigation.RomMSyncApp
import es.davidrg.rommsync.ui.theme.RomMSyncTheme
import es.davidrg.rommsync.util.LocaleController
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Idioma guardado ("" = sistema) antes de componer la primera pantalla.
        val languageTag = runBlocking {
            SettingsDataStore(applicationContext).getLanguageBlocking()
        }
        LocaleController.apply(languageTag)

        setContent {
            val revision by LocaleController.revision.collectAsState()
            key(revision) {
                RomMSyncTheme {
                    RomMSyncApp()
                }
            }
        }
    }
}
