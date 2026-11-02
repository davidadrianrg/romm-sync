package com.davidadrianrg.atril.domain.model

import com.davidadrianrg.atril.core.i18n.tr

/** Destino de descarga por defecto de una plataforma (modo dos rutas). */
enum class PlatformDownloadStorage(val id: String) {
    /** Preguntar al usuario en cada descarga. */
    ASK("ask"),

    /** Memoria interna (ruta principal configurada). */
    INTERNAL("internal"),

    /** Tarjeta SD (segunda ruta configurada). */
    SD("sd");

    /**
     * Etiqueta para la UI: se traduce en el momento de leerla
     * (`android.storage.ask` / `.internal` / `.sd`).
     */
    val label: String get() = tr("android.storage.$id")

    companion object {
        fun fromRaw(raw: String?): PlatformDownloadStorage? = when (raw) {
            "internal" -> INTERNAL
            "sd" -> SD
            else -> null // null o desconocido = ASK
        }
    }
}

data class Platform(
    val id: Int,
    val slug: String,
    val name: String,
    val romCount: Int,
    val visible: Boolean = true,
    val emulatorId: String? = null,
    val savesPathOverride: String? = null,
    val aspectRatio: String? = null,
    val measuredAspectRatio: Float? = null,
    /** Destino de descarga por defecto (solo aplica con modo dos rutas activo). */
    val downloadStorage: PlatformDownloadStorage? = null,
)
