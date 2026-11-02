package com.davidadrianrg.atril.desktop

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TilingWmWorkaroundTest {

    private val swayEnv = mapOf(
        "DISPLAY" to ":0",
        "WAYLAND_DISPLAY" to "wayland-1",
        "SWAYSOCK" to "/run/user/1000/sway-ipc.sock",
    )

    @Test
    fun relaunchesUnderSway() {
        assertTrue(TilingWmWorkaround.needsRelaunch("Linux", swayEnv))
    }

    @Test
    fun relaunchesWhenDesktopNameIsTiling() {
        val env = mapOf("DISPLAY" to ":0", "XDG_CURRENT_DESKTOP" to "Hyprland")
        assertTrue(TilingWmWorkaround.needsRelaunch("Linux", env))
    }

    @Test
    fun doesNotRelaunchTwice() {
        val env = swayEnv + (TilingWmWorkaround.ENV_VAR to "1")
        assertFalse(TilingWmWorkaround.needsRelaunch("Linux", env))
    }

    @Test
    fun leavesReparentingDesktopsAlone() {
        val env = mapOf("DISPLAY" to ":0", "XDG_CURRENT_DESKTOP" to "KDE")
        assertFalse(TilingWmWorkaround.needsRelaunch("Linux", env))
        assertFalse(TilingWmWorkaround.needsRelaunch("Linux", mapOf("DISPLAY" to ":0", "XDG_CURRENT_DESKTOP" to "GNOME")))
    }

    @Test
    fun ignoresOtherPlatformsAndHeadless() {
        assertFalse(TilingWmWorkaround.needsRelaunch("Windows 11", swayEnv))
        assertFalse(TilingWmWorkaround.needsRelaunch("Mac OS X", swayEnv))
        assertFalse(TilingWmWorkaround.needsRelaunch("Linux", mapOf("SWAYSOCK" to "/x")))
    }
}
