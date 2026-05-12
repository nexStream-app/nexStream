package app.nexstream.player.ui.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class AppTheme {
    NEXSTREAM,

    LIGHTBLUE,
    HIGH_CONTRAST,
    CRIMSON,
    FOREST,
    PURPLE,
    AMBER
}

val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(name = "theme_prefs")

object ThemePreferenceKeys {
    val SELECTED_THEME = stringPreferencesKey("selected_theme")
}

fun Context.getThemeFlow(): Flow<AppTheme> = themeDataStore.data.map { prefs ->
    val name = prefs[ThemePreferenceKeys.SELECTED_THEME] ?: AppTheme.NEXSTREAM.name
    try {
        // Migrate old theme names
        when (name) {
            "TIVIMATE" -> AppTheme.NEXSTREAM
            "SKY" -> AppTheme.LIGHTBLUE
            else -> AppTheme.valueOf(name)
        }
    } catch (e: Exception) { AppTheme.NEXSTREAM }
}

suspend fun Context.saveTheme(theme: AppTheme) {
    themeDataStore.edit { prefs ->
        prefs[ThemePreferenceKeys.SELECTED_THEME] = theme.name
    }
}