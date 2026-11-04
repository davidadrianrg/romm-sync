package com.davidadrianrg.atril.core.localgames

import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder

/** Respuesta HTTP mínima, para poder simular proveedores en los tests. */
data class HttpReply(val code: Int, val body: String)

fun interface HttpFetcher {
    @Throws(IOException::class)
    fun get(url: String, headers: Map<String, String>): HttpReply
}

class OkHttpFetcher(private val client: OkHttpClient = OkHttpClient()) : HttpFetcher {
    override fun get(url: String, headers: Map<String, String>): HttpReply {
        val req = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }
            .header("User-Agent", "Atril/1.0").build()
        client.newCall(req).execute().use { return HttpReply(it.code, it.body?.string().orEmpty()) }
    }
}

/** Lo que aporta un proveedor para un juego. */
data class ScrapedGame(val coverUrl: String?, val summary: String?, val source: String)

sealed interface ScrapeOutcome {
    data class Found(val game: ScrapedGame) : ScrapeOutcome

    /** El proveedor respondió bien pero no conoce el juego. */
    data object NotFound : ScrapeOutcome

    /** Faltan o son inválidas las credenciales (401/403): no tiene sentido reintentar. */
    data class BadCredentials(val provider: String) : ScrapeOutcome

    /** Límite de uso (429) o red caída: se puede reintentar más tarde. */
    data class Retryable(val provider: String, val reason: String) : ScrapeOutcome

    /** El usuario no configuró este proveedor. */
    data object NotConfigured : ScrapeOutcome
}

interface GameScraper {
    val id: String
    fun scrape(title: String, platformSlug: String): ScrapeOutcome
}

private val moshiAny = Moshi.Builder().build().adapter(Any::class.java)

@Suppress("UNCHECKED_CAST")
private fun parse(json: String): Map<String, Any?>? =
    runCatching { moshiAny.fromJson(json) as? Map<String, Any?> }.getOrNull()

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

/** Clasifica el código HTTP común a ambos proveedores; null = seguir con el cuerpo. */
private fun classify(provider: String, code: Int): ScrapeOutcome? = when {
    code in 200..299 -> null
    code == 401 || code == 403 -> ScrapeOutcome.BadCredentials(provider)
    code == 404 -> ScrapeOutcome.NotFound
    code == 429 -> ScrapeOutcome.Retryable(provider, "rate limited")
    else -> ScrapeOutcome.Retryable(provider, "HTTP $code")
}

/**
 * SteamGridDB: carátula vertical. Requiere la API key del usuario
 * (https://www.steamgriddb.com/profile/preferences/api). Nunca hardcodeada.
 */
class SteamGridDbScraper(
    private val apiKey: String?,
    private val fetcher: HttpFetcher,
    private val baseUrl: String = "https://www.steamgriddb.com/api/v2",
) : GameScraper {
    override val id = "steamgriddb"

    override fun scrape(title: String, platformSlug: String): ScrapeOutcome {
        val key = apiKey?.trim().orEmpty().ifEmpty { return ScrapeOutcome.NotConfigured }
        val auth = mapOf("Authorization" to "Bearer $key")
        return try {
            val search = fetcher.get("$baseUrl/search/autocomplete/${enc(title)}", auth)
            classify(id, search.code)?.let { return it }
            val gameId = (parse(search.body)?.get("data") as? List<*>)
                ?.firstNotNullOfOrNull { (it as? Map<*, *>)?.get("id") as? Number }?.toLong()
                ?: return ScrapeOutcome.NotFound

            val grids = fetcher.get("$baseUrl/grids/game/$gameId?dimensions=600x900&types=static", auth)
            classify(id, grids.code)?.let { return it }
            val url = (parse(grids.body)?.get("data") as? List<*>)
                ?.firstNotNullOfOrNull { (it as? Map<*, *>)?.get("url") as? String }
                ?: return ScrapeOutcome.NotFound
            ScrapeOutcome.Found(ScrapedGame(coverUrl = url, summary = null, source = id))
        } catch (e: IOException) {
            ScrapeOutcome.Retryable(id, e.message ?: "network")
        }
    }
}

/**
 * ScreenScraper: carátula (box-2D) y sinopsis. ScreenScraper exige credenciales
 * de desarrollador (devid/devpassword) además de las del usuario; todas las
 * configura el usuario en Ajustes y no se incluye ninguna en el código.
 */
