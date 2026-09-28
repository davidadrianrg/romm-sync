package es.davidrg.rommsync.desktop.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────────────────
// Paleta "Midnight Arcade" — mismo esquema que la app Android, portado a
// Compose Desktop para que ambas plataformas compartan identidad visual.
// Dark-first; la app desktop es siempre oscura (pantallas de handheld y
// salón), así que solo se define el esquema oscuro.
// ─────────────────────────────────────────────────────────────────────────

private val Primary = Color(0xFF9FCAFF)
private val OnPrimary = Color(0xFF003258)
private val PrimaryContainer = Color(0xFF00497D)
private val OnPrimaryContainer = Color(0xFFD2E4FF)

private val Secondary = Color(0xFF7FD98C)
private val OnSecondary = Color(0xFF00390F)
private val SecondaryContainer = Color(0xFF005319)
private val OnSecondaryContainer = Color(0xFF9BF6A6)

private val Tertiary = Color(0xFFFFB877)
private val OnTertiary = Color(0xFF4D2700)
private val TertiaryContainer = Color(0xFF6E3900)
private val OnTertiaryContainer = Color(0xFFFFDCC1)

private val ErrorColor = Color(0xFFFFB4AB)
private val OnError = Color(0xFF690005)
private val ErrorContainer = Color(0xFF93000A)
private val OnErrorContainer = Color(0xFFFFDAD6)

private val Background = Color(0xFF0B0F14)
private val OnBackground = Color(0xFFDFE3E8)
private val Surface = Color(0xFF0B0F14)
private val OnSurface = Color(0xFFDFE3E8)
private val SurfaceVariant = Color(0xFF40484F)
private val OnSurfaceVariant = Color(0xFFBFC8D2)
private val Outline = Color(0xFF89929C)
private val OutlineVariant = Color(0xFF3E464F)
private val Scrim = Color(0xFF000000)
private val InverseSurface = Color(0xFFDFE3E8)
private val InverseOnSurface = Color(0xFF2C3137)
private val InversePrimary = Color(0xFF0B5FA5)

private val SurfaceDim = Color(0xFF0B0F14)
private val SurfaceBright = Color(0xFF31363D)
private val SurfaceContainerLowest = Color(0xFF060A0E)
private val SurfaceContainerLow = Color(0xFF12171D)
private val SurfaceContainer = Color(0xFF161C23)
private val SurfaceContainerHigh = Color(0xFF20262E)
private val SurfaceContainerHighest = Color(0xFF2B313A)

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
val DesktopTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.25).sp,
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleLarge = TextStyle(
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
