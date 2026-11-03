package com.davidadrianrg.atril.core.launch

/**
 * Catálogo de emuladores de ESCRITORIO por plataforma. Es solo información
 * para sugerir un ejecutable y una plantilla: no hace falta que el emulador
 * esté instalado para guardar la configuración, y Atril nunca instala ni
 * descarga nada.
 *
 * Los ejecutables son los nombres habituales de los paquetes de distribución;
 * en Flatpak/AppImage el usuario los cambia (`flatpak` + `run org.…`).
 */
object DesktopEmulatorCatalog {

    /** Id reservado: el usuario escribe ejecutable y argumentos. */
    const val CUSTOM_ID = "custom"

    data class Emulator(
        val id: String,
        val displayName: String,
        /** Slugs canónicos ([LaunchPlatforms.canonical]) que sabe abrir. */
        val platforms: Set<String>,
        /** Nombre del ejecutable (se busca en PATH) o ruta absoluta. */
        val defaultExecutable: String,
        /** Plantilla por defecto; ver [CommandTemplate]. */
        val defaultArgs: String,
    ) {
        fun supports(platformSlug: String): Boolean =
            LaunchPlatforms.canonical(platformSlug) in platforms
    }

    val custom = Emulator(
        id = CUSTOM_ID,
        displayName = "Custom",
        platforms = emptySet(),
        defaultExecutable = "",
        defaultArgs = "{rom}",
    )

    val all: List<Emulator> = listOf(
        Emulator("retroarch", "RetroArch", RetroArchCores.platforms, "retroarch", "-L {core} {rom}"),
        Emulator("dolphin", "Dolphin", setOf("gc", "wii"), "dolphin-emu", "-b -e {rom}"),
        Emulator("ppsspp", "PPSSPP", setOf("psp"), "PPSSPPSDL", "{rom}"),
        Emulator("duckstation", "DuckStation", setOf("psx"), "duckstation-qt", "-batch -- {rom}"),
        Emulator("pcsx2", "PCSX2", setOf("ps2"), "pcsx2-qt", "-batch -- {rom}"),
        Emulator("rpcs3", "RPCS3", setOf("ps3"), "rpcs3", "{rom}"),
        Emulator("mupen64plus", "Mupen64Plus", setOf("n64"), "mupen64plus", "--fullscreen {rom}"),
        Emulator("mgba", "mGBA", setOf("gba", "gb", "gbc"), "mgba-qt", "-f {rom}"),
        Emulator("melonds", "melonDS", setOf("nds"), "melonDS", "{rom}"),
        Emulator("azahar", "Azahar", setOf("3ds"), "azahar", "{rom}"),
        Emulator("cemu", "Cemu", setOf("wiiu"), "cemu", "-g {rom}"),
        Emulator("eden", "Eden", setOf("switch"), "eden", "-g {rom}"),
        Emulator("flycast", "Flycast", setOf("dreamcast"), "flycast", "{rom}"),
        Emulator("xemu", "xemu", setOf("xbox"), "xemu", "-dvd_path {rom}"),
    )

    fun byId(id: String?): Emulator? =
        if (id == CUSTOM_ID) custom else all.firstOrNull { it.id == id }

    /** Emuladores que anuncian soporte para [platformSlug] (vacío = sin mapping). */
    fun forPlatform(platformSlug: String): List<Emulator> =
        all.filter { it.supports(platformSlug) }

    /**
     * Core libretro de escritorio para [platformSlug], o null si no hay.
     * Se parte de los cores de Android y se corrigen los que en escritorio
     * se llaman distinto (la variante `gles3` es solo móvil).
     */
    fun coreFor(platformSlug: String): String? {
        val slug = LaunchPlatforms.canonical(platformSlug)
        val name = desktopCoreOverrides[slug] ?: RetroArchCores.coreFor(slug) ?: return null
        return "${name}_libretro.so"
    }

    private val desktopCoreOverrides = mapOf(
        "n64" to "mupen64plus_next",
        "saturn" to "mednafen_saturn",
    )
}
