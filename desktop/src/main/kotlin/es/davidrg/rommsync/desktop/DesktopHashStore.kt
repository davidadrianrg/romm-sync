package es.davidrg.rommsync.desktop

import java.io.File
import java.util.Properties

/**
 * Persiste el fingerprint de los saves de cada ROM tras un ciclo de sync
 * limpio (puerto del SyncedHashStore de Android). Si en el próximo ciclo el
 * fingerprint local coincide, se salta el zipeo+hash de ese ROM completo.
 *
 * Formato: properties en ~/.config/romm-sync/sync-hashes.properties
 *   fp.<romId> = fingerprint
 */
class DesktopHashStore(private val file: File) {

    private val props = Properties()

    init {
        if (file.isFile) runCatching { file.inputStream().use { props.load(it) } }
    }

    @Synchronized
    private fun persist() {
        runCatching {
            file.parentFile?.mkdirs()
            file.outputStream().use { props.store(it, "RomM Sync desktop sync hashes") }
        }
    }

    fun getFingerprint(romId: Int): String? = props.getProperty("fp.$romId")

    fun setFingerprint(romId: Int, fingerprint: String) {
        props["fp.$romId"] = fingerprint
        persist()
    }
}
