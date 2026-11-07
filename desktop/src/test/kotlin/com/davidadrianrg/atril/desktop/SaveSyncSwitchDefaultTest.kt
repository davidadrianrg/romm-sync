package com.davidadrianrg.atril.desktop

import java.util.prefs.Preferences
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Interruptor general de sync de saves en escritorio: activado por defecto (como Android) y persistente. */
class SaveSyncSwitchDefaultTest {

    private val prefs = Preferences.userNodeForPackage(DesktopConfig::class.java)

    @Before
    fun requireIsolatedConfig() {
        assertTrue(System.getProperty("java.util.prefs.userRoot").orEmpty().contains("test-home"))
        clear()
    }

    @After
    fun clear() {
        prefs.remove("save_sync_enabled")
    }

    @Test
    fun `fresh and existing installs keep save sync on`() {
        assertTrue(DesktopConfig.saveSyncEnabled)
    }

    @Test
    fun `turning it off persists`() {
        DesktopConfig.saveSyncEnabled = false
        assertFalse(DesktopConfig.saveSyncEnabled)
        DesktopConfig.saveSyncEnabled = true
        assertTrue(DesktopConfig.saveSyncEnabled)
    }
}
