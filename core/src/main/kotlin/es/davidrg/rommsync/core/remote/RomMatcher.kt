package es.davidrg.rommsync.core.remote

import es.davidrg.rommsync.core.remote.dto.SearchRomDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/**
 * Búsqueda de metadatos ("buscar coincidencia").
 *
 * RomM SIEMPRE filtra la búsqueda por nombre por la plataforma del rom_id
 * (IGDB recibe el platform_igdb_id), así que para buscar sin filtro se consulta
 * cada plataforma con mapping IGDB usando un ROM cualquiera suyo y se
 * deduplican los resultados por igdb_id.
 */
class RomMatcher(private val api: RomMApiService) {

    suspend fun search(romId: Int, term: String, byId: Boolean): List<SearchRomDto> =
        api.searchRomMetadata(romId, term, if (byId) "id" else "name")

    suspend fun searchAllPlatforms(
        term: String,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): List<SearchRomDto> = coroutineScope {
        val platforms = runCatching { api.getPlatforms() }.getOrDefault(emptyList())
            .filter { it.igdbId != null && it.romCount > 0 }
        val total = platforms.size
        val results = Collections.synchronizedList(mutableListOf<SearchRomDto>())
        val semaphore = Semaphore(4)
        val done = AtomicInteger(0)

        platforms.map { platform ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val sample = runCatching {
                        api.getRoms(
                            mapOf("platform_ids" to platform.id.toString(), "limit" to "1"),
                        ).items.firstOrNull()
                    }.getOrNull()
                    if (sample != null) {
                        runCatching { api.searchRomMetadata(sample.id, term, "name") }
                            .getOrDefault(emptyList())
                            .let { results.addAll(it) }
                    }
                    onProgress(done.incrementAndGet(), total)
                }
            }
        }.awaitAll()

        results.distinctBy { it.igdbId ?: it.name }
    }
}
