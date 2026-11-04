package com.davidadrianrg.atril.desktop

import com.davidadrianrg.atril.core.input.AxisNormalizer
import com.davidadrianrg.atril.core.input.PadAction
import com.davidadrianrg.atril.core.input.PadKeyMap
import com.davidadrianrg.atril.core.input.StickRepeater
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.swing.SwingUtilities

/**
 * Gamepad on Linux without extra dependencies: reads the kernel's evdev nodes of joystick-like
 * devices (`/proc/bus/input/devices` lists them with a `js*` handler). Needs read access to
 * `/dev/input/event*` (group `input`); without a pad or without access everything stays as is.
 *
 * Pad actions are turned into the same AWT key events a keyboard would send (arrows, Enter,
 * Esc, F10), so focus movement and activation go through the normal Compose paths.
 */
class GamepadInput(private val onAction: (PadAction) -> Unit) {
    private val threads = mutableListOf<Thread>()
    @Volatile private var running = false

    /** Event nodes of devices that look like gamepads (have a `js` handler). */
    fun findDevices(): List<File> {
        val text = runCatching { File("/proc/bus/input/devices").readText() }.getOrNull() ?: return emptyList()
        return parseDevices(text).map { File("/dev/input/$it") }.filter { it.canRead() }
    }

    fun start() {
        if (running) return
        running = true
        findDevices().forEach { dev ->
            threads += Thread({ read(dev) }, "gamepad-${dev.name}").apply { isDaemon = true; start() }
        }
    }

    fun stop() {
        running = false
        threads.forEach { it.interrupt() }
        threads.clear()
    }

    private fun read(dev: File) {
        val stickL = StickRepeater()
        val normX = AxisNormalizer(); val normY = AxisNormalizer()
        var sx = 0f; var sy = 0f
        val buf = ByteArray(EVENT_SIZE)
        runCatching {
            FileInputStream(dev).use { input ->
                while (running) {
                    var n = 0
                    while (n < EVENT_SIZE) { val r = input.read(buf, n, EVENT_SIZE - n); if (r < 0) return; n += r }
                    val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
                    bb.position(EVENT_SIZE - 8)
                    val type = bb.short.toInt() and 0xFFFF
                    val code = bb.short.toInt() and 0xFFFF
                    val value = bb.int
                    val action: PadAction? = when (type) {
                        PadKeyMap.EV_KEY -> PadKeyMap.fromEvdevKey(code, value)
                        PadKeyMap.EV_ABS -> when (code) {
                            PadKeyMap.ABS_HAT0X, PadKeyMap.ABS_HAT0Y -> PadKeyMap.fromEvdevHat(code, value)
                            PadKeyMap.ABS_X -> { sx = normX.normalize(value); stickL.update(sx, sy, System.currentTimeMillis()) }
                            PadKeyMap.ABS_Y -> { sy = normY.normalize(value); stickL.update(sx, sy, System.currentTimeMillis()) }
                            else -> null
                        }
                        else -> null
                    }
                    action?.let(onAction)
                }
            }
        }
    }

    companion object {
        // struct input_event on 64-bit Linux: timeval (16) + type (2) + code (2) + value (4).
        private const val EVENT_SIZE = 24

        /** `eventN` names of the devices whose Handlers line has a `js*` entry. */
        fun parseDevices(procBusInputDevices: String): List<String> =
            procBusInputDevices.split("\n\n").mapNotNull { block ->
                val handlers = block.lineSequence().firstOrNull { it.startsWith("H: Handlers=") } ?: return@mapNotNull null
                val parts = handlers.removePrefix("H: Handlers=").trim().split(Regex("\\s+"))
                if (parts.none { it.startsWith("js") }) return@mapNotNull null
                parts.firstOrNull { it.startsWith("event") }
            }

        /** AWT key code a pad action is delivered as. */
        fun keyCodeFor(a: PadAction): Int = when (a) {
            PadAction.UP -> KeyEvent.VK_UP
            PadAction.DOWN -> KeyEvent.VK_DOWN
            PadAction.LEFT -> KeyEvent.VK_LEFT
            PadAction.RIGHT -> KeyEvent.VK_RIGHT
            PadAction.SELECT -> KeyEvent.VK_ENTER
            PadAction.BACK -> KeyEvent.VK_ESCAPE
            PadAction.MENU -> KeyEvent.VK_F10
            PadAction.DETAILS -> KeyEvent.VK_F9
            PadAction.PREV_PAGE -> KeyEvent.VK_PAGE_UP
            PadAction.NEXT_PAGE -> KeyEvent.VK_PAGE_DOWN
        }

        /** Delivers [a] to the focused component exactly like a keyboard press+release would. */
        fun dispatchAsKey(a: PadAction) {
            SwingUtilities.invokeLater {
                val target: Component = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
                    ?: KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
                    ?: return@invokeLater
                if (System.getenv("ATRIL_PAD_SELFTEST") != null) System.err.println("[pad] $a → ${target.javaClass.simpleName}")
                val code = keyCodeFor(a)
                val now = System.currentTimeMillis()
                target.dispatchEvent(KeyEvent(target, KeyEvent.KEY_PRESSED, now, 0, code, KeyEvent.CHAR_UNDEFINED))
                target.dispatchEvent(KeyEvent(target, KeyEvent.KEY_RELEASED, now + 1, 0, code, KeyEvent.CHAR_UNDEFINED))
            }
        }
    }
}
