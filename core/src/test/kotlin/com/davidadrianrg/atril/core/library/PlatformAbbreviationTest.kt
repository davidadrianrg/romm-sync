package com.davidadrianrg.atril.core.library

import org.junit.Assert.assertEquals
import org.junit.Test

class PlatformAbbreviationTest {
    @Test fun shortSlugsAreKeptWhole() {
        assertEquals("GBA", PlatformAbbreviation.of("gba"))
        assertEquals("ACPC", PlatformAbbreviation.of("acpc"))
    }

    @Test fun longSlugsAreCut() {
        assertEquals("GENE", PlatformAbbreviation.of("genesis"))
    }

    @Test fun separatorsNeverShow() {
        assertEquals("NEOG", PlatformAbbreviation.of("neo-geo-pocket"))
        assertEquals("TIC8", PlatformAbbreviation.of("tic-80"))
        assertEquals("GAND", PlatformAbbreviation.of("g-and-w"))
    }

    @Test fun blankSlugGivesEmptyLabel() {
        assertEquals("", PlatformAbbreviation.of(""))
        assertEquals("", PlatformAbbreviation.of("--"))
    }
}
