package com.davidadrianrg.atril.desktop

import org.junit.Assert.assertEquals
import org.junit.Test

class UiScaleTest {
    @Test fun noScaleAt1080() = assertEquals(1f, desktopUiScale(1080, 1f), 0f)
    @Test fun scalesAt1440() = assertEquals(1.5f, desktopUiScale(1440, 1f), 0f)
    @Test fun scalesAt4k() = assertEquals(2f, desktopUiScale(2160, 1f), 0f)
    @Test fun respectsSystemScale() = assertEquals(1f, desktopUiScale(1440, 2f), 0f)
    @Test fun narrowWindowNotScaled() = assertEquals(1f, desktopUiScale(1380, 1f, 853), 0f)
    @Test fun wide1440Scaled() = assertEquals(1.5f, desktopUiScale(1440, 1f, 2560), 0f)
}
