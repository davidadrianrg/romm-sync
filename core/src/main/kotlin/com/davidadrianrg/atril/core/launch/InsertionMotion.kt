package com.davidadrianrg.atril.core.launch

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * Coreografía de la animación «insertar cartucho/disco» (Android y escritorio
 * comparten esta función; cada plataforma solo traduce la [Pose] a su capa
 * gráfica). Es pura: [progress] de 0 a 1 → pose, sin estado, así se prueba.
 *
 * Fases (progreso):
 *  - 0.00–0.15  aparece el fondo y el objeto flota sobre la ranura
 *  - 0.04–0.62  giro 3D (cartucho: una vuelta sobre su eje vertical; disco: gira)
 *  - 0.12–0.78  aproximación en arco hacia la ranura (acelera y frena)
 *  - 0.58–0.86  el cartucho se inclina hasta quedar «de pie» sobre la ranura
 *  - 0.72–0.92  descenso final: se aplasta y se hunde en la ranura
 *  - 0.70–1.00  la ranura se ilumina; pulso de impacto al encajar (~0.9)
 */
object InsertionMotion {

    /** Duración recomendada del progreso 0→1, en milisegundos (1–1,5 s). */
    const val DURATION_MS = 1300

    data class Pose(
        /** Opacidad del fondo y del título. */
        val appear: Float,
        /** 0 = posición inicial (flotando), 1 = junto a la ranura. */
        val travel: Float,
        /** Arco: elevación extra durante el trayecto, en fracción del tamaño del objeto. */
        val arc: Float,
        /** Escala uniforme del objeto. */
        val scale: Float,
        /** Aplastamiento vertical al hundirse (1 = sin aplastar). */
        val squashY: Float,
        val rotationX: Float,
        val rotationY: Float,
        val rotationZ: Float,
        /** Opacidad del objeto. */
        val alpha: Float,
        /** Sombra proyectada sobre la ranura. */
        val shadowAlpha: Float,
        val shadowScale: Float,
        /** Brillo de la ranura (0–1) y pulso de encaje (0–1). */
        val glow: Float,
        val impact: Float,
    )

    fun easeInOutCubic(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).toDouble().pow(3.0).toFloat() / 2f
    }

    fun easeOutCubic(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return 1f - (1f - x).toDouble().pow(3.0).toFloat()
    }

    private fun seg(p: Float, start: Float, end: Float): Float =
        ((p - start) / (end - start)).coerceIn(0f, 1f)

    fun pose(progress: Float, optical: Boolean): Pose {
        val p = progress.coerceIn(0f, 1f)
        val appear = easeInOutCubic(seg(p, 0f, 0.15f))
        val travel = easeInOutCubic(seg(p, 0.12f, 0.78f))
        val spin = easeOutCubic(seg(p, 0.04f, 0.62f))
        val tilt = easeInOutCubic(seg(p, 0.58f, 0.86f))
        val sink = easeInOutCubic(seg(p, 0.72f, 0.92f))
        val glow = easeInOutCubic(seg(p, 0.70f, 1f))
        val impact = sin(PI.toFloat() * seg(p, 0.84f, 1f)).coerceAtLeast(0f)
        val hover = easeInOutCubic(seg(p, 0.02f, 0.28f))

        return Pose(
            appear = appear,
            travel = travel,
            arc = 0.10f * sin(PI.toFloat() * travel),
            scale = (1f + 0.06f * hover) * (1f - 0.42f * travel),
            squashY = 1f - 0.55f * sink,
            rotationX = if (optical) 0f else 58f * tilt,
            rotationY = if (optical) 0f else 360f * spin,
            rotationZ = if (optical) 540f * travel else 0f,
            alpha = (1f - sink) * appear,
            shadowAlpha = (0.25f + 0.45f * travel) * (1f - sink) * appear,
            shadowScale = 0.65f + 0.35f * travel,
            glow = glow,
            impact = impact,
        )
    }
}
