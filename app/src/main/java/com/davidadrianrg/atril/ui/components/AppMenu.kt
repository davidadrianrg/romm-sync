package com.davidadrianrg.atril.ui.components

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Abre el menú superpuesto de la app (mismas secciones en todas las pantallas). Lo provee
 * `RomMSyncApp`; las cabeceras y la Biblioteca lo usan para su botón de hamburguesa.
 */
val LocalOpenAppMenu = staticCompositionLocalOf<() -> Unit> { {} }
