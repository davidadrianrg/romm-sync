package com.davidadrianrg.atril.ui.viewmodel

import com.davidadrianrg.atril.domain.model.ErrorKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySearchGuardTest {

    private fun reload(
        newSearch: String? = null,
        currentSearch: String? = null,
        hasPlatform: Boolean = true,
        isLoading: Boolean = false,
        hasRoms: Boolean = false,
    ) = shouldReloadForSearch(newSearch, currentSearch, hasPlatform, isLoading, hasRoms)

    @Test
    fun skipsDuplicateWhileSamePlatformLoadIsRunning() {
        assertFalse(reload(isLoading = true))
    }

    @Test
    fun skipsDuplicateOnceRomsAreLoaded() {
        assertFalse(reload(hasRoms = true))
    }

    @Test
    fun retriesWhenSameSearchLeftNothingLoaded() {
        assertTrue(reload(isLoading = false, hasRoms = false))
    }

    @Test
    fun reloadsWhenSearchTextChanges() {
        assertTrue(reload(newSearch = "mario", currentSearch = null, hasRoms = true))
        assertTrue(reload(newSearch = null, currentSearch = "mario", isLoading = true))
    }

    @Test
    fun alwaysLoadsWithoutSelectedPlatformState() {
        assertTrue(reload(hasPlatform = false, hasRoms = true, isLoading = true))
    }

    @Test
    fun onlyServerAndNetworkErrorsAreTransient() {
        assertTrue(ErrorKind.SERVER.isTransient())
        assertTrue(ErrorKind.NETWORK.isTransient())
        assertFalse(ErrorKind.AUTH.isTransient())
        assertFalse(ErrorKind.NOT_FOUND.isTransient())
        assertFalse(ErrorKind.UNKNOWN.isTransient())
    }
}
