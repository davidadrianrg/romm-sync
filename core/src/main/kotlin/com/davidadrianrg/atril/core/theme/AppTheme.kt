package com.davidadrianrg.atril.core.theme

/**
 * Temas de la app, compartidos por Android y escritorio. Los colores son datos
 * puros (ARGB) para que ambas plataformas pinten exactamente lo mismo; cada una
 * los traduce a su `ColorScheme` de Material 3.
 */
enum class AppTheme(val id: String) {
    /** Tema original: papel, plástico y ámbar sobre negro cálido. */
    WARM_ARCHIVE("warm_archive"),

    /** Azul/índigo profundo, oscuro, con acentos fríos. */
    MIDNIGHT("midnight"),
    ;

    companion object {
        val DEFAULT = WARM_ARCHIVE

        /** Id guardado → tema; cualquier valor desconocido o vacío cae en [DEFAULT]. */
        fun fromId(id: String?): AppTheme = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/** Roles de color de un tema, en ARGB (0xAARRGGBB). */
data class ThemePalette(
    val primary: Long, val onPrimary: Long, val primaryContainer: Long, val onPrimaryContainer: Long,
    val secondary: Long, val onSecondary: Long, val secondaryContainer: Long, val onSecondaryContainer: Long,
    val tertiary: Long, val onTertiary: Long, val tertiaryContainer: Long, val onTertiaryContainer: Long,
    val error: Long, val onError: Long, val errorContainer: Long, val onErrorContainer: Long,
    val background: Long, val onBackground: Long,
    val surface: Long, val onSurface: Long, val surfaceVariant: Long, val onSurfaceVariant: Long,
    val outline: Long, val outlineVariant: Long, val scrim: Long,
    val inverseSurface: Long, val inverseOnSurface: Long, val inversePrimary: Long,
    val surfaceDim: Long, val surfaceBright: Long,
    val surfaceContainerLowest: Long, val surfaceContainerLow: Long, val surfaceContainer: Long,
    val surfaceContainerHigh: Long, val surfaceContainerHighest: Long,
    /** Fondo del lanzador (degradado de arriba a abajo) y su panel. */
    val launcherBgTop: Long, val launcherBgBottom: Long, val launcherPanel: Long,
) {
    /** Pares (texto, fondo) que deben ser legibles; los usa el test de contraste. */
    fun readablePairs(): List<Triple<String, Long, Long>> = listOf(
        Triple("onPrimary/primary", onPrimary, primary),
        Triple("onPrimaryContainer/primaryContainer", onPrimaryContainer, primaryContainer),
        Triple("onSecondary/secondary", onSecondary, secondary),
        Triple("onSecondaryContainer/secondaryContainer", onSecondaryContainer, secondaryContainer),
        Triple("onTertiary/tertiary", onTertiary, tertiary),
        Triple("onTertiaryContainer/tertiaryContainer", onTertiaryContainer, tertiaryContainer),
        Triple("onError/error", onError, error),
        Triple("onErrorContainer/errorContainer", onErrorContainer, errorContainer),
        Triple("onBackground/background", onBackground, background),
        Triple("onSurface/surface", onSurface, surface),
        Triple("onSurface/surfaceContainer", onSurface, surfaceContainer),
        Triple("onSurface/surfaceContainerHighest", onSurface, surfaceContainerHighest),
        Triple("onSurfaceVariant/surface", onSurfaceVariant, surface),
        Triple("onSurfaceVariant/surfaceVariant", onSurfaceVariant, surfaceVariant),
        Triple("primary/background", primary, background),
        Triple("secondary/background", secondary, background),
    )
}

object ThemePalettes {

    fun of(theme: AppTheme): ThemePalette = when (theme) {
        AppTheme.WARM_ARCHIVE -> warmArchive
        AppTheme.MIDNIGHT -> midnight
    }

    val warmArchive = ThemePalette(
        primary = 0xFFE7B46A, onPrimary = 0xFF2A1A08, primaryContainer = 0xFF4A3320, onPrimaryContainer = 0xFFF7E8CE,
        secondary = 0xFF8FBF9A, onSecondary = 0xFF10251A, secondaryContainer = 0xFF24402E, onSecondaryContainer = 0xFFC6E8CE,
        tertiary = 0xFFC98A5E, onTertiary = 0xFF2B1608, tertiaryContainer = 0xFF4A2C1A, onTertiaryContainer = 0xFFF4D9C7,
        error = 0xFFE08B7E, onError = 0xFF3A0A06, errorContainer = 0xFF5A211A, onErrorContainer = 0xFFF7D6D0,
        background = 0xFF0B0A09, onBackground = 0xFFF2E6D2,
        surface = 0xFF0B0A09, onSurface = 0xFFF2E6D2, surfaceVariant = 0xFF2A241C, onSurfaceVariant = 0xFFBFB09A,
        outline = 0xFF8A7A64, outlineVariant = 0xFF3A3227, scrim = 0xFF000000,
        inverseSurface = 0xFFF2E6D2, inverseOnSurface = 0xFF2A241C, inversePrimary = 0xFF7A4F1E,
        surfaceDim = 0xFF0B0A09, surfaceBright = 0xFF3A3227,
        surfaceContainerLowest = 0xFF070606, surfaceContainerLow = 0xFF12100D, surfaceContainer = 0xFF191611,
        surfaceContainerHigh = 0xFF221D16, surfaceContainerHighest = 0xFF2C261D,
        launcherBgTop = 0xFF1A1610, launcherBgBottom = 0xFF050403, launcherPanel = 0xFF2A241C,
    )

    val midnight = ThemePalette(
        primary = 0xFF9FB4FF, onPrimary = 0xFF0C1646, primaryContainer = 0xFF28336E, onPrimaryContainer = 0xFFDCE3FF,
        secondary = 0xFF86CFE6, onSecondary = 0xFF00303D, secondaryContainer = 0xFF1B4B5C, onSecondaryContainer = 0xFFC3EBF8,
        tertiary = 0xFFC4A8F0, onTertiary = 0xFF2B1459, tertiaryContainer = 0xFF433072, onTertiaryContainer = 0xFFEBDDFF,
        error = 0xFFFFA8A0, onError = 0xFF4A0A06, errorContainer = 0xFF6A2019, onErrorContainer = 0xFFFFDAD6,
        background = 0xFF070A1A, onBackground = 0xFFE4E7F7,
        surface = 0xFF070A1A, onSurface = 0xFFE4E7F7, surfaceVariant = 0xFF1F2547, onSurfaceVariant = 0xFFB5BBDA,
        outline = 0xFF7C84AC, outlineVariant = 0xFF2C3359, scrim = 0xFF000000,
        inverseSurface = 0xFFE4E7F7, inverseOnSurface = 0xFF1F2547, inversePrimary = 0xFF3F51B5,
        surfaceDim = 0xFF070A1A, surfaceBright = 0xFF2C3359,
        surfaceContainerLowest = 0xFF040612, surfaceContainerLow = 0xFF0C1024, surfaceContainer = 0xFF11162F,
        surfaceContainerHigh = 0xFF181E3C, surfaceContainerHighest = 0xFF212850,
        launcherBgTop = 0xFF121838, launcherBgBottom = 0xFF03040D, launcherPanel = 0xFF1F2547,
    )
}

/** Contraste WCAG 2.x entre dos colores ARGB (1.0 – 21.0). */
object Contrast {
    private fun lin(c: Int): Double {
        val s = c / 255.0
        return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
    }

    fun luminance(argb: Long): Double {
        val r = ((argb shr 16) and 0xFF).toInt()
        val g = ((argb shr 8) and 0xFF).toInt()
        val b = (argb and 0xFF).toInt()
        return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
    }

    fun ratio(a: Long, b: Long): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }
}
