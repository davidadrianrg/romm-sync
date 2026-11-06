package com.davidadrianrg.atril.core.library

/** Short platform label for cards whose cover is missing or still loading. */
object PlatformAbbreviation {
    private const val MAX_LENGTH = 4

    /** Slugs separate words with hyphens ("neo-geo-pocket"); a cut must not leave one showing. */
    fun of(slug: String): String =
        slug.filter(Char::isLetterOrDigit).uppercase().take(MAX_LENGTH)
}
