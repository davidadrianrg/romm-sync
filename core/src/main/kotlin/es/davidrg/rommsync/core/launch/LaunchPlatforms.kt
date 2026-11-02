package es.davidrg.rommsync.core.launch

/**
 * Normaliza los slugs de plataforma de RomM (que vienen de IGDB/ES-DE y
 * varían: `dc`, `ngc`, `genesis-slash-megadrive`…) a un slug canónico corto
 * con el que se indexan emuladores y cores.
 */
object LaunchPlatforms {

    private val aliases: Map<String, String> = buildMap {
        fun alias(canonical: String, vararg others: String) {
            put(canonical, canonical)
            others.forEach { put(it, canonical) }
        }
        alias("nes", "famicom", "fds", "famicom-disk-system")
        alias("snes", "sfam", "super-famicom", "superfamicom", "sfc")
        alias("n64", "nintendo-64", "nintendo64")
        alias("gb", "gameboy", "game-boy")
        alias("gbc", "gameboy-color", "game-boy-color")
        alias("gba", "gameboy-advance", "game-boy-advance")
        alias("nds", "ds", "nintendo-ds")
        alias("3ds", "n3ds", "nintendo-3ds", "nintendo_3ds", "new-nintendo-3ds")
        alias("genesis", "genesis-slash-megadrive", "megadrive", "mega-drive", "md", "sega-genesis")
        alias("sms", "mastersystem", "master-system", "sega-master-system")
        alias("gg", "gamegear", "game-gear", "sega-game-gear")
        alias("scd", "segacd", "sega-cd", "megacd", "mega-cd")
        alias("32x", "sega32", "sega-32x", "sega32x")
        alias("sg1000", "sg-1000")
        alias("psx", "ps1", "ps", "playstation", "playstation-1")
        alias("ps2", "playstation-2", "playstation2")
        alias("ps3", "playstation-3", "playstation3")
        alias("psp", "playstation-portable")
        alias("saturn", "sega-saturn")
        alias("dreamcast", "dc", "sega-dreamcast")
        alias("gc", "ngc", "gamecube", "nintendo-gamecube")
        alias("wii", "nintendo-wii")
        alias("wiiu", "wii-u", "wii_u", "nintendo-wii-u")
        alias("switch", "nintendo-switch")
        alias("xbox", "microsoft-xbox")
        alias("xbox360", "xbox-360")
        alias("tg16", "turbografx16--1", "turbografx-16", "turbografx16", "pce", "pc-engine",
            "turbografx-16-slash-pc-engine")
        alias("tgcd", "turbografx-cd", "pce-cd", "pcenginecd", "turbografx-16-slash-pc-engine-cd")
        alias("pcfx", "pc-fx")
        alias("cdi", "philips-cd-i", "cd-i")
        alias("3do", "3do-interactive-multiplayer")
        alias("atari2600", "atari-2600")
        alias("atari5200", "atari-5200")
        alias("atari7800", "atari-7800")
        alias("lynx", "atari-lynx")
        alias("jaguar", "atari-jaguar")
        alias("ngp", "neo-geo-pocket")
        alias("ngpc", "neo-geo-pocket-color")
        alias("neogeo", "neogeoaes", "neogeomvs", "neo-geo", "neo-geo-aes", "neo-geo-mvs")
        alias("arcade", "mame", "fbneo", "cps1", "cps2", "cps3")
        alias("msx", "msx2")
        alias("wonderswan", "ws")
        alias("wsc", "wonderswan-color")
        alias("virtualboy", "virtual-boy", "vb")
        alias("pokemini", "pokemon-mini")
    }

    /** Slug canónico; si no se reconoce, el propio slug en minúsculas. */
    fun canonical(slug: String): String {
        val s = slug.trim().lowercase()
        return aliases[s] ?: s
    }

    private val optical = setOf(
        "psx", "ps2", "ps3", "psp", "saturn", "scd", "dreamcast", "gc", "wii", "wiiu",
        "xbox", "xbox360", "tgcd", "pcfx", "cdi", "3do",
    )

    /** Plataformas de disco (para elegir animación de inserción: disco vs cartucho). */
    fun isOptical(slug: String): Boolean = canonical(slug) in optical
}
