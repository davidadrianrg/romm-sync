package com.davidadrianrg.atril.core.media

import com.davidadrianrg.atril.core.remote.dto.RomDto
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class GameMediaTest {
    private fun dto(
        name: String = "Gex",
        fs: String = "Gex (USA).chd",
        slug: String? = "3do",
        shots: List<String> = emptyList(),
        video: String? = null,
        manual: String? = null,
        cover: String? = "/assets/romm/resources/roms/1/2/cover/big.png?ts=2026-01-01 10:00:00",
    ) = RomDto(
        id = 7, name = name, fileName = fs, fileNameNoExt = fs.substringBeforeLast('.'),
        platformId = 1, platformSlug = slug, pathCoverLarge = cover, summary = "A gecko.",
        mergedScreenshots = shots, pathVideo = video, pathManual = manual,
    )

    @Test fun defaultRootIsUnderRomsAndCustomWins() {
        assertEquals("/r/ROMs/downloaded_media", GameMediaPaths.resolveRoot("", "/r/ROMs/"))
        assertEquals("/r/ROMs/downloaded_media", GameMediaPaths.resolveRoot("  ", "/r/ROMs"))
        assertEquals("/x/media", GameMediaPaths.resolveRoot("/x/media/", "/r/ROMs"))
    }

    @Test fun namesCannotEscapeTheMediaRoot() {
        assertEquals("rom_7", GameMediaPaths.safeName("..", "rom_7"))
        assertEquals("etcpasswd", GameMediaPaths.safeName("../etc/passwd", "x").replace(".", ""))
        assertFalse(GameMediaPaths.safeName("../../evil", "x").contains("/"))
        assertEquals("unknown", GameMediaPaths.safeSlug("../.."))
        assertEquals("snes", GameMediaPaths.safeSlug("SNES"))
    }

    @Test fun planLaysOutPerPlatformFolders() {
        val root = "/m"
        val items = GameMediaPlanner.plan(
            dto(shots = listOf("/a/s1.jpg", "/a/s2.png", "https://cdn.x/s3.webp"), video = "/v/g.mp4", manual = "/m/g.pdf"),
            "https://romm.test/", root,
        )
        val paths = items.map { it.target.path }
        assertTrue(paths.contains("/m/3do/covers/Gex USA.png"))
        assertTrue(paths.contains("/m/3do/screenshots/Gex USA.jpg"))
        assertTrue(paths.contains("/m/3do/screenshots/Gex USA_2.png"))
        assertTrue(paths.contains("/m/3do/screenshots/Gex USA_3.webp"))
        assertTrue(paths.contains("/m/3do/videos/Gex USA.mp4"))
        assertTrue(paths.contains("/m/3do/manuals/Gex USA.pdf"))
        assertTrue(items.first().url.contains("%20")) // timestamp spaces encoded
    }

    @Test fun planWithoutMediaIsEmptyAndShotsAreCapped() {
        assertTrue(GameMediaPlanner.plan(dto(cover = null), "https://r", "/m").isEmpty())
        val many = (1..9).map { "/s/$it.jpg" }
        assertEquals(GameMediaPaths.MAX_SCREENSHOTS, GameMediaPlanner.plan(dto(shots = many, cover = null), "https://r", "/m").size)
    }

    @Test fun metadataJsonHasTitlePlatformDescriptionAndRelativeFiles() {
        val d = dto(shots = listOf("/a/s1.jpg"))
        val items = GameMediaPlanner.plan(d, "https://romm.test", "/m")
        val json = GameMediaPlanner.metadataJson(d, "https://romm.test", items, "/m")
        assertTrue(json, json.contains("\"title\": \"Gex\""))
        assertTrue(json, json.contains("\"platform\": \"3do\""))
        assertTrue(json, json.contains("\"description\": \"A gecko.\""))
        assertTrue(json, json.contains("covers/Gex USA.png"))
        assertFalse("must not leak the token or absolute local paths", json.contains("/m/3do"))
    }

    @Test fun downloaderIsBestEffortSkipsExistingAndOnlySendsTokenToRomm() {
        val server = MockWebServer()
        val seen = mutableListOf<RecordedRequest>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                seen += request
                return when {
                    request.path!!.contains("cover") -> MockResponse().setBody("COVERBYTES")
                    request.path!!.contains("manual") -> MockResponse().setResponseCode(404)
                    else -> MockResponse().setBody("SHOT")
                }
            }
        }
        server.start()
        val root = Files.createTempDirectory("media").toFile()
        try {
            val base = server.url("/").toString().trimEnd('/')
            val d = dto(shots = listOf("/s/shot1.jpg"), manual = "/m/manual.pdf", cover = "/c/cover.jpg")
            val dl = GameMediaDownloader(OkHttpClient(), "SECRET", base)
            val r1 = dl.run(d, root.path)
            assertEquals(2, r1.downloaded)
            assertEquals(1, r1.failed.size) // manual 404: reported, did not abort
            assertEquals("COVERBYTES", File(root, "3do/covers/Gex USA.jpg").readText())
            assertTrue(File(root, "3do/metadata/Gex USA.json").exists())
            assertTrue(seen.all { it.getHeader("Authorization") == "Bearer SECRET" })

            val before = seen.size
            val r2 = dl.run(d, root.path) // second run: existing files are skipped, only the failed one retried
            assertEquals(2, r2.alreadyThere)
            assertEquals(before + 1, seen.size)
            assertTrue("no .part leftovers", root.walkTopDown().none { it.name.endsWith(".part") })
        } finally {
            server.shutdown(); root.deleteRecursively()
        }
    }

    @Test fun externalHostsNeverReceiveTheToken() {
        val romm = MockWebServer().also { it.start() }
        val cdn = MockWebServer().also { it.start() }
        val root = Files.createTempDirectory("media2").toFile()
        try {
            cdn.enqueue(MockResponse().setBody("SHOT"))
            // Same host name, different port: still a different origin.
            val rommBase = romm.url("/").toString().trimEnd('/')
            val cdnUrl = cdn.url("/s.jpg").toString() // host 127.0.0.1
            val d = dto(shots = listOf(cdnUrl), cover = null)
            GameMediaDownloader(OkHttpClient(), "SECRET", rommBase).run(d, root.path)
            assertNull(cdn.takeRequest().getHeader("Authorization"))
        } finally {
            romm.shutdown(); cdn.shutdown(); root.deleteRecursively()
        }
    }
}
