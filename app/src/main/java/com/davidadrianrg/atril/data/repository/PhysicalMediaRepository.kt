package com.davidadrianrg.atril.data.repository

import com.davidadrianrg.atril.core.library.PhysicalMedia
import com.davidadrianrg.atril.core.remote.NetworkModule
import com.davidadrianrg.atril.data.local.dao.RomPresentationDao
import com.davidadrianrg.atril.data.local.entity.RomPresentationEntity
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

/** Cómo se muestra un juego en el launcher. */
enum class LauncherDisplayMode(val id: String) {
    CARD("card"),
    PHYSICAL("physical"),
    ;

    companion object {
        fun fromId(id: String?): LauncherDisplayMode? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Presentación por juego (carta / medio físico) y caché local de la imagen del
 * medio físico que sirve RomM. La imagen vive en `filesDir/physical-media` para
 * que funcione sin conexión y la use la animación de inserción.
 */
class PhysicalMediaRepository(
    private val dao: RomPresentationDao,
    private val cacheDir: File,
) {

    /** Ámbito propio: una descarga no se cancela al cerrar la hoja de detalle. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _downloading = MutableStateFlow<Set<Int>>(emptySet())
    /** romIds con descarga de medio físico en curso. */
    val downloading: StateFlow<Set<Int>> = _downloading.asStateFlow()

    fun observe(): Flow<Map<Int, RomPresentationEntity>> =
        dao.observeAll().map { rows -> rows.associateBy { it.romId } }

    /** Versión "dispara y olvida" de [setDisplayMode] para la UI. */
    fun updateDisplayMode(romIds: List<Int>, mode: LauncherDisplayMode?) {
        scope.launch { setDisplayMode(romIds, mode) }
    }

    /** Lanza la descarga en segundo plano; [onResult] llega en el hilo de IO. */
    fun enqueueDownload(romId: Int, url: String, apiKey: String, onResult: (Result<File>) -> Unit) {
        if (romId in _downloading.value) return
        _downloading.update { it + romId }
        scope.launch {
            val result = download(romId, url, apiKey)
            _downloading.update { it - romId }
            onResult(result)
        }
    }

    suspend fun setDisplayMode(romIds: List<Int>, mode: LauncherDisplayMode?) {
        romIds.forEach { id ->
            val current = dao.get(id) ?: RomPresentationEntity(romId = id)
            dao.upsert(current.copy(displayMode = mode?.id, updatedAt = System.currentTimeMillis()))
        }
    }

    /**
     * Descarga la imagen de [url] (RomM) y la guarda para [romId]. Escribe a un
     * `.part` y renombra, así una descarga cortada nunca deja una imagen rota.
     * Tras descargar, el juego pasa a mostrarse como medio físico.
     */
    suspend fun download(romId: Int, url: String, apiKey: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            cacheDir.mkdirs()
            val target = File(cacheDir, "$romId.${PhysicalMedia.cacheExtension(url)}")
            val part = File(cacheDir, "${target.name}.part")
            val client = NetworkModule.createOkHttpClient(apiKey)
            client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val body = response.body ?: throw IOException("empty body")
                val type = body.contentType()
                if (type != null && type.type != "image") throw IOException("not an image ($type)")
                if (body.contentLength() > MAX_BYTES) throw IOException("image too large")
                part.outputStream().use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(16 * 1024)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > MAX_BYTES) throw IOException("image too large")
                            out.write(buffer, 0, read)
                        }
                    }
                }
            }
            if (part.length() == 0L) throw IOException("empty image")
            target.delete()
            if (!part.renameTo(target)) throw IOException("rename failed")

            val current = dao.get(romId) ?: RomPresentationEntity(romId = romId)
            current.physicalMediaPath?.let { old -> if (old != target.absolutePath) File(old).delete() }
            dao.upsert(
                current.copy(
                    displayMode = LauncherDisplayMode.PHYSICAL.id,
                    physicalMediaPath = target.absolutePath,
                    physicalMediaSource = url,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            target
        }.onFailure {
            cacheDir.listFiles { f -> f.name.startsWith("$romId.") && f.name.endsWith(".part") }
                ?.forEach { it.delete() }
        }
    }

    /** Borra preferencia e imagen cacheada (al borrar el juego del dispositivo). */
    suspend fun remove(romIds: List<Int>) = withContext(Dispatchers.IO) {
        romIds.forEach { id ->
            dao.get(id)?.physicalMediaPath?.let { File(it).delete() }
            dao.delete(id)
        }
    }

    private companion object {
        const val MAX_BYTES = 20L * 1024 * 1024
    }
}
