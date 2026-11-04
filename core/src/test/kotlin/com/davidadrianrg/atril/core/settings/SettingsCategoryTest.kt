package com.davidadrianrg.atril.core.settings

import com.davidadrianrg.atril.core.i18n.I18n
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsCategoryTest {
    @Test fun everyCategoryHasATranslationInBothLanguages() {
        SettingsCategory.entries.forEach { c ->
            assertNotEquals("missing en: ${c.labelKey}", c.labelKey, I18n.tr(c.labelKey))
        }
    }

    @Test fun orderIsStable() {
        assertEquals(
            listOf("CONNECTION", "APPEARANCE", "LOCAL", "SYNC", "DOWNLOADS", "ADVANCED", "ABOUT"),
            SettingsCategory.entries.map { it.name },
        )
    }
}
