package es.davidrg.rommsync.core.launch

/**
 * Catálogo de emuladores lanzables. Paquetes, actividades y extras salen de
 * Argosy (`EmulatorRegistry.kt` / `GameLauncher.kt`), no de suposiciones.
 *
 * Los ids de familia coinciden con [es.davidrg.rommsync.core.sync.platform.SaveHandlerRegistry.EmulatorId]
 * cuando existe equivalente, de modo que el emulador elegido para los saves
 * de una plataforma es también el que se lanza.
 */
object EmulatorCatalog {

    const val ACTION_MAIN = "android.intent.action.MAIN"
    const val ACTION_VIEW = "android.intent.action.VIEW"

    /** Cómo se entrega la ROM al emulador. */
    data class Recipe(
        val action: String,
        /** ROM como `data` del intent (content URI del FileProvider). */
        val romAsData: Boolean,
        /** Extras con la ruta absoluta de la ROM. */
        val pathExtras: List<String> = emptyList(),
        /** Extras con el content URI de la ROM (tipo Uri). */
        val uriExtras: List<String> = emptyList(),
        /** RetroArch: extras propios (core, config, dirs). */
        val retroArch: Boolean = false,
        /** Contrato confirmado en Argosy. False = mejor esfuerzo. */
        val verified: Boolean = true,
    )

    /** Un paquete concreto de la familia (Play Store, GitHub, 64-bit…). */
    data class Variant(
        val packageName: String,
        /** Null = intent solo por paquete (lo resuelve el sistema). */
        val activityClass: String?,
    )

    enum class Family(
        val id: String,
        val displayName: String,
        val platforms: Set<String>,
        val variants: List<Variant>,
        val recipe: Recipe,
    ) {
        RETROARCH(
            id = "retroarch",
            displayName = "RetroArch",
            platforms = RetroArchCores.platforms,
            variants = listOf("com.retroarch", "com.retroarch.aarch64", "com.retroarch.ra32").map {
                Variant(it, "com.retroarch.browser.retroactivity.RetroActivityFuture")
            },
            recipe = Recipe(action = ACTION_MAIN, romAsData = false, retroArch = true),
        ),
        MUPEN64PLUS_FZ(
            id = "mupen64plus_fz",
            displayName = "Mupen64Plus FZ",
            platforms = setOf("n64"),
            variants = listOf("org.mupen64plusae.v3.fzurita", "org.mupen64plusae.v3.fzurita.pro").map {
                Variant(it, "paulscode.android.mupen64plusae.SplashActivity")
            },
            recipe = Recipe(action = ACTION_VIEW, romAsData = true),
        ),
        ARMSX2(
            id = "armsx2",
            displayName = "ARMSX2",
            platforms = setOf("ps2"),
            variants = listOf(
                Variant("com.armsx2", "com.armsx2.MainActivity"),
                Variant("come.nanodata.armsx2", "kr.co.iefriends.pcsx2.activities.MainActivity"),
            ),
            recipe = Recipe(action = ACTION_VIEW, romAsData = true),
        ),
        ARMSX3(
            id = "armsx3",
            displayName = "ARMSX3",
            platforms = setOf("ps3"),
            variants = listOf("com.armsx3", "com.armsx3.play").map {
                Variant(it, "com.armsx2.BootSplashActivity")
            },
            recipe = Recipe(action = ACTION_MAIN, romAsData = false, pathExtras = listOf("path")),
        ),
        DOLPHIN(
            id = "dolphin",
            displayName = "Dolphin",
            platforms = setOf("gc", "wii"),
            variants = listOf("org.dolphinemu.dolphinemu", "org.dolphinemu.handheld").map {
                Variant(it, "org.dolphinemu.dolphinemu.ui.main.MainActivity")
            },
            recipe = Recipe(action = ACTION_MAIN, romAsData = false, uriExtras = listOf("AutoStartFile")),
        ),
        EDEN(
            id = "eden",
            displayName = "Eden",
            platforms = setOf("switch"),
            variants = listOf(Variant("dev.eden.eden_emulator", "org.yuzu.yuzu_emu.activities.EmulationActivity")),
            recipe = Recipe(action = ACTION_VIEW, romAsData = true),
        ),
        XENDROID_PLUS(
            id = "xendroid_plus",
            displayName = "XenDroid+",
            platforms = setOf("xbox360"),
            variants = listOf(Variant("xendroid.compose.fork", "xendroid.compose.EmulatorHostActivity")),
            recipe = Recipe(action = "xendroid.intent.action.xendroid", romAsData = false, pathExtras = listOf("game_uri")),
        ),
        XENDROID(
            id = "xendroid",
            displayName = "XenDroid",
            platforms = setOf("xbox360"),
            variants = listOf(Variant("xendroid.compose", "xendroid.compose.EmulatorHostActivity")),
            recipe = Recipe(action = "xendroid.intent.action.xendroid", romAsData = false, pathExtras = listOf("game_uri")),
        ),
        HAKUX(
            id = "hakux",
            displayName = "hakuX",
            platforms = setOf("xbox"),
            // Argosy no define contrato propio: VIEW por paquete con content URI.
            variants = listOf(Variant("com.rfandango.haku_x", null)),
            recipe = Recipe(action = ACTION_VIEW, romAsData = true, verified = false),
        ),
        MELONDS(
            id = "melonds",
            displayName = "melonDS",
            platforms = setOf("nds"),
            variants = listOf(Variant("me.magnum.melonds", "me.magnum.melonds.ui.emulator.EmulatorActivity")),
            recipe = Recipe(action = "me.magnum.melonds.LAUNCH_ROM", romAsData = true, uriExtras = listOf("uri")),
        ),
        PPSSPP(
            id = "ppsspp",
            displayName = "PPSSPP",
            platforms = setOf("psp"),
            variants = listOf(
                Variant("org.ppsspp.ppsspp", "org.ppsspp.ppsspp.PpssppActivity"),
                Variant("org.ppsspp.ppssppgold", "org.ppsspp.ppsspp.PpssppActivity"),
            ),
            recipe = Recipe(action = ACTION_VIEW, romAsData = true),
        ),
        AZAHAR(
            id = "azahar",
            displayName = "Azahar",
            platforms = setOf("3ds"),
            variants = listOf(Variant("org.azahar_emu.azahar", "org.citra.citra_emu.activities.EmulationActivity")),
            recipe = Recipe(action = ACTION_VIEW, romAsData = true, pathExtras = listOf("SelectedGame")),
        ),
        CEMU(
            id = "cemu",
            displayName = "Cemu",
            platforms = setOf("wiiu"),
            variants = listOf(Variant("info.cemu.cemu", "info.cemu.cemu.emulation.EmulationActivity")),
            recipe = Recipe(action = ACTION_VIEW, romAsData = true),
        ),
        ;

        fun supports(platformSlug: String): Boolean = LaunchPlatforms.canonical(platformSlug) in platforms

        companion object {
            fun fromId(id: String?): Family? = id?.let { wanted -> entries.firstOrNull { it.id == wanted } }
        }
    }

