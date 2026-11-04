package com.davidadrianrg.atril

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.abs
import com.davidadrianrg.atril.core.input.PadAction
import com.davidadrianrg.atril.core.input.PadKeyMap
import com.davidadrianrg.atril.core.input.StickRepeater
import com.davidadrianrg.atril.ui.input.PadEvents
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import com.davidadrianrg.atril.data.local.SettingsDataStore
import com.davidadrianrg.atril.data.update.UpdateNotificationManager
import com.davidadrianrg.atril.ui.navigation.RomMSyncApp
import com.davidadrianrg.atril.ui.theme.RomMSyncTheme
import com.davidadrianrg.atril.util.LocaleController
import com.davidadrianrg.atril.util.hasNotificationPermission
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    private val leftStick = StickRepeater()

    /**
     * Mando: A → "pulsar" el elemento enfocado (DPAD_CENTER), B → atrás, Start/Mode → menú.
     * El D-pad ya lo traduce Android a DPAD_* y Compose mueve el foco; el stick izquierdo se
     * convierte en DPAD con repetición (ver [onGenericMotionEvent]). Solo actúa con eventos
     * de dispositivos de mando, nunca con el teclado, y no escribe texto.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val fromPad = event.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
        if (!fromPad) return super.dispatchKeyEvent(event)
        when (PadKeyMap.fromAndroidKeyCode(event.keyCode)) {
            PadAction.SELECT ->
                if (event.keyCode == KeyEvent.KEYCODE_BUTTON_A) {
                    return super.dispatchKeyEvent(
                        KeyEvent(event.downTime, event.eventTime, event.action, KeyEvent.KEYCODE_DPAD_CENTER, event.repeatCount),
                    )
                }
            PadAction.BACK -> {
                if (event.action == KeyEvent.ACTION_UP) onBackPressedDispatcher.onBackPressed()
                return true
            }
            PadAction.MENU -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) PadEvents.menu.tryEmit(Unit)
                return true
            }
            else -> {}
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK &&
            event.action == MotionEvent.ACTION_MOVE
        ) {
            val dir = leftStick.update(event.getAxisValue(MotionEvent.AXIS_X), event.getAxisValue(MotionEvent.AXIS_Y), event.eventTime)
            val code = when (dir) {
                PadAction.UP -> KeyEvent.KEYCODE_DPAD_UP
                PadAction.DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
                PadAction.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
                PadAction.RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
                else -> null
            }
            if (code != null) {
                val t = event.eventTime
                super.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
                super.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
                return true
            }
            // Mientras el stick está inclinado hay que consumir también los eventos sin pulsación
            // (entre repeticiones): si no, el SyntheticJoystickHandler de ViewRootImpl convierte
            // AXIS_X/Y en DPAD por su cuenta y el foco avanza dos veces. Con el stick centrado se
            // deja pasar para que los mandos que reportan la cruceta como HAT sigan funcionando.
            if (abs(event.getAxisValue(MotionEvent.AXIS_X)) >= 0.5f || abs(event.getAxisValue(MotionEvent.AXIS_Y)) >= 0.5f) {
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

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
                val themeId by remember { SettingsDataStore(applicationContext).theme }.collectAsState(initial = "")
                RomMSyncTheme(theme = com.davidadrianrg.atril.core.theme.AppTheme.fromId(themeId)) {
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
