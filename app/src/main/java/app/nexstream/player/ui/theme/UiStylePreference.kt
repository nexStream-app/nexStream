package app.nexstream.player.ui.theme

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

enum class UiStyle { CLASSIC, MODERN }

val LocalUiStyle = staticCompositionLocalOf { UiStyle.CLASSIC }

private val UI_STYLE_KEY = stringPreferencesKey("ui_style")

fun Context.getUiStyleFlow(): Flow<UiStyle> =
    themeDataStore.data.map { prefs ->
        try { UiStyle.valueOf(prefs[UI_STYLE_KEY] ?: UiStyle.CLASSIC.name) }
        catch (_: Exception) { UiStyle.CLASSIC }
    }

suspend fun Context.saveUiStyle(style: UiStyle) {
    themeDataStore.edit { it[UI_STYLE_KEY] = style.name }
}

@Singleton
class UiStylePreference @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val uiStyle: StateFlow<UiStyle> = context.getUiStyleFlow()
        .stateIn(scope, SharingStarted.Eagerly, UiStyle.CLASSIC)

    suspend fun setUiStyle(style: UiStyle) = context.saveUiStyle(style)
}
