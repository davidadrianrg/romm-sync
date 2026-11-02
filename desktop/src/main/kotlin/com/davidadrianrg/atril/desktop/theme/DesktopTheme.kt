package com.davidadrianrg.atril.desktop.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────────────────
// Paleta "Warm Archive" — mismo esquema que la app Android, portado a
// Compose Desktop para que ambas plataformas compartan identidad visual.
// Dark-first; la app desktop es siempre oscura (pantallas de handheld y
// salón), así que solo se define el esquema oscuro.
// ─────────────────────────────────────────────────────────────────────────

private val Primary = Color(0xFFE7B46A)
private val OnPrimary = Color(0xFF2A1A08)
private val PrimaryContainer = Color(0xFF4A3320)
private val OnPrimaryContainer = Color(0xFFF7E8CE)

private val Secondary = Color(0xFF8FBF9A)
private val OnSecondary = Color(0xFF10251A)
private val SecondaryContainer = Color(0xFF24402E)
private val OnSecondaryContainer = Color(0xFFC6E8CE)

private val Tertiary = Color(0xFFC98A5E)
private val OnTertiary = Color(0xFF2B1608)
private val TertiaryContainer = Color(0xFF4A2C1A)
private val OnTertiaryContainer = Color(0xFFF4D9C7)

private val ErrorColor = Color(0xFFE08B7E)
private val OnError = Color(0xFF3A0A06)
private val ErrorContainer = Color(0xFF5A211A)
private val OnErrorContainer = Color(0xFFF7D6D0)

private val Background = Color(0xFF0B0A09)
private val OnBackground = Color(0xFFF2E6D2)
private val Surface = Color(0xFF0B0A09)
private val OnSurface = Color(0xFFF2E6D2)
private val SurfaceVariant = Color(0xFF2A241C)
private val OnSurfaceVariant = Color(0xFFBFB09A)
private val Outline = Color(0xFF8A7A64)
private val OutlineVariant = Color(0xFF3A3227)
private val Scrim = Color(0xFF000000)
private val InverseSurface = Color(0xFFF2E6D2)
private val InverseOnSurface = Color(0xFF2A241C)
private val InversePrimary = Color(0xFF7A4F1E)

private val SurfaceDim = Color(0xFF0B0A09)
private val SurfaceBright = Color(0xFF3A3227)
private val SurfaceContainerLowest = Color(0xFF070606)
private val SurfaceContainerLow = Color(0xFF12100D)
private val SurfaceContainer = Color(0xFF191611)
private val SurfaceContainerHigh = Color(0xFF221D16)
private val SurfaceContainerHighest = Color(0xFF2C261D)

private val MidnightArcadeScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    error = ErrorColor,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline,
    outlineVariant = OutlineVariant,
    scrim = Scrim,
    inverseSurface = InverseSurface,
    inverseOnSurface = InverseOnSurface,
    inversePrimary = InversePrimary,
    surfaceDim = SurfaceDim,
    surfaceBright = SurfaceBright,
    surfaceContainerLowest = SurfaceContainerLowest,
    surfaceContainerLow = SurfaceContainerLow,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = SurfaceContainerHighest,
)

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
fun RomMSyncDesktopTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MidnightArcadeScheme,
        typography = DesktopTypography,
        content = content,
    )
}
