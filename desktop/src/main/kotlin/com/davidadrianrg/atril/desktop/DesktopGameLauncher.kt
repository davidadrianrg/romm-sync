package com.davidadrianrg.atril.desktop

import com.davidadrianrg.atril.core.launch.DesktopLaunchPlanner
import com.davidadrianrg.atril.core.launch.DesktopLaunchPlanner.Plan
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Lanza un juego con el emulador configurado para su plataforma.
 *
 * Siempre `ProcessBuilder` con una lista de argumentos (jamás un shell), así
 * una ruta con espacios o metacaracteres llega intacta como un solo argumento.
 * No hay fallback al «abrir con» del sistema: sin emulador configurado se
 * devuelve [Result.Rejected] y la UI explica cómo configurarlo.
 */
class DesktopGameLauncher(
    private val pathEnv: String? = System.getenv("PATH"),
    /** Punto de arranque; sustituible en tests. */
    private val start: (List<String>, File?) -> Process = { command, dir ->
        ProcessBuilder(command)
            .directory(dir)
            .redirectInput(ProcessBuilder.Redirect.from(File("/dev/null")))
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
    },
) {

    sealed interface Result {
        /** Proceso arrancado. [process] sigue vivo salvo que el emulador salga enseguida. */
        data class Started(val command: List<String>, val process: Process) : Result

        /** No se pudo ni intentar: el [plan] dice por qué (no configurado, ejecutable ausente…). */
        data class Rejected(val plan: Plan) : Result

        data class Failed(val message: String) : Result
    }

    fun launch(platformSlug: String, config: DesktopLaunchPlanner.Config, rom: File): Result {
        val plan = DesktopLaunchPlanner.plan(platformSlug, config, rom.absolutePath, pathEnv)
        if (plan !is Plan.Ready) return Result.Rejected(plan)
        return try {
            Result.Started(plan.command, start(plan.command, rom.absoluteFile.parentFile))
        } catch (e: Exception) {
            Result.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    companion object {
        /** Si el proceso muere en menos de esto con error, se avisa al usuario. */
        const val EARLY_EXIT_WINDOW_MS = 4000L

        /** Espera (en un hilo aparte) a que el proceso salga pronto; null = sigue vivo. */
        fun earlyFailureCode(process: Process, windowMs: Long = EARLY_EXIT_WINDOW_MS): Int? =
            if (process.waitFor(windowMs, TimeUnit.MILLISECONDS)) process.exitValue().takeIf { it != 0 } else null
    }
}
