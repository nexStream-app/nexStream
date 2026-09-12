package app.nexstream.player.ui.theme

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.playerPrefsDataStore by preferencesDataStore(name = "player_prefs")

private val KEY_AUTO_FRAME_RATE      = booleanPreferencesKey("auto_frame_rate")
private val KEY_SMART_BUFFER         = booleanPreferencesKey("smart_buffer")
private val KEY_CLOUD_SYNC_ENABLED   = booleanPreferencesKey("cloud_sync_enabled")
private val KEY_AUTO_UPDATE_ENABLED  = booleanPreferencesKey("auto_update_enabled")
private val KEY_DOWNLOADS_LOCATION   = stringPreferencesKey("downloads_location")
private val KEY_DEDUPLICATE_CONTENT  = booleanPreferencesKey("deduplicate_content")
private val KEY_EPG_MINI_PLAYER      = booleanPreferencesKey("epg_mini_player")

fun Context.getAutoFrameRateFlow(): Flow<Boolean> =
    playerPrefsDataStore.data.map { it[KEY_AUTO_FRAME_RATE] ?: true }

fun Context.getSmartBufferFlow(): Flow<Boolean> =
    playerPrefsDataStore.data.map { it[KEY_SMART_BUFFER] ?: true }

suspend fun Context.saveAutoFrameRate(enabled: Boolean) {
    playerPrefsDataStore.edit { it[KEY_AUTO_FRAME_RATE] = enabled }
}

suspend fun Context.saveSmartBuffer(enabled: Boolean) {
    playerPrefsDataStore.edit { it[KEY_SMART_BUFFER] = enabled }
}

fun Context.getCloudSyncEnabledFlow(): Flow<Boolean> =
    playerPrefsDataStore.data.map { it[KEY_CLOUD_SYNC_ENABLED] ?: true }

suspend fun Context.saveCloudSyncEnabled(enabled: Boolean) {
    playerPrefsDataStore.edit { it[KEY_CLOUD_SYNC_ENABLED] = enabled }
}

fun Context.getAutoUpdateEnabledFlow(): Flow<Boolean> =
    playerPrefsDataStore.data.map { it[KEY_AUTO_UPDATE_ENABLED] ?: false }

suspend fun Context.saveAutoUpdateEnabled(enabled: Boolean) {
    playerPrefsDataStore.edit { it[KEY_AUTO_UPDATE_ENABLED] = enabled }
}

fun Context.getDownloadsLocationFlow(): Flow<String> =
    playerPrefsDataStore.data.map { it[KEY_DOWNLOADS_LOCATION] ?: "" }

suspend fun Context.saveDownloadsLocation(path: String) {
    playerPrefsDataStore.edit { it[KEY_DOWNLOADS_LOCATION] = path }
}

fun Context.getDeduplicateContentFlow(): Flow<Boolean> =
    playerPrefsDataStore.data.map { it[KEY_DEDUPLICATE_CONTENT] ?: false }

suspend fun Context.saveDeduplicateContent(enabled: Boolean) {
    playerPrefsDataStore.edit { it[KEY_DEDUPLICATE_CONTENT] = enabled }
}

fun Context.getEpgMiniPlayerFlow(): Flow<Boolean> =
    playerPrefsDataStore.data.map { it[KEY_EPG_MINI_PLAYER] ?: true }

suspend fun Context.saveEpgMiniPlayer(enabled: Boolean) {
    playerPrefsDataStore.edit { it[KEY_EPG_MINI_PLAYER] = enabled }
}

private val KEY_KEYBOARD_FONT_SCALE      = floatPreferencesKey("keyboard_font_scale")
private val KEY_KEYBOARD_BOLD            = booleanPreferencesKey("keyboard_bold")

fun Context.getKeyboardFontScaleFlow(): Flow<Float> =
    playerPrefsDataStore.data.map { it[KEY_KEYBOARD_FONT_SCALE] ?: 1.4f }

suspend fun Context.saveKeyboardFontScale(scale: Float) {
    playerPrefsDataStore.edit { it[KEY_KEYBOARD_FONT_SCALE] = scale }
}

fun Context.getKeyboardBoldFlow(): Flow<Boolean> =
    playerPrefsDataStore.data.map { it[KEY_KEYBOARD_BOLD] ?: true }

suspend fun Context.saveKeyboardBold(bold: Boolean) {
    playerPrefsDataStore.edit { it[KEY_KEYBOARD_BOLD] = bold }
}

private val KEY_MOVIE_SORT_ORDER         = stringPreferencesKey("movie_sort_order")
private val KEY_SERIES_SORT_ORDER        = stringPreferencesKey("series_sort_order")
private val KEY_SPORTS_HIDDEN_CATEGORIES = stringSetPreferencesKey("sports_hidden_categories")
private val KEY_SPORTS_CATEGORY_ORDER    = stringPreferencesKey("sports_category_order")

fun Context.getMovieSortOrderFlow(): Flow<String> =
    playerPrefsDataStore.data.map { it[KEY_MOVIE_SORT_ORDER] ?: "A_Z" }

