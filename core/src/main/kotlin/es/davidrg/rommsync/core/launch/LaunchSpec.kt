package es.davidrg.rommsync.core.launch

import es.davidrg.rommsync.core.launch.EmulatorCatalog.Family
import es.davidrg.rommsync.core.launch.EmulatorCatalog.Variant

/** Flags de intent, independientes de Android. */
enum class LaunchFlag { NEW_TASK, CLEAR_TASK, NO_HISTORY }

/** Extra del intent. [RomUri] lo sustituye el dispatcher por el content URI. */
sealed interface LaunchExtra {
    val key: String

    data class Text(override val key: String, val value: String) : LaunchExtra
    data class RomUri(override val key: String) : LaunchExtra
}

/**
 * Descripción pura de un intent de lanzamiento. El dispatcher de `:app` la
 * convierte en `Intent`, genera el content URI de [romPath] y concede permisos.
 */
data class LaunchSpec(
    val packageName: String,
    /** Null = intent por paquete: el dispatcher debe comprobar que resuelve. */
    val activityClass: String?,
    val action: String,
    val romPath: String,
    /** Si true, `data` = content URI de la ROM con [mimeType]. */
    val romUriAsData: Boolean,
    val mimeType: String?,
    val extras: List<LaunchExtra>,
    val flags: Set<LaunchFlag>,
    /** Conceder lectura del content URI (grant + clipData). */
    val grantRomRead: Boolean,
)

/** Datos del dispositivo que necesitan algunos emuladores (RetroArch). */
data class LaunchEnvironment(
    /** Raíz del almacenamiento principal, p. ej. `/storage/emulated/0`. */
    val primaryStorageRoot: String,
)

object LaunchSpecBuilder {

    const val DEFAULT_MIME = "application/octet-stream"
    private const val RETROARCH_IME = "com.android.inputmethod.latin/.LatinIME"

    /**
     * Construye el intent para [family]/[variant]. Devuelve null solo cuando
     * RetroArch no tiene core para la plataforma.
     */
    fun build(
        family: Family,
        variant: Variant,
        romPath: String,
        platformSlug: String,
        env: LaunchEnvironment,
    ): LaunchSpec? {
        val recipe = family.recipe
        if (recipe.retroArch) {
            val core = RetroArchCores.coreFor(platformSlug) ?: return null
            val pkg = variant.packageName
            val dataDir = "/data/data/$pkg"
            val externalDir = "${env.primaryStorageRoot}/Android/data/$pkg/files"
            return LaunchSpec(
                packageName = pkg,
                activityClass = variant.activityClass,
                action = recipe.action,
                romPath = romPath,
                romUriAsData = false,
                mimeType = null,
                extras = listOf(
                    LaunchExtra.Text("ROM", romPath),
                    LaunchExtra.Text("LIBRETRO", "$dataDir/cores/${core}_libretro_android.so"),
                    LaunchExtra.Text("CONFIGFILE", "$externalDir/retroarch.cfg"),
                    LaunchExtra.Text("IME", RETROARCH_IME),
                    LaunchExtra.Text("DATADIR", dataDir),
                    LaunchExtra.Text("SDCARD", env.primaryStorageRoot),
                    LaunchExtra.Text("EXTERNAL", externalDir),
                ),
                flags = setOf(LaunchFlag.NEW_TASK, LaunchFlag.CLEAR_TASK, LaunchFlag.NO_HISTORY),
                grantRomRead = true,
            )
        }

        val extras = recipe.pathExtras.map { LaunchExtra.Text(it, romPath) } +
            recipe.uriExtras.map { LaunchExtra.RomUri(it) }
        val usesUri = recipe.romAsData || recipe.uriExtras.isNotEmpty()
        // Igual que Argosy: si la ROM va en `data` (VIEW) el emulador queda en
        // recientes; si va solo en extras se lanza como one-shot.
        val flags = if (recipe.action == EmulatorCatalog.ACTION_VIEW) {
            setOf(LaunchFlag.NEW_TASK, LaunchFlag.CLEAR_TASK)
        } else {
            setOf(LaunchFlag.NEW_TASK, LaunchFlag.CLEAR_TASK, LaunchFlag.NO_HISTORY)
        }
        return LaunchSpec(
            packageName = variant.packageName,
            activityClass = variant.activityClass,
            action = recipe.action,
            romPath = romPath,
            romUriAsData = recipe.romAsData,
            mimeType = if (recipe.romAsData) DEFAULT_MIME else null,
            extras = extras,
            flags = flags,
            grantRomRead = usesUri,
        )
    }
}
