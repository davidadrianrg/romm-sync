package es.davidrg.rommsync.core.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Casos reales de la biblioteca de David (RomM 5.4) y regresiones conocidas.
 */
class MultiDiscGrouperTest {

    private val mgsDisc1 = DiscEntry(
        id = 14189,
        name = "Metal Gear Solid: The Twin Snakes",
        platformSlug = "gc",
        igdbId = 4006,
        fileNames = listOf("Metal Gear Solid - The Twin Snakes (Europe) (En,Fr,De,Es,It) (Disc 1).rvz"),
    )
    private val mgsDisc2 = DiscEntry(
        id = 14190,
        name = "Metal Gear Solid: The Twin Snakes",
        platformSlug = "gc",
        igdbId = 4006,
        fileNames = listOf("Metal Gear Solid - The Twin Snakes (Europe) (En,Fr,De,Es,It) (Disc 2).rvz"),
    )
    private val mgsComplete = DiscEntry(
        id = 14351,
        name = "Metal Gear Solid: The Twin Snakes",
        platformSlug = "gc",
        igdbId = 4006,
        fileNames = listOf(
            "Metal Gear Solid - The Twin Snakes (Europe) (En,Fr,De,Es,It) (Disc 1).rvz",
            "Metal Gear Solid - The Twin Snakes (Europe) (En,Fr,De,Es,It) (Disc 2).rvz",
        ),
    )

    @Test
    fun `three MGS entries collapse into one card and only the superset downloads`() {
        val groups = MultiDiscGrouper.group(listOf(mgsDisc1, mgsDisc2, mgsComplete))
        assertEquals(1, groups.size)
        val g = groups.single()
        assertEquals(14351, g.representativeId)
        assertEquals(listOf(14351), g.downloadIds)
        assertEquals(setOf(14189, 14190, 14351), g.allIds.toSet())
        assertEquals(2, g.discCount)
    }

    @Test
    fun `split discs without a combined entry group together and both download`() {
        val groups = MultiDiscGrouper.group(listOf(mgsDisc1, mgsDisc2))
        val g = groups.single()
        assertEquals(listOf(14189, 14190).toSet(), g.downloadIds.toSet())
        assertEquals(2, g.discCount)
    }

    @Test
    fun `golden sun 1 and 2 share igdb id but stay separate`() {
        val gs1 = DiscEntry(1, "Golden Sun", "gba", 1000, listOf("Golden Sun (USA).gba"))
        val gs2 = DiscEntry(2, "Golden Sun: The Lost Age", "gba", 1000, listOf("Golden Sun - The Lost Age (USA).gba"))
        val groups = MultiDiscGrouper.group(listOf(gs1, gs2))
        assertEquals(2, groups.size)
        assertTrue(groups.all { it.discCount == 1 })
    }

    @Test
    fun `region variants of the same game without disc markers stay separate`() {
        val usa = DiscEntry(1, "Pokemon Emerald", "gba", 500, listOf("Pokemon Emerald (USA).gba"))
        val eur = DiscEntry(2, "Pokemon Emerald", "gba", 500, listOf("Pokemon Emerald (Europe).gba"))
        assertEquals(2, MultiDiscGrouper.group(listOf(usa, eur)).size)
    }

    @Test
    fun `exact duplicate entries collapse to a single download`() {
        val a = DiscEntry(1, "Chrono Trigger", "snes", 900, listOf("Chrono Trigger (USA).sfc"))
        val b = DiscEntry(2, "Chrono Trigger", "snes", 900, listOf("Chrono Trigger (USA).sfc"))
        val g = MultiDiscGrouper.group(listOf(a, b)).single()
        assertEquals(1, g.downloadIds.size)
        assertEquals(2, g.allIds.size)
    }

    @Test
    fun `multi-file disc entry alone reports its disc count`() {
        val g = MultiDiscGrouper.group(listOf(mgsComplete)).single()
        assertEquals(2, g.discCount)
        assertEquals(listOf(14351), g.downloadIds)
    }

    @Test
    fun `disc markers with different games do not merge across platforms`() {
        val gc = mgsDisc1
        val ps = DiscEntry(7, "Metal Gear Solid: The Twin Snakes", "ps2", 4006,
            listOf("Metal Gear Solid - The Twin Snakes (Disc 1).iso"))
        assertEquals(2, MultiDiscGrouper.group(listOf(gc, ps)).size)
    }

    @Test
    fun `disc markers from different regions do not merge`() {
        val usa = DiscEntry(1, "Some Game", "ps1", 77, listOf("Some Game (USA) (Disc 1).cue", "Some Game (USA) (Disc 1).bin"))
        val eur = DiscEntry(2, "Some Game", "ps1", 77, listOf("Some Game (Europe) (Disc 1).cue", "Some Game (Europe) (Disc 1).bin"))
        assertEquals(2, MultiDiscGrouper.group(listOf(usa, eur)).size)
    }

    @Test
    fun `normalization strips disc markers from names`() {
        assertEquals(
            "metal gear solid: the twin snakes",
            MultiDiscGrouper.normalizeName("Metal Gear Solid: The Twin Snakes (Disc 2)"),
        )
        assertEquals(2, MultiDiscGrouper.discNumber("Game (Disc 2).rvz"))
        assertEquals(1, MultiDiscGrouper.discNumber("Game (CD1).bin"))
        assertEquals(null, MultiDiscGrouper.discNumber("Game (USA).rvz"))
    }
}
