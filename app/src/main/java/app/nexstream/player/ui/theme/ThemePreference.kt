package app.nexstream.player.ui.theme

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode(val label: String, val icon: ImageVector) {
    SYSTEM("System", Icons.Default.SettingsBrightness),
    DARK("Dark", Icons.Default.DarkMode),
    LIGHT("Light", Icons.Default.LightMode),
    HIGH_CONTRAST("High Contrast", Icons.Default.DarkMode),
}

val Context.themeModeDataStore: DataStore<Preferences> by preferencesDataStore("theme_mode_prefs")
private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")

fun Context.getThemeModeFlow(): Flow<ThemeMode> =
    applicationContext.themeModeDataStore.data.map { prefs ->
        val stored = ThemeMode.entries.firstOrNull { it.name == prefs[THEME_MODE_KEY] } ?: ThemeMode.DARK
        if (stored == ThemeMode.SYSTEM) ThemeMode.DARK else stored
    }

suspend fun Context.saveThemeMode(mode: ThemeMode) {
    applicationContext.themeModeDataStore.edit { it[THEME_MODE_KEY] = mode.name }
}

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
    val SELECTED_THEME   = stringPreferencesKey("selected_theme")
    val FONT_SCALE       = floatPreferencesKey("font_scale")
    val FONT_BOLD        = booleanPreferencesKey("font_bold")  // legacy — migrated to FONT_WEIGHT
    val FONT_WEIGHT      = stringPreferencesKey("font_weight") // "normal" | "semibold" | "bold"
    val RAIL_ORDER       = stringPreferencesKey("rail_order")
    val RAIL_HIDDEN      = stringPreferencesKey("rail_hidden") // comma-separated hidden route names
    val START_ROUTE      = stringPreferencesKey("start_route")
    val MUSIC_FAVOURITES = stringPreferencesKey("music_favourites") // comma-separated album keys
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

fun Context.getFontScaleFlow(): kotlinx.coroutines.flow.Flow<Float?> =
    themeDataStore.data.map { it[ThemePreferenceKeys.FONT_SCALE] }

suspend fun Context.saveFontScale(scale: Float) {
    themeDataStore.edit { it[ThemePreferenceKeys.FONT_SCALE] = scale }
}

fun Context.getFontBoldFlow(): kotlinx.coroutines.flow.Flow<Boolean?> =
    themeDataStore.data.map { it[ThemePreferenceKeys.FONT_BOLD] }

suspend fun Context.saveFontBold(bold: Boolean) {
    themeDataStore.edit { it[ThemePreferenceKeys.FONT_BOLD] = bold }
}

/** Returns the user's font-weight override: "normal", "semibold", or "bold".
 *  Falls back to migrating the legacy FONT_BOLD boolean (true → "bold"). */
fun Context.getFontWeightFlow(): kotlinx.coroutines.flow.Flow<String?> =
    themeDataStore.data.map { prefs ->
        prefs[ThemePreferenceKeys.FONT_WEIGHT]
            ?: if (prefs[ThemePreferenceKeys.FONT_BOLD] == true) "bold" else null
    }

suspend fun Context.saveFontWeight(weight: String) {
    themeDataStore.edit { prefs ->
        prefs[ThemePreferenceKeys.FONT_WEIGHT] = weight
        prefs.remove(ThemePreferenceKeys.FONT_BOLD) // clear legacy key
    }
}

fun Context.getRailOrderFlow(): Flow<String?> =
    themeDataStore.data.map { it[ThemePreferenceKeys.RAIL_ORDER] }

suspend fun Context.saveRailOrder(order: String) {
    themeDataStore.edit { it[ThemePreferenceKeys.RAIL_ORDER] = order }
}

fun Context.getRailHiddenFlow(): Flow<String?> =
    themeDataStore.data.map { it[ThemePreferenceKeys.RAIL_HIDDEN] }

suspend fun Context.saveRailHidden(hidden: String) {
    themeDataStore.edit { it[ThemePreferenceKeys.RAIL_HIDDEN] = hidden }
}

fun Context.getStartRouteFlow(): Flow<String?> =
    themeDataStore.data.map { it[ThemePreferenceKeys.START_ROUTE] }

suspend fun Context.saveStartRoute(route: String) {
    themeDataStore.edit { it[ThemePreferenceKeys.START_ROUTE] = route }
}

fun Context.getMusicFavouritesFlow(): Flow<String?> =
    themeDataStore.data.map { it[ThemePreferenceKeys.MUSIC_FAVOURITES] }

suspend fun Context.saveMusicFavourites(keys: String) {
    themeDataStore.edit { it[ThemePreferenceKeys.MUSIC_FAVOURITES] = keys }
}

private val ONBOARDING_DONE_KEY = booleanPreferencesKey("onboarding_done")

fun Context.getOnboardingDoneFlow(): kotlinx.coroutines.flow.Flow<Boolean> =
    themeDataStore.data.map { it[ONBOARDING_DONE_KEY] ?: false }

suspend fun Context.saveOnboardingDone(done: Boolean) {
    themeDataStore.edit { it[ONBOARDING_DONE_KEY] = done }
}

// ── Cross-device settings sync helpers ───────────────────────────────────────

// themeModeDataStore is intentionally excluded — theme_mode is per-profile, synced via ProfileSyncManager
fun Context.themePrefsChanges(): Flow<Unit> = themeDataStore.data.map { Unit }

suspend fun Context.collectSyncableThemePrefs(): Map<String, Any> {
    val themePrefs = themeDataStore.data.first()
    return buildMap {
        themePrefs[ThemePreferenceKeys.SELECTED_THEME]?.let { put("selected_theme", it) }
        themePrefs[ThemePreferenceKeys.RAIL_ORDER]?.let    { put("rail_order", it) }
        themePrefs[ThemePreferenceKeys.RAIL_HIDDEN]?.let   { put("rail_hidden", it) }
        themePrefs[ThemePreferenceKeys.START_ROUTE]?.let   { put("start_route", it) }
        // theme_mode, font_scale, font_weight are per-profile — synced via ProfileSyncManager only
    }
}

suspend fun Context.applySyncedThemePrefs(settings: Map<String, Any?>) {
    themeDataStore.edit { prefs ->
        (settings["selected_theme"] as? String)?.let { prefs[ThemePreferenceKeys.SELECTED_THEME] = it }
        (settings["rail_order"] as? String)?.let  { prefs[ThemePreferenceKeys.RAIL_ORDER] = it }
        (settings["rail_hidden"] as? String)?.let { prefs[ThemePreferenceKeys.RAIL_HIDDEN] = it }
        (settings["start_route"] as? String)?.let { prefs[ThemePreferenceKeys.START_ROUTE] = it }
        // theme_mode, font_scale, font_weight are per-profile — not applied from device-level settings
    }
}

// ── Per-profile notification preferences ─────────────────────────────────────

val Context.notificationPrefsDataStore: DataStore<Preferences> by preferencesDataStore("notification_prefs")

private fun adminNotifsKey(profileId: String) = booleanPreferencesKey("admin_notifs_$profileId")

fun Context.getAdminNotificationsEnabledFlow(profileId: String): Flow<Boolean> =
    notificationPrefsDataStore.data.map { it[adminNotifsKey(profileId)] ?: true }

suspend fun Context.saveAdminNotificationsEnabled(profileId: String, enabled: Boolean) {
    notificationPrefsDataStore.edit { it[adminNotifsKey(profileId)] = enabled }
}