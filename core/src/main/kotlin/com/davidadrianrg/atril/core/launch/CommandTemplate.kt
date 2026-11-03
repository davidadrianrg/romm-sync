package com.davidadrianrg.atril.core.launch

/**
 * Plantilla de línea de comandos de un emulador de escritorio, p. ej.
 * `-L {core} {rom}`.
 *
 * NO se pasa por ningún shell: [parse] la trocea en argumentos al estilo
 * shell (espacios separan, `"…"` y `'…'` agrupan, `\` escapa) y [expand]
 * sustituye los marcadores DENTRO de cada argumento ya separado. Así una ruta
 * con espacios, comillas o `;` viaja siempre como un único argumento y no
 * puede inyectar otros comandos.
 *
 * Marcadores: `{rom}` (ruta absoluta del juego) y `{core}` (core libretro).
 */
object CommandTemplate {

    const val ROM = "{rom}"
    const val CORE = "{core}"

    sealed interface Parsed {
        data class Ok(val args: List<String>) : Parsed
        data class Error(val reason: String) : Parsed
    }

    /** Trocea [template] en argumentos. Vacío es válido (sin argumentos). */
    fun parse(template: String): Parsed {
        val args = mutableListOf<String>()
        val cur = StringBuilder()
        var inToken = false
        var quote: Char? = null
        var i = 0
        while (i < template.length) {
            val c = template[i]
            when {
                quote == '\'' -> if (c == '\'') quote = null else cur.append(c)
                quote == '"' -> when {
                    c == '"' -> quote = null
                    c == '\\' && i + 1 < template.length && template[i + 1] in "\"\\" -> {
                        cur.append(template[i + 1]); i++
                    }
                    else -> cur.append(c)
                }
                c == '\'' || c == '"' -> { quote = c; inToken = true }
                c == '\\' && i + 1 < template.length -> { cur.append(template[i + 1]); inToken = true; i++ }
                c.isWhitespace() -> if (inToken) { args += cur.toString(); cur.clear(); inToken = false }
                else -> { cur.append(c); inToken = true }
            }
            i++
        }
        if (quote != null) return Parsed.Error("comilla $quote sin cerrar")
        if (inToken) args += cur.toString()
        return Parsed.Ok(args)
    }

    /** Marcadores `{…}` desconocidos en [args] (todo lo que no sea rom/core). */
    fun unknownPlaceholders(args: List<String>): List<String> =
        args.flatMap { Regex("""\{[^{}]*}""").findAll(it).map { m -> m.value }.toList() }
            .filter { it != ROM && it != CORE }
            .distinct()

    fun usesCore(args: List<String>): Boolean = args.any { CORE in it }

    fun usesRom(args: List<String>): Boolean = args.any { ROM in it }

    /**
     * Sustituye los marcadores. Si ningún argumento contiene `{rom}`, la ruta
     * del juego se añade como último argumento (convención de casi todos los
     * emuladores). [core] puede ser null si la plantilla no usa `{core}`.
     */
    fun expand(args: List<String>, rom: String, core: String?): List<String> {
        val out = args.map { a ->
            var s = a.replace(ROM, rom)
            if (core != null) s = s.replace(CORE, core)
            s
        }
        return if (usesRom(args)) out else out + rom
    }
}
