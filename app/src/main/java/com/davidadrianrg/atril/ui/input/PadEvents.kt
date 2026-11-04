package com.davidadrianrg.atril.ui.input

import kotlinx.coroutines.flow.MutableSharedFlow

/** Gamepad intents that screens subscribe to (Start/Menu opens the launcher menu). */
object PadEvents {
    val menu = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Start: abre el detalle del juego seleccionado (Play). */
    val details = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** L1 / R1: filtro de plataforma anterior / siguiente (-1 / +1). */
    val page = MutableSharedFlow<Int>(extraBufferCapacity = 1)
}
