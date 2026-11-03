package com.davidadrianrg.atril.core.localgames

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.util.UUID

/**
 * Persistencia de los juegos locales en un único fichero JSON. Escritura
 * atómica (temporal + rename) para que un cierre a mitad no corrompa la lista,
 * y un fichero ilegible se conserva como `.corrupt` en lugar de perderse.
 */
class LocalGameStore(
    private val file: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val adapter = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        .adapter<List<LocalGame>>(Types.newParameterizedType(List::class.java, LocalGame::class.java))

    private var cache: List<LocalGame>? = null

    @Synchronized
    fun all(): List<LocalGame> = load().sortedBy { it.title.lowercase() }

    @Synchronized
    fun get(id: String): LocalGame? = load().firstOrNull { it.id == id }

    @Synchronized
    fun add(title: String, platformSlug: String, path: String): LocalGame {
        val game = LocalGame(
            id = UUID.randomUUID().toString(),
            title = title.trim().ifBlank { LocalGameNames.titleFromFileName(path) },
            platformSlug = platformSlug,
            path = path,
            addedAt = clock(),
        )
        save(load() + game)
        return game
    }

    /** Aplica [change] al juego [id]; devuelve el resultado o null si no existe. */
    @Synchronized
    fun update(id: String, change: (LocalGame) -> LocalGame): LocalGame? {
        val list = load()
        val current = list.firstOrNull { it.id == id } ?: return null
        val updated = change(current).copy(id = current.id)
        save(list.map { if (it.id == id) updated else it })
        return updated
    }

    @Synchronized
    fun remove(id: String): Boolean {
        val list = load()
        if (list.none { it.id == id }) return false
        save(list.filterNot { it.id == id })
        return true
    }

    private fun load(): List<LocalGame> {
        cache?.let { return it }
        val loaded = if (file.isFile) {
            runCatching { adapter.fromJson(file.readText()).orEmpty() }.getOrElse {
                file.copyTo(File(file.path + ".corrupt"), overwrite = true)
                emptyList()
            }
        } else {
            emptyList()
        }
        cache = loaded
        return loaded
    }

    private fun save(list: List<LocalGame>) {
        file.absoluteFile.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(adapter.toJson(list))
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
        cache = list
    }
}
