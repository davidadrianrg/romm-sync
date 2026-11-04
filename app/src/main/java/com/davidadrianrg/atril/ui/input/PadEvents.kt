package com.davidadrianrg.atril.ui.input

import kotlinx.coroutines.flow.MutableSharedFlow

/** Gamepad intents that screens subscribe to (Start/Menu opens the launcher menu). */
object PadEvents {
    val menu = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}
