package es.davidrg.rommsync.core.sync

/**
 * Política de resolución de conflictos de saves (copia local vs servidor
 * cambiaron ambas desde el último sync):
 * - ASK: comportamiento clásico — se encolan y el usuario decide en la UI.
 * - PREFER_LOCAL: se sube la versión local con overwrite automáticamente.
 * - PREFER_SERVER: se descarga la versión del servidor automáticamente
 *   (con backup previo de la copia local).
 */
enum class ConflictPolicy(val id: String, val displayName: String) {
    ASK("ask", "Preguntar"),
    PREFER_LOCAL("local", "Siempre local"),
    PREFER_SERVER("server", "Siempre servidor"),
    ;

    companion object {
        fun fromId(id: String?): ConflictPolicy =
            entries.firstOrNull { it.id == id } ?: ASK
    }
}
