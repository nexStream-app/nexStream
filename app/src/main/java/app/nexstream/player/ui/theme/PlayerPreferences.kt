// app/nexstream/player/ui/theme/PlayerPreferences.kt
package app.nexstream.player.ui.theme

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.playerPrefsDataStore by preferencesDataStore(name = "player_prefs")

private val KEY_AUTO_FRAME_RATE  = booleanPreferencesKey("auto_frame_rate")
private val KEY_SMART_BUFFER     = booleanPreferencesKey("smart_buffer")

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