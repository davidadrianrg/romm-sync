package es.davidrg.rommsync.desktop

import java.io.File

/**
 * Exporta la metadata de la biblioteca local a ES-DE: escribe/fusiona
 * gamelist.xml por plataforma con las entradas de los ROMs descargados.
 *
 * Merge in-place REAL: los <game> ya presentes en el gamelist (identificados
 * por su <path>) se conservan TAL CUAL — nunca se sobrescriben campos que el
 * usuario haya editado a mano — y solo se añaden al final las entradas nuevas.
 * El resto del XML (comentarios, <folder>, atributos) se preserva verbatim.
 *
 * Estructura: <esdeDataDir>/gamelists/<system>/gamelist.xml
 */
class DesktopEsdeExporter(
    private val esdeDataDir: String,
    private val romsRoot: String,
    private val library: DesktopLibrary,
) {

    /** Entrada a exportar: ruta relativa (./...) y nombre visible. */
    data class Entry(val relPath: String, val name: String)

    /** Exporta y devuelve un resumen legible. */
    fun export(): String {
        val roms = library.roms()
        if (roms.isEmpty()) return "No hay ROMs descargados que exportar"

        val byPlatform = roms.groupBy { it.platformSlug }
        var totalEntries = 0
        val written = mutableListOf<String>()

        for ((slug, romsInPlatform) in byPlatform) {
            val gamelistDir = File(File(esdeDataDir, "gamelists"), slug)
            gamelistDir.mkdirs()
            val gamelist = File(gamelistDir, "gamelist.xml")

            val entries = romsInPlatform.map { rom ->
                val relPath = "./" + (rom.localPath
                    ?.let { relativeToRomsRoot(it) }
                    ?: "${slug}/${rom.fileName}")
                Entry(relPath, rom.name)
            }

            val existingText = if (gamelist.isFile) runCatching { gamelist.readText() }.getOrNull() else null
            val (merged, added) = mergeGamelist(existingText, entries)
            gamelist.writeText(merged)
            totalEntries += added
            written.add("$slug (+$added)")
        }

        return "Añadidas $totalEntries entradas en ${written.size} gamelists: " +
            written.joinToString(", ")
    }

    companion object {

        private val GAME_BLOCK = Regex("<game[^>]*>[\\s\\S]*?</game>")
        private val PATH_FIELD = Regex("<path>([\\s\\S]*?)</path>")

        /**
         * Fusiona las entradas nuevas con un gamelist existente.
         *
         * - Entrada nueva cuyo <path> ya existe → se conserva el bloque
         *   existente sin tocar (ediciones manuales intactas), no se añade.
         * - Bloques existentes no relacionados → se preservan en su orden,
         *   junto con todo el texto de alrededor (skeleton verbatim).
         * - Entradas nuevas → se añaden al final, antes de </gameList>.
         *
         * @return XML fusionado + nº de entradas añadidas.
         */
        internal fun mergeGamelist(existingText: String?, entries: List<Entry>): Pair<String, Int> {
            val text = existingText?.takeIf { it.isNotBlank() }
            if (text == null) {
                return buildString {
                    append("<?xml version=\"1.0\"?>\n<gameList>\n")
                    entries.forEach { append(newBlock(it)) }
                    append("</gameList>\n")
                } to entries.size
            }

            val matches = GAME_BLOCK.findAll(text).toList()
            val existingPaths = matches
                .mapNotNull { m -> PATH_FIELD.find(m.value)?.groupValues?.get(1)?.trim() }
                .toSet()

            val toAdd = entries.filter { escape(it.relPath) !in existingPaths }
            if (toAdd.isEmpty()) return text to 0

            val newBlocks = toAdd.joinToString("") { newBlock(it) }

            // Sin bloques previos: insertar antes del cierre (o al final si el
            // XML no tiene <gameList> reconocible).
            if (matches.isEmpty()) {
                val closeIdx = text.lastIndexOf("</gameList>")
                val merged = if (closeIdx >= 0) {
                    text.substring(0, closeIdx) + newBlocks + text.substring(closeIdx)
                } else {
                    text + newBlocks
                }
                return merged to toAdd.size
            }

            // Reconstrucción interleaved: skeleton[i] + block[i] ... + skeleton[n].
            // Las entradas nuevas van justo antes del skeleton final (que suele
            // ser "\n</gameList>\n"), es decir, al final del bloque de <game>.
            val lastMatch = matches.last()
            val before = text.substring(0, lastMatch.range.last + 1)
            val after = text.substring(lastMatch.range.last + 1)
            return (before + "\n" + newBlocks.trimEnd() + after) to toAdd.size
        }

        private fun newBlock(e: Entry): String = buildString {
            append("    <game>\n")
            append("        <path>").append(escape(e.relPath)).append("</path>\n")
            append("        <name>").append(escape(e.name)).append("</name>\n")
            append("    </game>\n")
        }

        private fun escape(s: String): String = s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    private fun relativeToRomsRoot(absolute: String): String {
        val root = romsRoot.trimEnd('/')
        return if (absolute.startsWith(root)) absolute.removePrefix(root).trimStart('/') else absolute
    }
}
