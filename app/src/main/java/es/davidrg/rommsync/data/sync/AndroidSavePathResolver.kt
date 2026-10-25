package es.davidrg.rommsync.data.sync

import android.content.Context
import es.davidrg.rommsync.core.sync.platform.DolphinSaveHandler
import es.davidrg.rommsync.core.sync.platform.SaveHandlerRegistry
import es.davidrg.rommsync.core.util.RootShell
import es.davidrg.rommsync.data.storage.ManagedStorageAccessor
import java.io.File

/**
 * Resuelve la ruta de saves por defecto de cada emulador en Android mirando
 * TODAS las ubicaciones conocidas (registro portado de Argosy) y eligiendo la
 * que existe y tiene contenido. Antes se usaba una sola ruta fija y cualquier
 * variante de paquete (`com.retroarch.aarch64`, `ppssppgold`, `Dolphin MMJR`,
 * `melondualds`, `org.azahar_emu`…) o de layout (saves por core) dejaba el
 * sync a cero en silencio.
 *
 * Se consulta antes del staging: si la elegida está en Android/data y no es
 * legible con File, [SavePathAccess] la copia vía DocumentsContract manage=true.
 */
object AndroidSavePathResolver {

    private const val EXT = "/storage/emulated/0"

    private val GC_SLUGS = setOf("gc", "gamecube", "ngc")

    private val SAVE_DIR_RE = Regex(
        """^\s*savefile_directory\s*=\s*"([^"]*)"""",
        RegexOption.MULTILINE,
    )

    fun resolveDefault(
        emulatorId: String,
        platformSlug: String,
        retroArchBase: String,
        context: Context,
    ): String {
        val candidates = candidatesFor(emulatorId, platformSlug, retroArchBase, context).distinct()
        candidates.firstOrNull { hasContent(it, context) }?.let { return it }
        candidates.firstOrNull { isAccessible(it, context) }?.let { return it }
        candidates.firstOrNull()?.let { return it }
        return SaveHandlerRegistry.getDefaultSavesPath(emulatorId, platformSlug, retroArchBase)
    }

    private fun candidatesFor(
        emulatorId: String,
        platformSlug: String,
        retroArchBase: String,
        context: Context,
    ): List<String> {
        val slug = platformSlug.lowercase()

        return when (emulatorId) {
            SaveHandlerRegistry.EmulatorId.RETROARCH.id -> buildList {
                retroArchBase.trim().takeIf { it.isNotEmpty() }?.let { add(it) }
                addAll(retroArchConfigCandidates(context))
                addAll(
                    listOf(
                        "$EXT/RetroArch",
                        "$EXT/Android/data/com.retroarch/files",
                        "$EXT/Android/data/com.retroarch.aarch64/files",
                        "$EXT/Android/data/com.retroarch.ra32/files",
                    ),
                )
            }
            SaveHandlerRegistry.EmulatorId.MELONDS.id -> listOf(
                "$EXT/Android/data/me.magnum.melonds/files/saves",
                "$EXT/Android/data/me.magnum.melondualds/files/saves",
                "$EXT/melonds/saves",
            )
            SaveHandlerRegistry.EmulatorId.PPSSPP.id -> listOf(
                // Android/data primero: /PSP/SAVEDATA puede ser un memstick viejo.
                "$EXT/Android/data/org.ppsspp.ppsspp/files/PSP/SAVEDATA",
                "$EXT/Android/data/org.ppsspp.ppssppgold/files/PSP/SAVEDATA",
                "$EXT/PSP/SAVEDATA",
            )
            SaveHandlerRegistry.EmulatorId.AETHERSX2.id -> listOf(
                "$EXT/Android/data/xyz.aethersx2.android/files/memcards",
            )
            SaveHandlerRegistry.EmulatorId.ARMSX2.id -> listOf(
                "$EXT/Android/data/com.armsx2/files/memcards",
            )
            SaveHandlerRegistry.EmulatorId.ARMSX3.id -> listOf(
                "$EXT/Android/data/com.armsx3/files/config/dev_hdd0/home/00000001/savedata",
                "$EXT/Android/data/com.armsx3.play/files/config/dev_hdd0/home/00000001/savedata",
            )
            SaveHandlerRegistry.EmulatorId.DOLPHIN.id -> if (slug in GC_SLUGS) listOf(
                "$EXT/Android/data/org.dolphinemu.dolphinemu/files/GC",
                "$EXT/Android/data/org.dolphinemu.handheld/files/GC",
                "$EXT/Android/data/org.dolphinemu.mmjr/files/GC",
                "$EXT/Android/data/com.joeyos.dolphinemu/files/GC",
                "$EXT/dolphin-emu/GC",
            ) else listOf(
                "$EXT/Android/data/org.dolphinemu.dolphinemu/files/Wii/title/00010000",
                "$EXT/Android/data/org.dolphinemu.handheld/files/Wii/title/00010000",
                "$EXT/Android/data/org.dolphinemu.mmjr/files/Wii/title/00010000",
                "$EXT/dolphin-emu/Wii/title/00010000",
            )
            SaveHandlerRegistry.EmulatorId.EDEN.id -> listOf(
                "$EXT/Android/data/dev.eden.eden_emulator/files/nand/user/save",
                "$EXT/Android/data/dev.eden/files/nand/user/save",
            )
            SaveHandlerRegistry.EmulatorId.AZAHAR.id -> listOf(
                "$EXT/Android/data/org.azahar_emu.azahar/files/sdmc/Nintendo 3DS",
                "$EXT/Android/data/io.github.azahar_emu.azahar/files/sdmc/Nintendo 3DS",
            )
            SaveHandlerRegistry.EmulatorId.CEMU.id -> listOf(
                "$EXT/Android/data/info.cemu.cemu/files/mlc01/usr/save/00050000",
            )
            SaveHandlerRegistry.EmulatorId.ANDROID_NATIVE.id -> listOf("$EXT/Android/data")
            else -> emptyList()
        }
    }

