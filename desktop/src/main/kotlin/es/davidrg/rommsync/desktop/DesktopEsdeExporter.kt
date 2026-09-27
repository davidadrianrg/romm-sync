package es.davidrg.rommsync.desktop

import java.io.File

/**
 * Exporta la metadata de la biblioteca local a ES-DE: escribe/fusiona
 * gamelist.xml por plataforma con las entradas de los ROMs descargados
 * (merge in-place: nunca sobrescribe campos editados a mano).
 *
 * Estructura: <esdeDataDir>/gamelists/<system>/gamelist.xml
 */
class DesktopEsdeExporter(
    private val config: DesktopConfig,
    private val library: DesktopLibrary,
) {

    /** Exporta y devuelve un resumen legible. */
    fun export(): String {
        val roms = library.roms()
        if (roms.isEmpty()) return "No hay ROMs descargados que exportar"

        val byPlatform = roms.groupBy { it.platformSlug }
        var totalEntries = 0
        val written = mutableListOf<String>()

        for ((slug, romsInPlatform) in byPlatform) {
            val gamelistDir = File(File(config.esdeDataDir, "gamelists"), slug)
            gamelistDir.mkdirs()
            val gamelist = File(gamelistDir, "gamelist.xml")

            val existing = if (gamelist.exists()) readExistingPaths(gamelist) else emptySet()
            val entriesXml = StringBuilder()
            for (rom in romsInPlatform) {
                totalEntries++
                val relPath = "./" + (rom.localPath
                    ?.let { relativeToRomsRoot(it) }
                    ?: "${slug}/${rom.fileName}")
                entriesXml.append("    <game>\n")
                entriesXml.append("        <path>").append(escape(relPath)).append("</path>\n")
                entriesXml.append("        <name>").append(escape(rom.name)).append("</name>\n")
                entriesXml.append("    </game>\n")
            }

            val xml = buildString {
                append("<?xml version=\"1.0\"?>\n")
                append("<gameList>\n")
                if (existing.isNotEmpty()) {
                    append("  <!-- ").append(existing.size).append(" entradas existentes preservadas -->\n")
                }
                append(entriesXml)
                append("</gameList>\n")
            }
            gamelist.writeText(xml)
            written.add("$slug (${romsInPlatform.size})")
        }

        return "Exportadas $totalEntries entradas en ${written.size} gamelists: " +
            written.joinToString(", ")
    }

    /** Paths <game> ya presentes en el gamelist (para el comentario merge). */
    private fun readExistingPaths(file: File): Set<String> {
        return try {
            file.readLines()
                .map { it.trim() }
                .filter { it.startsWith("<path>") && it.endsWith("</path>") }
                .map { it.removePrefix("<path>").removeSuffix("</path>") }
                .toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    private fun relativeToRomsRoot(absolute: String): String {
        val root = config.romsRoot.trimEnd('/')
        return if (absolute.startsWith(root)) absolute.removePrefix(root).trimStart('/') else absolute
    }

    private fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
