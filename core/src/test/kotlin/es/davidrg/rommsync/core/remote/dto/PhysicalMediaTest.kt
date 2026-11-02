package es.davidrg.rommsync.core.remote.dto

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import es.davidrg.rommsync.core.library.PhysicalMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhysicalMediaTest {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(RomDto::class.java)

    private fun parse(extra: String) = adapter.fromJson(
        """{"id":2914,"name":"Mario","fs_name":"Mario.z64","platform_id":64 $extra}""",
    )!!

    @Test
    fun `parses ss physical metadata from the list endpoint`() {
        val dto = parse(
            """,
            "ss_metadata": {
              "physical_path": "roms/64/2914/physical/physical.png",
              "physical_url": "https://neoclone.screenscraper.fr/x",
              "physical_disc": null,
              "physical_extra_discs": []
            },
            "gamelist_metadata": {"physical_path": null}""",
        )
        assertEquals("roms/64/2914/physical/physical.png", dto.physicalMediaPath)
        assertEquals("https://neoclone.screenscraper.fr/x", dto.ssMetadata?.physicalUrl)
    }

    @Test
    fun `falls back to gamelist and tolerates unknown disc shapes`() {
        val dto = parse(
            """,
            "ss_metadata": {"physical_path": "", "physical_disc": {"path": "d.png"}, "physical_extra_discs": [{"a":1}]},
            "gamelist_metadata": {"physical_path": "roms/1/2/physical/box.jpg"}""",
        )
        assertEquals("roms/1/2/physical/box.jpg", dto.physicalMediaPath)
    }

    @Test
    fun `no physical metadata means no media`() {
        assertNull(parse("").physicalMediaPath)
        assertNull(parse(""","ss_metadata": null""").physicalMediaPath)
    }

    @Test
    fun `builds the RomM resources url`() {
        assertEquals(
            "https://romm.example/assets/romm/resources/roms/64/2914/physical/physical.png",
            PhysicalMedia.resourceUrl("https://romm.example/", "/roms/64/2914/physical/physical.png"),
        )
        assertEquals(
            "https://romm.example/assets/romm/resources/roms/1/p.png",
            PhysicalMedia.resourceUrl("https://romm.example", "assets/romm/resources/roms/1/p.png"),
        )
        assertNull(PhysicalMedia.resourceUrl("", "roms/1/p.png"))
        assertNull(PhysicalMedia.resourceUrl("https://romm.example", "  "))
    }

    @Test
    fun `cache extension falls back to png`() {
        assertEquals("jpg", PhysicalMedia.cacheExtension("https://x/a/box.JPG?v=2"))
        assertEquals("png", PhysicalMedia.cacheExtension("https://x/a/box"))
    }
}
