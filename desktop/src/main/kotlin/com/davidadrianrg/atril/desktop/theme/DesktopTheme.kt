package com.davidadrianrg.atril.desktop.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.davidadrianrg.atril.core.theme.AppTheme
import com.davidadrianrg.atril.core.theme.ThemePalettes

// ─────────────────────────────────────────────────────────────────────────
// Temas: las paletas ("Warm Archive" y "Midnight") viven en :core
// (ThemePalettes) y se comparten con Android; aquí solo se traducen a un
// ColorScheme de Material 3. Ambos son oscuros (pantallas de handheld y salón).
// ─────────────────────────────────────────────────────────────────────────

private fun c(argb: Long) = Color(argb.toInt())

fun colorSchemeFor(theme: AppTheme): ColorScheme {
    val p = ThemePalettes.of(theme)
    return darkColorScheme(
        primary = c(p.primary), onPrimary = c(p.onPrimary),
        primaryContainer = c(p.primaryContainer), onPrimaryContainer = c(p.onPrimaryContainer),
        secondary = c(p.secondary), onSecondary = c(p.onSecondary),
        secondaryContainer = c(p.secondaryContainer), onSecondaryContainer = c(p.onSecondaryContainer),
        tertiary = c(p.tertiary), onTertiary = c(p.onTertiary),
        tertiaryContainer = c(p.tertiaryContainer), onTertiaryContainer = c(p.onTertiaryContainer),
        error = c(p.error), onError = c(p.onError),
        errorContainer = c(p.errorContainer), onErrorContainer = c(p.onErrorContainer),
        background = c(p.background), onBackground = c(p.onBackground),
        surface = c(p.surface), onSurface = c(p.onSurface),
        surfaceVariant = c(p.surfaceVariant), onSurfaceVariant = c(p.onSurfaceVariant),
        outline = c(p.outline), outlineVariant = c(p.outlineVariant), scrim = c(p.scrim),
        inverseSurface = c(p.inverseSurface), inverseOnSurface = c(p.inverseOnSurface),
        inversePrimary = c(p.inversePrimary),
        surfaceDim = c(p.surfaceDim), surfaceBright = c(p.surfaceBright),
        surfaceContainerLowest = c(p.surfaceContainerLowest), surfaceContainerLow = c(p.surfaceContainerLow),
        surfaceContainer = c(p.surfaceContainer), surfaceContainerHigh = c(p.surfaceContainerHigh),
        surfaceContainerHighest = c(p.surfaceContainerHighest),
    )
}

/** Colores del lanzador que no existen en Material; siguen al tema activo. */
val LocalLauncherColors = androidx.compose.runtime.staticCompositionLocalOf {
    ThemePalettes.of(AppTheme.DEFAULT)
}

// Escala tipográfica idéntica a la de Android (Type.kt) para paridad visual.
private val DisplayFont = FontFamily.Serif

val DesktopTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.25).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = DisplayFont,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)

@Composable
fun RomMSyncDesktopTheme(theme: AppTheme = AppTheme.DEFAULT, content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalLauncherColors provides ThemePalettes.of(theme)) {
        MaterialTheme(
            colorScheme = colorSchemeFor(theme),
            typography = DesktopTypography,
            content = content,
        )
    }
}
