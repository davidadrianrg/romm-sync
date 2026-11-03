package com.davidadrianrg.atril.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import com.davidadrianrg.atril.core.theme.AppTheme
import com.davidadrianrg.atril.core.theme.ThemePalettes

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    scrim = LightScrim,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    inversePrimary = LightInversePrimary,
    surfaceDim = LightSurfaceDim,
    surfaceBright = LightSurfaceBright,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
)

private fun c(argb: Long) = Color(argb.toInt())

/** Esquema oscuro de [theme]; la paleta vive en :core y es la misma que en escritorio. */
internal fun darkSchemeFor(theme: AppTheme) = ThemePalettes.of(theme).let { p ->
    darkColorScheme(
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

@Composable
fun RomMSyncTheme(
    // La identidad de la app es oscura y cálida (papel, plástico, ámbar).
    // El tema claro sigue disponible para quien lo fuerce desde código.
    darkTheme: Boolean = true,
    theme: AppTheme = AppTheme.DEFAULT,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) darkSchemeFor(theme) else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Edge-to-edge: barras transparentes, solo ajustamos el contraste
            // de los iconos según el tema activo.
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalLauncherColors provides ThemePalettes.of(theme)) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}
