package app.nexstream.player.ui.theme

import android.app.Application
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import app.nexstream.player.ui.theme.ThemeMode
import app.nexstream.player.ui.theme.getThemeModeFlow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────

@HiltViewModel
class ThemeViewModel @Inject constructor(
    application: Application,
    val themeManager: ThemeManager,
) : AndroidViewModel(application) {

    data class ThemePair(val dark: NexStreamTheme, val light: NexStreamTheme, val highContrast: NexStreamTheme)

    val themes: StateFlow<ThemePair> = combine(
        themeManager.darkTheme,
        themeManager.lightTheme,
        themeManager.highContrastTheme,
    ) { dark, light, hc -> ThemePair(dark, light, hc) }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.Eagerly,
        initialValue = ThemePair(ThemeDefaults.dark, ThemeDefaults.light, ThemeDefaults.dark),
    )
}

// ─────────────────────────────────────────────────────────────
// Composable provider
// ─────────────────────────────────────────────────────────────

@Composable
fun NexStreamThemeProvider(
    themeViewModel: ThemeViewModel,
    initialThemeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val themes    by themeViewModel.themes.collectAsState()
    // Use applicationContext so we always read from the same DataStore instance
    // as AppearanceScreen, regardless of which Activity context is current.
    val context            = LocalContext.current.applicationContext
    val themeMode          by context.getThemeModeFlow().collectAsState(initial = initialThemeMode)
    val userFontScaleOrNull by context.getFontScaleFlow().collectAsState(initial = null)
    val userFontWeightOrNull by context.getFontWeightFlow().collectAsState(initial = null)
    val systemDark = isSystemInDarkTheme()

    val isDark = when (themeMode) {
        ThemeMode.DARK         -> true
        ThemeMode.LIGHT        -> false
        ThemeMode.HIGH_CONTRAST -> true
        ThemeMode.SYSTEM       -> systemDark
    }

    val activeRaw = when (themeMode) {
        ThemeMode.HIGH_CONTRAST -> themes.highContrast
        else -> if (isDark) themes.dark else themes.light
    }
    android.util.Log.d("ThemeDebug", "active.global.surface=${activeRaw.global.surface} themes.light.global.surface=${themes.light.global.surface}")

    // Apply user overrides — falls back to JSON theme values when not yet set by the user
    val s           = userFontScaleOrNull  ?: activeRaw.typography.scale
    val fontWeight  = userFontWeightOrNull ?: activeRaw.typography.weight
    val active      = activeRaw.copy(typography = activeRaw.typography.copy(scale = s, weight = fontWeight))
    val g        = active.global

    val colorScheme = remember(g, isDark) {
        if (isDark) darkColorScheme(
            primary            = g.primary,
            primaryContainer   = g.primaryContainer,
            onPrimary          = g.onPrimary,
            onPrimaryContainer = g.onPrimaryContainer,
            surface            = g.surface,
            surfaceVariant     = g.surfaceVariant,
            onSurface          = g.onSurface,
            onSurfaceVariant   = g.onSurfaceVariant,
            outline            = g.outline,
            error              = g.error,
            background         = g.surface,
            onBackground       = g.onSurface,
        ) else lightColorScheme(
            primary            = g.primary,
            primaryContainer   = g.primaryContainer,
            onPrimary          = g.onPrimary,
            onPrimaryContainer = g.onPrimaryContainer,
            surface            = g.surface,
            surfaceVariant     = g.surfaceVariant,
            onSurface          = g.onSurface,
            onSurfaceVariant   = g.onSurfaceVariant,
            outline            = g.outline,
            error              = g.error,
            background         = g.surface,
            onBackground       = g.onSurface,
        )
    }

    val uiStyle by context.getUiStyleFlow().collectAsState(initial = UiStyle.CLASSIC)

    // Map active theme to Modern UI semantic tokens
    val nsGradientOverlay = Color.Black.copy(alpha = 0.65f)

    CompositionLocalProvider(
        LocalNexStreamTheme    provides active,
        LocalUiStyle           provides uiStyle,
        LocalNsBackground      provides g.surface,
        LocalNsSurface         provides g.surfaceVariant,
        LocalNsSurfaceFocused  provides g.primaryContainer,
        LocalNsAccent          provides g.primary,
        LocalNsTextPrimary     provides g.onSurface,
        LocalNsTextSecondary   provides g.onSurfaceVariant,
        LocalNsTextOnAccent    provides g.onPrimary,
        LocalNsGradientOverlay provides nsGradientOverlay,
        LocalNsDivider         provides g.outline,
    ) {
        val weight = when (fontWeight) {
            "bold"     -> androidx.compose.ui.text.font.FontWeight.Bold
            "semibold" -> androidx.compose.ui.text.font.FontWeight.SemiBold
            else       -> androidx.compose.ui.text.font.FontWeight.Normal
        }

        val f = active.identity.fontFamily

        val typography = remember(f, s, weight) {
            androidx.compose.material3.Typography(
                displayLarge   = androidx.compose.ui.text.TextStyle(fontFamily = f, fontWeight = weight, fontSize = (57 * s).sp),
                displayMedium  = androidx.compose.ui.text.TextStyle(fontFamily = f, fontWeight = weight, fontSize = (45 * s).sp),
                displaySmall   = androidx.compose.ui.text.TextStyle(fontFamily = f, fontWeight = weight, fontSize = (36 * s).sp),
                headlineLarge  = androidx.compose.ui.text.TextStyle(fontFamily = f, fontWeight = weight, fontSize = (32 * s).sp),
                headlineMedium = androidx.compose.ui.text.TextStyle(fontFamily = f, fontWeight = weight, fontSize = (28 * s).sp),
                headlineSmall  = androidx.compose.ui.text.TextStyle(fontFamily = f, fontWeight = weight, fontSize = (24 * s).sp),
                titleLarge     = androidx.compose.ui.text.TextStyle(fontFamily = f, fontWeight = weight, fontSize = (22 * s).sp),
                titleMedium    = androidx.compose.ui.text.TextStyle(fontFamily = f, fontWeight = weight, fontSize = (16 * s).sp),
                titleSmall     = androidx.compose.ui.text.TextStyle(fontFamily = f, fontWeight = weight, fontSize = (14 * s).sp),
                bodyLarge      = androidx.compose.ui.text.TextStyle(fontFamily = f, fontWeight = weight, fontSize = (16 * s).sp),
                bodyMedium     = androidx.compose.ui.text.TextStyle(fontFamily = f, fontWeight = weight, fontSize = (14 * s).sp),
                bodySmall      = androidx.compose.ui.text.TextStyle(fontFamily = f, fontWeight = weight, fontSize = (12 * s).sp),
                labelLarge     = androidx.compose.ui.text.TextStyle(fontFamily = f, fontWeight = weight, fontSize = (14 * s).sp),
                labelMedium    = androidx.compose.ui.text.TextStyle(fontFamily = f, fontWeight = weight, fontSize = (12 * s).sp),
                labelSmall     = androidx.compose.ui.text.TextStyle(fontFamily = f, fontWeight = weight, fontSize = (11 * s).sp),
            )
        }

        MaterialTheme(colorScheme = colorScheme, typography = typography) {
            content()
        }
    }
}