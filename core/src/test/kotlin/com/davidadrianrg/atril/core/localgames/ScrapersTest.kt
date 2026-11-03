package com.davidadrianrg.atril.core.localgames

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ScrapersTest {

    private class Fake(private val routes: List<Pair<String, HttpReply>>) : HttpFetcher {
        val urls = mutableListOf<String>()
        val headers = mutableListOf<Map<String, String>>()
        override fun get(url: String, headers: Map<String, String>): HttpReply {
            urls += url; this.headers += headers
            return routes.firstOrNull { url.contains(it.first) }?.second ?: HttpReply(404, "")
        }
    }

    private fun ok(body: String) = HttpReply(200, body)

    // ── SteamGridDB ──

    @Test
    fun `steamgriddb finds a cover with the bearer key`() {
        val f = Fake(listOf(
            "search/autocomplete" to ok("""{"success":true,"data":[{"id":5247,"name":"Super Mario 64"}]}"""),
            "grids/game/5247" to ok("""{"data":[{"url":"https://cdn/x.png"},{"url":"https://cdn/y.png"}]}"""),
        ))
        val out = SteamGridDbScraper("KEY", f).scrape("Super Mario 64", "n64")
        assertEquals(ScrapeOutcome.Found(ScrapedGame("https://cdn/x.png", null, "steamgriddb")), out)
        assertEquals("Bearer KEY", f.headers.first()["Authorization"])
        assertTrue(f.urls.first().endsWith("/search/autocomplete/Super%20Mario%2064"))
        assertTrue(f.urls[1].contains("dimensions=600x900"))
    }

    @Test
    fun `steamgriddb without key is not configured and never calls the network`() {
        val f = Fake(emptyList())
        assertEquals(ScrapeOutcome.NotConfigured, SteamGridDbScraper(null, f).scrape("x", "n64"))
        assertEquals(ScrapeOutcome.NotConfigured, SteamGridDbScraper("  ", f).scrape("x", "n64"))
        assertTrue(f.urls.isEmpty())
    }

    @Test
    fun `steamgriddb maps errors`() {
        fun run(code: Int) = SteamGridDbScraper("K", Fake(listOf("search" to HttpReply(code, "")))).scrape("x", "n64")
        assertEquals(ScrapeOutcome.BadCredentials("steamgriddb"), run(401))
        assertEquals(ScrapeOutcome.BadCredentials("steamgriddb"), run(403))
        assertEquals(ScrapeOutcome.Retryable("steamgriddb", "rate limited"), run(429))
        assertTrue(run(500) is ScrapeOutcome.Retryable)
        val noMatch = SteamGridDbScraper("K", Fake(listOf("search" to ok("""{"data":[]}""")))).scrape("x", "n64")
        assertEquals(ScrapeOutcome.NotFound, noMatch)
        val noGrid = SteamGridDbScraper("K", Fake(listOf("search" to ok("""{"data":[{"id":1}]}"""), "grids" to ok("""{"data":[]}""")))).scrape("x", "n64")
        assertEquals(ScrapeOutcome.NotFound, noGrid)
    }

    @Test
    fun `network failure is retryable`() {
        val boom = HttpFetcher { _, _ -> throw IOException("timeout") }
        assertEquals(ScrapeOutcome.Retryable("steamgriddb", "timeout"), SteamGridDbScraper("K", boom).scrape("x", "n64"))
    }

    // ── ScreenScraper ──

    private val ssCreds = ScreenScraperScraper.Credentials("dev", "devpw", "user", "p&ss word")

    @Test
    fun `screenscraper parses cover and synopsis and encodes credentials`() {
        val body = """{"response":{"jeux":[{"id":"1234","synopsis":[{"langue":"fr","text":"Bonjour"},{"langue":"en","text":"Hello"}],
            "medias":[{"type":"ss","url":"https://s/ss.png"},{"type":"box-2D","region":"jp","url":"https://s/jp.png"},{"type":"box-2D","region":"eu","url":"https://s/eu.png"}]}]}}"""
        val f = Fake(listOf("jeuRecherche" to ok(body)))
        val out = ScreenScraperScraper(ssCreds, f).scrape("Gex", "3do")
        assertEquals(ScrapeOutcome.Found(ScrapedGame("https://s/eu.png", "Hello", "screenscraper")), out)
        val url = f.urls.single()
        assertTrue(url.contains("sspassword=p%26ss%20word"))
        assertTrue(url.contains("systemeid=29"))
        assertTrue(url.contains("recherche=Gex"))
    }

    @Test
    fun `screenscraper incomplete credentials are not configured`() {
        val f = Fake(emptyList())
        assertEquals(ScrapeOutcome.NotConfigured, ScreenScraperScraper(null, f).scrape("x", "n64"))
        assertEquals(
            ScrapeOutcome.NotConfigured,
            ScreenScraperScraper(ScreenScraperScraper.Credentials("d", "", "u", "p"), f).scrape("x", "n64"),
        )
        assertTrue(f.urls.isEmpty())
    }

    @Test
    fun `screenscraper not found and error mapping`() {
        fun run(r: HttpReply) = ScreenScraperScraper(ssCreds, Fake(listOf("jeuRecherche" to r))).scrape("x", "n64")
        assertEquals(ScrapeOutcome.NotFound, run(ok("""{"response":{"jeux":[{}]}}""")))
        assertEquals(ScrapeOutcome.NotFound, run(ok("not json")))
        assertEquals(ScrapeOutcome.BadCredentials("screenscraper"), run(HttpReply(403, "")))
        assertEquals(ScrapeOutcome.Retryable("screenscraper", "rate limited"), run(HttpReply(429, "")))
    }

    @Test
    fun `unknown platform omits the system id`() {
        assertNull(ScreenScraperScraper.systemId("zxspectrum"))
        val f = Fake(listOf("jeuRecherche" to ok("""{"response":{"jeux":[]}}""")))
        ScreenScraperScraper(ssCreds, f).scrape("x", "zxspectrum")
        assertTrue(!f.urls.single().contains("systemeid"))
    }

    // ── Composite ──

    private class Const(override val id: String, val out: ScrapeOutcome) : GameScraper {
        var calls = 0
        override fun scrape(title: String, platformSlug: String): ScrapeOutcome { calls++; return out }
    }

    @Test
    fun `composite returns the first found and stops`() {
        val found = ScrapeOutcome.Found(ScrapedGame("u", null, "b"))
        val a = Const("a", ScrapeOutcome.NotFound); val b = Const("b", found); val c = Const("c", found)
        assertEquals(found, CompositeScraper(listOf(a, b, c)).scrape("x", "n64"))
        assertEquals(0, c.calls)
    }

    @Test
    fun `composite reports the most informative failure`() {
        val retry = ScrapeOutcome.Retryable("b", "timeout")
        assertEquals(retry, CompositeScraper(listOf(Const("a", ScrapeOutcome.NotFound), Const("b", retry), Const("c", ScrapeOutcome.NotConfigured))).scrape("x", "n64"))
        assertEquals(ScrapeOutcome.NotFound, CompositeScraper(listOf(Const("a", ScrapeOutcome.NotConfigured), Const("b", ScrapeOutcome.NotFound))).scrape("x", "n64"))
        assertEquals(ScrapeOutcome.NotConfigured, CompositeScraper(emptyList()).scrape("x", "n64"))
    }

    // ── OkHttpFetcher (servidor simulado) ──

    @Test
    fun `okhttp fetcher sends headers and returns code and body`() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
            server.enqueue(MockResponse().setResponseCode(429))
            val f = OkHttpFetcher()
            val r1 = f.get(server.url("/x").toString(), mapOf("Authorization" to "Bearer K"))
            assertEquals(200, r1.code); assertEquals("""{"ok":true}""", r1.body)
            assertEquals("Bearer K", server.takeRequest().getHeader("Authorization"))
            assertEquals(429, f.get(server.url("/y").toString(), emptyMap()).code)
        }
    }
}
