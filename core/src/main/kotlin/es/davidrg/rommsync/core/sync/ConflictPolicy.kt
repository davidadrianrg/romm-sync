package es.davidrg.rommsync.core.sync

import es.davidrg.rommsync.core.i18n.tr

/**
 * Política de resolución de conflictos de saves (copia local vs servidor
 * cambiaron ambas desde el último sync):
 * - ASK: comportamiento clásico — se encolan y el usuario decide en la UI.
 * - PREFER_LOCAL: se sube la versión local con overwrite automáticamente.
 * - PREFER_SERVER: se descarga la versión del servidor automáticamente
 *   (con backup previo de la copia local).
 */
enum class ConflictPolicy(val id: String) {
    ASK("ask"),
    PREFER_LOCAL("local"),
    PREFER_SERVER("server"),
    ;

    val displayName: String get() = tr("conflict_policy.$id")

    companion object {
        fun fromId(id: String?): ConflictPolicy =
            entries.firstOrNull { it.id == id } ?: ASK
    }
}
