package es.davidrg.rommsync.domain.model

/** Destino de descarga por defecto de una plataforma (modo dos rutas). */
enum class PlatformDownloadStorage(val label: String) {
    /** Preguntar al usuario en cada descarga. */
    ASK("Preguntar"),

    /** Memoria interna (ruta principal configurada). */
    INTERNAL("Interna"),

    /** Tarjeta SD (segunda ruta configurada). */
    SD("Tarjeta SD");

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
