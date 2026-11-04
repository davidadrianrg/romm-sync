package com.davidadrianrg.atril.core.input

import kotlin.math.abs

/** Platform-neutral gamepad intents (Android and Linux map their raw events to these). */
enum class PadAction { UP, DOWN, LEFT, RIGHT, SELECT, BACK, MENU }

/**
 * Mapping (documented in Settings → Controls):
 *  D-pad / left stick = move focus · A (south) = select · B (east) = back · Start/Menu/Home = open menu.
 */
object PadKeyMap {
    // android.view.KeyEvent codes (kept as ints so this module stays pure JVM).
    private const val DPAD_UP = 19
    private const val DPAD_DOWN = 20
    private const val DPAD_LEFT = 21
    private const val DPAD_RIGHT = 22
    private const val DPAD_CENTER = 23
    private const val BUTTON_A = 96
    private const val BUTTON_B = 97
    private const val BUTTON_MODE = 110
    private const val BUTTON_START = 108

    /** Android gamepad key code → intent. D-pad arrows are left to Compose's own focus system. */
    fun fromAndroidKeyCode(code: Int): PadAction? = when (code) {
        DPAD_UP -> PadAction.UP
        DPAD_DOWN -> PadAction.DOWN
        DPAD_LEFT -> PadAction.LEFT
        DPAD_RIGHT -> PadAction.RIGHT
        DPAD_CENTER, BUTTON_A -> PadAction.SELECT
        BUTTON_B -> PadAction.BACK
        BUTTON_START, BUTTON_MODE -> PadAction.MENU
        else -> null
    }

    // Linux evdev codes (linux/input-event-codes.h).
    const val EV_KEY = 1
    const val EV_ABS = 3
    private const val BTN_SOUTH = 304   // A (Xbox) / Cross
    private const val BTN_EAST = 305    // B (Xbox) / Circle
    private const val BTN_START = 315
    private const val BTN_MODE = 316
    private const val BTN_DPAD_UP = 544
    private const val BTN_DPAD_DOWN = 545
    private const val BTN_DPAD_LEFT = 546
    private const val BTN_DPAD_RIGHT = 547
    const val ABS_X = 0
    const val ABS_Y = 1
    const val ABS_HAT0X = 16
    const val ABS_HAT0Y = 17

    /** Button press (value 1 only; releases and autorepeat are ignored) → intent. */
    fun fromEvdevKey(code: Int, value: Int): PadAction? {
        if (value != 1) return null
        return when (code) {
            BTN_SOUTH -> PadAction.SELECT
            BTN_EAST -> PadAction.BACK
            BTN_START, BTN_MODE -> PadAction.MENU
            BTN_DPAD_UP -> PadAction.UP
            BTN_DPAD_DOWN -> PadAction.DOWN
            BTN_DPAD_LEFT -> PadAction.LEFT
            BTN_DPAD_RIGHT -> PadAction.RIGHT
            else -> null
        }
    }

    /** Hat axis (D-pad reported as ABS_HAT0X/Y with -1/0/1) → intent on the edge. */
    fun fromEvdevHat(code: Int, value: Int): PadAction? = when {
        code == ABS_HAT0X && value < 0 -> PadAction.LEFT
        code == ABS_HAT0X && value > 0 -> PadAction.RIGHT
        code == ABS_HAT0Y && value < 0 -> PadAction.UP
        code == ABS_HAT0Y && value > 0 -> PadAction.DOWN
        else -> null
    }
}

/**
 * Turns analog stick positions (-1..1) into discrete, auto-repeating directions:
 * fires when the stick crosses [threshold], then repeats after [firstRepeatMs] / [repeatMs].
 * Pure and time-injected so it can be tested; one instance per stick.
 */
class StickRepeater(
    private val threshold: Float = 0.6f,
    private val firstRepeatMs: Long = 380,
    private val repeatMs: Long = 130,
) {
    private var held: PadAction? = null
    private var nextAt = 0L

    /** Feed the latest position; returns the action to emit now (or null). */
    fun update(x: Float, y: Float, nowMs: Long): PadAction? {
        val dir: PadAction? = when {
            abs(x) < threshold && abs(y) < threshold -> null
            abs(x) >= abs(y) -> if (x < 0) PadAction.LEFT else PadAction.RIGHT
            else -> if (y < 0) PadAction.UP else PadAction.DOWN
        }
        if (dir == null) { held = null; return null }
        if (dir != held) { held = dir; nextAt = nowMs + firstRepeatMs; return dir }
        if (nowMs >= nextAt) { nextAt = nowMs + repeatMs; return dir }
        return null
    }
}

/** Normalises a raw evdev stick value to -1..1 without reading the device's absinfo. */
class AxisNormalizer {
    private var min = Int.MAX_VALUE
    private var max = Int.MIN_VALUE

    fun normalize(raw: Int): Float {
        if (raw < min) min = raw
        if (raw > max) max = raw
        // Signed 16-bit style axis (Xbox, most pads): any negative or >255 reading.
        if (min < 0 || max > 255) return (raw / 32768f).coerceIn(-1f, 1f)
        // Only 0..255 seen so far: it's an unsigned 8-bit axis (e.g. DualShock 4) once the stick
        // has actually travelled (≥200); until then tiny values are treated as idle noise.
        return if (max >= 200) ((raw - 127.5f) / 127.5f).coerceIn(-1f, 1f) else raw / 32768f
    }
}
