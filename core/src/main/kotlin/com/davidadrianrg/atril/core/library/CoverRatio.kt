package com.davidadrianrg.atril.core.library

/** Aspect ratio (width / height) of a cover image, kept sane for layout. */
object CoverRatio {
    const val DEFAULT = 2f / 3f
    private const val MIN = 0.4f
    private const val MAX = 2.5f

    /** Real ratio of a decoded bitmap; [fallback] when the size is unknown/invalid. */
    fun of(width: Int, height: Int, fallback: Float = DEFAULT): Float =
        if (width <= 0 || height <= 0) fallback else (width.toFloat() / height).coerceIn(MIN, MAX)

    /** Median of known ratios (for placeholders before each cover has loaded). */
    fun median(ratios: Collection<Float>, fallback: Float = DEFAULT): Float {
        val s = ratios.filter { it > 0f }.sorted()
        return if (s.isEmpty()) fallback else s[s.size / 2]
    }
}