suspend fun Context.saveMovieSortOrder(order: String) {
    playerPrefsDataStore.edit { it[KEY_MOVIE_SORT_ORDER] = order }
}

fun Context.getSeriesSortOrderFlow(): Flow<String> =
    playerPrefsDataStore.data.map { it[KEY_SERIES_SORT_ORDER] ?: "A_Z" }

suspend fun Context.saveSeriesSortOrder(order: String) {
    playerPrefsDataStore.edit { it[KEY_SERIES_SORT_ORDER] = order }
}

fun Context.getSportsHiddenCategoriesFlow(): Flow<Set<String>> =
    playerPrefsDataStore.data.map { it[KEY_SPORTS_HIDDEN_CATEGORIES] ?: emptySet() }

fun Context.getSportsCategoryOrderFlow(): Flow<List<String>> =
    playerPrefsDataStore.data.map {
        it[KEY_SPORTS_CATEGORY_ORDER]?.split(",")?.filter { s -> s.isNotBlank() } ?: emptyList()
    }

suspend fun Context.saveSportsHiddenCategories(hidden: Set<String>) {
    playerPrefsDataStore.edit { it[KEY_SPORTS_HIDDEN_CATEGORIES] = hidden }
}

suspend fun Context.saveSportsCategoryOrder(order: List<String>) {
    playerPrefsDataStore.edit { it[KEY_SPORTS_CATEGORY_ORDER] = order.joinToString(",") }
}

private val KEY_EXT_PLAYER_LIVE_TV  = stringPreferencesKey("ext_player_live_tv")
private val KEY_EXT_PLAYER_MOVIES   = stringPreferencesKey("ext_player_movies")
private val KEY_EXT_PLAYER_SERIES   = stringPreferencesKey("ext_player_series")
private val KEY_EXT_PLAYER_CATCHUP  = stringPreferencesKey("ext_player_catchup")

fun Context.getExtPlayerLiveTvFlow(): Flow<String>  = playerPrefsDataStore.data.map { it[KEY_EXT_PLAYER_LIVE_TV]  ?: "nexstream" }
suspend fun Context.saveExtPlayerLiveTv(pkg: String)  { playerPrefsDataStore.edit { it[KEY_EXT_PLAYER_LIVE_TV]  = pkg } }

fun Context.getExtPlayerMoviesFlow(): Flow<String>   = playerPrefsDataStore.data.map { it[KEY_EXT_PLAYER_MOVIES]   ?: "nexstream" }
suspend fun Context.saveExtPlayerMovies(pkg: String)   { playerPrefsDataStore.edit { it[KEY_EXT_PLAYER_MOVIES]   = pkg } }

fun Context.getExtPlayerSeriesFlow(): Flow<String>   = playerPrefsDataStore.data.map { it[KEY_EXT_PLAYER_SERIES]   ?: "nexstream" }
suspend fun Context.saveExtPlayerSeries(pkg: String)   { playerPrefsDataStore.edit { it[KEY_EXT_PLAYER_SERIES]   = pkg } }

fun Context.getExtPlayerCatchupFlow(): Flow<String>  = playerPrefsDataStore.data.map { it[KEY_EXT_PLAYER_CATCHUP]  ?: "nexstream" }
suspend fun Context.saveExtPlayerCatchup(pkg: String)  { playerPrefsDataStore.edit { it[KEY_EXT_PLAYER_CATCHUP]  = pkg } }

// ── Cross-device settings sync helpers ───────────────────────────────────────

fun Context.playerPrefsChanges(): Flow<Unit> = playerPrefsDataStore.data.map { Unit }

suspend fun Context.collectSyncablePlayerPrefs(): Map<String, Any> {
    val prefs = playerPrefsDataStore.data.first()
    return buildMap {
        prefs[KEY_AUTO_FRAME_RATE]?.let { put("auto_frame_rate", it) }
        prefs[KEY_SMART_BUFFER]?.let { put("smart_buffer", it) }
        prefs[KEY_DEDUPLICATE_CONTENT]?.let { put("deduplicate_content", it) }
        prefs[KEY_EPG_MINI_PLAYER]?.let { put("epg_mini_player", it) }
        prefs[KEY_KEYBOARD_FONT_SCALE]?.let { put("keyboard_font_scale", it) }
        prefs[KEY_KEYBOARD_BOLD]?.let { put("keyboard_bold", it) }
        prefs[KEY_MOVIE_SORT_ORDER]?.let { put("movie_sort_order", it) }
        prefs[KEY_SERIES_SORT_ORDER]?.let { put("series_sort_order", it) }
        prefs[KEY_EPG_TIME_OFFSET]?.let { put("epg_time_offset_hours", it) }
    }
}

