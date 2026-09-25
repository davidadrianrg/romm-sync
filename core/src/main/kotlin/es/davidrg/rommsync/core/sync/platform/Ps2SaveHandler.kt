package es.davidrg.rommsync.core.sync.platform

import es.davidrg.rommsync.core.util.RomHeaderIdReader
import es.davidrg.rommsync.core.util.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Handler de saves para PS2 (AetherSX2 / NetherSX2) en modo Folder Memory Card.
 *
 * Estructura esperada:
 * ```
 * {basePath}/memcards/{cardName}.ps2/{BAserial}/
 * ```
 *
 * El emulador debe estar configurado con "Folder Memory Card" activo.
 * Cada juego tiene su propia subcarpeta identificada por serial del disco
 * con prefijo "BA" (p.ej. `BASLUS-21050`).
 *
 * Para sync se zipea la carpeta del serial y se sube/baja como un solo asset.
 *
 * NOTA: Si hay multiples memory cards (.ps2 dirs), se busca en todas.
 * El serial se extrae del nombre del ROM.
 */
class Ps2SaveHandler : SaveHandler {

    override suspend fun findSaves(
        romId: Int,
        romFileName: String,
        platformSlug: String,
        savesBasePath: String,
        romLocalPath: String?,
    ): List<LocalSave> = withContext(Dispatchers.IO) {
        val results = mutableListOf<LocalSave>()
        val memcardsDir = File(savesBasePath)
        // Android 13+ bloquea Android/data/<pkg> a apps terceras aunque tengan
        // "todos los archivos": listFiles() devuelve null. Con root disponible
        // se accede vía su (tar determinista), igual que AndroidSaveHandler.
        if (!memcardsDir.isDirectory) {
            return@withContext if (RootShell.available) {
                findRootSaves(romId, romFileName, platformSlug, savesBasePath, romLocalPath)
            } else {
                results
            }
        }

        // Serial: primero desde el header del ISO, luego desde el nombre.
        val serial = romLocalPath?.let { RomHeaderIdReader.readGameId(File(it), platformSlug) }
            ?: extractSerialFromFileName(romFileName)
        if (serial == null) return@withContext results
        val baSerial = toBaFolderName(serial)

        // Buscar en todas las memory cards (.ps2 dirs)
        val cardDirs = memcardsDir.listFiles()?.filter {
            it.isDirectory && it.name.endsWith(".ps2", ignoreCase = true)
        } ?: return@withContext results

        for (cardDir in cardDirs) {
            val saveFolder = cardDir.listFiles()?.firstOrNull { dir ->
                dir.isDirectory && matchesFolderName(dir.name, serial)
            } ?: continue

            val mtime = folderLastModified(saveFolder)
            val zipFile = File.createTempFile("ps2_save_${baSerial}_", ".zip")
            zipFolderDeterministic(saveFolder, zipFile)

            results.add(
                LocalSave(
                    romId = romId,
                    fileName = "${baSerial}_save.zip",
                    file = zipFile,
                    lastModified = mtime,
                    sha1 = zipFile.sha1(),
                ),
            )
            break // Usar la primera memory card que tenga el save
        }

        results
    }

    override suspend fun prepareForUpload(save: LocalSave): File = save.file

    override suspend fun savesFingerprint(
        romId: Int, romFileName: String, platformSlug: String,
        savesBasePath: String, romLocalPath: String?,
    ): String? = withContext(Dispatchers.IO) {
        val serial = romLocalPath?.let { RomHeaderIdReader.readGameId(File(it), platformSlug) }
            ?: extractSerialFromFileName(romFileName) ?: return@withContext null
        val cardDirs = File(savesBasePath).listFiles()?.filter {
            it.isDirectory && it.name.endsWith(".ps2", ignoreCase = true)
        }
        if (cardDirs == null) {
            // Ruta inaccesible (Android 13+): fingerprint vía root si existe.
            if (!RootShell.available) return@withContext null
            for (cardDirPath in rootListCardDirs(savesBasePath)) {
                val saveFolderPath = rootFindSaveFolder(cardDirPath, serial) ?: continue
                return@withContext rootFolderFingerprint(saveFolderPath)
            }
            return@withContext null
        }
        cardDirs.forEach { card ->
            val saveFolder = card.listFiles()?.firstOrNull {
                it.isDirectory && matchesFolderName(it.name, serial)
            } ?: return@forEach
            return@withContext folderFingerprint(saveFolder)
        }
        null
    }

