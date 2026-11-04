package com.davidadrianrg.atril.data.localgames

import com.davidadrianrg.atril.core.localgames.LocalGame
import com.davidadrianrg.atril.core.localgames.LocalGameStore
import com.davidadrianrg.atril.core.localgames.ScrapeOutcome
import com.davidadrianrg.atril.core.localgames.ScrapeStatus
import com.davidadrianrg.atril.core.localgames.ScraperCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream

/**
 * Juegos locales (fuera de RomM): lista persistida, carátulas en disco y
 * scrapeo bajo demanda. Es una fuente SEPARADA: nada de esto se sube ni se
 * mezcla con los datos de RomM.
 */
class LocalGamesRepository(
    private val store: LocalGameStore,
    private val coversDir: File,
    private val credentials: suspend () -> ScraperCredentials,
    private val http: OkHttpClient = OkHttpClient(),
) {
    private val _games = MutableStateFlow(store.all())
    val games: StateFlow<List<LocalGame>> = _games

    private fun refresh() { _games.value = store.all() }

    suspend fun add(title: String, platformSlug: String, path: String): LocalGame = withContext(Dispatchers.IO) {
        store.add(title, platformSlug, path).also { refresh() }
    }

    suspend fun rename(id: String, title: String) = withContext(Dispatchers.IO) {
        if (title.isNotBlank()) store.update(id) { it.copy(title = title.trim()) }
        refresh()
    }

    suspend fun remove(id: String) = withContext(Dispatchers.IO) {
        store.get(id)?.coverPath?.let { File(it).delete() }
        store.remove(id)
        refresh()
    }

    /** Carátula elegida a mano por el usuario (copia el contenido a disco). */
    suspend fun setManualCover(id: String, input: InputStream): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val dest = coverFile(id)
            input.use { src -> dest.outputStream().use { src.copyTo(it) } }
            store.update(id) { it.copy(coverPath = dest.path, scrapeSource = "manual", scrapeStatus = ScrapeStatus.DONE) }
            refresh()
        }.isSuccess
    }

    /** Busca carátula/metadatos con los proveedores configurados y los guarda. */
    suspend fun scrape(id: String): ScrapeOutcome = withContext(Dispatchers.IO) {
        val game = store.get(id) ?: return@withContext ScrapeOutcome.NotFound
        val creds = credentials()
        if (!creds.hasAny) return@withContext ScrapeOutcome.NotConfigured
        val outcome = creds.buildScraper().scrape(game.title, game.platformSlug)
        when (outcome) {
            is ScrapeOutcome.Found -> {
                val cover = outcome.game.coverUrl?.let { downloadCover(id, it) }
                if (outcome.game.coverUrl != null && cover == null && outcome.game.summary == null) {
                    mark(id, ScrapeStatus.FAILED)
                } else {
                    store.update(id) {
                        it.copy(
                            coverPath = cover ?: it.coverPath,
                            summary = outcome.game.summary ?: it.summary,
                            scrapeSource = outcome.game.source,
                            scrapeStatus = ScrapeStatus.DONE,
                        )
                    }
                    refresh()
                }
            }
            ScrapeOutcome.NotFound -> mark(id, ScrapeStatus.NOT_FOUND)
            is ScrapeOutcome.Retryable, is ScrapeOutcome.BadCredentials -> mark(id, ScrapeStatus.FAILED)
            ScrapeOutcome.NotConfigured -> Unit
        }
        outcome
    }

    private fun mark(id: String, status: ScrapeStatus) {
        store.update(id) { it.copy(scrapeStatus = status) }
        refresh()
    }

    private fun coverFile(id: String): File = File(coversDir, "$id.img").also { it.parentFile?.mkdirs() }

    private fun downloadCover(id: String, url: String): String? = runCatching {
        http.newCall(Request.Builder().url(url).header("User-Agent", "Atril/1.0").build()).execute().use { r ->
            if (!r.isSuccessful) return null
            val dest = coverFile(id)
            r.body?.byteStream()?.use { src -> dest.outputStream().use { src.copyTo(it) } } ?: return null
            dest.path
        }
    }.getOrNull()
}
