package com.davidadrianrg.atril.core.launch

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * Coreografía de la animación «insertar cartucho/disco» (Android y escritorio
 * comparten esta función; cada plataforma solo traduce la [Pose] a su capa
 * gráfica). Es pura: [progress] de 0 a 1 → pose, sin estado, así se prueba.
 *
 * El medio **no gira**: baja en vertical hacia una consola oscura en el borde
 * inferior y se hunde en su ranura (la consola se dibuja por encima, así el
 * cartucho «desaparece» dentro).
 *
 *  - 0.00–0.20  aparece el fondo y la consola sube desde el borde inferior
 *  - 0.10–0.72  descenso hasta apoyarse sobre la ranura (acelera y frena)
 *  - 0.72–0.94  empuje final: se hunde en la ranura
 *  - 0.66–1.00  el LED y el borde de la ranura se iluminan
 *  - 0.86–1.00  pulso de impacto al encajar (la consola se estremece un pelo)
 */
object InsertionMotion {

    /** Duración recomendada del progreso 0→1, en milisegundos (1–1,5 s). */
    const val DURATION_MS = 1300

    data class Pose(
        /** Opacidad del fondo y del título. */
        val appear: Float,
        /** 0 = consola fuera de pantalla, 1 = en su sitio. */
        val console: Float,
        /** 0 = flotando arriba, 1 = apoyado sobre la ranura. */
        val drop: Float,
        /** 0 = apoyado, 1 = totalmente hundido en la ranura. */
        val seat: Float,
        /** Escala uniforme del medio (se aleja un poco al bajar). */
        val scale: Float,
        /** Sombra proyectada sobre la ranura. */
        val shadowAlpha: Float,
        val shadowScale: Float,
        /** Brillo de la ranura/LED (0–1) y pulso de encaje (0–1). */
        val glow: Float,
        val impact: Float,
    )

    fun easeInOutCubic(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).toDouble().pow(3.0).toFloat() / 2f
    }

    fun easeInCubic(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * x
    }

    private fun seg(p: Float, start: Float, end: Float): Float =
        ((p - start) / (end - start)).coerceIn(0f, 1f)

    fun pose(progress: Float): Pose {
        val p = progress.coerceIn(0f, 1f)
        val appear = easeInOutCubic(seg(p, 0f, 0.20f))
        val console = easeInOutCubic(seg(p, 0f, 0.20f))
        val drop = easeInOutCubic(seg(p, 0.10f, 0.72f))
        val seat = easeInCubic(seg(p, 0.72f, 0.94f))
        val glow = easeInOutCubic(seg(p, 0.66f, 1f))
        val impact = sin(PI.toFloat() * seg(p, 0.86f, 1f)).coerceAtLeast(0f)

        return Pose(
            appear = appear,
            console = console,
            drop = drop,
            seat = seat,
            scale = 1f - 0.16f * drop,
            shadowAlpha = (0.15f + 0.5f * drop) * (1f - seat) * appear,
            shadowScale = 0.7f + 0.3f * drop,
            glow = glow,
            impact = impact,
        )
    }
}