class ScreenScraperScraper(
    private val creds: Credentials?,
    private val fetcher: HttpFetcher,
    private val language: String = "en",
    private val baseUrl: String = "https://api.screenscraper.fr/api2",
) : GameScraper {
    override val id = "screenscraper"

    data class Credentials(val devId: String, val devPassword: String, val userId: String, val userPassword: String) {
        val isComplete get() = listOf(devId, devPassword, userId, userPassword).all { it.isNotBlank() }
    }

    override fun scrape(title: String, platformSlug: String): ScrapeOutcome {
        val c = creds?.takeIf { it.isComplete } ?: return ScrapeOutcome.NotConfigured
        val url = buildString {
            append(baseUrl).append("/jeuRecherche.php?output=json&softname=atril")
            append("&devid=").append(enc(c.devId)).append("&devpassword=").append(enc(c.devPassword))
            append("&ssid=").append(enc(c.userId)).append("&sspassword=").append(enc(c.userPassword))
            systemId(platformSlug)?.let { append("&systemeid=").append(it) }
            append("&recherche=").append(enc(title))
        }
        return try {
            val reply = fetcher.get(url, emptyMap())
            classify(id, reply.code)?.let { return it }
            val game = ((parse(reply.body)?.get("response") as? Map<*, *>)?.get("jeux") as? List<*>)
                ?.firstNotNullOfOrNull { it as? Map<*, *> }
                ?.takeIf { it.isNotEmpty() && it["id"] != null }
                ?: return ScrapeOutcome.NotFound
            val cover = (game["medias"] as? List<*>)?.filterIsInstance<Map<*, *>>()
                ?.filter { it["type"] == "box-2D" }
                ?.let { boxes -> boxes.firstOrNull { it["region"] in preferredRegions } ?: boxes.firstOrNull() }
                ?.get("url") as? String
            val summary = (game["synopsis"] as? List<*>)?.filterIsInstance<Map<*, *>>()
                ?.let { s -> s.firstOrNull { it["langue"] == language } ?: s.firstOrNull() }
                ?.get("text") as? String
            if (cover == null && summary == null) ScrapeOutcome.NotFound
            else ScrapeOutcome.Found(ScrapedGame(cover, summary, id))
        } catch (e: IOException) {
            ScrapeOutcome.Retryable(id, e.message ?: "network")
        }
    }

    private val preferredRegions = listOf("wor", "eu", "us")

    companion object {
        /** IDs de sistema de ScreenScraper para las plataformas habituales. */
        private val systems = mapOf(
            "genesis" to 1, "megadrive" to 1, "sms" to 2, "nes" to 3, "snes" to 4, "gb" to 9, "gbc" to 10,
            "gba" to 12, "gc" to 13, "ngc" to 13, "n64" to 14, "nds" to 15, "wii" to 16, "3ds" to 17,
            "saturn" to 22, "dreamcast" to 23, "3do" to 29, "psx" to 57, "ps2" to 58, "psp" to 61,
        )

        fun systemId(platformSlug: String): Int? = systems[platformSlug.lowercase()]
    }
}

/**
 * Prueba los proveedores en orden y se queda con el primero que encuentra el
 * juego. Si ninguno lo encuentra, devuelve el resultado más informativo:
 * reintentable > credenciales > no encontrado > sin configurar.
 */
class CompositeScraper(private val scrapers: List<GameScraper>) {
    fun scrape(title: String, platformSlug: String): ScrapeOutcome {
        var best: ScrapeOutcome = ScrapeOutcome.NotConfigured
        fun rank(o: ScrapeOutcome) = when (o) {
            is ScrapeOutcome.Found -> 5
            is ScrapeOutcome.Retryable -> 4
            is ScrapeOutcome.BadCredentials -> 3
            ScrapeOutcome.NotFound -> 2
            ScrapeOutcome.NotConfigured -> 1
        }
        for (s in scrapers) {
            val out = s.scrape(title, platformSlug)
            if (out is ScrapeOutcome.Found) return out
            if (rank(out) > rank(best)) best = out
        }
        return best
    }
}

/** Credenciales que el usuario configura en Ajustes (todas vacías por defecto). */
data class ScraperCredentials(
    val steamGridDbKey: String = "",
    val ssDevId: String = "",
    val ssDevPassword: String = "",
    val ssUser: String = "",
    val ssPassword: String = "",
) {
    val hasAny: Boolean get() = steamGridDbKey.isNotBlank() || screenScraper() != null

    fun screenScraper(): ScreenScraperScraper.Credentials? =
        ScreenScraperScraper.Credentials(ssDevId, ssDevPassword, ssUser, ssPassword).takeIf { it.isComplete }

    /** Proveedores en orden de preferencia: SteamGridDB (carátula) y luego ScreenScraper. */
    fun buildScraper(fetcher: HttpFetcher = OkHttpFetcher(), language: String = "en") = CompositeScraper(
        listOf(
            SteamGridDbScraper(steamGridDbKey.ifBlank { null }, fetcher),
            ScreenScraperScraper(screenScraper(), fetcher, language),
        ),
    )
}
