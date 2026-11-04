package com.davidadrianrg.atril.core.sync

import com.davidadrianrg.atril.core.sync.WatchScope.DownloadedGame
import org.junit.Assert.*
import org.junit.Test

class WatchScopeTest {
    @Test fun onlyPlatformsWithSyncedDownloadsAreWatched() {
        val active = WatchScope.activePlatforms(
            listOf(
                DownloadedGame("gba", false), DownloadedGame("gba", true),
                DownloadedGame("n64", true), DownloadedGame("ngc", false),
            ),
        )
        assertEquals(setOf("gba", "ngc"), active) // n64: todos excluidos del sync
        assertTrue(WatchScope.activePlatforms(emptyList()).isEmpty())
    }

    @Test fun romAndCachePathsAreExcluded() {
        assertTrue(WatchScope.isExcludedPath("/storage/emulated/0/ROMs/gba"))
        assertTrue(WatchScope.isExcludedPath("/storage/emulated/0/RetroArch/thumbnails/GBA"))
        assertTrue(WatchScope.isExcludedPath("/storage/emulated/0/Android/data/org.dolphinemu.dolphinemu/files/GC"))
        assertTrue(WatchScope.isExcludedPath("/x/Android/obb/foo"))
        assertFalse(WatchScope.isExcludedPath("/storage/emulated/0/RetroArch/saves"))
        assertFalse(WatchScope.isExcludedPath("/storage/emulated/0/dolphin/GC/USA/Card A"))
    }

    @Test fun descentIsShallowAndSkipsNoiseDirs() {
        assertTrue(WatchScope.shouldDescend("USA", 1))
        assertTrue(WatchScope.shouldDescend("Card A", 2))
        assertFalse(WatchScope.shouldDescend("deep", WatchScope.MAX_DEPTH + 1))
        assertFalse(WatchScope.shouldDescend("cache", 1))
        assertFalse(WatchScope.shouldDescend("Shaders", 1))
        assertFalse(WatchScope.shouldDescend(".thumbnails", 1))
    }

    @Test fun limitsAreSmall() {
        assertTrue(WatchScope.MAX_DIRS <= 60)
        assertTrue(WatchScope.MAX_DEPTH <= 2)
    }
}
