package es.davidrg.rommsync.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Puente entre la Estantería y la Biblioteca: la estantería pide abrir un
 * juego concreto (platformId, romId) y la Biblioteca lo selecciona y muestra
 * el mismo detalle que si se hubiera tocado allí.
 */
object ShelfOpen {
    val request = MutableStateFlow<Pair<Int, Int>?>(null)
}
