package com.davidadrianrg.atril.core.scrape

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoPhysicalMediaTest {
    private fun ask(
        enabled: Boolean = true, connected: Boolean = true, local: Boolean = false,
        has: Boolean = false, busy: Boolean = false,
    ) = AutoPhysicalMedia.shouldRequest(enabled, connected, local, has, busy)

    @Test fun requestsWhenEverythingAllows() = assertTrue(ask())
    @Test fun offByDefaultMeansNothing() = assertFalse(ask(enabled = false))
    @Test fun neverWithoutRomM() = assertFalse(ask(connected = false))
    @Test fun neverForLocalGames() = assertFalse(ask(local = true))
    @Test fun skipsWhenAlreadyHasMedia() = assertFalse(ask(has = true))
    @Test fun skipsWhenARequestIsRunning() = assertFalse(ask(busy = true))
}