    /** Todos los paquetes conocidos (para `<queries>` y detección). */
    val allPackages: Set<String> get() = Family.entries.flatMap { f -> f.variants.map { it.packageName } }.toSet()

    /**
     * Orden por defecto por plataforma: RetroArch para todo lo de Dreamcast hacia
     * abajo (N64 incluido, Mupen64Plus FZ como alternativa), y los standalone que
     * ya usa la sincronización de saves para el resto.
     */
    fun defaultChain(platformSlug: String): List<Family> = when (LaunchPlatforms.canonical(platformSlug)) {
        "n64" -> listOf(Family.RETROARCH, Family.MUPEN64PLUS_FZ)
        "ps2" -> listOf(Family.ARMSX2)
        "ps3" -> listOf(Family.ARMSX3)
        "gc", "wii" -> listOf(Family.DOLPHIN, Family.RETROARCH)
        "switch" -> listOf(Family.EDEN)
        "xbox" -> listOf(Family.HAKUX)
        "xbox360" -> listOf(Family.XENDROID_PLUS, Family.XENDROID)
        "nds" -> listOf(Family.MELONDS, Family.RETROARCH)
        "psp" -> listOf(Family.PPSSPP, Family.RETROARCH)
        "3ds" -> listOf(Family.AZAHAR, Family.RETROARCH)
        "wiiu" -> listOf(Family.CEMU)
        else -> if (Family.RETROARCH.supports(platformSlug)) listOf(Family.RETROARCH) else emptyList()
    }

    /** Familias que pueden lanzar la plataforma (para el selector de la UI). */
    fun familiesFor(platformSlug: String): List<Family> =
        (defaultChain(platformSlug) + Family.entries.filter { it.supports(platformSlug) }).distinct()
}

/** Core libretro por plataforma canónica (preferencias de Argosy). */
object RetroArchCores {
    private val cores = mapOf(
        "nes" to "fceumm",
        "snes" to "snes9x",
        "n64" to "mupen64plus_next_gles3",
        "gb" to "gambatte",
        "gbc" to "gambatte",
        "gba" to "mgba",
        "nds" to "melonds",
        "3ds" to "citra",
        "genesis" to "genesis_plus_gx",
        "sms" to "genesis_plus_gx",
        "gg" to "genesis_plus_gx",
        "scd" to "genesis_plus_gx",
        "sg1000" to "genesis_plus_gx",
        "32x" to "picodrive",
        "psx" to "pcsx_rearmed",
        "psp" to "ppsspp",
        "saturn" to "yabasanshiro",
        "dreamcast" to "flycast",
        "gc" to "dolphin",
        "wii" to "dolphin",
        "tg16" to "mednafen_pce_fast",
        "tgcd" to "mednafen_pce_fast",
        "pcfx" to "mednafen_pcfx",
        "cdi" to "same_cdi",
        "3do" to "opera",
        "atari2600" to "stella",
        "atari5200" to "atari800",
        "atari7800" to "prosystem",
        "lynx" to "handy",
        "jaguar" to "virtualjaguar",
        "ngp" to "mednafen_ngp",
        "ngpc" to "mednafen_ngp",
        "neogeo" to "fbneo",
        "arcade" to "fbneo",
        "msx" to "bluemsx",
        "wonderswan" to "mednafen_wswan",
        "wsc" to "mednafen_wswan",
        "virtualboy" to "mednafen_vb",
        "pokemini" to "pokemini",
    )

    val platforms: Set<String> get() = cores.keys

    fun coreFor(platformSlug: String): String? = cores[LaunchPlatforms.canonical(platformSlug)]
}
