package app.nexstream.player.subtitle

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.subtitleDataStore: DataStore<Preferences> by preferencesDataStore(name = "subtitle_prefs")

@Singleton
class SubtitlePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val PREFERRED_LANGUAGES = stringSetPreferencesKey("preferred_languages")

    val preferredLanguages: Flow<Set<String>> = context.subtitleDataStore.data
        .map { prefs -> prefs[PREFERRED_LANGUAGES] ?: setOf("en") }

    suspend fun savePreferredLanguages(languages: Set<String>) {
        context.subtitleDataStore.edit { prefs ->
            prefs[PREFERRED_LANGUAGES] = languages
        }
    }
}