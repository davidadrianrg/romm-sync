package com.davidadrianrg.atril.core.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GamepadTest {
    @Test fun androidKeysMapToIntents() {
        assertEquals(PadAction.SELECT, PadKeyMap.fromAndroidKeyCode(96))  // BUTTON_A
        assertEquals(PadAction.SELECT, PadKeyMap.fromAndroidKeyCode(23))  // DPAD_CENTER
        assertEquals(PadAction.BACK, PadKeyMap.fromAndroidKeyCode(97))    // BUTTON_B
        assertEquals(PadAction.DETAILS, PadKeyMap.fromAndroidKeyCode(108)) // BUTTON_START
        assertEquals(PadAction.MENU, PadKeyMap.fromAndroidKeyCode(109))    // BUTTON_SELECT
        assertEquals(PadAction.MENU, PadKeyMap.fromAndroidKeyCode(110))    // BUTTON_MODE
        assertEquals(PadAction.PREV_PAGE, PadKeyMap.fromAndroidKeyCode(102)) // L1
        assertEquals(PadAction.NEXT_PAGE, PadKeyMap.fromAndroidKeyCode(103)) // R1
        assertEquals(PadAction.LEFT, PadKeyMap.fromAndroidKeyCode(21))
        assertNull(PadKeyMap.fromAndroidKeyCode(29))                       // 'A' key: not a pad button
    }

    @Test fun evdevButtonsMapOnlyOnPress() {
        assertEquals(PadAction.SELECT, PadKeyMap.fromEvdevKey(304, 1))
        assertNull(PadKeyMap.fromEvdevKey(304, 0)) // release
        assertNull(PadKeyMap.fromEvdevKey(304, 2)) // autorepeat
        assertEquals(PadAction.BACK, PadKeyMap.fromEvdevKey(305, 1))
        assertEquals(PadAction.DETAILS, PadKeyMap.fromEvdevKey(315, 1))
        assertEquals(PadAction.MENU, PadKeyMap.fromEvdevKey(314, 1))
        assertEquals(PadAction.PREV_PAGE, PadKeyMap.fromEvdevKey(310, 1))
        assertEquals(PadAction.NEXT_PAGE, PadKeyMap.fromEvdevKey(311, 1))
        assertEquals(PadAction.UP, PadKeyMap.fromEvdevKey(544, 1))
        assertNull(PadKeyMap.fromEvdevKey(999, 1))
    }

    @Test fun evdevHatMapsEdges() {
        assertEquals(PadAction.LEFT, PadKeyMap.fromEvdevHat(PadKeyMap.ABS_HAT0X, -1))
        assertEquals(PadAction.DOWN, PadKeyMap.fromEvdevHat(PadKeyMap.ABS_HAT0Y, 1))
        assertNull(PadKeyMap.fromEvdevHat(PadKeyMap.ABS_HAT0X, 0)) // centered
    }

    @Test fun stickRepeatsWithInitialDelayAndNeverInDeadzone() {
        val s = StickRepeater(threshold = 0.6f, firstRepeatMs = 400, repeatMs = 100)
        assertNull(s.update(0.3f, 0.2f, 0))               // inside the deadzone
        assertEquals(PadAction.RIGHT, s.update(0.9f, 0f, 10)) // crosses the threshold → fires once
        assertNull(s.update(0.9f, 0f, 200))               // held, before first repeat
        assertEquals(PadAction.RIGHT, s.update(0.9f, 0f, 420))
        assertNull(s.update(0.9f, 0f, 480))
        assertEquals(PadAction.RIGHT, s.update(0.9f, 0f, 530))
        assertNull(s.update(0f, 0f, 600))                 // release
        assertEquals(PadAction.LEFT, s.update(-1f, 0f, 610)) // new direction fires immediately
    }

    @Test fun stickPicksTheDominantAxis() {
        assertEquals(PadAction.DOWN, StickRepeater().update(0.4f, 0.95f, 0))
        assertEquals(PadAction.UP, StickRepeater().update(-0.5f, -0.9f, 0))
    }

    @Test fun axisNormalizerHandlesSignedAndUnsignedRanges() {
        val signed = AxisNormalizer()
        assertEquals(0f, signed.normalize(0), 0.01f)
        assertEquals(1f, signed.normalize(32767), 0.01f)
        assertEquals(-1f, signed.normalize(-32768), 0.01f)
        val u8 = AxisNormalizer()
        assertEquals(1f, u8.normalize(255), 0.01f)
        assertEquals(-1f, u8.normalize(0), 0.01f)
        assertEquals(0f, u8.normalize(128), 0.02f)
    }

    @Test fun platformFilterCyclesThroughAllAndEachSlug() {
        val slugs = listOf("gba", "n64", "ngc")
        assertEquals("gba", PlatformCycle.step(null, slugs, +1))
        assertEquals("n64", PlatformCycle.step("gba", slugs, +1))
        assertNull(PlatformCycle.step("ngc", slugs, +1))          // wraps to "all"
        assertEquals("ngc", PlatformCycle.step(null, slugs, -1))  // L1 from "all" → last
        assertEquals("gba", PlatformCycle.step("n64", slugs, -1))
        assertNull(PlatformCycle.step("x", emptyList(), +1))
        assertEquals("gba", PlatformCycle.step("vanished", slugs, +1)) // unknown current acts as "all"
    }
}
