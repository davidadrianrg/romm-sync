package es.davidrg.rommsync.desktop

import java.io.File
import java.util.Properties

/**
 * Registro de ROMs descargados en desktop y config por plataforma.
 * Formato Properties (cero dependencias): ~/.config/romm-sync/library.properties
 *
 *   rom.<id>.name = ...
 *   rom.<id>.file = ...
 *   rom.<id>.platform = ...
 *   platform.<slug>.emulator = ...
 *   platform.<slug>.savesPath = ...
 */
class DesktopLibrary(private val file: File) {

    private val props = Properties()

    init {
        if (file.isFile) runCatching { file.inputStream().use { props.load(it) } }
    }

    @Synchronized
    private fun persist() {
        file.parentFile?.mkdirs()
        file.outputStream().use { props.store(it, "RomM Sync desktop library") }
    }

    data class RomEntry(
        val romId: Int,
        val name: String,
        val fileName: String,
        val platformSlug: String,
        val localPath: String? = null,
        val savesPathOverride: String? = null,
        val excludedFromSync: Boolean = false,
    )

    data class PlatformEntry(
        val slug: String,
        val emulatorId: String? = null,
        val savesPathOverride: String? = null,
    )

    fun roms(): List<RomEntry> {
        val ids = props.stringPropertyNames()
            .filter { it.startsWith("rom.") && it.endsWith(".name") }
            .mapNotNull { it.substringAfter("rom.").substringBefore('.').toIntOrNull() }
        return ids.mapNotNull { id -> readRom(id) }
    }

    fun rom(id: Int): RomEntry? = readRom(id)

    @Synchronized
    fun upsertRom(entry: RomEntry) {
        val p = "rom.${entry.romId}"
        props["$p.name"] = entry.name
        props["$p.file"] = entry.fileName
        props["$p.platform"] = entry.platformSlug
        props["$p.path"] = entry.localPath ?: ""
        props["$p.excluded"] = entry.excludedFromSync.toString()
        persist()
    }

    @Synchronized
    fun removeRom(romId: Int) {
        val prefix = "rom.$romId."
        props.stringPropertyNames().filter { it.startsWith(prefix) }.forEach { props.remove(it) }
        persist()
    }

    fun platform(slug: String): PlatformEntry {
        val emu = props.getProperty("platform.$slug.emulator")
        val saves = props.getProperty("platform.$slug.savesPath")?.takeIf { it.isNotBlank() }
        return PlatformEntry(slug, emu?.takeIf { it.isNotBlank() }, saves)
    }

    @Synchronized
    fun upsertPlatform(entry: PlatformEntry) {
        props["platform.${entry.slug}.emulator"] = entry.emulatorId ?: ""
        props["platform.${entry.slug}.savesPath"] = entry.savesPathOverride ?: ""
        persist()
    }

    private fun readRom(id: Int): RomEntry? {
        val name = props.getProperty("rom.$id.name") ?: return null
        return RomEntry(
            romId = id,
            name = name,
            fileName = props.getProperty("rom.$id.file") ?: name,
            platformSlug = props.getProperty("rom.$id.platform") ?: "unknown",
            localPath = props.getProperty("rom.$id.path")?.takeIf { it.isNotBlank() },
            excludedFromSync = props.getProperty("rom.$id.excluded") == "true",
        )
    }
}
