package com.davidadrianrg.atril.upload

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Estado de la subida activa, compartido entre el servicio y el diálogo. */
data class UploadState(
    val running: Boolean = false,
    val progress: Int? = null,
    val phase: String? = null,
    val message: String? = null,
    val error: String? = null,
)

object UploadBus {

    private val _state = MutableStateFlow(UploadState())
    val state: StateFlow<UploadState> = _state.asStateFlow()

    fun start() {
        _state.value = UploadState(running = true, progress = 0)
    }

    fun progress(percent: Int) {
        _state.update { it.copy(progress = percent.coerceIn(0, 100)) }
    }

    fun phase(text: String?) {
        _state.update { it.copy(phase = text) }
    }

    fun progressAndPhase(percent: Int, text: String?) {
        _state.update { it.copy(progress = percent.coerceIn(0, 100), phase = text) }
    }

    fun success(message: String) {
        _state.value = UploadState(running = false, progress = 100, message = message)
    }

    fun fail(error: String) {
        _state.value = UploadState(running = false, error = error)
    }

    fun cancelled(message: String) {
        _state.value = UploadState(running = false, message = message)
    }

    /** Limpia el resultado anterior al abrir el diálogo. */
    fun consumeResult() {
        if (!_state.value.running) _state.value = UploadState()
    }
}
