package es.davidrg.rommsync.core.launch

import es.davidrg.rommsync.core.launch.EmulatorCatalog.Family
import es.davidrg.rommsync.core.launch.EmulatorCatalog.Variant

/** Resultado de elegir emulador para una plataforma. */
sealed interface EmulatorSelection {
    /** Emulador instalado listo para lanzar. [preferred] = es la primera opción. */
    data class Ready(val family: Family, val variant: Variant, val preferred: Boolean) : EmulatorSelection

    /** Hay emuladores conocidos para la plataforma pero ninguno instalado. */
    data class NotInstalled(val candidates: List<Family>) : EmulatorSelection

    /** No conocemos ningún emulador que lance esta plataforma. */
    data object Unsupported : EmulatorSelection
}

/**
 * Elige el emulador a lanzar.
 *
 * Orden de preferencia:
 * 1. Override de lanzamiento elegido por el usuario para la plataforma.
 * 2. Emulador de saves configurado para la plataforma (si es lanzable).
 * 3. Cadena por defecto ([EmulatorCatalog.defaultChain]).
 *
 * Después se toma el primer paquete instalado recorriendo ese orden; si el
 * preferido no está, se cae al siguiente. Puro: los paquetes instalados se
 * inyectan, así se prueba sin Android.
 */
object EmulatorSelector {

    fun candidates(
        platformSlug: String,
        overrideFamilyId: String? = null,
        saveEmulatorId: String? = null,
    ): List<Family> {
        val override = Family.fromId(overrideFamilyId)?.takeIf { it.supports(platformSlug) }
        val fromSaves = Family.fromId(saveEmulatorId)?.takeIf { it.supports(platformSlug) }
        return (listOfNotNull(override, fromSaves) + EmulatorCatalog.defaultChain(platformSlug)).distinct()
    }

    fun select(
        platformSlug: String,
        installedPackages: Set<String>,
        overrideFamilyId: String? = null,
        saveEmulatorId: String? = null,
    ): EmulatorSelection {
        val ordered = candidates(platformSlug, overrideFamilyId, saveEmulatorId)
        if (ordered.isEmpty()) return EmulatorSelection.Unsupported
        ordered.forEachIndexed { index, family ->
            val variant = family.variants.firstOrNull { it.packageName in installedPackages }
            if (variant != null) return EmulatorSelection.Ready(family, variant, preferred = index == 0)
        }
        return EmulatorSelection.NotInstalled(ordered)
    }
}
