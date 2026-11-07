package com.davidadrianrg.atril.desktop

import java.util.prefs.Preferences
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Valor por defecto del interruptor "Usar ES-DE" en escritorio, incluida la migración de instalaciones previas. */
class EsdeSwitchDefaultTest {

    private val prefs = Preferences.userNodeForPackage(DesktopConfig::class.java)

    @Before
    fun requireIsolatedConfig() {
        assertTrue(System.getProperty("java.util.prefs.userRoot").orEmpty().contains("test-home"))
        clear()
    }

    @After
    fun clear() {
        prefs.remove("esde_export_enabled")
        prefs.remove("esde_data_dir")
    }

    @Test
    fun `fresh install starts off, like Android`() {
        assertFalse(DesktopConfig.esdeExportEnabled)
    }

    @Test
    fun `install that already exported keeps the ES-DE settings visible`() {
        DesktopConfig.esdeDataDir = "/tmp/es-de"
        assertTrue(DesktopConfig.esdeExportEnabled)
    }

    @Test
    fun `explicit choice wins over the migration`() {
        DesktopConfig.esdeDataDir = "/tmp/es-de"
        DesktopConfig.esdeExportEnabled = false
        assertFalse(DesktopConfig.esdeExportEnabled)
    }
}
