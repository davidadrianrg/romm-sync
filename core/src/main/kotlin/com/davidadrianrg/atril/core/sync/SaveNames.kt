package com.davidadrianrg.atril.core.sync

/**
 * Nombres de fichero de saves en RomM.
 *
 * Las subidas con slot se guardan en el servidor con una etiqueta de versión
 * (`Juego [2026-11-01_12-34-56].srm`). Al descargar hay que quitarla: el
 * emulador espera el nombre canónico (`Juego.srm`).
 */
object SaveNames {

    private val VERSION_TAG = Regex("""\s*\[\d{4}-\d{2}-\d{2}_\d{2}-\d{2}-\d{2}](?=\.[^.]+$)""")

    fun stripVersionTag(fileName: String): String =
        VERSION_TAG.replace(fileName, "")
}