    /**
     * Lee `retroarch.cfg` (File, managed access o root) y traduce
     * `savefile_directory` a la raíz que espera [RetroArchSaveHandler]
     * (el padre cuando el cfg apunta a .../saves).
     */
    private fun retroArchConfigCandidates(context: Context): List<String> {
        val cfgPaths = listOf(
            "$EXT/Android/data/com.retroarch/files/retroarch.cfg",
            "$EXT/Android/data/com.retroarch.aarch64/files/retroarch.cfg",
            "$EXT/Android/data/com.retroarch.ra32/files/retroarch.cfg",
            "$EXT/RetroArch/retroarch.cfg",
            "$EXT/RetroArch/config/retroarch.cfg",
        )
        val out = mutableListOf<String>()
        for (cfg in cfgPaths) {
            val text = readText(cfg, context) ?: continue
            val saveDir = SAVE_DIR_RE.find(text)?.groupValues?.get(1)?.trim()?.trimEnd('/')
            if (!saveDir.isNullOrEmpty() && !saveDir.equals("default", ignoreCase = true) &&
                !saveDir.equals(":", ignoreCase = true)
            ) {
                val normalized = if (saveDir.startsWith("/")) saveDir else "$EXT/$saveDir"
                out += if (normalized.substringAfterLast('/').equals("saves", ignoreCase = true)) {
                    normalized.substringBeforeLast('/')
                } else {
                    normalized
                }
            }
            out += cfg.substringBeforeLast('/')
            break
        }
        return out
    }

    private fun readText(path: String, context: Context): String? {
        val direct = File(path)
        if (direct.isFile && direct.canRead()) {
            return runCatching { direct.readText() }.getOrNull()
        }
        if (ManagedStorageAccessor.canManage(path)) {
            val parsed = ManagedStorageAccessor.parseManagedPath(path)
            if (parsed != null) {
                val accessor = ManagedStorageAccessor(context)
                return runCatching {
                    accessor.openInput(parsed.first, parsed.second)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
        }
        if (RootShell.available) {
            return RootShell.run("cat ${RootShell.sq(path)}")
        }
        return null
    }

    private fun isAccessible(path: String, context: Context): Boolean {
        val direct = File(path)
        if (direct.isDirectory && direct.listFiles() != null) return true
        if (ManagedStorageAccessor.canManage(path)) {
            val parsed = ManagedStorageAccessor.parseManagedPath(path)
            if (parsed != null && ManagedStorageAccessor(context).isListable(parsed.first, parsed.second)) {
                return true
            }
        }
        return RootShell.available && RootShell.run("test -d ${RootShell.sq(path)}") != null
    }

    private fun hasContent(path: String, context: Context): Boolean {
        val direct = File(path)
        if (direct.isDirectory) {
            val children = direct.listFiles() ?: return false
            return children.isNotEmpty()
        }
        if (ManagedStorageAccessor.canManage(path)) {
            val parsed = ManagedStorageAccessor.parseManagedPath(path)
            if (parsed != null) {
                val entries = ManagedStorageAccessor(context).list(parsed.first, parsed.second)
                if (entries != null) return entries.isNotEmpty()
            }
        }
        if (RootShell.available) {
            val out = RootShell.run("ls -A ${RootShell.sq(path)} 2>/dev/null | head -1")
            return !out.isNullOrBlank()
        }
        return false
    }
}
