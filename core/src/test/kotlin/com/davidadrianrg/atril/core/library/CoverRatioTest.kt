package com.davidadrianrg.atril.core.library

import org.junit.Assert.assertEquals
import org.junit.Test

class CoverRatioTest {
    @Test fun realRatioIsPreserved() {
        assertEquals(0.7f, CoverRatio.of(700, 1000), 0.001f)
        assertEquals(1.0f, CoverRatio.of(500, 500), 0.001f) // square (CD, GBA label)
    }
    @Test fun invalidSizesFallBack() {
        assertEquals(CoverRatio.DEFAULT, CoverRatio.of(0, 100), 0f)
        assertEquals(1.5f, CoverRatio.of(-1, 5, fallback = 1.5f), 0f)
    }
    @Test fun extremesAreClamped() {
        assertEquals(2.5f, CoverRatio.of(5000, 100), 0f)
        assertEquals(0.4f, CoverRatio.of(100, 5000), 0f)
    }
    @Test fun medianIgnoresInvalid() {
        assertEquals(0.7f, CoverRatio.median(listOf(0.7f, 0f, 1f, 0.5f)), 0f)
        assertEquals(CoverRatio.DEFAULT, CoverRatio.median(emptyList()), 0f)
    }
}
