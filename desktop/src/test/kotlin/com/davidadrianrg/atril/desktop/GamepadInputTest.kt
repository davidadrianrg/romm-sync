package com.davidadrianrg.atril.desktop

import com.davidadrianrg.atril.core.input.PadAction
import org.junit.Assert.assertEquals
import org.junit.Test
import java.awt.event.KeyEvent

class GamepadInputTest {
    private val proc = """
I: Bus=0003 Vendor=045e Product=028e Version=0110
N: Name="Microsoft X-Box 360 pad"
H: Handlers=event12 js0
B: PROP=0

I: Bus=0011 Vendor=0001 Product=0001 Version=ab41
N: Name="AT Translated Set 2 keyboard"
H: Handlers=sysrq kbd event3 leds
B: PROP=0

I: Bus=0003 Vendor=054c Product=09cc Version=8111
N: Name="Wireless Controller"
H: Handlers=event13 js1
""".trimIndent()

    @Test fun onlyJoystickLikeDevicesAreChosen() {
        assertEquals(listOf("event12", "event13"), GamepadInput.parseDevices(proc))
    }

    @Test fun actionsBecomeKeyboardKeys() {
        assertEquals(KeyEvent.VK_ENTER, GamepadInput.keyCodeFor(PadAction.SELECT))
        assertEquals(KeyEvent.VK_ESCAPE, GamepadInput.keyCodeFor(PadAction.BACK))
        assertEquals(KeyEvent.VK_F10, GamepadInput.keyCodeFor(PadAction.MENU))
        assertEquals(KeyEvent.VK_LEFT, GamepadInput.keyCodeFor(PadAction.LEFT))
        assertEquals(KeyEvent.VK_F9, GamepadInput.keyCodeFor(PadAction.DETAILS))
        assertEquals(KeyEvent.VK_PAGE_UP, GamepadInput.keyCodeFor(PadAction.PREV_PAGE))
        assertEquals(KeyEvent.VK_PAGE_DOWN, GamepadInput.keyCodeFor(PadAction.NEXT_PAGE))
    }
}
