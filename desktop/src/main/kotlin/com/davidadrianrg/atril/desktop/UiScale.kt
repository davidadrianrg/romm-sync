package com.davidadrianrg.atril.desktop

/**
 * Factor extra de escala de la UI según la altura real de la ventana en píxeles.
 * Si el sistema ya escala (GDK_SCALE/DPI, [systemDensity] > 1.25) no se añade nada.
 * A 1440p el texto Material (14sp) se queda minúsculo; ×1,5 lo equipara a Android.
 */
fun desktopUiScale(windowHeightPx: Int, systemDensity: Float, windowWidthPx: Int = Int.MAX_VALUE): Float {
    if (systemDensity > 1.25f) return 1f
    // Una ventana estrecha (tiling) no debe agrandarse: usa la dimensión menor ponderada.
    val basis = minOf(windowHeightPx, (windowWidthPx.toLong() * 9 / 16).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    return when {
        basis >= 2000 -> 2f
        basis >= 1300 -> 1.5f
        else -> 1f
    }
}
