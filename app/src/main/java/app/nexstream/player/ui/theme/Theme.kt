package app.nexstream.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── nexStream theme (default) ─────────────────────────────────────────────────
private val NexStreamColorScheme = darkColorScheme(
    primary = Color(0xFF00B8D4),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF1565C0),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFF00B8D4),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF1E1E1E),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFF4CAF50),
    onTertiary = Color(0xFF000000),
    background = Color(0xFF0F0F0F),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF181818),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF242424),
    onSurfaceVariant = Color(0xFFCCCCCC),
    outline = Color(0xFF333333),
    outlineVariant = Color(0xFF2A2A2A),
    error = Color(0xFFCF6679),
    onError = Color(0xFFFFFFFF)
)

// ── Navy theme (Sky-inspired) ─────────────────────────────────────────────────
// Dark navy background, Sky blue accents, yellow highlight for selected items
// ── Navy theme (Sky-inspired) ─────────────────────────────────────────────────
private val NavyColorScheme = darkColorScheme(
    primary = Color(0xFF0365C7),           // Sky blue
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFCCE0C),  // Yellow — selected/highlighted
    onPrimaryContainer = Color(0xFF000000),
    secondary = Color(0xFF0365C7),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF021830),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFFFCCE0C),
    onTertiary = Color(0xFF000000),
    background = Color(0xFF010810),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF03111F),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF0A1E35),
    onSurfaceVariant = Color(0xFFCCDDEE),
    outline = Color(0xFF1A3A5C),
    outlineVariant = Color(0xFF0F2440),
    error = Color(0xFFFF5555),
    onError = Color(0xFFFFFFFF)
)

// ── High Contrast theme ────────────────────────────────────────────────────────
private val HighContrastColorScheme = darkColorScheme(
    primary = Color(0xFFFFD700),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF403500),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFF00E5FF),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF003540),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFF00FF88),
    onTertiary = Color(0xFF000000),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF0D0D0D),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFFFFFFFF),
    outline = Color(0xFFFFD700),
    outlineVariant = Color(0xFF666600),
    error = Color(0xFFFF4444),
    onError = Color(0xFF000000)
)

// ── Crimson theme ──────────────────────────────────────────────────────────────
private val CrimsonColorScheme = darkColorScheme(
    primary = Color(0xFFE53935),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF7F0000),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFFFF6D6D),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF1C0A0A),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFFFF8A65),
    onTertiary = Color(0xFF000000),
    background = Color(0xFF0D0808),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF160C0C),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF221010),
    onSurfaceVariant = Color(0xFFCCBBBB),
    outline = Color(0xFF3D1515),
    outlineVariant = Color(0xFF2A0E0E),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF000000)
)

// ── Forest theme ───────────────────────────────────────────────────────────────
private val ForestColorScheme = darkColorScheme(
    primary = Color(0xFF4CAF50),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF1B5E20),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFF81C784),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF0A160A),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFF26A69A),
    onTertiary = Color(0xFF000000),
    background = Color(0xFF070D07),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF0D140D),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF162016),
    onSurfaceVariant = Color(0xFFBBCCBB),
    outline = Color(0xFF2A3D2A),
    outlineVariant = Color(0xFF1A2A1A),
    error = Color(0xFFCF6679),
    onError = Color(0xFFFFFFFF)
)

// ── Purple theme ───────────────────────────────────────────────────────────────
private val PurpleColorScheme = darkColorScheme(
    primary = Color(0xFFCE93D8),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF6A1B9A),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFFBA68C8),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF120A18),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFF9575CD),
    onTertiary = Color(0xFF000000),
    background = Color(0xFF0A070D),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF110D18),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF1C1424),
    onSurfaceVariant = Color(0xFFC8BBCC),
    outline = Color(0xFF3A2250),
    outlineVariant = Color(0xFF271638),
    error = Color(0xFFCF6679),
    onError = Color(0xFFFFFFFF)
)

// ── Amber theme ────────────────────────────────────────────────────────────────
private val AmberColorScheme = darkColorScheme(
    primary = Color(0xFFFFB300),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF7A4F00),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFFFFCC02),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF18120A),
    onSecondaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFFFF8F00),
    onTertiary = Color(0xFF000000),
    background = Color(0xFF0D0A05),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF16120A),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF241C0E),
    onSurfaceVariant = Color(0xFFCCC4AA),
    outline = Color(0xFF403010),
    outlineVariant = Color(0xFF2A200A),
    error = Color(0xFFCF6679),
    onError = Color(0xFFFFFFFF)
)

@Composable
fun NexStreamTheme(
    appTheme: AppTheme = AppTheme.NEXSTREAM,
    content: @Composable () -> Unit
) {
    val colorScheme = when (appTheme) {
        AppTheme.NEXSTREAM -> NexStreamColorScheme
        AppTheme.LIGHTBLUE -> NavyColorScheme
        AppTheme.HIGH_CONTRAST -> HighContrastColorScheme
        AppTheme.CRIMSON -> CrimsonColorScheme
        AppTheme.FOREST -> ForestColorScheme
        AppTheme.PURPLE -> PurpleColorScheme
        AppTheme.AMBER -> AmberColorScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}