    override suspend fun extractDownload(
        tempFile: File,
        romFileName: String,
        platformSlug: String,
        savesBasePath: String,
        targetFileName: String,
    ): Boolean = withContext(Dispatchers.IO) {
        // Tar creado por findRootSaves (ruta inaccesible + root): extraer con su
        if (targetFileName.endsWith(".tar")) {
            return@withContext extractRootTar(tempFile, savesBasePath)
        }

        val memcardsDir = File(savesBasePath)
        // Si la ruta no es accesible con la API File (Android 13+ bloquea
        // Android/data/<pkg>), ni mkdirs ni la extracción zip funcionarían —
        // con root se extrae el zip a un temp accesible y se copia con su.
        if (!memcardsDir.isDirectory && !memcardsDir.mkdirs()) {
            return@withContext if (RootShell.available) {
                extractZipViaRoot(tempFile, romFileName, savesBasePath)
            } else {
                false
            }
        }

        val serial = extractSerialFromFileName(romFileName) ?: return@withContext false
        val baSerial = toBaFolderName(serial)

        // Encontrar o crear una memory card
        val cardDirs = memcardsDir.listFiles()?.filter {
            it.isDirectory && it.name.endsWith(".ps2", ignoreCase = true)
        } ?: emptyList()

        val targetCard = if (cardDirs.isEmpty()) {
            File(memcardsDir, "Shared.ps2").also { it.mkdirs() }
        } else {
            cardDirs[0]
        }

        // Borrar carpeta existente del serial
        targetCard.listFiles()?.filter { dir ->
            dir.isDirectory && matchesFolderName(dir.name, serial)
        }?.forEach { it.deleteRecursively() }

        // Extraer zip dentro de la memory card
        try {
            ZipInputStream(FileInputStream(tempFile)).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    val outFile = File(targetCard, entry.name)
                    if (!outFile.canonicalPath.startsWith(targetCard.canonicalPath)) {
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                        continue
                    }
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { out -> zipIn.copyTo(out) }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Intenta extraer un serial PS2 del nombre del ROM.
     * Formatos típicos: "SLUS-21050 - Game.iso", "[SLUS_21050] Game.iso"
     */
    private fun extractSerialFromFileName(fileName: String): String? {
        val patterns = listOf(
            Regex("""([A-Z]{4}[-_]\d{5})"""),
            Regex("""\[?([A-Z]{4}\d{5})\]?"""),
        )
        for (pattern in patterns) {
            val match = pattern.find(fileName)
            if (match != null) {
                return match.groupValues[1].replace("_", "-")
            }
        }
        return null
    }

    /**
     * Convierte un serial PS2 (SLUS-21050) al formato carpeta BA (BASLUS-21050).
     */
    private fun toBaFolderName(serial: String): String {
        val cleaned = serial.replace("-", "").replace("_", "")
        val match = Regex("^([A-Za-z]{4})(\\d+)$").find(cleaned)
        return if (match != null) {
            "BA${match.groupValues[1].uppercase()}-${match.groupValues[2]}"
        } else {
            "BA$cleaned"
        }
    }

    private fun matchesFolderName(folderName: String, serial: String): Boolean {
        val stripped = serial.replace("-", "")
        val baSerial = if (stripped.startsWith("BA", ignoreCase = true)) stripped else "BA$stripped"
        val folderStripped = folderName.replace("-", "")
        return folderStripped.startsWith(baSerial, ignoreCase = true)
    }

    private fun folderLastModified(dir: File): Long {
        var newest = dir.lastModified()
        dir.walkTopDown().forEach { file ->
            if (file.lastModified() > newest) newest = file.lastModified()
        }
        return newest
    }

    // ─────────────────────────────────────────────────────────────────────
    // Acceso root: Android 13+ impide leer Android/data/<pkg> con la API File
    // incluso con MANAGE_EXTERNAL_STORAGE. Con su se lista el árbol con find,
    // se empaqueta con tar y se restaura igual (mismo patrón que
    // AndroidSaveHandler para /data/data/<pkg>).
    // ─────────────────────────────────────────────────────────────────────

    /** Lista las carpetas de memory card (.ps2) de una ruta inaccesible. */
    private fun rootListCardDirs(savesBasePath: String): List<String> {
        val listing = RootShell.run(
            "find ${RootShell.sq(savesBasePath)} -maxdepth 1 -type d -name '*.ps2' | sort",
        ) ?: return emptyList()
        return listing.lineSequence().filter { it.isNotBlank() }.toList()
    }

    /** Busca la carpeta de save del serial dentro de un card dir (vía root). */
    private fun rootFindSaveFolder(cardDirPath: String, serial: String): String? {
        val listing = RootShell.run(
            "find ${RootShell.sq(cardDirPath)} -maxdepth 1 -type d | sort",
        ) ?: return null
        return listing.lineSequence()
            .map { it.trim() }
            .firstOrNull { matchesFolderName(File(it).name, serial) }
    }

    /**
     * findSaves por root: empaqueta la carpeta BASLUS-xxxxx del serial como
     * tar determinista (uid/gid/mtime estables bajo su).
     */
    private fun findRootSaves(
        romId: Int,
        romFileName: String,
        platformSlug: String,
        savesBasePath: String,
        romLocalPath: String?,
    ): List<LocalSave> {
        val serial = romLocalPath?.let { RomHeaderIdReader.readGameId(File(it), platformSlug) }
            ?: extractSerialFromFileName(romFileName)
        if (serial == null) return emptyList()
        val baSerial = toBaFolderName(serial)

        for (cardDirPath in rootListCardDirs(savesBasePath)) {
            val saveFolderPath = rootFindSaveFolder(cardDirPath, serial) ?: continue

            // mtime más reciente dentro de la carpeta (est -c %Y)
            val newestMtime = RootShell.run(
                "find ${RootShell.sq(saveFolderPath)} -type f -exec stat -c %Y {} + | sort -n | tail -1",
            )?.trim()?.toLongOrNull() ?: System.currentTimeMillis()

            // tar de la carpeta del serial dentro de su card dir
            val cardDir = File(cardDirPath)
            val parent = cardDir.parentFile ?: continue
            val tarFile = File.createTempFile("ps2_root_${baSerial}_", ".tar")
            if (!tarFile.delete()) continue
            val ok = RootShell.runToFile(
                "tar -cf ${RootShell.sq(tarFile.absolutePath)} " +
                    "-C ${RootShell.sq(parent.absolutePath)} " +
                    "'${cardDir.name}/${File(saveFolderPath).name}'",
            )
            if (!ok || !tarFile.isFile || tarFile.length() == 0L) {
                tarFile.delete()
                continue
            }

            return listOf(
                LocalSave(
                    romId = romId,
                    fileName = "${baSerial}_save.tar",
                    file = tarFile,
                    lastModified = newestMtime,
                    sha1 = tarFile.sha1(),
                ),
            )
        }
        return emptyList()
    }

    /** Fingerprint de carpeta inaccesible vía root: stat de cada fichero. */
    private fun rootFolderFingerprint(saveFolderPath: String): String? {
        val listing = RootShell.run(
            "find ${RootShell.sq(saveFolderPath)} -type f -exec stat -c '%n %s %Y' {} + | sort",
        ) ?: return null
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update(listing.toByteArray())
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Extrae un tar (creado por findRootSaves) en la memory card con root. */
    private fun extractRootTar(tempFile: File, savesBasePath: String): Boolean {
        // Asegurar que exista la estructura de carpetas del card dir
        RootShell.runToFile("mkdir -p ${RootShell.sq(savesBasePath)}")
        // Extraer el contenido del tar directamente en la raíz de memcards
        return RootShell.runToFile(
            "tar -xf ${RootShell.sq(tempFile.absolutePath)} -C ${RootShell.sq(savesBasePath)}",
        )
    }

    /**
     * Restauración de un zip (creado sin root) hacia una memory card
     * inaccesible: extrae a una carpeta temporal accesible y la mueve dentro
     * del card dir con su. La estructura original (cardName.ps2/BASLUS-xxxx)
     * se reconstruye bajo savesBasePath.
     */
    private fun extractZipViaRoot(tempFile: File, romFileName: String, savesBasePath: String): Boolean {
        val serial = extractSerialFromFileName(romFileName) ?: return false
        val baSerial = toBaFolderName(serial)

        // 1. Extraer el zip a un temp accesible
        val staging = File.createTempFile("ps2_stage_${baSerial}_", ".dir")
        if (!staging.delete()) return false
        if (!staging.mkdirs()) return false
        try {
            ZipInputStream(FileInputStream(tempFile)).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    val outFile = File(staging, entry.name)
                    if (!outFile.canonicalPath.startsWith(staging.canonicalPath)) {
                        zipIn.closeEntry(); entry = zipIn.nextEntry; continue
                    }
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { out -> zipIn.copyTo(out) }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
        } catch (_: Exception) {
            staging.deleteRecursively()
            return false
        }

        // 2. Copiar la carpeta del serial dentro del primer card dir existente
        //    (o crear Shared.ps2) bajo savesBasePath, todo con su.
        val existingCards = rootListCardDirs(savesBasePath)
        val cardName = existingCards.firstOrNull()?.let { File(it).name } ?: "Shared.ps2"
        val targetCardPath = "${savesBasePath.trimEnd('/')}/$cardName"
        val ok = RootShell.runToFile(
            "mkdir -p ${RootShell.sq(targetCardPath)} && " +
                "cp -a ${RootShell.sq(staging.absolutePath)}/. ${RootShell.sq(targetCardPath)}/",
        )
        staging.deleteRecursively()
        return ok
    }


    companion object {
        const val DEFAULT_SAVES_PATH = "/storage/emulated/0/Android/data/xyz.aethersx2.android/files/memcards"

        /**
         * ARMSX2 (fork nativo ARM64 de PCSX2). Mismo layout de folder memory
         * cards que AetherSX2 (directorios ".ps2" bajo files/memcards),
         * distinto package: `com.armsx2` (configurable en build; este es el
         * stable). Ojo: el asistente de primera ejecución propone una carpeta
         * custom fuera de Android/data — si el usuario la usó, la ruta base
         * debe apuntar ahí (configurable por plataforma).
         */
        const val ARMSX2_SAVES_PATH = "/storage/emulated/0/Android/data/com.armsx2/files/memcards"
    }
}