suspend fun Context.applySyncedPlayerPrefs(settings: Map<String, Any?>) {
    playerPrefsDataStore.edit { prefs ->
        (settings["auto_frame_rate"] as? Boolean)?.let { prefs[KEY_AUTO_FRAME_RATE] = it }
        (settings["smart_buffer"] as? Boolean)?.let { prefs[KEY_SMART_BUFFER] = it }
        (settings["deduplicate_content"] as? Boolean)?.let { prefs[KEY_DEDUPLICATE_CONTENT] = it }
        (settings["epg_mini_player"] as? Boolean)?.let { prefs[KEY_EPG_MINI_PLAYER] = it }
        (settings["keyboard_font_scale"] as? Number)?.toFloat()?.let { prefs[KEY_KEYBOARD_FONT_SCALE] = it }
        (settings["keyboard_bold"] as? Boolean)?.let { prefs[KEY_KEYBOARD_BOLD] = it }
        (settings["movie_sort_order"] as? String)?.let { prefs[KEY_MOVIE_SORT_ORDER] = it }
        (settings["series_sort_order"] as? String)?.let { prefs[KEY_SERIES_SORT_ORDER] = it }
        (settings["epg_time_offset_hours"] as? Number)?.toInt()?.let { prefs[KEY_EPG_TIME_OFFSET] = it }
    }
}

// ── App display language ──────────────────────────────────────────────────────
// SharedPreferences (not DataStore) so attachBaseContext can read synchronously
// without runBlocking, and so commit() guarantees the write is on disk before
// recreate() fires.

private fun Context.langPrefs() =
    getSharedPreferences("nexstream_lang", Context.MODE_PRIVATE)

fun Context.getAppLanguageFlow(): Flow<String> =
    kotlinx.coroutines.flow.flow { emit(getAppLanguageBlocking()) }

fun Context.saveAppLanguage(code: String) {
    langPrefs().edit().putString("app_language", code).commit()
}

fun Context.getAppLanguageBlocking(): String =
    langPrefs().getString("app_language", "") ?: ""

// ── EPG timezone offset ───────────────────────────────────────────────────────

private val KEY_EPG_TIME_OFFSET = androidx.datastore.preferences.core.intPreferencesKey("epg_time_offset_hours")

fun Context.getEpgTimeOffsetFlow(): Flow<Int> =
    playerPrefsDataStore.data.map { it[KEY_EPG_TIME_OFFSET] ?: 0 }

suspend fun Context.saveEpgTimeOffset(hours: Int) {
    playerPrefsDataStore.edit { it[KEY_EPG_TIME_OFFSET] = hours.coerceIn(-12, 12) }
}

// ── Stream proxy ──────────────────────────────────────────────────────────────
// OFF = no proxy, BUILTIN = proxy.nexstream.uk, CUSTOM = user-configured

private val KEY_PROXY_MODE     = stringPreferencesKey("proxy_mode")
private val KEY_PROXY_HOST     = stringPreferencesKey("proxy_host")
private val KEY_PROXY_PORT     = androidx.datastore.preferences.core.intPreferencesKey("proxy_port")
private val KEY_PROXY_TYPE     = stringPreferencesKey("proxy_type") // HTTP or SOCKS5
private val KEY_PROXY_USERNAME = stringPreferencesKey("proxy_username")
private val KEY_PROXY_PASSWORD = stringPreferencesKey("proxy_password")

fun Context.getProxyModeFlow(): Flow<String> =
    playerPrefsDataStore.data.map { it[KEY_PROXY_MODE] ?: "OFF" }

fun Context.getProxyHostFlow(): Flow<String> =
    playerPrefsDataStore.data.map { it[KEY_PROXY_HOST] ?: "" }

fun Context.getProxyPortFlow(): Flow<Int> =
    playerPrefsDataStore.data.map { it[KEY_PROXY_PORT] ?: 8080 }

fun Context.getProxyTypeFlow(): Flow<String> =
    playerPrefsDataStore.data.map { it[KEY_PROXY_TYPE] ?: "HTTP" }

fun Context.getProxyUsernameFlow(): Flow<String> =
    playerPrefsDataStore.data.map { it[KEY_PROXY_USERNAME] ?: "" }

fun Context.getProxyPasswordFlow(): Flow<String> =
    playerPrefsDataStore.data.map { it[KEY_PROXY_PASSWORD] ?: "" }

suspend fun Context.saveProxyMode(mode: String) {
    playerPrefsDataStore.edit { it[KEY_PROXY_MODE] = mode }
}

suspend fun Context.saveProxyHost(host: String) {
    playerPrefsDataStore.edit { it[KEY_PROXY_HOST] = host }
}

suspend fun Context.saveProxyPort(port: Int) {
    playerPrefsDataStore.edit { it[KEY_PROXY_PORT] = port }
}

suspend fun Context.saveProxyType(type: String) {
    playerPrefsDataStore.edit { it[KEY_PROXY_TYPE] = type }
}

suspend fun Context.saveProxyUsername(username: String) {
    playerPrefsDataStore.edit { it[KEY_PROXY_USERNAME] = username }
}

suspend fun Context.saveProxyPassword(password: String) {
    playerPrefsDataStore.edit { it[KEY_PROXY_PASSWORD] = password }
}
