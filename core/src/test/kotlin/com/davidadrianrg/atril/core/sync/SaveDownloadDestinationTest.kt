package com.davidadrianrg.atril.core.sync

import org.junit.Assert.*
import org.junit.Test

class SaveDownloadDestinationTest {
    @Test fun customFolderWinsWhenValid() {
        assertEquals("/storage/x/saves", SaveDownloadDestination.resolve("/storage/x/saves/", "/sync"))
    }
    @Test fun invalidOrMissingCustomFallsBackToSyncPath() {
        assertEquals("/sync", SaveDownloadDestination.resolve(null, "/sync"))
        assertEquals("/sync", SaveDownloadDestination.resolve("", "/sync"))
        assertEquals("/sync", SaveDownloadDestination.resolve("relative/dir", "/sync"))
        assertEquals("/sync", SaveDownloadDestination.resolve("/a/../etc", "/sync"))
    }
    @Test fun validation() {
        assertTrue(SaveDownloadDestination.isValidFolder("/a/b"))
        assertFalse(SaveDownloadDestination.isValidFolder("a/b"))
        assertFalse(SaveDownloadDestination.isValidFolder("/a/../b"))
        assertFalse(SaveDownloadDestination.isValidFolder("  "))
    }
}
