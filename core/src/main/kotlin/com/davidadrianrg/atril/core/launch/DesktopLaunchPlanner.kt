package com.davidadrianrg.atril.core.launch

import java.io.File

/**
 * Convierte la configuración de lanzamiento de una plataforma en la lista
 * exacta de argumentos para `ProcessBuilder`, o en un error explicable.
 * Sin efectos: no arranca nada, así que se prueba sin emuladores reales.
 */
object DesktopLaunchPlanner {

    /** Lo que el usuario guardó para una plataforma. Todo vacío = sin configurar. */
    data class Config(
        val emulatorId: String? = null,
        val executable: String? = null,
        val args: String? = null,
    ) {
        val isConfigured: Boolean get() = !emulatorId.isNullOrBlank()
    }

    sealed interface Plan {
        /** [command] = ejecutable resuelto + argumentos; pasar tal cual a ProcessBuilder. */
        data class Ready(val command: List<String>, val emulatorName: String) : Plan

        /** No hay emulador elegido para la plataforma. */
        data class NotConfigured(val platformSlug: String) : Plan

        /** Elegido, pero sin ejecutable (p. ej. «Personalizado» sin rellenar). */
        data class NoExecutable(val emulatorName: String) : Plan

        data class ExecutableMissing(val executable: String, val emulatorName: String) : Plan
        data class InvalidArgs(val reason: String) : Plan

        /** La plantilla usa `{core}` pero la plataforma no tiene core libretro. */
        data class NoCore(val platformSlug: String) : Plan
    }

    /** Ejecutable y plantilla efectivos: lo escrito por el usuario o el valor por defecto. */
    fun effectiveExecutable(config: Config): String {
        val emulator = DesktopEmulatorCatalog.byId(config.emulatorId)
        return config.executable?.trim()?.takeIf { it.isNotEmpty() } ?: emulator?.defaultExecutable.orEmpty()
    }

    fun effectiveArgs(config: Config): String {
        val emulator = DesktopEmulatorCatalog.byId(config.emulatorId)
        return config.args?.takeIf { it.isNotBlank() } ?: emulator?.defaultArgs.orEmpty()
    }

    /**
     * Resuelve [executable] a un fichero ejecutable: ruta con `/` tal cual, o
     * búsqueda en las carpetas de [pathEnv] (PATH). Null si no existe.
     */
    fun resolveExecutable(executable: String, pathEnv: String? = System.getenv("PATH")): File? {
        val name = executable.trim()
        if (name.isEmpty()) return null
        if ('/' in name) return File(name).takeIf { it.isFile && it.canExecute() }
        return pathEnv.orEmpty().split(File.pathSeparatorChar)
            .filter { it.isNotBlank() }
            .map { File(it, name) }
            .firstOrNull { it.isFile && it.canExecute() }
    }

    fun plan(
        platformSlug: String,
        config: Config,
        romPath: String,
        pathEnv: String? = System.getenv("PATH"),
    ): Plan {
        if (!config.isConfigured) return Plan.NotConfigured(platformSlug)
        val emulator = DesktopEmulatorCatalog.byId(config.emulatorId)
        val name = emulator?.displayName ?: config.emulatorId.orEmpty()

        val exeText = effectiveExecutable(config)
        if (exeText.isEmpty()) return Plan.NoExecutable(name)

        val args = when (val parsed = CommandTemplate.parse(effectiveArgs(config))) {
            is CommandTemplate.Parsed.Error -> return Plan.InvalidArgs(parsed.reason)
            is CommandTemplate.Parsed.Ok -> parsed.args
        }
        CommandTemplate.unknownPlaceholders(args).firstOrNull()?.let {
            return Plan.InvalidArgs("marcador desconocido $it")
        }

        val core = if (CommandTemplate.usesCore(args)) {
            DesktopEmulatorCatalog.coreFor(platformSlug) ?: return Plan.NoCore(platformSlug)
        } else {
            null
        }

        val exe = resolveExecutable(exeText, pathEnv) ?: return Plan.ExecutableMissing(exeText, name)
        return Plan.Ready(listOf(exe.absolutePath) + CommandTemplate.expand(args, romPath, core), name)
    }
}
