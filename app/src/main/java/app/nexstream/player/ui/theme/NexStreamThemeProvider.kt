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
import app.nexstream.player.ui.screens.appearance.ThemeMode
import app.nexstream.player.ui.screens.appearance.getThemeModeFlow
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

    data class ThemePair(val dark: NexStreamTheme, val light: NexStreamTheme)

    val themes: StateFlow<ThemePair> = combine(
        themeManager.darkTheme,
        themeManager.lightTheme,
    ) { dark, light -> ThemePair(dark, light) }.stateIn(
        scope        = viewModelScope,
        started      = SharingStarted.Eagerly,
        initialValue = ThemePair(ThemeDefaults.dark, ThemeDefaults.light),
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
    val context   = LocalContext.current.applicationContext
    val themeMode by context.getThemeModeFlow().collectAsState(initial = initialThemeMode)
    val systemDark = isSystemInDarkTheme()

    val isDark = when (themeMode) {
        ThemeMode.DARK   -> true
        ThemeMode.LIGHT  -> false
        ThemeMode.SYSTEM -> systemDark
    }

    val active = if (isDark) themes.dark else themes.light
    android.util.Log.d("ThemeDebug", "active.global.surface=${active.global.surface} themes.light.global.surface=${themes.light.global.surface}")
    val g      = active.global

    val colorScheme = remember(active, isDark) {
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

    CompositionLocalProvider(LocalNexStreamTheme provides active) {
        val weight = if (active.typography.bold)
            androidx.compose.ui.text.font.FontWeight.Bold
        else
            androidx.compose.ui.text.font.FontWeight.Normal

        val s = active.typography.scale
        val f = active.identity.fontFamily

        val typography = remember(active, f, s, weight) {
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